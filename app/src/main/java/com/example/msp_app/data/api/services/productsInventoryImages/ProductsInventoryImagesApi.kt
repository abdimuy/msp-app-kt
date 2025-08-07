package com.example.msp_app.data.api.services.productsInventoryImages

import retrofit2.http.GET

data class ProductsInventoryImagesResponse(
    val id: Int,
    val urls: List<String>
)

interface ProductsInventoryImagesApi {
    @GET("imagenes")
    suspend fun getAllImages(): List<ProductsInventoryImagesResponse>
}