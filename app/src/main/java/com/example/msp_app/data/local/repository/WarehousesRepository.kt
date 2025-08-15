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

    suspend fun addMultipleProductsToWarehouse(products: List<AddProductRequest>): Result<List<WarehouseResponse>> {
        return withContext(Dispatchers.IO) {
            try {
                val responses = mutableListOf<WarehouseResponse>()
                products.forEach { product ->
                    val response = remoteDataSource.addProductToWarehouse(product)
                    if (!response.error.isNullOrEmpty()) {
                        return@withContext Result.failure(Exception("Error adding product ${product.ARTICULO}: ${response.error}"))
                    }
                    responses.add(response)
                }
                Result.success(responses)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}