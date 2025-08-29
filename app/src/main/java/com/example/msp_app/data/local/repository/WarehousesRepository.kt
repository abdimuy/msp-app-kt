package com.example.msp_app.data.local.repository

import com.example.msp_app.data.api.services.warehouses.TransferRequest
import com.example.msp_app.data.api.services.warehouses.TransferResponse
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
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
    suspend fun getAllWarehouses(): Result<List<WarehouseListResponse.Warehouse>> {
        return withContext(Dispatchers.IO) {
            try {
                val list = remoteDataSource.getAllWarehouses()
                Result.success(list)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

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

    suspend fun createTransfer(transferRequest: TransferRequest): Result<TransferResponse> {
        return withContext(Dispatchers.IO) {
            try {
                val response = remoteDataSource.createTransfer(transferRequest)
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