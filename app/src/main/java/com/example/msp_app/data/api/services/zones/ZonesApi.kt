package com.example.msp_app.data.api.services.zones

import com.example.msp_app.data.models.zone.ClientZone
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET

/**
 * Respuesta de la API de zonas
 */
data class ZonesResponse(
    @SerializedName("body")
    val body: List<ClientZone> = emptyList(),

    @SerializedName("error")
    val error: String? = null
) {
    fun getZonesList(): List<ClientZone> {
        return when {
            body.isNotEmpty() -> body
            else -> emptyList()
        }
    }

    fun isSuccess(): Boolean = error.isNullOrBlank() && body.isNotEmpty()
}

/**
 * API para zonas de clientes
 */
interface ZonesApi {
    @GET("/zonas-cliente")
    suspend fun getClientZones(): ZonesResponse
}
