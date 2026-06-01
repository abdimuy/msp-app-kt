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
}

data class SyncVentasResponse(
    val items: List<VentaDto>,
    val max_updated_at: String,
    val server_now: String,
    val has_more: Boolean
)

data class SyncPagosResponse(
    val items: List<PagoDto>,
    val max_updated_at: String,
    val server_now: String,
    val has_more: Boolean
)
