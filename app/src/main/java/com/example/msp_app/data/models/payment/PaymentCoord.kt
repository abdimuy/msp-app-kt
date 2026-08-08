package com.example.msp_app.data.models.payment

import com.example.msp_app.core.database.entities.PaymentLocation

data class
PaymentLocationsGroup(
    val saleId: Int,
    val locations: List<PaymentLocation>
)
