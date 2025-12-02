package com.example.msp_app.data.local.datasource.zonesRemoteDataSource

import com.example.msp_app.data.api.services.zones.ZonesApi
import com.example.msp_app.data.api.services.zones.ZonesResponse

class ZonesRemoteDataSource(
    private val zonesApi: ZonesApi
) {
    suspend fun getClientZones(): ZonesResponse {
        return zonesApi.getClientZones()
    }
}