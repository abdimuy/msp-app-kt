package com.example.msp_app.data.api.services.zones

import com.example.msp_app.data.models.zone.ClientZone
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

data class ZonesResponse(
    @SerializedName("body")
    val body: List<ClientZone> = emptyList(),

    @SerializedName("error")
    val error: String? = null,

    ) {
    fun getZonesList(): List<ClientZone> {
        return when {
            body.isNotEmpty() -> body
            else -> emptyList()
        }
    }
}


interface ZonesApi {
    @GET("/zonas-cliente")
    suspend fun getClientZones(): ZonesResponse
}