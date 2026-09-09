package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.port.PagosPort

/**
 * Adaptador Room de [PagosPort] sobre [PaymentDao.getPaymentsBySaleId]
 * (`DOCTO_CC_ACR_ID`).
 *
 * **Filtra con [VentanaCobro.FORMAS_COBRO_COBRANZA]**, el MISMO conjunto que
 * usa `EstadoCuentaDeriver` para sumar `AbonoSemana`. Es deliberado que sea el
 * mismo y no una copia: la condonación (137026) no es dinero que entró, y si el
 * riel la pintara como abono el historial contaría una cosa y el estado del
 * periodo otra, sobre los mismos pagos. La lógica de condonación no se toca —
 * queda fuera, no reescrita.
 *
 * `IMPORTE` es `Double` en el schema; cruza a [Money] aquí y solo aquí, vía
 * `Money.of(Double)` (que usa `BigDecimal.valueOf`, nunca `BigDecimal(double)`).
 */
class RoomPagosAdapter(
    private val paymentDao: PaymentDao,
    private val telemetry: Telemetry
) : PagosPort {

    override suspend fun pagosDe(ventaId: Int): List<PagoDelHistorial> =
        aHistorial(paymentDao.getPaymentsBySaleId(ventaId))

    /**
     * Un abono por su id — la entrada del ticket de pago (Task 20), que solo
     * lleva el `pagoId` en la ruta para sobrevivir a la muerte del proceso.
     *
     * Pasa por el MISMO [aHistorial] que las otras dos lecturas, así que hereda
     * el filtro de formas de cobranza y el descarte reportado de una fecha
     * impresentable: una condonación NO devuelve ticket de pago.
     */
    override suspend fun pago(pagoId: String): PagoDelHistorial? =
        paymentDao.getPaymentById(pagoId)?.let { aHistorial(listOf(it)) }?.firstOrNull()

    /**
     * Los abonos de TODA la ruta dentro de la ventana, en UNA consulta — la
     * lista de clientes (Task 17) deriva el periodo de cientos de ventas y una
     * lectura por venta serían cientos de viajes a Room.
     *
     * `getPaymentsByDate` ya trae dentro el mismo `FORMA_COBRO_ID IN (157, 158,
     * 52569)`; [aHistorial] lo vuelve a aplicar a propósito, para que la
     * invariante "solo cobranza real, nunca condonación" viva en este adaptador
     * y no dependa de que una `@Query` de otro módulo no cambie.
     */
    override suspend fun pagosDelPeriodo(ventana: VentanaCobro): List<PagoDelHistorial> {
        val (desde, hasta) = RangoDeConsulta.de(ventana)
        return aHistorial(paymentDao.getPaymentsByDate(desde, hasta))
    }

    private fun aHistorial(crudos: List<PaymentEntity>): List<PagoDelHistorial> {
        val delaCobranza = crudos.filter { it.FORMA_COBRO_ID in VentanaCobro.FORMAS_COBRO_COBRANZA }
        val legibles = delaCobranza.mapNotNull { it.aPagoDelHistorial() }
        reportarLosQueSeCayeron(delaCobranza.size - legibles.size)
        return legibles.sortedByDescending { it.fecha }
    }

    /**
     * Un abono que se cae del historial **se cae también del subtotal del mes**
     * en el riel: es dinero desapareciendo de una pantalla de dinero, aunque no
     * haya un `catch` de por medio. Se reporta con conteo, sin PII — ni el id
     * del pago, ni la venta, ni el importe, ni la fecha cruda.
     */
    private fun reportarLosQueSeCayeron(cuantos: Int) {
        if (cuantos <= 0) return
        telemetry.error(
            code = PagosTelemetria.CODE_ABONO_SIN_FECHA_LEGIBLE,
            message = "FECHA_HORA_PAGO no se pudo parsear; el abono no entra al historial ni al subtotal del mes",
            props = mapOf(PagosTelemetria.PROP_OCURRENCIAS to cuantos.toString())
        )
    }
}

/**
 * Un pago con `FECHA_HORA_PAGO` impresentable se descarta del historial en vez
 * de aterrizar en una fecha inventada: el riel agrupa por mes y un pago con
 * fecha falsa se pintaría en el mes equivocado, que es peor que no pintarlo.
 * No hay `catch` aquí —`parseWireFormatOrNull` devuelve `null` por contrato—
 * pero el descarte NO es silencioso: lo cuenta y lo emite [RoomPagosAdapter].
 */
private fun PaymentEntity.aPagoDelHistorial(): PagoDelHistorial? {
    val fecha = AppTime.parseWireFormatOrNull(FECHA_HORA_PAGO) ?: return null
    return PagoDelHistorial(
        pagoId = ID,
        ventaId = DOCTO_CC_ACR_ID,
        fecha = fecha,
        importe = Money.of(IMPORTE),
        formaCobroId = FORMA_COBRO_ID,
        metodo = MetodoDeCobro.de(FORMA_COBRO_ID),
        nota = null,
        cobrador = COBRADOR,
        // El UUID de la captura, cuando el merge ya re-llaveó la fila. Ver el
        // KDoc de `PagoDelHistorial.capturaId`: es el único rastro que queda del
        // id con el que el teléfono escribió este abono.
        capturaId = PAGO_RECIBIDO_ID
    )
}
