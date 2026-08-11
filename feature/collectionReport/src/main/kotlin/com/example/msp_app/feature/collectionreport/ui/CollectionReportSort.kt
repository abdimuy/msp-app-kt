package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.feature.collectionreport.domain.ReportAggregator
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.DateRange

/**
 * Orden PURO del detalle de pagos por [DetailSort] (Hora/Nombre), separado de
 * [CollectionReportStateBuilder] SOLO para no cruzar el umbral `TooManyFunctions` de detekt en
 * ese objeto (Task 4 agregó el reordenado de Semana) — mismo criterio que separó
 * `SheetPaymentRow.kt` de `ReportSheetContent.kt`. Consumido por
 * [CollectionReportStateBuilder.sortedPaymentRows] (Día, delegado sin cambiar su API pública)
 * y directo por `CollectionReportViewModel.setSort` (Semana, para no reconsultar los puertos).
 */
internal object CollectionReportSort {

    /** Filas de pago ordenadas por [sort] — Día, y el ticket impreso (`CollectionReportFormatter`). */
    fun paymentRows(payments: List<CollectionPayment>, sort: DetailSort): List<PaymentRowUi> =
        sortRows(payments.map { it.toPaymentRowUi() }, sort)

    /**
     * Pagos del ciclo (Semana) agrupados por día ([ReportAggregator.paymentsByDay], mismo
     * reparto que usa `CollectionReportStateBuilder.buildContent`) y ordenados DENTRO de cada
     * día por [sort] — el orden ENTRE días sigue siendo cronológico.
     */
    fun dayPaymentRows(
        payments: List<CollectionPayment>,
        range: DateRange,
        sort: DetailSort
    ): List<List<PaymentRowUi>> = ReportAggregator.paymentsByDay(payments, range)
        .map { dayPayments -> sortRows(dayPayments.map { it.toPaymentRowUi() }, sort) }

    private fun sortRows(rows: List<PaymentRowUi>, sort: DetailSort): List<PaymentRowUi> =
        when (sort) {
            DetailSort.HORA -> rows.sortedBy { it.paidAt }
            DetailSort.NOMBRE -> rows.sortedBy { it.cliente.lowercase() }
        }

    private fun CollectionPayment.toPaymentRowUi(): PaymentRowUi = PaymentRowUi(
        id = id,
        cliente = cliente,
        ventaLabel = ventaLabel,
        paidAt = paidAt,
        amount = amount,
        method = method,
        synced = synced,
        folio = folio,
        saldo = saldo
    )
}
