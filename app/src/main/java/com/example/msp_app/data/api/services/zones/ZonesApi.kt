package com.example.msp_app.data.api.services.zones

import com.example.msp_app.data.models.zone.ClientZone
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

data class ZonesResponse(
    @SerializedName("body")
    val body: List<ClientZone> = emptyList(),

    @SerializedName("error")
    val error: String? = null,

    // Por si la API devuelve las zonas directamente sin wrapper
    @SerializedName("zonas")
    val zonas: List<ClientZone>? = null,

    @SerializedName("data")
    val data: List<ClientZone>? = null
) {
    // Propiedad calculada que intenta obtener las zonas de cualquier campo
    fun getZonesList(): List<ClientZone> {
        return when {
            body.isNotEmpty() -> body
            zonas?.isNotEmpty() == true -> zonas
            data?.isNotEmpty() == true -> data
            else -> emptyList()
        }
    }
}


interface ZonesApi {
    @GET("/zonas-cliente")
    suspend fun getClientZones(): ZonesResponse
}