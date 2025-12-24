package com.example.msp_app.features.zones

import com.example.msp_app.data.api.ApiProvider
import com.example.msp_app.data.api.services.zones.ZonesApi
import com.example.msp_app.data.models.zone.ClientZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository para zonas
 */
class ZonesRepository {

    private val api: ZonesApi by lazy {
        ApiProvider.create(ZonesApi::class.java)
    }

    /**
     * Obtiene las zonas de cliente desde la API
     */
    suspend fun getClientZones(): Result<List<ClientZone>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getClientZones()

                if (response.isSuccess()) {
                    Result.success(response.body)
                } else {
                    Result.failure(Exception(response.error ?: "Error desconocido al obtener zonas"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
