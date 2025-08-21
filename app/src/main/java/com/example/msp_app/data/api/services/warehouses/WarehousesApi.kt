package com.example.msp_app.data.api.services.warehouses

import com.example.msp_app.data.models.productInventory.ProductInventory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class WarehouseResponse(
    val body: Body,
    val error: String?
) {
    data class Body(
        val ALMACEN: Warehouse,
        val ARTICULOS: List<ProductInventory>
    )

    data class Warehouse(
        val ALMACEN_ID: Int,
        val ALMACEN: String,
        val EXISTENCIAS: Int
    )
}

data class AddProductRequest(
    val ALMACEN_ID: Int,
    val ARTICULO: String,
    val EXISTENCIAS: Int
)

interface WarehousesApi {
    @GET("/almacenes/{almacenId}")
    suspend fun getWarehouseProducts(
        @Path("almacenId") warehouseId: Int
    ): WarehouseResponse

    @POST("/almacenes/products")
    suspend fun postProductsToWarehouse(
        @Body product: List<AddProductRequest>
    ): WarehouseResponse
}
