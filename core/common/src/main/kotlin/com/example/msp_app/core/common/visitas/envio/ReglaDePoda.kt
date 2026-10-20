package com.example.msp_app.core.common.visitas.envio

import com.example.msp_app.core.common.time.AppTime
import java.time.Duration
import java.time.Instant

/**
 * Retención local de visitas ya entregadas (spec visitas DA-4, §4.2-8; cierra
 * H2 junto con `VisitDao.deleteUploadedVisits`).
 *
 * - **Confirmadas** (`CONFIRMADA_EN`): 30 días tras la confirmación. Es la
 *   ventana que revisa la reconciliación inversa (Tarea 13): una visita que el
 *   servidor pierda dentro de esa ventana todavía está aquí para volver a
 *   pendiente.
 * - **Resguardadas** (`RESGUARDADA_EN`, en la bandeja de la oficina, no en
 *   `MSP_VISITAS`): 90 días, y se reportan antes de podar.
 * - **Legadas** (`GUARDADO_EN_MICROSIP = 1` con las dos marcas nulas): no
 *   tienen límite aquí; no se podan hasta que la reconciliación inversa las
 *   confirme o las devuelva a pendiente.
 *
 * Los límites salen en el formato de cable de [AppTime.toWireFormat]
 * (`ISO_INSTANT`, UTC), el mismo con el que se escriben las marcas, así que
 * SQLite los compara como texto.
 */
object ReglaDePoda {
    const val CONFIRMADAS_DIAS = 30
    const val RESGUARDADAS_DIAS = 90

    /**
     * Una semana, contada desde la `CREADA_EN` de una foto sin enviar. Tiene
     * dos usos, y ninguno decide si la foto viaja:
     *
     * - **La poda** (`VisitsLocalDataSource.RETENCION_DE_COMPROBANTES` se
     *   deriva de aquí): durante esta semana un comprobante sin subir impide
     *   podar su visita. Sin cambios en el arreglo de E-VIS-032.
     * - **La alerta del barrido** de la reconciliación inversa: una foto más
     *   vieja se cuenta en `visitas.conciliacion.fotos_sin_enviar_viejas`.
     *   Hasta E-VIS-032 la foto dejaba de devolver su visita pasado este
     *   límite (E-APP-079, "abandonada"); como la edad sale del reloj de pared
     *   del teléfono, un reloj atrasado y corregido la abandonaba sin que
     *   pasara un día real. Ahora el barrido la devuelve siempre.
     */
    const val FOTOS_SIN_ENVIAR_DIAS = 7

    /** Una confirmada ANTES de este instante ya se puede podar. */
    fun limiteConfirmadas(ahora: Instant): String =
        AppTime.toWireFormat(ahora.minus(Duration.ofDays(CONFIRMADAS_DIAS.toLong())))

    /** Una foto sin enviar creada ANTES de este instante cuenta como vieja (solo telemetría, E-VIS-032). */
    fun limiteFotosSinEnviar(ahora: Instant): Instant =
        ahora.minus(Duration.ofDays(FOTOS_SIN_ENVIAR_DIAS.toLong()))

    /** Una resguardada ANTES de este instante ya se puede podar (tras reportarla). */
    fun limiteResguardadas(ahora: Instant): String =
        AppTime.toWireFormat(ahora.minus(Duration.ofDays(RESGUARDADAS_DIAS.toLong())))
}
