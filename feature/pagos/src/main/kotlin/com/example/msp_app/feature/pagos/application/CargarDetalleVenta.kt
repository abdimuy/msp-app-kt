package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.PlanDeAbonos
import com.example.msp_app.feature.pagos.domain.RielDePagos
import com.example.msp_app.feature.pagos.domain.RitmoDePagos
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.HistorialDePagos
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.port.GarantiasPort
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
            productos = productosDe(venta.descripcion, venta.totalVenta),
            historial = HistorialDePagos(
                semanas = semanas,
                resumen = RitmoDePagos.resumen(semanas),
                meses = RielDePagos.de(pagos),
                totalPagos = pagos.size
            ),
            liquidacion = cobranza.liquidaciones[ventaId],
            garantia = garantiasPort.garantiaDe(venta.creditoId)
        )
    }

    /**
     * Los productos de la venta. Room los trae concatenados en una sola columna
     * (`GROUP_CONCAT(p.ARTICULO, ', ')` en `SaleDao.getByClientId`), así que
     * aquí se parten de vuelta. El importe individual NO existe en ese origen:
     * cuando hay un solo producto se le atribuye el total de la venta —que sí es
     * cierto—, y con varios se deja en `null` antes que repartir un total entre
     * renglones inventando precios.
     */
    private fun productosDe(descripcion: String, totalVenta: Money): List<ProductoDeVenta> {
        val nombres = descripcion.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val importe = totalVenta.takeIf { nombres.size == 1 }
        return nombres.map { ProductoDeVenta(nombre = it, importe = importe) }
    }
}
