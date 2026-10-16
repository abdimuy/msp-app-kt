package com.example.msp_app.core.network.diagnostico

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Motivo de un envío fallido, listo para guardarse y pintarse (E-APP-066).
 *
 * @property codigo `[a-z0-9_]`, 1..[ClasificadorDeFalloDeEnvio.MAX_CODIGO] caracteres.
 * @property mensaje español, no vacío, a lo más [ClasificadorDeFalloDeEnvio.MAX_MENSAJE].
 * @property http estado HTTP, o [ClasificadorDeFalloDeEnvio.SIN_RESPUESTA] (0)
 *   cuando no hubo respuesta.
 */
data class FalloDeEnvio(val codigo: String, val mensaje: String, val http: Int)

/**
 * Función pura `(http, cuerpo de error, excepción) → (código, mensaje)`
 * (E-APP-066). **Solo diagnóstico**: su resultado se guarda y se muestra, nunca
 * decide si algo se reenvía. Total: no lanza con ninguna entrada.
 *
 * Reglas:
 * - Hay excepción ⇒ no hubo respuesta: `timeout`, `dns_error`, `tls_error`,
 *   `network_error`, `cancelado` o `error_inesperado`, con `http = 0`.
 * - `http` 2xx sin excepción ⇒ `null` (no es un fallo).
 * - `http` no 2xx ⇒ el `code` del problem+json del servidor (`response.go`
 *   `Problem.Code`) si viene; si no, `http_<estado>`. El mensaje es el `detail`
 *   (o el primer `errors[].message`, o el `title`) del servidor, recortado; si
 *   no hay uno legible, un texto fijo por estado.
 */
@Suppress("TooManyFunctions") // pasos pequeños y privados de una sola función pura
object ClasificadorDeFalloDeEnvio {

    const val SIN_RESPUESTA = 0
    const val MAX_CODIGO = 64
    const val MAX_MENSAJE = 160

    private val NO_PERMITIDO_EN_CODIGO = Regex("[^a-z0-9_]+")
    private val ESPACIOS = Regex("\\s+")
    private val CODE_EN_TEXTO = Regex("\"code\"\\s*:\\s*\"([^\"\\\\]{1,200})\"")
    private val DETAIL_EN_TEXTO = Regex("\"detail\"\\s*:\\s*\"([^\"\\\\]{1,400})\"")

    @Suppress("TooGenericExceptionCaught", "SwallowedException") // total por contrato
    fun clasificar(http: Int?, cuerpo: String?, error: Throwable?): FalloDeEnvio? = try {
        clasificarSinProteccion(http, cuerpo, error)
    } catch (_: Throwable) {
        FalloDeEnvio("error_inesperado", "Error inesperado", http ?: SIN_RESPUESTA)
    }

    private fun clasificarSinProteccion(
        http: Int?,
        cuerpo: String?,
        error: Throwable?
    ): FalloDeEnvio? {
        if (error != null) return porExcepcion(error)
        val estado = http ?: return FalloDeEnvio("error_inesperado", "Error inesperado", SIN_RESPUESTA)
        if (estado in HTTP_OK_DESDE..HTTP_OK_HASTA) return null
        val problema = leerProblema(cuerpo)
        val codigo = problema?.codigo?.let(::sanear) ?: sanear("http_$estado") ?: "http"
        val mensaje = problema?.mensaje?.let(::recortarMensaje) ?: mensajePorEstado(estado)
        return FalloDeEnvio(codigo, mensaje, estado)
    }

    private fun porExcepcion(error: Throwable): FalloDeEnvio = when {
        error is SocketTimeoutException -> red("timeout", "Tiempo agotado")
        error is UnknownHostException -> red("dns_error", "Servidor no encontrado")
        error is SSLException -> red("tls_error", "Conexión no segura")
        error is ConnectException || error is NoRouteToHostException -> red(
            "network_error",
            "Sin conexión"
        )
        error is InterruptedIOException && error.message.orEmpty().contains("timeout", true) ->
            red("timeout", "Tiempo agotado")
        error is IOException && error.message.orEmpty().equals("Canceled", true) ->
            red("cancelado", "Envío interrumpido")
        error is IOException -> red("network_error", "Sin conexión")
        else -> red("error_inesperado", "Error inesperado")
    }

