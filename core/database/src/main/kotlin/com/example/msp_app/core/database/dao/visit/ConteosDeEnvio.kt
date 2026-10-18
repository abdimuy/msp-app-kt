package com.example.msp_app.core.database.dao.visit

/**
 * Conteos del envío de visitas para la telemetría (plan visitas P1, Tarea 14,
 * H14). Solo números y una marca de tiempo: ningún dato personal.
 *
 * @param pendientes filas con `GUARDADO_EN_MICROSIP = 0`.
 * @param masVieja `FECHA` de la pendiente más vieja, o `null` sin pendientes.
 * @param resguardadas en la bandeja de la oficina y sin confirmar.
 * @param confirmadas24h con `CONFIRMADA_EN` en las últimas 24 h.
 */
data class ConteosDeEnvio(
    val pendientes: Int,
    val masVieja: String?,
    val resguardadas: Int,
    val confirmadas24h: Int
)
