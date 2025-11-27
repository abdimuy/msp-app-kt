package com.example.msp_app.data.models.zone

// Modelo de dominio
data class ClientZone(
    val ZONA_CLIENTE_ID: Int,
    val ZONA_CLIENTE: String
)

// Entity para almacenamiento local
data class ClientZoneEntity(
    val ZONA_CLIENTE_ID: Int,
    val ZONA_CLIENTE: String,
)

// Función de conversión
fun ClientZoneEntity.toDomain(): ClientZone {
    return ClientZone(
        ZONA_CLIENTE_ID = this.ZONA_CLIENTE_ID,
        ZONA_CLIENTE = this.ZONA_CLIENTE,
    )
}

fun ClientZone.toEntity(): ClientZoneEntity {
    return ClientZoneEntity(
        ZONA_CLIENTE_ID = this.ZONA_CLIENTE_ID,
        ZONA_CLIENTE = this.ZONA_CLIENTE
    )
}