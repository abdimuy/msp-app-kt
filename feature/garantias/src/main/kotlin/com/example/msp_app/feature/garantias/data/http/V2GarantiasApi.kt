package com.example.msp_app.feature.garantias.data.http

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * El API de garantías v2, tal como lo consume la captura offline.
 *
 * ## El cuerpo viaja ya armado
 *
 * Las dos escrituras reciben un [RequestBody] con los **bytes congelados** del
 * comando y su `Content-Type` exacto (JSON o multipart con boundary fijo). El
 * adaptador no rearma nada: si lo hiciera, un reintento podría mandar otro
 * contenido con la misma clave y el servidor respondería 409
 * `warranty_idempotency_key_mismatch` para siempre (D3).
 *
 * ## Por qué las escrituras devuelven [Response]
 *
 * Un `409` con el código de negocio dentro del cuerpo no es una excepción: es
 * la entrada del clasificador de envío. Con `Response<ResponseBody>` el
 * status y el cuerpo llegan crudos, sin que Retrofit los convierta en
 * `HttpException`.
 *
 * ## Las rutas llegan por `@Url`
 *
 * La cola guarda la **plantilla** `v2/garantias/{folio}/…` y la resuelve en el
 * momento de enviar (D4), así que el servicio no puede declarar cada ruta con
 * su `@POST` fijo. Todas las operaciones de la captura son `POST`, y el
 * adaptador lo verifica antes de llamar.
 */
interface V2GarantiasApi {

    /**
     * Manda un comando o una imagen con su clave de idempotencia.
     *
     * La misma función sirve para las dos cosas porque el contrato es idéntico:
     * una clave, una ruta absoluta y bytes ya armados. La diferencia está en
     * la ruta y en el `Content-Type` del cuerpo, no en el verbo.
     */
    @POST
    suspend fun enviar(
        @Header("Idempotency-Key") clave: String,
        @Url ruta: String,
        @Body cuerpo: RequestBody
    ): Response<ResponseBody>

    /**
     * El detalle del folio con su línea de tiempo.
     *
     * Es la única forma de saber qué artículo creó un `agregar` y qué evento
     * creó una recolección: la respuesta del comando no los trae (D4). Lanza
     * `IOException` cuando no hay señal; el llamador decide si reintenta.
     */
    @GET
    suspend fun detalle(@Url ruta: String): DetalleDeGarantiaDto
}
