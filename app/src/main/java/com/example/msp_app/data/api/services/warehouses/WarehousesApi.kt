package com.example.msp_app.data.api.services.warehouses

import com.example.msp_app.data.models.productInventory.ProductInventory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class WarehouseListResponse(
    val body: List<Warehouse>,
    val error: String?
) {
    data class Warehouse(
        val ALMACEN_ID: Int,
        val ALMACEN: String,
        val EXISTENCIAS: Int
    )
}

data class WarehouseResponse(
    val body: Body,
    val error: String?
) {
    data class Body(
        val ALMACEN: WarehouseListResponse.Warehouse,
        val ARTICULOS: List<ProductInventory>
    )
}

data class TransferRequest(
    val almacenOrigenId: Int,
    val almacenDestinoId: Int,
    val descripcion: String,
    val detalles: List<TransferDetail>
)

data class TransferDetail(
    val articuloId: Int,
    val unidades: Int
)

data class TransferResponse(
    val body: TransferResult?,
    val error: String?
)

data class TransferResult(
    val transferId: Int,
    val mensaje: String
)

interface WarehousesApi {
    @GET("/almacenes")
    suspend fun getAllWarehouses(): WarehouseListResponse

    @GET("/almacenes/{almacenId}")
    suspend fun getWarehouseProducts(
        @Path("almacenId") warehouseId: Int
    ): WarehouseResponse

    @POST("/traspasos")
    suspend fun createTransfer(
        @Body transferRequest: TransferRequest
    ): TransferResponse
}