package com.example.msp_app.data.api.services.payment

import com.example.msp_app.data.models.payment.Payment
import retrofit2.http.Body
import retrofit2.http.POST

data class PaymentRequest(
    val pago: Payment
)

interface PaymentsApi {
    @POST("ventas/add-pago")
    suspend fun savePayment(@Body request: PaymentRequest)
}

