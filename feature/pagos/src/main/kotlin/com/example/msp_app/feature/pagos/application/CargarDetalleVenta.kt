package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.BitacoraDelCliente
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.RielDePagos
import com.example.msp_app.feature.pagos.domain.RitmoDePagos
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.HistorialDePagos
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.port.GarantiasPort
import com.example.msp_app.feature.pagos.domain.port.ProductosPort
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import javax.inject.Inject

/**
 * Arma el detalle de una venta: el estado grande del catálogo de ocho arriba, y
 * abajo el historial como **ritmo + riel** (decisión del `task-16-brief.md`).
 *
 * Devuelve `null` si el teléfono no tiene esa venta.
 */
class CargarDetalleVenta @Inject constructor(
    private val ventasPort: VentasPort,
    private val garantiasPort: GarantiasPort,
    private val productosPort: ProductosPort,
    private val reunirCobranzaDelCliente: ReunirCobranzaDelCliente,
    private val clock: AppClock
) {

    /**
     * Solo pide el `ventaId`: desde un pago o un recibo se entra directo a su
     * venta (Task 21) y ahí nadie conoce al cliente. El cliente se resuelve
     * aquí, y con él se reúne la cobranza COMPLETA — hace falta para derivar el
     * estado, porque una visita de alcance cliente se propaga a todas sus
     * ventas (Task 13).
     *
     * **Una sola consulta de productos, reusada dos veces** (ronda de arreglo
     * 1 de la Task 2): la revisión encontró que se pedía `venta.folio` DOS
     * veces — una directa para "productos" (con el respaldo por descripción)
     * y otra dentro del mapa de cuentas, que itera TODAS las ventas del
     * cliente e incluye esta misma. [ProductosPort.productosPorVenta] ya
     * resuelve TODAS las ventas del cliente en un solo lote (ver su KDoc); acá
     * se pide UNA vez y se reparte entre [productosDeLaVenta] (con su
     * respaldo) y [aCuentas] — el mismo patrón que ya usaba
     * [CargarDetalleCliente].
     */
    suspend operator fun invoke(ventaId: Int): DetalleVenta? {
        val cabecera = ventasPort.venta(ventaId) ?: return null
        val cobranza = reunirCobranzaDelCliente(cabecera.clienteId)
        val venta = cobranza.ventas.firstOrNull { it.ventaId == ventaId } ?: return null
        val pagos = cobranza.pagosDe(ventaId)
        val semanas = RitmoDePagos.de(
            pagos = pagos,
            parcialidad = venta.parcialidad,
            hoy = AppTime.todayInBusinessZone(clock)
        )
        val plan = PlanDeAbonos.de(
            totalVenta = venta.totalVenta,
            abonado = venta.abonado,
            parcialidad = venta.parcialidad
        )
        val productosPorVenta = productosPort.productosPorVenta(cobranza.ventas)
        return DetalleVenta(
            ventaId = venta.ventaId,
            folio = venta.folio,
            creditoId = venta.creditoId,
            clienteId = venta.clienteId,
            clienteNombre = venta.clienteNombre,
            titulo = venta.descripcion.ifBlank { venta.folio },
            fechaVenta = venta.fechaVenta,
            saldo = venta.saldo,
            parcialidad = venta.parcialidad,
            frecuencia = venta.frecuencia,
            abonosPagados = plan.pagados,
            abonosTotales = plan.totales,
            avance = plan.avance,
            totalVenta = venta.totalVenta,
            precioContado = venta.precioContado,
            enganche = venta.enganche,
            abonado = venta.abonado,
            vendedor = venta.vendedor,
            estado = cobranza.estados[ventaId] ?: EstadoDelPeriodo.sinTocar(venta.parcialidad),
            productos = productosDeLaVenta(
                productosPorVenta,
                venta.ventaId,
                venta.descripcion,
                venta.totalVenta
            ),
            historial = HistorialDePagos(
                semanas = semanas,
                resumen = RitmoDePagos.resumen(semanas),
                meses = RielDePagos.de(pagos),
                totalPagos = pagos.size
            ),
            // La línea del CLIENTE entero, no la de esta cuenta: `cobranza` ya
            // la traía reunida para derivar el estado y se estaba tirando. Ver
            // el KDoc de `DetalleVenta.contactos`. Por lo mismo, `cuentas` sale
            // de TODAS sus ventas y no solo de `venta.folio` — un contacto de
            // OTRA cuenta del mismo cliente también necesita poder nombrarse.
            contactos = BitacoraDelCliente.de(
                visitas = cobranza.visitas,
                pagos = cobranza.pagos,
                cuentas = productosPorVenta.aCuentas()
            ),
            liquidacion = cobranza.liquidaciones[ventaId],
            garantia = garantiasPort.garantiaDe(venta.creditoId),
            // La nota de ESTA cuenta, no la de la primera del domicilio: a
            // diferencia de `CargarDetalleCliente.notaDeLaVenta` —una
            // aproximación, la de la cuenta que encabeza— aquí no hace falta
            // aproximar nada, la venta ya está resuelta.
            nota = venta.notas.takeIf { it.isNotBlank() }
        )
    }

    /**
     * Los productos de la venta, **con su importe real** cuando la tabla
     * `products` los tiene.
     *
     * [ProductosPort] lee `PRECIO_TOTAL_NETO` renglón por renglón, que es el
     * importe de verdad. Antes esto partía por comas el `GROUP_CONCAT` de la
     * venta y **solo podía atribuir importe con un único producto**: una venta
     * de tres muebles pintaba tres renglones con la columna de dinero en blanco.
     *
     * El corte por comas se queda como **respaldo**, y no por prudencia
     * decorativa: `products` se sincroniza aparte de `sales`, así que un folio
     * puede existir con su descripción y todavía sin sus renglones. En ese caso
     * se prefiere pintar los nombres sin importe a no pintar productos — el
     * cobrador reconoce el mueble por su nombre, que es para lo que mira esta
     * sección. La atribución del total a un producto único se conserva igual:
     * con uno solo, el total de la venta SÍ es su importe.
     *
     * Lee de [productosPorVenta] —ya resuelto para TODAS las ventas del
     * cliente en el `invoke` que llama— en vez de volver a pedirle el folio al
     * puerto: es la misma consulta que ya se hizo para armar el mapa de
     * cuentas, y pedirla dos veces era justo el defecto que esta ronda cerró.
     */
    private fun productosDeLaVenta(
        productosPorVenta: Map<Int, List<ProductoDeVenta>>,
        ventaId: Int,
        descripcion: String,
        totalVenta: Money
    ): List<ProductoDeVenta> =
        productosPorVenta[ventaId].orEmpty().ifEmpty { deLaDescripcion(descripcion, totalVenta) }

    private fun deLaDescripcion(descripcion: String, totalVenta: Money): List<ProductoDeVenta> {
        val nombres = descripcion.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val importe = totalVenta.takeIf { nombres.size == 1 }
        return nombres.map { ProductoDeVenta(nombre = it, importe = importe) }
    }
}
