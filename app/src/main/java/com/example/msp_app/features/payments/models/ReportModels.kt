package com.example.msp_app.features.payments.models

import com.example.msp_app.core.models.PaymentMethod
import com.example.msp_app.features.payments.screens.PaymentMethodBreakdown

data class PaymentLineData(
    val date: String,
    val client: String,
    val amount: Double,
    val paymentMethod: PaymentMethod,
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
    val note: String,
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