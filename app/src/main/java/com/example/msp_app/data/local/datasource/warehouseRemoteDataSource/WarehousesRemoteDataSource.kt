package com.example.msp_app.data.local.datasource.warehouseRemoteDataSource

import com.example.msp_app.data.api.services.warehouses.AddProductRequest
import com.example.msp_app.data.api.services.warehouses.WarehouseResponse
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import javax.inject.Inject

class WarehouseRemoteDataSource @Inject constructor(
    private val warehousesApi: WarehousesApi
) {
    suspend fun getWarehouseProducts(warehouseId: Int): WarehouseResponse {
        return warehousesApi.getWarehouseProducts(warehouseId)
    }

    suspend fun addProductToWarehouse(product: AddProductRequest): WarehouseResponse {
        return warehousesApi.addProductToWarehouse(product)
    }
}