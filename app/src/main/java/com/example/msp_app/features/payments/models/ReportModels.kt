package com.example.msp_app.features.payments.models

import com.example.msp_app.core.models.PaymentMethod

/**
 * Desglose por forma de cobro de un lote de pagos (conteo + importe). Vive aquí
 * (modelo del reporte) tras absorber el reporte de cobranza en
 * `:feature:collectionReport` (Plan 5, Task 10): antes era un `data class`
 * top-level de `DailyReportScreen.kt` (ya eliminado), pero lo consumen
 * [PaymentTextData], `ReportFormatters` y `PdfGenerator`, que siguen vivos.
 */
data class PaymentMethodBreakdown(
    val method: PaymentMethod,
    val count: Int,
    val amount: Double
)

data class PaymentLineData(
    val date: String,
    val client: String,
    val amount: Double,
    val paymentMethod: PaymentMethod
)

data class PaymentTextData(
    val lines: List<PaymentLineData>,
    val totalCount: Int,
    val totalAmount: Double,
    val breakdownByMethod: List<PaymentMethodBreakdown> = emptyList()
)

data class VisitLineData(
    val date: String,
    val collector: String,
    val type: String,
    val note: String
)

data class VisitTextData(
    val lines: List<VisitLineData>,
    val totalCount: Int
)

data class ForgivenessTextData(
    val lines: List<PaymentLineData>,
    val totalCount: Int,
    val totalAmount: Double
)
