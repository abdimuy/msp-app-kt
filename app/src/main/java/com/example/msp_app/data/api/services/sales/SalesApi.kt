package com.example.msp_app.data.api.services.sales

import com.example.msp_app.data.models.guarantee.Guarantee
import com.example.msp_app.data.models.guarantee.GuaranteeEvent
import com.example.msp_app.data.models.payment.PaymentApi
import com.example.msp_app.data.models.product.Product
import com.example.msp_app.data.models.sale.Sale
import retrofit2.http.GET

data class SaleResponse(
    val body: Body
) {
    data class Body(
        val ventas: List<Sale>,
        val productos: List<Product>,
        val pagos: List<PaymentApi>,
        val garantias: List<Guarantee>,
        val eventosGarantias: List<GuaranteeEvent>
    )
}

interface SalesApi {
    @GET("/ventas/getAllVentasByZona/{zona}")
    suspend fun getAll(
        @retrofit2.http.Path("zona") zona: Int,
        @retrofit2.http.Query("dateInit") dateInit: String
    ): SaleResponse
}
