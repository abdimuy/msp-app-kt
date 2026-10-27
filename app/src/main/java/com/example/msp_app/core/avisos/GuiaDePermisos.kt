package com.example.msp_app.core.avisos

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.time.Duration
import java.time.Instant

/** Lo que se le puede pedir al cobrador para que los avisos lleguen a tiempo. */
enum class PasoDePermiso(val rotulo: String, val accion: String) {
    ALARMAS_EXACTAS("Alarmas exactas", "Permitir"),
    BATERIA("Batería sin restricción", "Permitir")
}

/**
 * Cuándo y cómo se piden los permisos de los avisos (plan visitas P3, Tarea 15;
 * spec §6.6; E-APP-134).
 *
 * - **Solo en el momento útil**: cuando ya hay avisos programados; antes no hay
 *   nada que explicar y pedirlo sería ruido.
 * - **Nunca bloquea**: es un diálogo que se cierra con "Ahora no", se vuelve a
 *   ofrecer a lo sumo cada 7 días y el cobro no depende de él. Si el cobrador
 *   niega, las alarmas caen a ventana de 10 min y las notificaciones se omiten
 *   sin error ([ReprogramadorDeAlarmas], [AvisoAlarmReceiver]).
 * - **Batería**: se abre la lista del sistema
 *   (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`); no se declara
 *   `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` porque Play lo restringe.
 * - Las notificaciones (Android 13+) ya se piden en el inicio junto a la
 *   ubicación (T12); aquí no se repiten.
 */
object GuiaDePermisos {
    const val TITULO = "Avisos de tus visitas"
    const val EXPLICACION =
        "Para avisarte a tiempo de tus promesas y citas, permite las alarmas exactas " +
            "y deja la app sin restricción de batería. Tu cobro no cambia"
    const val AHORA_NO = "Ahora no"

    private val REINTENTO = Duration.ofDays(7)

    fun pasosPendientes(
        estado: EstadoDePermisos,
        sdk: Int,
        hayAvisos: Boolean,
        ultimaPregunta: Instant?,
        ahora: Instant
    ): List<PasoDePermiso> {
        if (!hayAvisos) return emptyList()
        if (ultimaPregunta != null && Duration.between(ultimaPregunta, ahora) < REINTENTO) {
            return emptyList()
        }
        return buildList {
            if (sdk >= Build.VERSION_CODES.S && !estado.alarmasExactas) {
                add(
                    PasoDePermiso.ALARMAS_EXACTAS
                )
            }
            if (!estado.bateriaSinOptimizar) add(PasoDePermiso.BATERIA)
        }
    }

    fun intentDe(paso: PasoDePermiso, paquete: String, sdk: Int): Intent? = when (paso) {
        PasoDePermiso.BATERIA -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        PasoDePermiso.ALARMAS_EXACTAS ->
            if (sdk >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$paquete"))
            } else {
                null
            }
    }

    /** Abre el ajuste; total: si el aparato no tiene esa pantalla, no pasa nada. */
    @Suppress("TooGenericExceptionCaught")
    fun abrir(context: Context, paso: PasoDePermiso): Boolean {
        val intent = intentDe(paso, context.packageName, Build.VERSION.SDK_INT) ?: return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }
    }
}

/** Cuándo se ofreció por última vez (solo un instante). */
class MemoriaDeLaPregunta(private val prefs: SharedPreferences) {
    fun ultima(): Instant? = prefs.getLong(CLAVE, 0L).takeIf { it > 0L }?.let(Instant::ofEpochMilli)
    fun marcar(ahora: Instant) {
        prefs.edit().putLong(CLAVE, ahora.toEpochMilli()).apply()
    }

    companion object {
        private const val CLAVE = "ultima_pregunta"
        fun de(context: Context) = MemoriaDeLaPregunta(
            context.getSharedPreferences("avisos_permisos", Context.MODE_PRIVATE)
        )
    }
}
