package com.example.msp_app.core.common.visitas.envio

/**
 * Cómo se compara el compromiso que el teléfono tiene de una visita con el que
 * el servidor dice tener (plan visitas P2, Tarea 18; spec §4.6-1; E-APP-132).
 */
enum class BrechaDeCompromiso {
    /** Cada bloque que el teléfono tiene está en el servidor igual (== [compromisoProbado]). */
    COMPLETO,

    /**
     * Al servidor le falta algún bloque que el teléfono tiene y ningún bloque
     * presente difiere: un reenvío con el mismo id lo rellena (spec §5.2.3).
     */
    POR_RELLENAR,

    /** Algún bloque está en los dos lados y difiere: un reenvío sería el 422 `visita_compromiso_distinto`. */
    DISTINTO
}

/**
 * Clasifica [servidor] frente a [local], bloque por bloque (promesa y cita son
 * independientes). Un bloque que el teléfono no tiene no cuenta; uno de más en
 * el servidor no estorba (I10: la ausencia nunca borra). Cuando un bloque está
 * en los dos lados, la comparación es exacta de texto, como [compromisoProbado]:
 * por eso `brechaDeCompromiso(l, s) == COMPLETO` si y solo si
 * `compromisoProbado(l, s)`.
 *
 * Con un bloque distinto y otro vacío gana DISTINTO: reenviar traería el 422 y
 * no se reenvía nada.
 */
fun brechaDeCompromiso(local: CompromisoLocal, servidor: CompromisoLocal): BrechaDeCompromiso {
    val promesa = brechaDeBloque(
        local.tienePromesa,
        servidor.tienePromesa,
        servidor.promesaVentaId == local.promesaVentaId &&
            servidor.promesaFecha == local.promesaFecha &&
            servidor.promesaMontoCentavos == local.promesaMontoCentavos
    )
    val cita = brechaDeBloque(
        local.tieneCita,
        servidor.tieneCita,
        servidor.citaFecha == local.citaFecha && servidor.citaHora == local.citaHora
    )
    return when {
        promesa == BrechaDeCompromiso.DISTINTO || cita == BrechaDeCompromiso.DISTINTO ->
            BrechaDeCompromiso.DISTINTO
        promesa == BrechaDeCompromiso.POR_RELLENAR || cita == BrechaDeCompromiso.POR_RELLENAR ->
            BrechaDeCompromiso.POR_RELLENAR
        else -> BrechaDeCompromiso.COMPLETO
    }
}

private fun brechaDeBloque(
    enLocal: Boolean,
    enServidor: Boolean,
    iguales: Boolean
): BrechaDeCompromiso = when {
    !enLocal -> BrechaDeCompromiso.COMPLETO
    !enServidor -> BrechaDeCompromiso.POR_RELLENAR
    iguales -> BrechaDeCompromiso.COMPLETO
    else -> BrechaDeCompromiso.DISTINTO
}
