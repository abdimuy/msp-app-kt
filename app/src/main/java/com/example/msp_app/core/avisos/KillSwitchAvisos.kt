package com.example.msp_app.core.avisos

import android.util.Log
import androidx.annotation.VisibleForTesting
import com.example.msp_app.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Interruptor de los avisos (plan visitas P3, Tarea 13; E-APP-124): documento
 * `config/visitas_avisos`, campos `fcm` y `alarmas`.
 *
 * **Apagado por omisión.** Solo el booleano `true` enciende cada uno: documento
 * ausente, campo ausente, `"true"` como texto, `1` o cualquier otra cosa =
 * apagado. Un error de Firestore NO cambia el valor vigente. Solo lee.
 */
class KillSwitchAvisosVigente(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) {
    @Volatile
    var alarmas: Boolean = false
        private set

    @Volatile
    var fcm: Boolean = false
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
            Log.w(TAG, "config/$DOCUMENTO ilegible; se conserva alarmas=$alarmas", e)
        }
    }

    fun alRecibir(datos: Map<String, Any?>?, err: Throwable?) {
        if (err != null) {
            Log.w(TAG, "config/$DOCUMENTO ilegible; se conserva alarmas=$alarmas", err)
            return
        }
        alarmas = datos?.get(CAMPO_ALARMAS) == true
        fcm = datos?.get(CAMPO_FCM) == true
    }

    companion object {
        private const val TAG = "KillSwitchAvisos"
        const val DOCUMENTO = "visitas_avisos"
        const val CAMPO_ALARMAS = "alarmas"
        const val CAMPO_FCM = "fcm"
    }
}

/** Instancia del proceso; la instala `MspApplication.onCreate`. */
object KillSwitchAvisos {
    private val vigente = KillSwitchAvisosVigente()

    val alarmas: Boolean get() = vigente.alarmas

    fun instalar() = vigente.instalar()

    @VisibleForTesting
    fun aplicarParaPrueba(datos: Map<String, Any?>?) = vigente.alRecibir(datos, null)
}
