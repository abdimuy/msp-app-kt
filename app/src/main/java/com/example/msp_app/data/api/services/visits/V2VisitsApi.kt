package com.example.msp_app.data.api.services.visits

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit service for the msp-api v2 cobranza visita endpoint.
 *
 * `POST /v2/visitas` is a plain JSON endpoint — unlike
 * [com.example.msp_app.data.api.services.payment.V2PaymentsApi] there is no
 * multipart envelope, since visitas never carry a comprobante image.
 *
 * Idempotency is end-to-end by `id`: re-sending the same UUID returns the
 * existing visita with no double-insert, so the retry worker can safely
 * resend without a reconcile-via-GET step. The `Idempotency-Key` header is
 * set to the same id for defence in depth (if present it must equal the body
 * `id`).
 *
 * Auth is transparent: the v2 client's `BearerAuthInterceptor` (`:core:network`)
 * attaches the Firebase token and refreshes it once on a 401.
 */
interface V2VisitsApi {

    @POST("v2/visitas")
    suspend fun crearVisita(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CrearVisitaBody
    ): VisitaDTO

    /**
     * Asks the server which of [ids] it already holds — the read half of the
     * visitas reconciler (msp-api Task 8).
     *
     * [ids] is a **comma-separated** list, not repeated `ids=` parameters: the
     * server splits one `ids` query value on commas, so a Retrofit
     * `@Query("ids") ids: List<String>` (which emits `ids=a&ids=b`) would be
     * read as a single id and quietly confirm nothing. The caller joins.
     *
     * **At most 100 ids per call** (`maxIDsPorRequest` server-side); 101
     * answers `422 ids_too_many`. Chunking is the reconciler's job — see
     * `ReconcileVisitsUseCase.MAX_IDS_PER_REQUEST`.
     *
     * **No `zona_id`** (orchestrator Ruling B): the question is only "do you
     * have these UUIDs the phone uploaded?", and a zone could only make the
     * answer wrong.
     *
     * The response is a bare JSON array of the ids that exist. Ids the server
     * does not know are simply absent — never an error.
     */
    @GET("v2/visitas/by-ids")
    suspend fun visitasExistentesPorIds(@Query("ids") ids: String): List<String>
}

/**
 * Body serialized as the JSON request payload.
 *
 * Field names MUST match the Go `CrearVisitaBody` exactly and carry no
 * extras — the server decodes with `DisallowUnknownFields`, so an unknown
 * key is a 422. Gson omits null fields by default, so leaving [nota] /
 * [impte_docto_cc_id] null drops them from the JSON entirely (both are
 * optional server-side).
 *
 * Note the wire name is `lng` (not `lon` as in pagos), and both coordinates
 * are JSON numbers, not strings.
 */
data class CrearVisitaBody(
    val id: String,
    val cliente_id: Int,
    val cobrador_id: Int,
    val cobrador: String,
    val forma_cobro_id: Int,
    val lat: Double,
    val lng: Double,
    val nota: String? = null,
    val tipo_visita: String,
    val zona_cliente_id: Int,
    val impte_docto_cc_id: Int? = null,
    /** RFC3339 UTC with no fractional seconds (Go's time.RFC3339 rejects fractions). */
    val fecha: String
)

/**
 * Response of `POST /v2/visitas`. Fields are nullable so Gson tolerates
 * whatever subset the server returns; the worker only needs the call to
 * succeed (a 2xx) — it does not depend on any particular field.
 */
data class VisitaDTO(
    val id: String? = null,
    val cliente_id: Int? = null,
    val cobrador_id: Int? = null,
    val cobrador: String? = null,
    val forma_cobro_id: Int? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val nota: String? = null,
    val tipo_visita: String? = null,
    val zona_cliente_id: Int? = null,
    val impte_docto_cc_id: Int? = null,
    val fecha: String? = null,
    val created_at: String? = null
)
