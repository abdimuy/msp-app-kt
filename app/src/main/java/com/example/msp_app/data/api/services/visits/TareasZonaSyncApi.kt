package com.example.msp_app.data.api.services.visits

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Canal de bajada de las tareas de oficina de la zona (msp-api P3 Tareas 4 y
 * 5, `internal/visitas/infra/visitashttp/dto_tareas.go`; spec visitas §6.2;
 * plan P3 Tarea 11; E-APP-122).
 *
 * Mismos parámetros y mismas reglas de cursor que la bajada de visitas
 * ([VisitasZonaSyncApi]): `desde`, `cursor`, `after_id` y `limit`. La forma de
 * las respuestas es la de los goldens del servidor
 * (`testdata/tareas-sync-pagina.golden.json` y `tareas-sync-pagina-vacia`),
 * copiados en `src/test/resources/tareas-golden/` y ejercidos por
 * `TareasZonaSyncManagerTest`.
 *
 * Todos los campos son nulables: Gson deja en nulo lo ausente sin importar lo
 * que diga el tipo de Kotlin. Quien consume decide qué hacer con un nulo.
 *
 * Sin `@Singleton`: el servicio se crea por corrida (kill-switch de baseURL).
 */
interface TareasZonaSyncApi {

    /**
     * Una página de la bajada por cursor par `(updated_at, id)`. Las tareas
     * canceladas llegan como cambios (`cancelada_at` no nulo), sin lápidas.
     *
     * @param cursor `updated_at` de la última fila aplicada, tal como lo
     *   entregó el servidor; `null` = desde el inicio.
     * @param afterId id de la última fila aplicada; exige [cursor].
     */
    @GET("v2/visitas/sync/tareas/zona/{zona_id}")
    suspend fun sync(
        @Path("zona_id") zona: Int,
        @Query("desde") desde: String,
        @Query("cursor") cursor: String?,
        @Query("after_id") afterId: String?,
        @Query("limit") limit: Int?
    ): SyncTareasZonaResponse

    companion object {
        /** Filas por página que pide el teléfono (servidor: 1..2000, default 500). */
        const val LIMITE_PAGINA = 500
    }
}

/**
 * Una tarea de la zona tal como la entrega la bajada. `created_by` (el usuario
 * de la oficina) llega pero no se guarda: Gson lo ignora.
 */
data class TareaZonaDto(
    val id: String? = null,
    val venta_id: Int? = null,
    val cliente_id: Int? = null,
    val zona_cliente_id: Int? = null,
    val fecha: String? = null,
    val hora: String? = null,
    val nota: String? = null,
    val reemplaza_a: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val cancelada_at: String? = null
)

/**
 * Una página de la bajada. [cursor], [after_id] y [sync_epoch] significan lo
 * mismo que en [SyncVisitasZonaResponse] (`COBRANZA-SYNC.md` §2 y §3).
 */
data class SyncTareasZonaResponse(
    val items: List<TareaZonaDto>? = null,
    val cursor: String? = null,
    val after_id: String? = null,
    val has_more: Boolean? = null,
    val sync_epoch: Int? = null,
    val server_now: String? = null
)
