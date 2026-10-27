package com.example.msp_app.core.visitas.envio

import android.content.Context
import java.time.Duration
import java.time.Instant

/**
 * Cuándo vale la pena mandar `visitas_estado` (revisión de la Tarea 14;
 * E-VIS-029 en msp-api `docs/evidencia/visitas.md`).
 *
 * Sin límite, `VisitsReconcileWorker` lo mandaría cada 15 min: ~96 escrituras
 * al día por teléfono, ~5,500 en la flota (57 teléfonos), cuando el resto de
 * `RemoteLogger` solo escribe ante eventos. La cuota de Firestore ya cerró la
 * cuenta una vez. Con este límite se manda:
 *
 * - cuando el contenido cambia respecto al último mandado (sin contar la
 *   antigüedad de la más vieja, que cambia en cada corrida mientras haya
 *   pendientes), o
 * - como latido, si pasó [latido] (1 h) desde el último, aunque no cambie:
 *   así la alerta §12-5 ("teléfono sin reporte > 24 h") sigue sirviendo y la
 *   antigüedad de la pendiente más vieja se actualiza al menos cada hora.
 *
 * Techo: 24 latidos al día más los cambios reales.
 *
 * Un último envío en el futuro (reloj que retrocedió, o una memoria que vino
 * de otro aparato) cuenta como vencido: nunca silencia el reporte.
 */
class LimitadorDeEstadoDeVisitas(
    private val memoria: MemoriaDeEstadoEmitido,
    private val latido: Duration = LATIDO
) {

    fun debeEmitir(datos: Map<String, Any>, ahora: Instant): Boolean {
        val ultimo = memoria.ultimoEnvio() ?: return true
        if (ultimo.isAfter(ahora)) return true
        if (Duration.between(ultimo, ahora) >= latido) return true
        return huella(datos) != memoria.ultimaHuella()
    }

    fun anotar(datos: Map<String, Any>, ahora: Instant) {
        memoria.anotar(huella(datos), ahora)
    }

    companion object {
        val LATIDO: Duration = Duration.ofHours(1)

        /** El contenido comparable: todo menos la antigüedad, en orden estable. */
        fun huella(datos: Map<String, Any>): String =
            datos.filterKeys { it != TelemetriaDeVisitas.PENDIENTE_MAS_VIEJA_MIN }
                .toSortedMap()
                .toString()
    }
}

/** Lo último que se mandó de `visitas_estado`. */
interface MemoriaDeEstadoEmitido {
    fun ultimaHuella(): String?
    fun ultimoEnvio(): Instant?
    fun anotar(huella: String, en: Instant)
}

/** En memoria del proceso (pruebas, y el camino sin `Context`). */
class MemoriaDeEstadoEnProceso : MemoriaDeEstadoEmitido {
    @Volatile private var huella: String? = null

    @Volatile private var envio: Instant? = null

    override fun ultimaHuella(): String? = huella
    override fun ultimoEnvio(): Instant? = envio
    override fun anotar(huella: String, en: Instant) {
        this.huella = huella
        envio = en
    }
}

/**
 * En `SharedPreferences` (`visitas_telemetria`): sobrevive a que el sistema
 * mate el proceso entre corridas del worker. Se excluye del respaldo
 * (`data_extraction_rules.xml`, `backup_rules.xml`): solo vale en el aparato
 * que la escribió.
 */
class PreferenciasDeEstadoEmitido(context: Context) : MemoriaDeEstadoEmitido {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun ultimaHuella(): String? = prefs.getString(CLAVE_HUELLA, null)

    override fun ultimoEnvio(): Instant? =
        prefs.getLong(CLAVE_ENVIO, SIN_ENVIO).takeIf { it != SIN_ENVIO }?.let(Instant::ofEpochMilli)

    override fun anotar(huella: String, en: Instant) {
        prefs.edit().putString(CLAVE_HUELLA, huella).putLong(CLAVE_ENVIO, en.toEpochMilli()).apply()
    }

    companion object {
        const val PREFS = "visitas_telemetria"
        private const val CLAVE_HUELLA = "estado_huella"
        private const val CLAVE_ENVIO = "estado_enviado_en_ms"
        private const val SIN_ENVIO = Long.MIN_VALUE
    }
}
