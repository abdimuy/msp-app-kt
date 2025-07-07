package com.example.msp_app.data.models.payment

data class PaymentLocation(
    val DOCTO_CC_ACR_ID: Int,
    val LAT: Double,
    val LNG: Double
)

data class
PaymentLocationsGroup(
    val saleId: Int,
    val locations: List<PaymentLocation>
)