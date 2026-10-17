package com.example.msp_app.core.visitas.envio

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Modo vigente del envío de visitas, alimentado por `config/visitas_envio`
 * con un snapshot listener (patrón de `RemoteDbDebugger.startConfigListener`).
 *
 * - Arranca en `modoDesde(null)` (V3) y vuelve a V3 si el documento
 *   desaparece o trae basura.
 * - Un error de Firestore NO cambia el modo vigente.
 * - [instalar] nunca propaga: si Firestore no está disponible el modo vigente
 *   se conserva y el siguiente [instalar] vuelve a intentarlo.
 * - Leer [actual] no puede fallar: es un campo `@Volatile`.
 *
 * Solo lee; nunca escribe en Firestore. [firestore] es costura para pruebas.
 */
class ModoEnvioVisitasVigente(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    @Volatile
    var actual: ModoEnvioVisitas = modoDesde(null)
        private set

    @Volatile
    private var instalado = false

    @Synchronized
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

    /** Cuerpo del snapshot listener: con error, el modo vigente no cambia. */
    fun alRecibir(datos: Map<String, Any?>?, err: Throwable?) {
        if (err != null) {
            alFallar(err)
            return
        }
        aplicar(datos)
    }

    internal fun aplicar(datos: Map<String, Any?>?) {
        val nuevo = modoDesde(datos)
        if (nuevo != actual) Log.w(TAG, "modo de envío de visitas: $actual -> $nuevo")
        actual = nuevo
    }

    /** Un error de Firestore NO cambia el modo vigente. */
    internal fun alFallar(e: Throwable) {
        Log.w(TAG, "config/visitas_envio ilegible; se conserva $actual", e)
    }

    private companion object {
        const val TAG = "ModoEnvioVisitas"
        const val DOCUMENTO = "visitas_envio"
    }
}

/** Instancia del proceso; la instala `MspApplication.onCreate`. */
object FirestoreModoEnvioVisitas {
    private val vigente = ModoEnvioVisitasVigente()

    val actual: ModoEnvioVisitas get() = vigente.actual

    fun instalar() = vigente.instalar()

    @VisibleForTesting
    fun aplicarParaPrueba(datos: Map<String, Any?>?) = vigente.aplicar(datos)

    @VisibleForTesting
    fun alFallarParaPrueba(e: Throwable) = vigente.alFallar(e)
}
