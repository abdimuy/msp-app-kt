package com.example.msp_app.data.local.repository

import com.example.msp_app.data.api.services.zones.ZonesResponse
import com.example.msp_app.data.local.datasource.zonesRemoteDataSource.ZonesRemoteDataSource

class ZonesRepository(
    private val remoteDataSource: ZonesRemoteDataSource
) {
    suspend fun getClientZones(): Result<ZonesResponse> {
        return try {
            android.util.Log.d("ZonesRepository", "📡 Realizando petición al API...")

            val response = remoteDataSource.getClientZones()

            android.util.Log.d("ZonesRepository", "📦 Respuesta recibida")
            android.util.Log.d("ZonesRepository", "   - body: ${response.body.size} zonas")
            android.util.Log.d("ZonesRepository", "   - zonas: ${response.zonas?.size} zonas")
            android.util.Log.d("ZonesRepository", "   - data: ${response.data?.size} zonas")
            android.util.Log.d("ZonesRepository", "   - error: ${response.error}")

            val normalizedResponse =
                if (response.body.isEmpty() && response.getZonesList().isNotEmpty()) {
                    android.util.Log.d("ZonesRepository", "🔄 Normalizando respuesta...")
                    response.copy(body = response.getZonesList(), error = null)
                } else {
                    response
                }

            // ⭐ CORRECCIÓN: Solo es error si el campo error tiene contenido Y no hay zonas
            if (!normalizedResponse.error.isNullOrBlank() && normalizedResponse.body.isEmpty()) {
                android.util.Log.e(
                    "ZonesRepository",
                    "❌ API retornó error: ${normalizedResponse.error}"
                )
                Result.failure(Exception(normalizedResponse.error))
            } else {
                android.util.Log.d(
                    "ZonesRepository",
                    "✅ ${normalizedResponse.body.size} zonas procesadas correctamente"
                )
                Result.success(normalizedResponse)
            }
        } catch (e: Exception) {
            android.util.Log.e("ZonesRepository", "❌ Excepción en Repository: ${e.message}", e)
            Result.failure(e)
        }
    }
}