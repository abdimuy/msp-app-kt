package com.example.msp_app.core.ubicacion

import com.example.msp_app.core.common.location.captura.ConfigDeUbicacionVigente
import com.example.msp_app.core.common.location.captura.FuenteDeConfigRemota
import com.example.msp_app.core.utils.Constants
import com.google.firebase.firestore.FirebaseFirestore

/** Lee el documento `config/ubicacion` (E-APP-141). Mismo patrón que `KillSwitchAvisos`. */
class FirestoreConfigDeUbicacion(
    private val firestore: () -> FirebaseFirestore = { FirebaseFirestore.getInstance() }
) : FuenteDeConfigRemota {
    override fun escuchar(alRecibir: (Map<String, Any?>?, Throwable?) -> Unit) {
        firestore()
            .collection(Constants.COLLECTION_CONFIG)
            .document(DOCUMENTO)
            .addSnapshotListener { snap, err -> alRecibir(snap?.data, err) }
    }

    companion object {
        const val DOCUMENTO = "ubicacion"
    }
}

/** Instancia del proceso; la instala `MspApplication.onCreate`. */
object ConfigDeUbicacion {
    private val vigente = ConfigDeUbicacionVigente(FirestoreConfigDeUbicacion())

    val actual get() = vigente.actual

    fun instalar() = vigente.instalar()
}
