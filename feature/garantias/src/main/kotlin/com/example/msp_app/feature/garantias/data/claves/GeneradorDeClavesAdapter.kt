package com.example.msp_app.feature.garantias.data.claves

import com.example.msp_app.feature.garantias.domain.port.GeneradorDeClavesPort
import java.util.UUID
import javax.inject.Inject

/**
 * Genera la clave de idempotencia de cada comando y de cada foto.
 *
 * Un UUID al azar, generado **una sola vez** al guardar. El adaptador no
 * recuerda las claves: la memoria está en la fila de Room, que es la que
 * sobrevive a un cierre de la app y a un reintento.
 */
class GeneradorDeClavesAdapter @Inject constructor() : GeneradorDeClavesPort {
    override fun nueva(): String = UUID.randomUUID().toString()
}
