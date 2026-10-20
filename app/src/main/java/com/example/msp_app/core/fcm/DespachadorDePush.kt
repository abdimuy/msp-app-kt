package com.example.msp_app.core.fcm

import android.util.Log

/**
 * El push es solo un despertador (spec §6.4): lo único que se mira es
 * `tipo == "sync"`; el contenido del mensaje no se usa para nada, y [encolar]
 * no recibe argumentos para que no pueda usarse (plan P3, Tarea 14).
 */
class DespachadorDePush(
    private val fcmActivo: () -> Boolean,
    private val encolar: () -> Unit
) {
    fun alMensaje(datos: Map<String, String>) {
        if (datos["tipo"] != "sync") {
            Log.i(TAG, "push ignorado: tipo desconocido")
            return
        }
        despertar()
    }

    /** FCM descartó mensajes: no se sabe cuáles, se hace un sync completo. */
    fun alMensajesBorrados() = despertar()

    @Suppress("TooGenericExceptionCaught")
    private fun despertar() {
        if (!fcmActivo()) return
        try {
            encolar()
        } catch (e: Exception) {
            Log.w(TAG, "no se pudo encolar el sync de tareas", e)
        }
    }

    private companion object {
        const val TAG = "DespachadorDePush"
    }
}
