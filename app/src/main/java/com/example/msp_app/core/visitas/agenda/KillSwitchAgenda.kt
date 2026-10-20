package com.example.msp_app.core.visitas.agenda

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Interruptor de la bajada de la agenda de visitas (plan visitas P2, Tarea 16;
 * E-APP-103): documento `config/visitas_compromisos`, campo `bajada`.
 *
 * **Apagado por omisión.** La bajada es nueva y su modo seguro es no bajar:
 * solo `bajada: true` (booleano) la enciende. Documento ausente, campo
 * ausente, `"true"` como texto, `1` o cualquier otra cosa = apagada, hasta el
 * paso 7 del despliegue (spec §11).
 *
 * - Un error de Firestore NO cambia el valor vigente.
 * - [instalar] nunca propaga; es idempotente.
 * - Solo lee; nunca escribe en Firestore. Nunca `config/api_settings`.
 *
 * Mismo patrón que `ModoEnvioVisitasVigente` (`config/visitas_envio`).
 */
class KillSwitchAgendaVigente(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    @Volatile
    var bajada: Boolean = bajadaDesde(null)
        private set

    @Volatile
    private var instalado = false

    @Synchronized
    @Suppress("TooGenericExceptionCaught")
    fun instalar() {
        if (instalado) return
        try {
            firestore()
                .collection(Constants.COLLECTION_CONFIG)
                .document(DOCUMENTO)
                .addSnapshotListener { snap, err -> alRecibir(snap?.data, err) }
            instalado = true
        } catch (e: Exception) {
            alFallar(e)
        }
    }

    fun alRecibir(datos: Map<String, Any?>?, err: Throwable?) {
        if (err != null) {
            alFallar(err)
            return
        }
        aplicar(datos)
    }

    internal fun aplicar(datos: Map<String, Any?>?) {
        val nuevo = bajadaDesde(datos)
        if (nuevo != bajada) Log.w(TAG, "bajada de la agenda de visitas: $bajada -> $nuevo")
        bajada = nuevo
    }

    internal fun alFallar(e: Throwable) {
        Log.w(TAG, "config/$DOCUMENTO ilegible; se conserva bajada=$bajada", e)
    }

    companion object {
        private const val TAG = "KillSwitchAgenda"
        const val DOCUMENTO = "visitas_compromisos"
        const val CAMPO_BAJADA = "bajada"

        /** Solo el booleano `true` enciende la bajada. */
        fun bajadaDesde(datos: Map<String, Any?>?): Boolean = datos?.get(CAMPO_BAJADA) == true
    }
}

/** Instancia del proceso; la instala `MspApplication.onCreate`. */
object KillSwitchAgenda {
    private val vigente = KillSwitchAgendaVigente()

    val bajada: Boolean get() = vigente.bajada

    fun instalar() = vigente.instalar()

    @VisibleForTesting
    fun aplicarParaPrueba(datos: Map<String, Any?>?) = vigente.aplicar(datos)
}
