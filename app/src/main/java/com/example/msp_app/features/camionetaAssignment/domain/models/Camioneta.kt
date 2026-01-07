package com.example.msp_app.features.camionetaAssignment.domain.models

/**
 * Represents a camioneta (vehicle/warehouse) that can be assigned to users.
 * Maps from the Almacen API response but represents only warehouses that are not excluded.
 */
data class Camioneta(
    val almacenId: Int,
    val nombre: String,
    val existencias: Int,
    val usuariosAsignados: List<UsuarioAsignado> = emptyList()
) {
    /**
     * Maximum number of users that can be assigned to a single camioneta.
     */
    companion object {
        const val MAX_USUARIOS_POR_CAMIONETA = 3
    }

    /**
     * Returns true if this camioneta can accept more user assignments.
     */
    fun puedeAceptarMasUsuarios(): Boolean =
        usuariosAsignados.size < MAX_USUARIOS_POR_CAMIONETA

    /**
     * Returns the number of available slots for user assignments.
     */
    fun lugaresDisponibles(): Int =
        MAX_USUARIOS_POR_CAMIONETA - usuariosAsignados.size
}

/**
 * Represents a user assigned to a camioneta.
 */
data class UsuarioAsignado(
    val id: String,
    val nombre: String,
    val email: String,
    val cobradorId: Int
)
