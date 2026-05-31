package com.example.msp_app.data.api.services.cobranza

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the v2 Go backend's cobranza sync endpoints.
 *
 * Both endpoints page by `(cursor, after_id)` and return until `has_more`
 * is false. The client persists `max_updated_at` as the next cursor.
 */
interface V2CobranzaApi {

    @GET("v2/cobranza/sync/ventas/zona/{zona_id}")
    suspend fun syncVentas(
        @Path("zona_id") zonaId: Int,
        @Query("cursor") cursor: String? = null,
        @Query("after_id") afterId: Int = 0,
        @Query("limit") limit: Int = 1000
    ): SyncVentasResponse

    @GET("v2/cobranza/sync/pagos/zona/{zona_id}")
    suspend fun syncPagos(
        @Path("zona_id") zonaId: Int,
        @Query("cursor") cursor: String? = null,
        @Query("after_id") afterId: Int = 0,
        @Query("limit") limit: Int = 1000
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
