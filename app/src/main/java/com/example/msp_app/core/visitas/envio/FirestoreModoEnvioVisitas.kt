package com.example.msp_app.core.visitas.envio

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Lee `config/visitas_envio` con un snapshot listener (patrón de
 * `RemoteDbDebugger.startConfigListener`) y expone el modo vigente.
 *
 * - Arranca en V3 y vuelve a V3 si el documento desaparece o trae basura.
 * - Un error de Firestore NO cambia el modo vigente.
 * - [instalar] nunca propaga: si Firestore no está disponible el proceso
 *   sigue en el modo vigente y el siguiente arranque vuelve a intentarlo.
 * - Leer [actual] no puede fallar: es un campo `@Volatile`.
 *
 * Solo lee; nunca escribe en Firestore.
 */
object FirestoreModoEnvioVisitas {
    private const val TAG = "ModoEnvioVisitas"
    private const val DOCUMENTO = "visitas_envio"

    @Volatile
    var actual: ModoEnvioVisitas = ModoEnvioVisitas.V3
        private set

    @Volatile
    private var instalado = false

    @Synchronized
    fun instalar() {
        if (instalado) return
        try {
            FirebaseFirestore.getInstance()
                .collection(Constants.COLLECTION_CONFIG)
                .document(DOCUMENTO)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        alFallar(err)
                        return@addSnapshotListener
                    }
                    aplicar(snap?.data)
                }
            instalado = true
        } catch (e: Exception) {
            alFallar(e)
        }
    }

    private fun aplicar(datos: Map<String, Any?>?) {
        val nuevo = modoDesde(datos)
        if (nuevo != actual) Log.w(TAG, "modo de envío de visitas: $actual -> $nuevo")
        actual = nuevo
    }

    /** Un error de Firestore NO cambia el modo vigente. */
    private fun alFallar(e: Throwable) {
        Log.w(TAG, "config/visitas_envio ilegible; se conserva $actual", e)
    }

    @VisibleForTesting
    fun aplicarParaPrueba(datos: Map<String, Any?>?) = aplicar(datos)

    @VisibleForTesting
    fun alFallarParaPrueba(e: Throwable) = alFallar(e)
}
