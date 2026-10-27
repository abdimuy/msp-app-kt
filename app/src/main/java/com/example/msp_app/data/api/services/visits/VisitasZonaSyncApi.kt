package com.example.msp_app.data.api.services.visits

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Canal de bajada de las visitas de la zona (msp-api P2 Tarea 10,
 * `internal/visitas/infra/visitashttp/dto_sync.go`; spec visitas §5.4).
 *
 * Los cuatro canales comparten un solo `WHERE` en el servidor (I4) y deben
 * recibir el MISMO `desde` (RFC3339, instante). El teléfono lo calcula una vez
 * por corrida y lo manda en todas las páginas (`syncEnviaDesdeEnTodasLasPaginas`).
 *
 * ## Gson
 *
 * Todos los campos son nulables: Gson deja en nulo lo ausente sin importar lo
 * que diga el tipo de Kotlin, y un `Int` no nulo que llega nulo revienta lejos
 * de aquí. Quien consume decide qué hacer con un nulo.
 *
 * ## La nota no viaja (E-VIS-066)
 *
 * El servidor no manda la nota libre de la visita en la bajada (decisión
 * provisional del dueño). [VisitaZonaDto] no la declara: si un servidor la
 * mandara, Gson la ignora y nunca llega a `visita_zona` (E-APP-103).
 *
 * Sin `@Singleton`: el servicio se crea por corrida (kill-switch de baseURL).
 */
interface VisitasZonaSyncApi {

    /**
     * Una página de la bajada por cursor par `(updated_at, id)`.
     *
     * @param cursor `updated_at` de la última fila aplicada, tal como lo
     *   entregó el servidor; `null` = desde el inicio (Retrofit omite el
     *   parámetro).
     * @param afterId id de la última fila aplicada; exige [cursor].
     */
    @GET("v2/visitas/sync/zona/{zona_id}")
    suspend fun sync(
        @Path("zona_id") zona: Int,
        @Query("desde") desde: String,
        @Query("cursor") cursor: String?,
        @Query("after_id") afterId: String?,
        @Query("limit") limit: Int?
    ): SyncVisitasZonaResponse

    /** Resumen del MISMO conjunto que entrega [sync] para la misma zona y [desde]. */
    @GET("v2/visitas/sync/zona/{zona_id}/digest")
    suspend fun digest(
        @Path("zona_id") zona: Int,
        @Query("desde") desde: String
    ): DigestVisitasZonaResponse

    /** Ids del MISMO conjunto, ordenados por id en bytes, paginados por [after]. */
    @GET("v2/visitas/sync/zona/{zona_id}/ids")
    suspend fun ids(
        @Path("zona_id") zona: Int,
        @Query("desde") desde: String,
        @Query("after") after: String?,
        @Query("limit") limit: Int?
    ): IdsVisitasZonaResponse

    /**
     * Rescate puntual. [ids] va separado por comas, **máximo [MAX_IDS_BY_IDS]**
     * (el servidor responde 422 con 101: los ids viajan en la URL).
     */
    @GET("v2/visitas/sync/zona/{zona_id}/by-ids")
    suspend fun byIds(
        @Path("zona_id") zona: Int,
        @Query("desde") desde: String,
        @Query("ids") ids: String
    ): ByIdsVisitasZonaResponse

    companion object {
        /** `visitasapp.MaxIDsByIDsBajada` del servidor. */
        const val MAX_IDS_BY_IDS = 100

        /** Filas por página que pide el teléfono (servidor: 1..2000, default 500). */
        const val LIMITE_PAGINA = 500
    }
}

/** Una visita de la zona tal como la entrega la bajada. Sin `nota` (E-VIS-066). */
data class VisitaZonaDto(
    val id: String? = null,
    val cliente_id: Int? = null,
    val cobrador_id: Int? = null,
    val cobrador: String? = null,
    val forma_cobro_id: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val tipo_visita: String? = null,
    val zona_cliente_id: Int? = null,
    val impte_docto_cc_id: Int? = null,
    val fecha: String? = null,
    val promesa_venta_id: Int? = null,
    val promesa_fecha: String? = null,
    val promesa_monto_centavos: Long? = null,
    val cita_fecha: String? = null,
    val cita_hora: String? = null,
    val updated_at: String? = null
)

/**
 * Una página de la bajada.
 *
 * - [cursor]: `updated_at` de la última fila; en página vacía, **el cursor
 *   recibido**; `null` si nunca hubo.
 * - [after_id]: id de la última fila; `null` en página vacía = "sin posición
 *   nueva" (se conserva el guardado, `COBRANZA-SYNC.md` §2).
 * - [sync_epoch]: generación de la bajada; ausente, nulo, 0 o negativo =
 *   mecanismo apagado (`COBRANZA-SYNC.md` §3).
 */
data class SyncVisitasZonaResponse(
    val items: List<VisitaZonaDto>? = null,
    val cursor: String? = null,
    val after_id: String? = null,
    val has_more: Boolean? = null,
    val sync_epoch: Int? = null,
    val server_now: String? = null
)

data class DigestVisitasZonaResponse(
    val count: Int? = null,
    val hash: String? = null,
    val max_updated_at: String? = null
)

data class IdsVisitasZonaResponse(
    val ids: List<String>? = null,
    val has_more: Boolean? = null
)

data class ByIdsVisitasZonaResponse(
    val items: List<VisitaZonaDto>? = null
)
