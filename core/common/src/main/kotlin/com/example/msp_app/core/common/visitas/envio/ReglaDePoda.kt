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
     * Cuántos días, desde que se creó, una foto sin enviar de una visita ya
     * confirmada todavía devuelve su visita a pendiente (barrido de la
     * reconciliación inversa, E-APP-079). Es la misma semana durante la que
     * un comprobante sin subir bloquea la poda
     * (`VisitsLocalDataSource.RETENCION_DE_COMPROBANTES`): mientras el barrido
     * puede rescatarla, la poda no se lleva la visita. Pasado el límite la
     * foto se reporta como abandonada y deja de devolver: una foto que el
     * servidor nunca toma no puede reabrir su visita cada día para siempre.
     */
    const val FOTOS_SIN_ENVIAR_DIAS = 7

    /** Una confirmada ANTES de este instante ya se puede podar. */
    fun limiteConfirmadas(ahora: Instant): String =
        AppTime.toWireFormat(ahora.minus(Duration.ofDays(CONFIRMADAS_DIAS.toLong())))

    /** Una foto sin enviar creada ANTES de este instante ya no devuelve su visita. */
    fun limiteFotosSinEnviar(ahora: Instant): Instant =
        ahora.minus(Duration.ofDays(FOTOS_SIN_ENVIAR_DIAS.toLong()))

    /** Una resguardada ANTES de este instante ya se puede podar (tras reportarla). */
    fun limiteResguardadas(ahora: Instant): String =
        AppTime.toWireFormat(ahora.minus(Duration.ofDays(RESGUARDADAS_DIAS.toLong())))
}
