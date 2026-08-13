package com.example.msp_app.data.api.services.cobranza

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the v2 Go backend's cobranza sync endpoints.
 *
 * Both endpoints page by `(cursor, after_id)` and return until `has_more`
 * is false. The client persists `max_updated_at` as the next cursor.
 *
 * `desde` (RFC3339 UTC, p.ej. `2026-04-01T00:00:00Z`) extiende el filtro
 * de saldo en todas las páginas: además de las activas y los tombstones,
 * el backend incluye las ventas saldadas con `FECHA_ULT_PAGO >= desde` y
 * los pagos con `FECHA >= desde`. Debe enviarse en cada página del mismo
 * sync (no solo en la primera) o las páginas 2+ filtran como legacy y se
 * pierden las saldadas dentro de la ventana del cobrador.
 */
interface V2CobranzaApi {

    @GET("v2/cobranza/sync/ventas/zona/{zona_id}")
    suspend fun syncVentas(
        @Path("zona_id") zonaId: Int,
        @Query("cursor") cursor: String? = null,
        @Query("after_id") afterId: Int = 0,
        @Query("limit") limit: Int = 1000,
        @Query("desde") desde: String? = null
    ): SyncVentasResponse

    @GET("v2/cobranza/sync/pagos/zona/{zona_id}")
    suspend fun syncPagos(
        @Path("zona_id") zonaId: Int,
        @Query("cursor") cursor: String? = null,
        @Query("after_id") afterId: Int = 0,
        @Query("limit") limit: Int = 1000,
        @Query("desde") desde: String? = null
    ): SyncPagosResponse

    @GET("v2/cobranza/sync/pagos/zona/{zona_id}/digest")
    suspend fun pagosDigest(
        @Path("zona_id") zonaId: Int,
        @Query("desde") desde: String? = null
    ): DigestResponse

    @GET("v2/cobranza/sync/saldos/zona/{zona_id}/digest")
    suspend fun saldosDigest(
        @Path("zona_id") zonaId: Int,
        @Query("desde") desde: String? = null
    ): DigestResponse

    @GET("v2/cobranza/sync/pagos/zona/{zona_id}/ids")
    suspend fun listPagoIds(
        @Path("zona_id") zonaId: Int,
        @Query("after") after: Int = 0,
        @Query("limit") limit: Int = 5000,
        @Query("desde") desde: String? = null
    ): IdsResponse

    @GET("v2/cobranza/sync/saldos/zona/{zona_id}/ids")
    suspend fun listSaldoIds(
        @Path("zona_id") zonaId: Int,
        @Query("after") after: Int = 0,
        @Query("limit") limit: Int = 5000,
        @Query("desde") desde: String? = null
    ): IdsResponse

    /**
     * Trae los pagos cuyos IDs vienen en [ids] (CSV, máx 500 por llamada).
     * Usado por el path optimista SSE: en lugar de re-sincronizar todo,
     * solo pedimos los registros afectados.
     *
     * @param ids IDs separados por coma, p.ej. "1,2,3"
     */
    @GET("v2/cobranza/sync/pagos/by-ids")
    suspend fun pagosByIds(@Query("zona_id") zonaId: Int, @Query("ids") ids: String): List<PagoDto>

    /**
     * Trae los saldos (ventas) cuyos IDs vienen en [ids] (CSV, máx 500 por
     * llamada). Simétrico de [pagosByIds] para el stream de saldos.
     */
    @GET("v2/cobranza/sync/saldos/by-ids")
    suspend fun saldosByIds(
        @Query("zona_id") zonaId: Int,
        @Query("ids") ids: String
    ): List<VentaDto>
}

data class DigestResponse(
    val count_activos: Int,
    val ids_xor: String,
    val ids_sum: String,
    val max_updated_at: String?
)

/**
 * Generación de la proyección del servidor para un recurso de sync.
 *
 * El servidor la sube cuando cambia lo que proyecta (p.ej. las coordenadas de
 * un pago pasan a salir de otra tabla). Las filas ya guardadas en Room no
 * vuelven a bajar por el cursor incremental porque su `UPDATED_AT` no cambió,
 * así que el cliente detecta la generación distinta y replica desde cero.
 *
 * Declarado `Int?` con default a propósito, y NO por elegancia: Gson ignora
 * los valores por defecto de Kotlin (construye por `Unsafe`, sin llamar al
 * constructor), así que el default solo describe la intención — quien decide
 * es el tipo. Con `Int?` un servidor viejo que no manda el campo deja `null`,
 * que el manager lee como "este servidor no tiene generaciones" y desactiva el
 * mecanismo. Con un `Int` no nulo el campo ausente llegaría como 0 y sería
 * indistinguible de un 0 real del servidor. Un tipo complejo no nulo llegaría
 * como null y reventaría con NPE al primer uso — el incidente que ya se pagó
 * una vez en producción.
 */
data class SyncVentasResponse(
    val items: List<VentaDto>,
    val max_updated_at: String,
    val server_now: String,
    val has_more: Boolean,
    val sync_epoch: Int? = null
)

data class SyncPagosResponse(
    val items: List<PagoDto>,
    val max_updated_at: String,
    val server_now: String,
    val has_more: Boolean,
    val sync_epoch: Int? = null
)

data class IdsResponse(
    val ids: List<Int>,
    val has_more: Boolean
)
