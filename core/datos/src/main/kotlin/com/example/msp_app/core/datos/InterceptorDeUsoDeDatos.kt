package com.example.msp_app.core.datos

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.ForwardingSink
import okio.ForwardingSource
import okio.Sink
import okio.Source
import okio.buffer

/** Estimación del tamaño de cabeceras en el cable (HTTP/1.1 en texto; en HTTP/2 es algo menos). */
object Estimacion {
    private const val SEPARADOR = 4 // ": " + CRLF
    private const val CRLF = 2
    private const val LARGO_DEL_CODIGO = 3

    fun cabecerasDePeticion(request: Request): Long {
        val url = request.url
        val destino = url.encodedPath + (url.encodedQuery?.let { "?$it" } ?: "")
        val linea = request.method.length + 1 + destino.length + " HTTP/1.1".length + CRLF
        return (linea + sumaDeCabeceras(request.headers) + CRLF).toLong()
    }

    fun cabecerasDeRespuesta(response: Response): Long {
        val linea = response.protocol.toString().length + 1 + LARGO_DEL_CODIGO + 1 + response.message.length + CRLF
        return (linea + sumaDeCabeceras(response.headers) + CRLF).toLong()
    }

    private fun sumaDeCabeceras(h: okhttp3.Headers): Int {
        var n = 0
        for (i in 0 until h.size) n += h.name(i).length + h.value(i).length + SEPARADOR
        return n
    }
}

/**
 * Cuenta los bytes que cada endpoint mueve POR EL CABLE (es un `networkInterceptor`:
 * ve el cuerpo comprimido, no el que descomprime `BridgeInterceptor`).
 *
 * ## Regla dura (E-APP-171): jamás altera, retrasa, falla ni reintenta una petición
 *
 * Pasa por el POST de pagos. Por eso:
 * 1. `chain.proceed` se llama **una vez**, con una petición que lleva el mismo método,
 *    URL, cabeceras y bytes de cuerpo (el cuerpo va envuelto para contar lo que de
 *    verdad se escribe; reenvía `contentType`, `contentLength`, `isOneShot` e `isDuplex`).
 * 2. La excepción de la cadena se relanza **la misma instancia**.
 * 3. El cuerpo de la respuesta se envuelve con un contador incremental de la fuente
 *    (así un SSE se cuenta mientras corre y una descarga cortada cuenta lo que llegó);
 *    mismo `contentType` y `contentLength`, y los bytes pasan intactos.
 * 4. Todo lo propio va en `try/catch (Throwable)` que no propaga nada. Las
 *    excepciones de E/S de la fuente/destino original sí pasan: no son nuestras.
 * 5. Sin locks: contadores atómicos en estructuras concurrentes (ver [RegistroDeUso]).
 *
 * [claveFija] agrupa todo bajo una sola clave (imágenes de Coil, descargas): sus URLs
 * llevan nombres de archivo, no rutas de API.
 */
class InterceptorDeUsoDeDatos(
    private val contador: ContadorDeBytes,
    private val claveFija: String? = null
) : Interceptor {

    @Suppress("TooGenericExceptionCaught")
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val clave = protegidoDevolviendo { claveDe(original) }
        var peticion = original
        if (clave != null) {
            protegido { contador.peticion(clave) }
            peticion = protegidoDevolviendo { conCuerpoContado(original, clave) } ?: original
        }
        val respuesta = try {
            chain.proceed(peticion)
        } catch (fallo: Throwable) {
            if (clave != null) {
                protegido { contador.bytes(clave, 0, Estimacion.cabecerasDePeticion(original)) }
            }
            throw fallo
        }
        if (clave == null) return respuesta
        protegido {
            contador.bytes(
                clave,
                Estimacion.cabecerasDeRespuesta(respuesta),
                Estimacion.cabecerasDePeticion(original)
            )
        }
        val envuelta = protegidoDevolviendo { conCuerpoContado(respuesta, clave) }
        return envuelta ?: respuesta
    }

    private fun claveDe(request: Request): String {
        claveFija?.let { return it }
        val tipo = request.body?.contentType()
        val multipart = tipo != null && tipo.type.equals("multipart", ignoreCase = true)
        return NormalizadorDeRutas.clave(request.method, request.url, multipart)
    }

    private fun conCuerpoContado(request: Request, clave: String): Request {
        val cuerpo = request.body ?: return request
        return request.newBuilder().method(
            request.method,
            CuerpoDePeticionContado(cuerpo, clave, contador)
        ).build()
    }

    private fun conCuerpoContado(response: Response, clave: String): Response {
        val cuerpo = response.body ?: return response
        return response.newBuilder().body(CuerpoDeRespuestaContado(cuerpo, clave, contador)).build()
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun protegido(bloque: () -> Unit) {
        try {
            bloque()
        } catch (_: Throwable) {
            // Medición: jamás puede cambiar la petición.
        }
    }

    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private inline fun <T> protegidoDevolviendo(bloque: () -> T): T? = try {
        bloque()
    } catch (_: Throwable) {
        null
    }
}

@Suppress("TooGenericExceptionCaught", "SwallowedException")
private inline fun contarSinRiesgo(bloque: () -> Unit) {
    try {
        bloque()
    } catch (_: Throwable) {
        // Medición: nunca propaga.
    }
}

private class CuerpoDePeticionContado(
    private val delegado: RequestBody,
    private val clave: String,
    private val contador: ContadorDeBytes
) : RequestBody() {
    override fun contentType(): MediaType? = delegado.contentType()
    override fun contentLength(): Long = delegado.contentLength()
    override fun isOneShot(): Boolean = delegado.isOneShot()
    override fun isDuplex(): Boolean = delegado.isDuplex()

    override fun writeTo(sink: BufferedSink) {
        val contando = SinkQueCuenta(sink, clave, contador).buffer()
        delegado.writeTo(contando)
        // `emit` y no `flush`: pasa lo escrito al sink real sin forzar el socket,
        // igual que haría el `close` de OkHttp después.
        contando.emit()
    }
}

private class SinkQueCuenta(
    delegado: Sink,
    private val clave: String,
    private val contador: ContadorDeBytes
) : ForwardingSink(delegado) {
    override fun write(source: Buffer, byteCount: Long) {
        super.write(source, byteCount)
        contarSinRiesgo { contador.bytes(clave, 0, byteCount) }
    }
}

private class CuerpoDeRespuestaContado(
    private val delegado: ResponseBody,
    private val clave: String,
    private val contador: ContadorDeBytes
) : ResponseBody() {
    private val fuente: BufferedSource by lazy(LazyThreadSafetyMode.NONE) {
        FuenteQueCuenta(delegado.source(), clave, contador).buffer()
    }

    override fun contentType(): MediaType? = delegado.contentType()
    override fun contentLength(): Long = delegado.contentLength()
    override fun source(): BufferedSource = fuente
    override fun close() = delegado.close()
}

private class FuenteQueCuenta(
    delegado: Source,
    private val clave: String,
    private val contador: ContadorDeBytes
) : ForwardingSource(delegado) {
    override fun read(sink: Buffer, byteCount: Long): Long {
        val n = super.read(sink, byteCount)
        if (n > 0) contarSinRiesgo { contador.bytes(clave, n, 0) }
        return n
    }
}
