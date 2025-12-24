package com.example.msp_app.data.models.zone

import com.google.gson.annotations.SerializedName

/**
 * Modelo de zona de cliente
 */
data class ClientZone(
    @SerializedName("ZONA_CLIENTE_ID")
    val ZONA_CLIENTE_ID: Int,

    @SerializedName("ZONA_CLIENTE")
    val ZONA_CLIENTE: String
)

/**
 * Entidad de zona para cache (misma estructura)
 */
data class ClientZoneEntity(
    val ZONA_CLIENTE_ID: Int,
    val ZONA_CLIENTE: String
)

// Mappers
fun ClientZone.toEntity(): ClientZoneEntity {
    return ClientZoneEntity(
        ZONA_CLIENTE_ID = this.ZONA_CLIENTE_ID,
        ZONA_CLIENTE = this.ZONA_CLIENTE
    )
}

fun ClientZoneEntity.toDomain(): ClientZone {
    return ClientZone(
        ZONA_CLIENTE_ID = this.ZONA_CLIENTE_ID,
        ZONA_CLIENTE = this.ZONA_CLIENTE
    )
}
