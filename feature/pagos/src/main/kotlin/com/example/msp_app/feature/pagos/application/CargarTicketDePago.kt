package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.PagoImpreso
import com.example.msp_app.feature.pagos.domain.model.TicketDePago
import com.example.msp_app.feature.pagos.domain.port.PagosPort
import com.example.msp_app.feature.pagos.domain.port.VentasPort
import javax.inject.Inject

/**
 * Arma el contenido del ticket de pago a partir del `pagoId` de la ruta.
 *
 * **No lee nada nuevo de Room.** Usa los dos puertos que las Tasks 16-18 ya
 * dejaron —[PagosPort] y [VentasPort]— porque el ticket no necesita ni una
 * columna que el detalle de venta no leyera ya. Un adaptador propio para esta
 * pantalla sería una segunda proyección del mismo dinero, y dos proyecciones del
 * mismo dinero se despegan.
 *
 * El único dato que sí se agregó al camino existente es
 * [PagoDelHistorial.cobrador] (`Payment.COBRADOR`): quién cobró ESE abono. Sale
 * de la fila del pago y no de la sesión, así que un ticket reimpreso dice el
 * cobrador que cobró, no el que trae el teléfono en la mano.
 *
 * **El saldo anterior es derivado, no leído:** `saldo actual + importe`. El
 * `SALDO_REST` de la venta ya viene descontado (la escritura del abono baja el
 * saldo en la misma transacción), así que leerlo "antes" en otro lado sería
 * leer un número que ya no existe.
 */
class CargarTicketDePago @Inject constructor(
    private val pagos: PagosPort,
    private val ventas: VentasPort
) {

    /** El ticket, o `null` si el teléfono ya no tiene ese abono o su venta. */
    suspend operator fun invoke(pagoId: String): TicketDePago? {
        val pago = pagos.pago(pagoId) ?: return null
        val venta = ventas.venta(pago.ventaId) ?: return null
        val historial = pagos.pagosDe(pago.ventaId)
        val anteriores = historial
            .filter { it.pagoId != pagoId }
            .sortedByDescending { it.fecha }
            .take(ULTIMOS_PAGOS)
        return TicketDePago(
            pagoId = pago.pagoId,
            cobradoEn = pago.fecha,
            folio = venta.folio,
            cliente = venta.clienteNombre,
            domicilio = venta.direccion,
            telefono = venta.telefono,
            cobrador = pago.cobrador,
            metodo = pago.metodo,
            importe = pago.importe,
            saldoAnterior = venta.saldo + pago.importe,
            saldoActual = venta.saldo,
            totalVenta = venta.totalVenta,
            parcialidad = venta.parcialidad,
            // Este abono ya está en el historial, así que el conteo lo incluye:
            // el papel dice "12 de 52" contando el que el cliente acaba de pagar.
            abonosPagados = historial.size,
            abonosTotales = venta.abonosTotales,
            ultimosPagos = anteriores.map { it.aPagoImpreso() }
        )
    }

    private fun PagoDelHistorial.aPagoImpreso() = PagoImpreso(
        fecha = AppTime.toBusinessDate(fecha),
        importe = importe,
        metodo = metodo
    )

    private companion object {
        /** Cuántos abonos previos caben sin que el ticket se vuelva un rollo. */
        const val ULTIMOS_PAGOS = 4
    }
}
