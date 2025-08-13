package com.example.msp_app.data.local.datasource.warehousesLocalDataSource

import com.example.msp_app.data.api.services.warehouses.WarehouseResponse
import com.example.msp_app.data.api.services.warehouses.WarehousesApi
import com.example.msp_app.data.models.productInventory.ProductInventory

class WarehousesLocalDataSource(private val api: WarehousesApi) {

    suspend fun getProducts(almacenId: Int): WarehouseResponse {
        return api.getWarehouseProducts(almacenId)
    }

    suspend fun addProduct(product: ProductInventory): WarehouseResponse {
        return api.addProductToWarehouse(product)
    }
}