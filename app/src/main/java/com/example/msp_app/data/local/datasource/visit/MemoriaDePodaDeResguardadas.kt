package com.example.msp_app.data.local.datasource.visit

import android.content.Context

/**
 * Hasta qué límite ya se REPORTARON las visitas resguardadas por podar (spec
 * visitas §4.2-8: "se reporta antes de podar"; plan P1 Tarea 12).
 *
 * La poda de resguardadas de una corrida solo alcanza lo que una corrida
 * ANTERIOR ya contó y reportó: en la corrida N se reporta lo que rebasó el
 * límite de hoy y se anota ese límite; en la N+1 se poda hasta el límite
 * anotado. Una fila resguardada nunca se borra en la misma corrida en que se
 * reporta por primera vez.
 *
 * Valores en el formato de cable de `AppTime.toWireFormat`.
 */
interface MemoriaDePodaDeResguardadas {
    /** Límite reportado en una corrida anterior, o `null` si nunca se reportó. */
    fun reportadasHasta(): String?
    fun anotarReportadasHasta(limite: String)

    /**
     * Sin memoria: nunca hay nada reportado, así que nunca se poda una
     * resguardada. Es la opción segura para quien construye la fuente sin
     * `Context` (el camino `@Inject` / pruebas), que hoy no poda.
     */
    object Ninguna : MemoriaDePodaDeResguardadas {
        override fun reportadasHasta(): String? = null
        override fun anotarReportadasHasta(limite: String) = Unit
    }
}

/** Memoria en `SharedPreferences` (`visitas_poda`), sobrevive al proceso. */
class PreferenciasDePodaDeResguardadas(context: Context) : MemoriaDePodaDeResguardadas {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun reportadasHasta(): String? = prefs.getString(CLAVE, null)

    override fun anotarReportadasHasta(limite: String) {
        prefs.edit().putString(CLAVE, limite).apply()
    }

    private companion object {
        const val PREFS = "visitas_poda"
        const val CLAVE = "resguardadas_reportadas_hasta"
    }
}
