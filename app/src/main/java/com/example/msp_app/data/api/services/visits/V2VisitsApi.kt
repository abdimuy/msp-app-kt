package com.example.msp_app.data.api.services.visits

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Retrofit service for the msp-api v2 cobranza visita endpoint.
 *
 * ## `POST /v2/visitas` speaks TWO wire formats, and this client uses both
 *
 * An earlier version of this comment said visitas *"never carry a comprobante
 * image"*. That was true when it was written and is not any more: attaching
 * photos to a visita is a **product decision that was taken and then reversed**
 * (plan `pagos-y-visitas`, Task 23), and msp-api's Task 9 turned this route
 * from plain JSON into a route that reads its own body and accepts either
 * shape. The comment is updated rather than deleted so the next reader knows
 * the change was deliberate and not an oversight.
 *
 * What the server accepts (verified in `internal/visitas/infra/visitashttp`,
 * not assumed):
 *
 *  - **`application/json`** — the legacy body, byte-for-byte what it always
 *    was. It is NOT a fallback: the phones already in the field send this and
 *    must keep working, which is why the coexistence exists at all (Ruling E).
 *    An *absent* Content-Type also resolves to JSON.
 *  - **`multipart/form-data`** — the same JSON document in a `datos` field,
 *    plus 0..N repeated `imagen` file parts with their positional `id_<n>` /
 *    `descripcion_<n>` companions.
 *
 * This client sends JSON when the visita carries no pending photo and
 * multipart when it does — see `PendingVisitsWorker.uploadV2` for why keeping the
 * proven JSON path for the overwhelmingly common case is the smaller blast
 * radius.
 *
 * ## Idempotency
 *
 * End-to-end by `id`: re-sending the same UUID returns the existing visita with
 * no double-insert (**201, not 409** — on a collision the server does
 * `FindByID` and returns what it has), so the retry worker can safely resend
 * without a reconcile-via-GET step. With photos the guarantee holds too and by
 * the same key: `RegistrarVisitaConImagenes` reads which `imagen` ids the
 * visita already has and stores only the new ones, so a retry carrying the same
 * `id_<n>` values writes nothing and orphans no blob.
 *
 * The `Idempotency-Key` header is set to the same id for defence in depth (if
 * present it must equal the body `id`, or the server answers
 * `idempotency_key_mismatch`).
 *
 * Auth is transparent: the v2 client's `BearerAuthInterceptor` (`:core:network`)
 * attaches the Firebase token and refreshes it once on a 401.
 */
interface V2VisitsApi {

    /** The legacy JSON shape. Unchanged, and still what a photo-less visita sends. */
    @POST("v2/visitas")
    suspend fun crearVisita(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: CrearVisitaBody
    ): VisitaDTO

    /**
     * The multipart shape, used when the visita carries comprobantes.
     *
     * @param datos the very same [CrearVisitaBody] document, serialized to JSON
     *   — the server parses the `datos` field with the same decoder it uses for
     *   a JSON body (`decodeDatosField` → `decodeCrearVisitaJSON`), so the two
     *   formats cannot drift apart in what they accept.
     * @param imagenes the comprobante parts, already built by
     *   `partesDeComprobantesDeVisita`: N files under the **`imagen`** field
     *   plus their positional `id_<n>` / `descripcion_<n>` text parts.
     *   Deliberately an unnamed `@Part` — each part carries its own form name.
     */
    @Multipart
    @POST("v2/visitas")
    suspend fun crearVisitaConImagenes(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part("datos") datos: RequestBody,
        @Part imagenes: List<MultipartBody.Part>
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
     * answers `422 ids_too_many`. The cap is declared once, on the port that
     * describes this endpoint: `VisitCustodyRegistry.MAX_IDS_PER_REQUEST`.
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
 * Body serialized as the JSON request payload — either as the whole body
 * (`application/json`) or inside the multipart `datos` field. It is the SAME
 * document both ways; the server decodes it with the same function.
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
