package com.example.msp_app.core.visitas.envio

import com.example.msp_app.core.database.entities.VisitEntity

/**
 * Cómo se rotula el envío de una visita en la pantalla de pendientes (plan
 * visitas P1, Tarea 14; spec §4.4). Solo pinta: no decide nada.
 *
 * Una resguardada NO es "enviada": está en la bandeja de la oficina hasta que
 * la reaplique y la reconciliación inversa la confirme. Las legadas de antes
 * de Room v35 (soltadas sin marca) dicen "sin confirmar".
 */
fun rotuloDeEnvio(v: VisitEntity): String = when {
    v.CONFIRMADA_EN != null -> ROTULO_ENVIADA
    v.RESGUARDADA_EN != null -> ROTULO_EN_REVISION
    v.GUARDADO_EN_MICROSIP == 0 -> ROTULO_PENDIENTE
    else -> ROTULO_SIN_CONFIRMAR
}

const val ROTULO_ENVIADA = "enviada"
const val ROTULO_EN_REVISION = "en revisión de oficina"
const val ROTULO_PENDIENTE = "pendiente de envío"
const val ROTULO_SIN_CONFIRMAR = "enviada (sin confirmar)"
