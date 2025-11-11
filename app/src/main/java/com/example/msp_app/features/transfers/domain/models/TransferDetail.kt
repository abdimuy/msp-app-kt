package com.example.msp_app.features.transfers.domain.models

/**
 * Domain model for transfer detail (product/movement)
 */
data class TransferDetail(
    val id: Long,
    val doctoInId: Int,
    val articuloId: Int,
    val claveArticulo: String,
    val articuloNombre: String?,
    val descripcion1: String?,
    val descripcion2: String?,
    val unidades: Int,
    val costoUnitario: Double,
    val costoTotal: Double,
    val tipoMovimiento: MovementType,
    val movtoId: Int?
) {
    /**
     * Returns a user-friendly product display name
     */
    fun getDisplayName(): String {
        return articuloNombre ?: descripcion1 ?: claveArticulo
    }

    /**
     * Returns a complete product description
     */
    fun getFullDescription(): String {
        val parts = listOfNotNull(
            articuloNombre,
            descripcion1,
            descripcion2
        ).filter { it.isNotBlank() }

        return if (parts.isEmpty()) claveArticulo else parts.joinToString(" - ")
    }

    /**
     * Checks if this is an outbound movement
     */
    fun isOutbound(): Boolean = tipoMovimiento == MovementType.SALIDA

    /**
     * Checks if this is an inbound movement
     */
    fun isInbound(): Boolean = tipoMovimiento == MovementType.ENTRADA
}

/**
 * Movement type enum
 */
enum class MovementType(val code: String) {
    SALIDA("S"),
    ENTRADA("E");

    companion object {
        fun fromCode(code: String): MovementType {
            return when (code.uppercase()) {
                "S" -> SALIDA
                "E" -> ENTRADA
                else -> throw IllegalArgumentException("Unknown movement type: $code")
            }
        }
    }
}
