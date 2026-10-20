package com.example.msp_app.core.fcm

import com.google.firebase.messaging.FirebaseMessaging
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Suscripción a temas de FCM; interfaz para probar sin Firebase (plan P3, Tarea 14). */
interface Temas {
    suspend fun suscribir(tema: String)
    suspend fun desuscribir(tema: String)
}

/** Implementación real. Firebase reintenta por su cuenta; un fallo se propaga a quien llama. */
class FirebaseTemas(
    private val mensajeria: () -> FirebaseMessaging = { FirebaseMessaging.getInstance() }
) : Temas {
    override suspend fun suscribir(tema: String) = esperar { mensajeria().subscribeToTopic(tema) }

    override suspend fun desuscribir(tema: String) =
        esperar { mensajeria().unsubscribeFromTopic(tema) }

    private suspend fun esperar(tarea: () -> com.google.android.gms.tasks.Task<Void>) =
        suspendCancellableCoroutine<Unit> { c ->
            tarea().addOnSuccessListener { c.resume(Unit) }
                .addOnFailureListener { c.resumeWithException(it) }
        }
}
