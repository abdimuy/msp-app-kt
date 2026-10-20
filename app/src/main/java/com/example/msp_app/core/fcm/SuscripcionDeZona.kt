package com.example.msp_app.core.fcm

import android.content.Context
import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

/** Última zona a la que se suscribió con éxito (persistente entre arranques). */
interface AlmacenDeZonaSuscrita {
    fun leer(): Int?
    fun guardar(zona: Int)
}

class AlmacenEnPreferencias(context: Context) : AlmacenDeZonaSuscrita {
    private val prefs = context.applicationContext
        .getSharedPreferences("fcm_zona", Context.MODE_PRIVATE)

    override fun leer(): Int? = prefs.getInt(CLAVE, 0).takeIf { it > 0 }

    override fun guardar(zona: Int) {
        prefs.edit().putInt(CLAVE, zona).apply()
    }

    private companion object {
        const val CLAVE = "zona"
    }
}

/**
 * Tema `zona-<id>` (el mismo que usa el servidor, `visitasfcm.MensajeDeZona`).
 * No hay tabla de tokens (DA-10). Solo con el interruptor `fcm` encendido; una
 * zona solo se recuerda cuando la suscripción tuvo éxito, así que un fallo se
 * reintenta en la siguiente llamada. Nunca lanza salvo cancelación (plan P3,
 * Tarea 14).
 */
class SuscripcionDeZona(
    private val temas: Temas,
    private val almacen: AlmacenDeZonaSuscrita,
    private val fcmActivo: () -> Boolean
) {
    suspend fun alCambiarZona(nueva: Int) {
        if (nueva <= 0 || !fcmActivo()) return
        val vieja = almacen.leer()
        if (vieja == nueva) return
        if (vieja != null) intentar("desuscribir zona $vieja") { temas.desuscribir(tema(vieja)) }
        val ok = intentar("suscribir zona $nueva") { temas.suscribir(tema(nueva)) }
        if (ok) almacen.guardar(nueva)
    }

    /** `onNewToken`: vuelve a suscribir a la zona recordada. */
    suspend fun resuscribir() {
        val zona = almacen.leer() ?: return
        if (!fcmActivo()) return
        intentar("resuscribir zona $zona") { temas.suscribir(tema(zona)) }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun intentar(que: String, bloque: suspend () -> Unit): Boolean = try {
        bloque()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "FCM: no se pudo $que", e)
        false
    }

    private fun tema(zona: Int) = "zona-$zona"

    private companion object {
        const val TAG = "SuscripcionDeZona"
    }
}
