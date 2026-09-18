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
            productos = productosDe(venta.folio, venta.descripcion, venta.totalVenta),
            historial = HistorialDePagos(
                semanas = semanas,
                resumen = RitmoDePagos.resumen(semanas),
                meses = RielDePagos.de(pagos),
                totalPagos = pagos.size
            ),
            // La línea del CLIENTE entero, no la de esta cuenta: `cobranza` ya
            // la traía reunida para derivar el estado y se estaba tirando. Ver
            // el KDoc de `DetalleVenta.contactos`.
            contactos = BitacoraDelCliente.de(cobranza.visitas, cobranza.pagos),
            liquidacion = cobranza.liquidaciones[ventaId],
            garantia = garantiasPort.garantiaDe(venta.creditoId)
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
     */
    private suspend fun productosDe(
        folio: String,
        descripcion: String,
        totalVenta: Money
    ): List<ProductoDeVenta> =
        productosPort.productosDe(folio).ifEmpty { deLaDescripcion(descripcion, totalVenta) }

    private fun deLaDescripcion(descripcion: String, totalVenta: Money): List<ProductoDeVenta> {
        val nombres = descripcion.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val importe = totalVenta.takeIf { nombres.size == 1 }
        return nombres.map { ProductoDeVenta(nombre = it, importe = importe) }
    }
}
