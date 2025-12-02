package com.example.msp_app.data.local.repository

import com.example.msp_app.data.api.services.zones.ZonesResponse
import com.example.msp_app.data.local.datasource.zonesRemoteDataSource.ZonesRemoteDataSource

class ZonesRepository(
    private val remoteDataSource: ZonesRemoteDataSource
) {
    suspend fun getClientZones(): Result<ZonesResponse> {
        return try {

            val response = remoteDataSource.getClientZones()

            val normalizedResponse =
                if (response.body.isEmpty() && response.getZonesList().isNotEmpty()) {
                    response.copy(body = response.getZonesList(), error = null)
                } else {
                    response
                }

            if (!normalizedResponse.error.isNullOrBlank() && normalizedResponse.body.isEmpty()) {
                Result.failure(Exception(normalizedResponse.error))
            } else {
                Result.success(normalizedResponse)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}