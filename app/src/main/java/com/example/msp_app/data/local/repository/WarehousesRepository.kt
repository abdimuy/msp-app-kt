package com.example.msp_app.data.local.repository

import com.example.msp_app.data.api.services.warehouses.AddProductRequest
import com.example.msp_app.data.api.services.warehouses.WarehouseResponse
import com.example.msp_app.data.local.datasource.warehouseRemoteDataSource.WarehouseRemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WarehouseRepository @Inject constructor(
    private val remoteDataSource: WarehouseRemoteDataSource
) {
    suspend fun getWarehouseProducts(warehouseId: Int): Result<WarehouseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = remoteDataSource.getWarehouseProducts(warehouseId)
                if (response.error.isNullOrEmpty()) {
                    Result.success(response)
                } else {
                    Result.failure(Exception(response.error))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun postProductsToWarehouse(products: List<AddProductRequest>): Result<WarehouseResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = remoteDataSource.postProductsToWarehouse(products)
                if (!response.error.isNullOrEmpty()) {
                    Result.failure(Exception(response.error))
                } else {
                    Result.success(response)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

}