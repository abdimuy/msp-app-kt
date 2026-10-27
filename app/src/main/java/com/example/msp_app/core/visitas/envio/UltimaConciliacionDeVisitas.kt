package com.example.msp_app.core.visitas.envio

import android.content.Context
import com.example.msp_app.core.common.time.AppTime
import java.time.Duration
import java.time.Instant

/**
 * Cuándo corrió por última vez, completa, la reconciliación inversa de visitas
 * (plan visitas P1, Tarea 13; spec §4.6: "una vez al día, y al abrir la app si
 * pasaron > 24 h"). Vive en SharedPreferences `visitas_conciliacion_ultima`.
 *
 * Solo se anota una corrida que decidió todo lo que leyó (sin tandas fallidas
 * ni lectura ilegible): una corrida a medias deja la marca vieja y la siguiente
 * apertura de la app vuelve a intentarlo.
 */
object UltimaConciliacionDeVisitas {

    private const val PREFS = "visitas_conciliacion_ultima"
    private const val CLAVE = "ultima"

    /** Ventana del spec §4.6. */
    val PERIODO: Duration = Duration.ofHours(24)

    /**
     * Pura. Vencida si nunca corrió, si pasaron [PERIODO] o más, o si la marca
     * está en el futuro (el reloj del teléfono retrocedió): una marca futura no
     * puede posponer la reconciliación.
     */
    fun vencida(ultima: Instant?, ahora: Instant): Boolean =
        ultima == null || ultima.isAfter(ahora) || Duration.between(ultima, ahora) >= PERIODO

    fun leer(context: Context): Instant? = AppTime.parseWireFormatOrNull(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLAVE, null)
    )

    fun anotar(context: Context, cuando: Instant) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(CLAVE, AppTime.toWireFormat(cuando))
            .apply()
    }
}
