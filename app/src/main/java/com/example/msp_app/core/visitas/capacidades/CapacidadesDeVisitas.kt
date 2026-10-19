package com.example.msp_app.core.visitas.capacidades

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Lo que el servidor anunció en `GET /v2/visitas/capacidades` (spec visitas
 * §5.2.1). [contrato] 2 = el cuerpo de siempre; 3 = acepta y devuelve los
 * cinco campos del compromiso. [compromisos] es la bandera de servidor
 * `VISITAS_COMPROMISOS_ENABLED`.
 */
data class Capacidades(val contrato: Int, val compromisos: Boolean) {

    /** El ÚNICO gate de los campos nuevos en el envío (E-APP-097). */
    fun mandaCompromisos(): Boolean = contrato >= CONTRATO_CON_COMPROMISOS && compromisos

    companion object {
        const val CONTRATO_CON_COMPROMISOS = 3

        /** Lo que se asume sin prueba, y lo que significa un 404: API vieja. */
        val CONTRATO_2 = Capacidades(contrato = 2, compromisos = false)
    }
}

/**
 * La capacidad negociada, persistida en `SharedPreferences`
 * (`visitas_capacidades`, excluida del respaldo: es del servidor al que habla
 * ESTE aparato).
 *
 * E-APP-097 (plan visitas P2, Tarea 15). Reglas de [refrescar]:
 *  - 200 con `contrato` y `compromisos` → se guarda tal cual.
 *  - **404** → [Capacidades.CONTRATO_2]: un API vieja no tiene la ruta. Así un
 *    APK nuevo contra un API vieja nunca manda claves que esta rechaza con 422.
 *  - Cualquier otro error (red, 5xx, 401, cuerpo ilegible o sin las claves) →
 *    se **conserva la vigente** y no se marca como refrescada: el siguiente
 *    periodo lo vuelve a intentar.
 *
 * No guarda ningún servicio: el `api` llega por parámetro en cada llamada, y
 * quien lo crea lo crea por uso (regla del kill-switch de baseURL, nada de
 * `@Singleton` sobre un servicio Retrofit).
 */
class CapacidadesDeVisitas(
    private val prefs: SharedPreferences,
    private val clock: AppClock = AppClock.System
) {

    /** La vigente; [Capacidades.CONTRATO_2] si nunca se negoció. */
    fun actual(): Capacidades {
        if (!prefs.contains(CLAVE_CONTRATO)) return Capacidades.CONTRATO_2
        return Capacidades(
            contrato = prefs.getInt(CLAVE_CONTRATO, Capacidades.CONTRATO_2.contrato),
            compromisos = prefs.getBoolean(CLAVE_COMPROMISOS, false)
        )
    }

    /** `true` si nunca se refrescó o si pasaron [CADA] o más desde la última vez. */
    fun necesitaRefresco(ahora: Instant = clock.now()): Boolean {
        val ultima = prefs.getLong(CLAVE_REFRESCADA_EN, NUNCA).takeIf { it != NUNCA } ?: return true
        val transcurrido = Duration.between(Instant.ofEpochMilli(ultima), ahora)
        // Un reloj que retrocedió también refresca: no se confía en una marca del futuro.
        return transcurrido.isNegative || transcurrido >= CADA
    }

    /**
     * Pregunta al servidor y guarda lo que corresponda (reglas en la clase).
     * Nunca lanza salvo la cancelación.
     *
     * @return la capacidad vigente después de preguntar.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun refrescar(api: V2VisitsApi): Capacidades {
        val nueva = try {
            val dto = api.capacidades()
            val contrato = dto.contrato
            val compromisos = dto.compromisos
            if (contrato == null || compromisos == null) {
                Log.w(TAG, "capacidades sin contrato o compromisos; se conserva la vigente")
                null
            } else {
                Capacidades(contrato, compromisos)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            if (e.code() == HTTP_NOT_FOUND) {
                Capacidades.CONTRATO_2
            } else {
                Log.w(TAG, "capacidades HTTP ${e.code()}; se conserva la vigente")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "capacidades falló; se conserva la vigente", e)
            null
        }
        if (nueva != null) guardar(nueva)
        return actual()
    }

    /** [refrescar] solo si [necesitaRefresco]. */
    suspend fun refrescarSiToca(api: () -> V2VisitsApi): Capacidades =
        if (necesitaRefresco()) refrescar(api()) else actual()

    private fun guardar(c: Capacidades) {
        prefs.edit()
            .putInt(CLAVE_CONTRATO, c.contrato)
            .putBoolean(CLAVE_COMPROMISOS, c.compromisos)
            .putLong(CLAVE_REFRESCADA_EN, clock.now().toEpochMilli())
            .commit()
    }

    companion object {
        /** Nombre del archivo de preferencias; `ReglasDeRespaldoTest` lo exige excluido. */
        const val PREFS = "visitas_capacidades"

        /** Spec visitas §5.2.1: al abrir sesión y cada 6 h. */
        val CADA: Duration = Duration.ofHours(6)

        private const val TAG = "CapacidadesDeVisitas"
        private const val CLAVE_CONTRATO = "contrato"
        private const val CLAVE_COMPROMISOS = "compromisos"
        private const val CLAVE_REFRESCADA_EN = "refrescada_en_ms"
        private const val NUNCA = Long.MIN_VALUE
        private const val HTTP_NOT_FOUND = 404

        fun de(context: Context, clock: AppClock = AppClock.System): CapacidadesDeVisitas =
            CapacidadesDeVisitas(
                context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE),
                clock
            )
    }
}
