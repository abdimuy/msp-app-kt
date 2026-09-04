package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.domain.BusquedaDeClientes
import com.example.msp_app.feature.pagos.domain.OrdenDeCobranza
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.VentaEnLista
import com.example.msp_app.feature.pagos.domain.port.PagosPort
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import com.example.msp_app.feature.pagos.domain.port.VisitasPort
import java.time.LocalDate
import javax.inject.Inject

/**
 * La cartera del cobrador: sus clientes con sus ventas dentro, más el "hoy"
 * contra el que los segmentos preguntan si una promesa cae o ya se pasó.
 *
 * [hoy] viaja con los datos y no se vuelve a pedir en la pantalla: si la
 * proyección leyera su propio reloj, el estado derivado y el segmento podrían
 * contestar sobre dos días distintos en la misma pantalla.
 */
data class Cartera(
    val clientes: List<ClienteEnLista>,
    val hoy: LocalDate
)

/**
 * Arma la lista de cobranza **por cliente**, con el estado del periodo de cada
 * venta ya derivado.
 *
 * ## Una sola derivación por carga
 *
 * `EstadoCuentaDeriver` se invoca **una vez con la ruta completa**, no una vez
 * por cliente: la propagación de la Task 13 (una visita de alcance CLIENTE toca
 * todas las ventas de ese domicilio) ya está dentro del deriver, así que darle
 * el conjunto entero es a la vez más correcto y más barato. Y porque
 * [DerivarEstadoDelPeriodo] es quien reenvía las incidencias a telemetría,
 * llamarlo una vez es lo que mantiene la promesa de "una vez por carga, no una
 * por cliente ni una por recomposición" — probado en
 * `IncidenciasUnaVezPorCargaDeListaTest`.
 *
 * ## Tres lecturas, no 3N
 *
 * Ventas (todas), abonos de la ventana (una consulta) y visitas de la ventana
 * (una consulta). Recorrer cliente por cliente con los puertos del detalle
 * serían cientos de viajes a Room para pintar una sola pantalla.
 */
class ReunirCartera @Inject constructor(
    private val ventasPort: VentasPort,
    private val pagosPort: PagosPort,
    private val visitasPort: VisitasPort,
    private val resolverVentanaDeCobro: ResolverVentanaDeCobro,
    private val derivarEstadoDelPeriodo: DerivarEstadoDelPeriodo,
    private val telemetry: Telemetry
) {

    suspend operator fun invoke(): Cartera {
        val ventas = ventasPort.todasLasVentas()
        val ventana = resolverVentanaDeCobro()
        val pagos = ventana?.let { pagosPort.pagosDelPeriodo(it) }.orEmpty()
        val visitas = ventana?.let { visitasPort.visitasDelPeriodo(it) }.orEmpty()
        val estados = derivarEstadoDelPeriodo(ventas, pagos, visitas, ventana)
        reportarVentasSinFecha(ventas)
        return Cartera(clientes = agrupar(ventas, estados), hoy = resolverVentanaDeCobro.hoy())
    }

    /**
     * De una lista de ventas a una lista de clientes. **Aquí se cierra el
     * defecto estructural**: la clave del agrupado es `CLIENTE_ID`, no
     * `DOCTO_CC_ID`, así que un cliente con dos ventas es una fila con dos
     * cuentas y no dos personas distintas.
     *
     * El orden de los clientes es el de su primera venta en la fuente — el
     * "orden natural" que la búsqueda respeta. La ordenación de cobranza es
     * cosa de la proyección de pantalla, no de aquí.
     */
    private fun agrupar(
        ventas: List<DatosDeVenta>,
        estados: Map<Int, EstadoDelPeriodo>
    ): List<ClienteEnLista> = ventas
        .groupBy { it.clienteId }
        .map { (clienteId, suyas) -> cliente(clienteId, suyas, estados) }

    private fun cliente(
        clienteId: Int,
        suyas: List<DatosDeVenta>,
        estados: Map<Int, EstadoDelPeriodo>
    ): ClienteEnLista {
        // Los datos de contacto son del domicilio, no de la venta: se toman de
        // la primera, igual que hace `CargarDetalleCliente`.
        val primera = suyas.first()
        return ClienteEnLista(
            clienteId = clienteId,
            nombre = primera.clienteNombre,
            telefono = primera.telefono,
            direccion = primera.direccion,
            zona = primera.zona,
            saldoTotal = Money.sum(suyas.map { it.saldo }),
            ventas = suyas.map { it.aVentaEnLista(estados[it.ventaId]) },
            textoBuscable = BusquedaDeClientes.textoBuscable(
                // Los MISMOS cinco datos que concatenaba `SalesScreen.kt:68`
                // (nombre, folio, calle, ciudad, teléfono), salvo que los
                // folios de TODAS sus ventas entran al mismo texto: buscar el
                // folio de la segunda venta tiene que traer al cliente.
                listOf(primera.clienteNombre, primera.direccion, primera.telefono) +
                    suyas.map { it.folio }
            )
        )
    }

    /**
     * `Sale.FECHA` ilegible = venta sin fecha = venta que **cambia de lugar en
     * la lista** (cae al final de su grupo, ver `OrdenDeCobranza`). El orden de
     * la lista es el orden del día del cobrador; que se mueva sin una sola
     * señal es exactamente lo que la NORMA DE ERRORES existe para impedir.
     *
     * Se emite el CONTEO, una vez por carga. Sin PII: ni el folio, ni el
     * cliente, ni la cadena ofensora — que es texto que viene del servidor y
     * puede arrastrar datos de negocio.
     */
    private fun reportarVentasSinFecha(ventas: List<DatosDeVenta>) {
        val cuantas = ventas.count { it.fechaVenta == null }
        if (cuantas == 0) return
        telemetry.error(
            code = PagosTelemetria.CODE_VENTA_SIN_FECHA_LEGIBLE,
            message = "Sale.FECHA no se pudo leer; la venta se ordena al final de su grupo",
            props = mapOf(PagosTelemetria.PROP_OCURRENCIAS to cuantas.toString())
        )
    }
}

/**
 * Proyecta una venta a su fila dentro del cliente. Reusa
 * [aVentaDelCliente] —la MISMA proyección que pinta el detalle de cliente— y le
 * agrega el rango que la ordena.
 */
private fun DatosDeVenta.aVentaEnLista(estado: EstadoDelPeriodo?): VentaEnLista = VentaEnLista(
    venta = aVentaDelCliente(estado),
    rango = OrdenDeCobranza.rangoDe(
        saldo = saldo,
        totalVenta = totalVenta,
        enganche = enganche,
        fechaVenta = fechaVenta
    )
)
