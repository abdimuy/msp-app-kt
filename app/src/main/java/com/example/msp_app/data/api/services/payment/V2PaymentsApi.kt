package com.example.msp_app.data.api.services.payment

import okhttp3.RequestBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Retrofit service for the msp-api v2 cobranza pago endpoint.
 *
 * `POST /v2/cobranza/pagos` is a `multipart/form-data` endpoint: the pago JSON
 * travels in the `datos` field and (optionally) N `imagen` file parts. This
 * client sends **zero images** — the phone only needs to durably deliver the
 * pago; comprobantes are not part of the cobranza upload flow.
 *
 * Idempotency is end-to-end by `datos.id`: re-sending the same UUID returns the
 * existing pago (200) with no double-collection, so the retry worker can safely
 * resend without a reconcile-via-GET step. The `Idempotency-Key` header is set
 * to the same id for defence in depth.
 *
 * Auth is transparent: the v2 client's `BearerAuthInterceptor` (`:core:network`)
 * attaches the Firebase token and refreshes it once on a 401.
 */
interface V2PaymentsApi {

    @Multipart
    @POST("v2/cobranza/pagos")
    suspend fun crearPago(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part("datos") datos: RequestBody
    ): PagoRecibidoDTO
}

/**
 * Body serialized into the multipart `datos` field.
 *
 * Field names MUST match the Go `CrearPagoBody` exactly and carry no extras —
 * the server decodes with `DisallowUnknownFields`, so an unknown key is a 422.
 * Gson omits null fields by default, so leaving [lat]/[lon] null drops them
 * from the JSON entirely (both are optional server-side).
 *
 * Note the wire name is `lon` (not `lng`); the Room column is `LNG`.
 */
data class CrearPagoBody(
    val id: String,
    val cargo_docto_cc_id: Int,
    val cliente_id: Int,
    val cobrador_id: Int,
    val cobrador: String,
    /** Two-decimal fixed string, e.g. "1500.00". */
    val importe: String,
    val forma_cobro_id: Int,
    /** RFC3339 UTC with no fractional seconds (Go's time.RFC3339 rejects fractions). */
    val fecha_hora_pago: String,
    val lat: String? = null,
    val lon: String? = null
)

/**
 * Response of `POST /v2/cobranza/pagos`. Fields are nullable so Gson tolerates
 * whatever subset the server returns; the worker only needs the call to succeed
 * (a 2xx) — it does not depend on any particular field.
 */
data class PagoRecibidoDTO(
    val id: String? = null,
    val estado: String? = null
)
