package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisoEnServidor
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisoEnServidorRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.CompromisoEnServidorRegistry.Companion.MAX_IDS_PER_REQUEST
import com.example.msp_app.core.common.visitas.envio.CompromisoLocal
import com.example.msp_app.data.api.services.visits.V2VisitsApi
import javax.inject.Inject

/**
 * Adapter de [CompromisoEnServidorRegistry] sobre
 * `GET /v2/visitas/by-ids?detalle=1` (contrato 3, spec §5.2.4). Traduce y valida
 * la tanda; no decide nada: un fallo de transporte se propaga sin tocar. Sin
 * `@Singleton` (guarda un servicio Retrofit; kill-switch de baseURL).
 *
 * Un elemento sin `id` no se puede atribuir a ninguna visita y se descarta.
 */
class V2CompromisoEnServidorRegistry @Inject constructor(
    private val api: V2VisitsApi
) : CompromisoEnServidorRegistry {

    override suspend fun findCompromisos(visitIds: List<String>): List<CompromisoEnServidor> {
        require(visitIds.isNotEmpty()) {
            "by-ids nunca se llama con lista vacia: el servidor responde 422 ids_required"
        }
        require(visitIds.size <= MAX_IDS_PER_REQUEST) {
            "by-ids acepta como maximo $MAX_IDS_PER_REQUEST ids por peticion " +
                "(recibidos ${visitIds.size}); el servidor responde 422 ids_too_many"
        }
        return api.visitasExistentesPorIdsDetalle(
            visitIds.joinToString(separator = ","),
            V2VisitsApi.DETALLE_SI
        ).mapNotNull { v ->
            val id = v.id ?: return@mapNotNull null
            CompromisoEnServidor(
                visitaId = id,
                compromiso = CompromisoLocal(
                    promesaVentaId = v.promesa_venta_id,
                    promesaFecha = v.promesa_fecha,
                    promesaMontoCentavos = v.promesa_monto_centavos,
                    citaFecha = v.cita_fecha,
                    citaHora = v.cita_hora
                )
            )
        }
    }
}
