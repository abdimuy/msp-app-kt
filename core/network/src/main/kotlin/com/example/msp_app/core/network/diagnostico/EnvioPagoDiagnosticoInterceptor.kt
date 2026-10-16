package com.example.msp_app.core.network.diagnostico

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/** Un envío observado: [fallo] `null` significa 2xx. */
data class EnvioObservado(val tipo: String, val filaId: String, val fallo: FalloDeEnvio?)

/**
 * Destino de lo observado. La implementación de producción (`:app`) escribe en
 * la tabla de diagnóstico **sin bloquear** al hilo que llama: encola y regresa.
 */
fun interface EnvioDiagnosticoSink {
    fun registrar(observado: EnvioObservado)
}

/**
 * Observa, **sin alterar nada**, el `POST /v2/cobranza/pagos` del cliente v2 y
 * anota el motivo del fallo en el [sink] (E-APP-064).
 *
 * Garantías (cada una con prueba en `EnvioPagoDiagnosticoInterceptorTest` y
 * `PendingPaymentsWorkerDiagnosticoDecisionTest`):
 * 1. La petición pasa tal cual: `chain.proceed(chain.request())`, una sola vez.
 * 2. La respuesta que regresa es **el mismo objeto** que devolvió la cadena.
 * 3. Una excepción de la cadena se re-lanza **la misma instancia**.
 * 4. El cuerpo no se consume: solo `peekBody` acotado a [MAX_PEEK_BYTES], y
 *    solo en respuestas no 2xx. Retrofit de todos modos lee completo el cuerpo
 *    de error justo después (`OkHttpCall.parseResponse`, retrofit 2.9.0:
 *    `Utils.buffer(rawBody)` cuando `code < 200 || code >= 300`), así que el
 *    patrón de lectura de la red no cambia.
 * 5. Todo lo propio va dentro de `try/catch (Throwable)`: si el interceptor
 *    revienta, el envío sigue exactamente igual.
 *
 * El pago se identifica por `Idempotency-Key`, que el worker fija al `ID` del
 * pago (`PendingPaymentsWorker.uploadV2`: `idempotencyKey = payment.ID`).
 */
class EnvioPagoDiagnosticoInterceptor(
    private val sink: EnvioDiagnosticoSink
) : Interceptor {

    // Se atrapa TODO solo para anotarlo y se re-lanza la MISMA instancia.
    @Suppress("TooGenericExceptionCaught")
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val filaId = pagoObservado(request) ?: return chain.proceed(request)
        val response = try {
            chain.proceed(request)
        } catch (fallo: Throwable) {
            protegido {
                val motivo = ClasificadorDeFalloDeEnvio.clasificar(null, null, fallo)
                sink.registrar(EnvioObservado(TIPO_PAGO, filaId, motivo))
            }
            throw fallo
        }
        protegido { observarRespuesta(filaId, response) }
        return response
    }

    private fun observarRespuesta(filaId: String, response: Response) {
        val fallo = if (response.isSuccessful) {
            null
        } else {
            val cuerpo = protegidoDevolviendo { response.peekBody(MAX_PEEK_BYTES).string() }
            ClasificadorDeFalloDeEnvio.clasificar(response.code, cuerpo, null)
        }
        sink.registrar(EnvioObservado(TIPO_PAGO, filaId, fallo))
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun pagoObservado(request: Request): String? = try {
        val segmentos = request.url.pathSegments.filter { it.isNotEmpty() }
        val esPostDePagos = request.method == "POST" && segmentos.takeLast(RUTA_PAGOS.size) == RUTA_PAGOS
        request.header(HEADER_IDEMPOTENCY_KEY)?.trim()
            ?.takeIf { esPostDePagos && it.isNotEmpty() && it.length <= MAX_ID }
    } catch (_: Throwable) {
        null
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun protegido(bloque: () -> Unit) {
        try {
            bloque()
        } catch (_: Throwable) {
            // Diagnóstico: jamás puede cambiar el envío.
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun <T> protegidoDevolviendo(bloque: () -> T): T? = try {
        bloque()
    } catch (_: Throwable) {
        null
    }

    companion object {
        const val TIPO_PAGO = "pago"
        const val HEADER_IDEMPOTENCY_KEY = "Idempotency-Key"
        const val MAX_PEEK_BYTES = 8L * 1024L
        private const val MAX_ID = 128
        private val RUTA_PAGOS = listOf("v2", "cobranza", "pagos")
    }
}