    private fun red(codigo: String, mensaje: String) = FalloDeEnvio(codigo, mensaje, SIN_RESPUESTA)

    private data class Problema(val codigo: String?, val mensaje: String?)

    // Un cuerpo ilegible no es un problema; cada `return` es una forma de cuerpo distinta.
    @Suppress("TooGenericExceptionCaught", "SwallowedException", "ReturnCount")
    private fun leerProblema(cuerpo: String?): Problema? {
        if (cuerpo.isNullOrBlank()) return null
        val raiz = try {
            JsonParser.parseString(cuerpo)
        } catch (_: Exception) {
            // Cuerpo recortado por el vistazo acotado (o JSON roto): se rescata
            // lo que se pueda del texto.
            return problemaDeTextoParcial(cuerpo)
        }
        if (raiz == null || !raiz.isJsonObject) return null
        val obj = raiz.asJsonObject
        val codigo = texto(obj, "code")
        val mensaje = texto(obj, "detail")
            ?: primerMensajeDeErrores(obj)
            ?: texto(obj, "message")
            ?: texto(obj, "title")
        if (codigo == null && mensaje == null) return null
        return Problema(codigo, mensaje)
    }

    private fun problemaDeTextoParcial(cuerpo: String): Problema? {
        val codigo = CODE_EN_TEXTO.find(cuerpo)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val mensaje = DETAIL_EN_TEXTO.find(cuerpo)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        if (codigo == null && mensaje == null) return null
        return Problema(codigo, mensaje)
    }

    private fun primerMensajeDeErrores(obj: JsonObject): String? {
        val errores = obj.get("errors")
        if (errores == null || !errores.isJsonArray) return null
        return errores.asJsonArray.firstNotNullOfOrNull { e ->
            if (e.isJsonObject) texto(e.asJsonObject, "message") else null
        }
    }

    private fun texto(obj: JsonObject, campo: String): String? {
        val valor: JsonElement = obj.get(campo) ?: return null
        if (!valor.isJsonPrimitive || !valor.asJsonPrimitive.isString) return null
        return valor.asString.takeIf { it.isNotBlank() }
    }

    /** Código seguro para guardar y mandar a telemetría, o `null` si no queda nada. */
    private fun sanear(crudo: String): String? {
        val limpio = crudo.lowercase()
            .replace(NO_PERMITIDO_EN_CODIGO, "_")
            .trim('_')
            .take(MAX_CODIGO)
            .trimEnd('_')
        return limpio.ifEmpty { null }
    }

    private fun recortarMensaje(crudo: String): String? {
        val limpio = crudo.map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(ESPACIOS, " ")
            .trim()
        if (limpio.isEmpty()) return null
        val recortado = if (limpio.length > MAX_MENSAJE) {
            limpio.take(MAX_MENSAJE - 1).trimEnd() + "…"
        } else {
            limpio
        }
        return recortado.replaceFirstChar { it.uppercaseChar() }
    }

    @Suppress("MagicNumber", "CyclomaticComplexMethod") // catálogo cerrado de estados HTTP
    private fun mensajePorEstado(estado: Int): String = when (estado) {
        401 -> "Sesión vencida"
        403 -> "Sin permiso"
        404 -> "No encontrado"
        408 -> "Tiempo agotado"
        409 -> "Conflicto en el servidor"
        413 -> "Envío muy grande"
        422 -> "Datos rechazados"
        425, 429 -> "Demasiados intentos"
        in 500..599 -> "Falla del servidor"
        in 400..499 -> "Rechazado por el servidor"
        else -> "Respuesta inesperada"
    }

    private const val HTTP_OK_DESDE = 200
    private const val HTTP_OK_HASTA = 299
}
