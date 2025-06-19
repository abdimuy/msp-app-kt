package com.example.msp_app.data.api.services.sales

import com.example.msp_app.data.models.payment.Payment
import com.example.msp_app.data.models.payment.PaymentApi
import com.example.msp_app.data.models.product.Product
import com.example.msp_app.data.models.sale.Sale
import retrofit2.http.GET
import retrofit2.http.Path

data class SaleResponse(
    val body: Body
) {
    data class Body(
        val ventas: List<Sale>,
        val productos: List<Product>,
        val pagos: List<PaymentApi>
    )
}

interface SalesApi {
    @GET("/ventas/getAllVentasByZona/21563?dateInit=2025-04-15")
    suspend fun getAll(): SaleResponse
}
