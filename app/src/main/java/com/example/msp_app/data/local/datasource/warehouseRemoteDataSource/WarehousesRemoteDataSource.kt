package com.example.msp_app.data.local.datasource.warehouseRemoteDataSource

import com.example.msp_app.data.api.services.warehouses.TransferRequest
import com.example.msp_app.data.api.services.warehouses.TransferResponse
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
import com.example.msp_app.data.api.services.warehouses.WarehouseResponse
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import javax.inject.Inject

class WarehouseRemoteDataSource @Inject constructor(
    private val warehousesApi: WarehousesApi
) {
    suspend fun getAllWarehouses(): List<WarehouseListResponse.Warehouse> {
        val response = warehousesApi.getAllWarehouses()
        if (!response.error.isNullOrEmpty()) {
            throw Exception(response.error)
        }
        return response.body
    }

    suspend fun getWarehouseProducts(warehouseId: Int): WarehouseResponse {
        return warehousesApi.getWarehouseProducts(warehouseId)
    }

    suspend fun createTransfer(transferRequest: TransferRequest): TransferResponse {
        return warehousesApi.createTransfer(transferRequest)
    }
}