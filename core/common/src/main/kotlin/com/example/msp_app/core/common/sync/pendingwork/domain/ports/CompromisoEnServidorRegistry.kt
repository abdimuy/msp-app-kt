package com.example.msp_app.core.common.sync.pendingwork.domain.ports

import com.example.msp_app.core.common.visitas.envio.CompromisoLocal

/** El compromiso que el servidor dice tener de una visita que existe ([CompromisoLocal.NINGUNO] si no tiene). */
data class CompromisoEnServidor(val visitaId: String, val compromiso: CompromisoLocal)

/**
 * `GET /v2/visitas/by-ids?detalle=1` visto por la conciliación inversa del
 * compromiso (E-APP-132). Una visita que el servidor no tiene simplemente no
 * aparece en la respuesta; un fallo de transporte se propaga: solo el caso de
 * uso decide qué significa (nunca "el servidor no la tiene").
 */
interface CompromisoEnServidorRegistry {

    suspend fun findCompromisos(visitIds: List<String>): List<CompromisoEnServidor>

    companion object {
        const val MAX_IDS_PER_REQUEST: Int = VisitCustodyRegistry.MAX_IDS_PER_REQUEST
    }
}
