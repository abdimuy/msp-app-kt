package com.example.msp_app.core.database.dao.enviodiagnostico

/**
 * Fallos vigentes de un tipo agrupados por código (plan visitas P1, Tarea 14,
 * H14). [intentosMax] deja ver una fila que se reintenta para siempre. Solo
 * diagnóstico: nunca decide un envío.
 */
data class FallosPorCodigo(
    val codigo: String,
    val filas: Int,
    val intentosMax: Int
)
