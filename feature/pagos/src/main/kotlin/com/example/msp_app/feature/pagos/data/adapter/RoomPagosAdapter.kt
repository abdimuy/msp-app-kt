package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.core.database.entities.PaymentEntity
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
    private val paymentDao: PaymentDao
) : PagosPort {

    override suspend fun pagosDe(ventaId: Int): List<PagoDelHistorial> =
        paymentDao.getPaymentsBySaleId(ventaId)
            .filter { it.FORMA_COBRO_ID in VentanaCobro.FORMAS_COBRO_COBRANZA }
            .mapNotNull { it.aPagoDelHistorial() }
            .sortedByDescending { it.fecha }
}

/**
 * Un pago con `FECHA_HORA_PAGO` impresentable se descarta del historial en vez
 * de aterrizar en una fecha inventada: el riel agrupa por mes y un pago con
 * fecha falsa se pintaría en el mes equivocado, que es peor que no pintarlo.
 * No hay `catch` aquí — `parseWireFormatOrNull` devuelve `null` por contrato.
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
        nota = null
    )
}
