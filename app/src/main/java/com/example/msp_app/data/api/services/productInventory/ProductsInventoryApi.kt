package com.example.msp_app.data.api.services.productInventory

import com.example.msp_app.data.models.productInventory.ProductInventory
import retrofit2.http.GET

data class ProductInventoryResponse(
    val body: List<ProductInventory>
)

interface ProductInventoryApi {
    @GET("articulos")
    suspend fun getProductInventory(): ProductInventoryResponse
}
