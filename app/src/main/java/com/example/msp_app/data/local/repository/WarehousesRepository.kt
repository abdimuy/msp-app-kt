package com.example.msp_app.data.local.repository

import com.example.msp_app.data.api.services.warehouses.TransferRequest
import com.example.msp_app.data.api.services.warehouses.TransferResponse
import com.example.msp_app.data.api.services.warehouses.WarehouseListResponse
import com.example.msp_app.data.api.services.warehouses.WarehouseResponse
import com.example.msp_app.data.local.datasource.warehouseRemoteDataSource.WarehouseRemoteDataSource
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deliberadamente SIN `@Singleton`: es pura delegación sin estado propio, y
 * envuelve un [WarehouseRemoteDataSource] que a su vez envuelve la
 * `WarehousesApi` NO-singleton provista por `NetworkModule` (kill-switch de
 * baseURL por Firestore, ver `NetworkModule.provideWarehousesApi`). Si esta
 * clase fuera `@Singleton`, Dagger materializaría este objeto UNA sola vez
 * para todo el proceso — congelando por valor la `WarehousesApi` de esa
 * primera resolución y volviendo sordo el kill-switch a partir de la primera
 * visita a cualquier pantalla de almacenes. Sin scope, cada
 * `WarehouseViewModel` construido (= cada visita de pantalla, igual que antes
 * de Hilt) vuelve a resolver la cadena completa.
 */
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
