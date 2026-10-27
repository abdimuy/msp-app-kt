package com.example.msp_app.core.avisos

import android.content.Context
import android.content.SharedPreferences
import java.time.Duration
import java.time.Instant

/**
 * Cuántas alarmas de aviso sonaron en las últimas 24 h, para la telemetría
 * (`avisos.disparados_24h`; plan P3, Tarea 15; E-APP-134). Ventana móvil en
 * `SharedPreferences`: solo guarda instantes, nada del cliente ni de la visita.
 */
class ContadorDeDisparos(private val prefs: SharedPreferences) {

    fun registrar(ahora: Instant) {
        val vigentes = leer(ahora) + ahora.toEpochMilli()
        prefs.edit().putString(CLAVE, vigentes.joinToString(",")).apply()
    }

    fun ultimas24h(ahora: Instant): Int = leer(ahora).size

    private fun leer(ahora: Instant): List<Long> {
        val corte = ahora.minus(VENTANA).toEpochMilli()
        return prefs.getString(CLAVE, "").orEmpty()
            .split(",")
            .mapNotNull { it.toLongOrNull() }
            .filter { it > corte }
    }

    companion object {
        private const val CLAVE = "disparos"
        private val VENTANA = Duration.ofHours(24)

        fun de(context: Context) = ContadorDeDisparos(
            context.getSharedPreferences("avisos_disparos", Context.MODE_PRIVATE)
        )
    }
}
