package com.example.msp_app.core.appgate

import kotlinx.coroutines.flow.Flow

/**
 * De dónde sale [MinVersionConfig].
 *
 * Es un puerto —no la clase de Firestore— por una razón concreta: la decisión
 * de bloquear la app es lo más delicado que hace este módulo y tiene que poder
 * probarse con un `Flow` de fixtures, sin inicializar Firebase ni depender de
 * la red. La implementación real vive aparte
 * ([FirestoreMinVersionConfigSource]).
 *
 * El `Flow` es **caliente en la fuente y frío en el borde**: emite cada vez
 * que el documento remoto cambia y nunca termina por sí solo. Errores de red
 * NO se propagan como excepción — simplemente no hay emisión, y quien consume
 * se queda con el último veredicto en caché (ver [VersionGateCache]).
 */
fun interface MinVersionConfigSource {
    fun observe(): Flow<MinVersionConfig>
}
