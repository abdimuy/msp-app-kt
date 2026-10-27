package com.example.msp_app.data.api.services.visits

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Conciliación directa de la agenda (plan visitas P2, Tarea 18; spec §4.6-2 y
 * §5.5; E-APP-131). El teléfono manda `(venta, visita, estado)` de lo que su
 * [com.example.msp_app.core.visitas.agenda.AgendaLocal] derivó para su "hoy"; el
 * servidor deriva la suya con el mismo "hoy" y responde las ventas que difieren.
 *
 * ## La ruta está FUERA de `/v2/visitas` a propósito (E-VIS-116)
 *
 * La captura de intentos fallidos de visitas es por prefijo `/v2/visitas`: bajo
 * él, cada 4xx/5xx de esta consulta diaria dejaría una fila en
 * `MSP_FAILED_INTENTS`, que es la bandeja de dinero. Decisión del dueño
 * (2026-10-27): la conciliación de visitas no escribe ahí. Su reintento es
 * propio: la siguiente corrida del worker diario.
 *
 * ## Gson
 *
 * La respuesta es toda nulable: Gson deja en nulo lo ausente sin importar el
 * tipo de Kotlin. El cuerpo de la petición lo arma el teléfono y no lleva
 * datos personales: ids numéricos de venta, UUID de visita y un estado.
 *
 * Sin `@Singleton`: el servicio se crea por corrida (kill-switch de baseURL).
 */
interface VisitasAgendaApi {

    @POST("v2/agenda-compromisos/conciliacion")
    suspend fun conciliar(@Body body: ConciliacionAgendaRequest): ConciliacionAgendaResponse

    companion object {
        /** `visitasapp.MaxItemsConciliacion` del servidor: arriba de esto responde 422. */
        const val MAX_ITEMS = 5000
    }
}

data class ConciliacionAgendaRequest(
    val zona_id: Int,
    val hoy: String,
    val items: List<ItemConciliacionDto>
)

data class ItemConciliacionDto(
    val venta_id: Int,
    val visita_id: String,
    val estado: String
)

data class ConciliacionAgendaResponse(val diferencias: List<DiferenciaAgendaDto>?)

data class DiferenciaAgendaDto(
    val venta_id: Int?,
    val telefono: LadoConciliacionDto?,
    val servidor: LadoConciliacionDto?
)

data class LadoConciliacionDto(val visita_id: String?, val estado: String?)
