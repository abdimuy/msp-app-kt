package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.payment.PaymentDao
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.port.HistoricalTotalsPort

/**
 * Adapter Room de [HistoricalTotalsPort]: suma el cobrado por día de negocio a
 * partir de [PaymentDao.getPaymentsGroupedByDaySince] (que ya filtra las formas
 * cobradas 157/158/52569 y excluye la condonación 137026, y agrupa por día en
 * zona de negocio).
 *
 * El inicio de la ventana se calcula con [AppClock] (nunca `Instant.now()`
 * directo) para ser determinista en tests. El resultado sale en orden
 * cronológico ASCENDENTE (el día más reciente al final) para que el
 * `takeLast(window)` de `SuggestedGoal` seleccione los días recientes —
 * `getPaymentsGroupedByDaySince` los entrega descendentes, así que se
 * re-ordena por la clave `yyyy-MM-dd`.
 */
class RoomHistoricalTotalsAdapter(
    private val paymentDao: PaymentDao,
    private val clock: AppClock
) : HistoricalTotalsPort {

    override suspend fun dailyTotals(days: Int): List<Money> {
        if (days <= 0) return emptyList()
        val firstDay = AppTime
            .todayInBusinessZone(clock)
            .minusDays((days - 1).toLong())
        val startIso = AppTime.toWireFormat(AppTime.startOfDay(firstDay))
        return paymentDao
            .getPaymentsGroupedByDaySince(startIso)
            .entries
            .sortedBy { it.key }
            .map { (_, rows) -> Money.sum(rows.map { Money.of(it.IMPORTE) }) }
    }
}
