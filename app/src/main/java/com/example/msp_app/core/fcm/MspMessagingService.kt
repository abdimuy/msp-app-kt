package com.example.msp_app.core.fcm

import com.example.msp_app.core.avisos.KillSwitchAvisos
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Recibe el push de zona: solo despierta el sync, nunca trae datos (spec §6.4). */
class MspMessagingService : FirebaseMessagingService() {

    private val despachador by lazy {
        DespachadorDePush(
            fcmActivo = { KillSwitchAvisos.fcm },
            encolar = { encolarSyncTareas(applicationContext) }
        )
    }

    override fun onMessageReceived(message: RemoteMessage) = despachador.alMensaje(message.data)

    override fun onDeletedMessages() = despachador.alMensajesBorrados()

    override fun onNewToken(token: String) {
        // El token no se guarda ni se manda: la suscripción es por tema.
        alcance.launch { SuscripcionDeZonaProvider.de(applicationContext).resuscribir() }
    }

    private companion object {
        val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
