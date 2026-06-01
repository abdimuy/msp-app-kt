package com.example.msp_app.core.sync.cobranza

import java.time.Instant

/**
 * Contexto del cobrador que el [CobranzaSyncManager] necesita en cada
 * tick: la zona asignada y la fecha de inicio de la ventana visible
 * (FECHA_CARGA_INICIAL del documento de usuario en Firestore).
 *
 * Se resuelve perezosamente por tick — la capa Compose lo provee como un
 * thunk, así si la zona o la ventana cambian a media sesión el siguiente
 * sync ya las ve.
 */
data class UserContext(
    val zona: Int,
    val fechaCargaInicial: Instant?
)
