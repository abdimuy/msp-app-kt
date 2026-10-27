package com.example.msp_app.feature.pagos.domain.model

import java.time.LocalDate
import java.time.LocalTime

/*
 * El compromiso vigente de una venta, tal como lo pinta la lista y el detalle
 * (plan visitas P2, Tarea 20; spec §5.7 B, C, E, F; E-APP-115).
 *
 * Es la ENTRADA de la presentación, no la derivación: el estado ya viene
 * derivado (spec §5.3.2) y aquí no se compara el día con hoy para decidirlo.
 * Los códigos son los del contrato del servidor (`GET /v2/visitas/agenda`),
 * los mismos que rotula el escritorio (T21).
 *
 * Quién lo llena: la agenda local de la Tarea 17 (`AgendaLocal.derivar`, que
 * devuelve `CompromisoVigente` por venta). Mientras T17 no esté integrada nadie
 * lo llena y la lista y el detalle se ven como hoy; al integrarla, el mapeo es
 * campo a campo por `codigo` ([TipoDeCompromiso.deCodigo],
 * [EstadoDeCompromiso.deCodigo]).
 */

/** Promesa o cita. [codigo] es el del servidor. */
enum class TipoDeCompromiso(val codigo: String) {
    PROMESA("promesa"),
    CITA("cita");

    companion object {
        /** `null` si el código no es del contrato: lo desconocido no se pinta. */
        fun deCodigo(codigo: String?): TipoDeCompromiso? =
            entries.firstOrNull { it.codigo == codigo }
    }
}

/** Estado derivado del compromiso (spec §5.3.2). `reemplazada` no existe: no hay entrada. */
enum class EstadoDeCompromiso(val codigo: String) {
    CUMPLIDA("cumplida"),
    CITA_CUMPLIDA("cita_cumplida"),
    VIGENTE_FUTURA("vigente_futura"),
    VIGENTE_HOY("vigente_hoy"),
    INCUMPLIDA("incumplida"),
    CERRADA_SIN_CUMPLIR("cerrada_sin_cumplir");

    companion object {
        /** `null` si el código no es del contrato: lo desconocido no se pinta. */
        fun deCodigo(codigo: String?): EstadoDeCompromiso? =
            entries.firstOrNull { it.codigo == codigo }
    }
}

/**
 * El compromiso de una venta. [hora] solo en citas (nula = cita sin hora, que
 * sigue siendo una cita con su día: spec §5.7 F). [montoCentavos] es el de la
 * promesa; en una cita no se pinta, igual que en el escritorio.
 */
data class CompromisoDeLaVenta(
    val tipo: TipoDeCompromiso,
    val estado: EstadoDeCompromiso,
    val dia: LocalDate,
    val hora: LocalTime? = null,
    val montoCentavos: Long? = null
)
