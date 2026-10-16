package com.example.msp_app.core.network.diagnostico

import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLHandshakeException
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-066: la clasificación `(http, cuerpo, excepción) → (código, mensaje)`
 * es pura, total y nunca decide nada. Tabla exhaustiva + propiedades sobre
 * entradas aleatorias con semilla fija (reproducible).
 */
class ClasificadorDeFalloDeEnvioTest {

    private fun c(http: Int?, cuerpo: String? = null, error: Throwable? = null) =
        ClasificadorDeFalloDeEnvio.clasificar(http, cuerpo, error)

    // ─── excepciones: no hubo respuesta ⇒ http 0 ───────────────────────────────

    @Test
    fun `E-APP-066 cada clase de excepcion tiene su codigo y http 0`() {
        val casos = listOf(
            SocketTimeoutException("timeout") to "timeout",
            InterruptedIOException("timeout") to "timeout",
            UnknownHostException("apidev.loclx.io") to "dns_error",
            SSLHandshakeException("bad cert") to "tls_error",
            ConnectException("refused") to "network_error",
            NoRouteToHostException("no route") to "network_error",
            EOFException("unexpected end of stream") to "network_error",
            IOException("Canceled") to "cancelado",
            IOException("unexpected end of stream on http://x") to "network_error",
            IllegalStateException("boom") to "error_inesperado",
            CancellationException("job cancelled") to "error_inesperado",
            OutOfMemoryError("no") to "error_inesperado"
        )
        casos.forEach { (error, esperado) ->
            val fallo = c(http = null, error = error)!!
            assertEquals("codigo para ${error.javaClass.simpleName}", esperado, fallo.codigo)
            assertEquals(ClasificadorDeFalloDeEnvio.SIN_RESPUESTA, fallo.http)
            assertTrue(fallo.mensaje.isNotBlank())
        }
    }

    @Test
    fun `la excepcion manda aunque tambien venga un http`() {
        assertEquals("timeout", c(http = 500, error = SocketTimeoutException())!!.codigo)
    }

    // ─── HTTP ──────────────────────────────────────────────────────────────────

    @Test
    fun `2xx sin excepcion no es fallo`() {
        (200..299).forEach { assertNull("http $it", c(it)) }
    }

    @Test
    fun `sin http y sin excepcion es error inesperado, no exito`() {
        assertEquals("error_inesperado", c(http = null)!!.codigo)
    }

    @Test
    fun `problem+json del servidor - code y detail`() {
        val cuerpo = """{"type":"about:blank","title":"Unprocessable Entity","status":422,
            |"detail":"el saldo no alcanza para la condonación","code":"saldo_insuficiente"}
        """.trimMargin()
        val fallo = c(422, cuerpo)!!
        assertEquals("saldo_insuficiente", fallo.codigo)
        assertEquals("El saldo no alcanza para la condonación", fallo.mensaje)
        assertEquals(422, fallo.http)
    }

    @Test
    fun `problema de Huma sin code - toma errors message y codigo por estado`() {
        val cuerpo = """{"title":"Request Entity Too Large","status":413,
            |"errors":[{"message":"request body is too large","location":"body"}]}
        """.trimMargin()
        val fallo = c(413, cuerpo)!!
        assertEquals("http_413", fallo.codigo)
        assertEquals("Request body is too large", fallo.mensaje)
    }

    @Test
    fun `solo title`() {
        assertEquals("Forbidden", c(403, """{"title":"Forbidden"}""")!!.mensaje)
    }

    @Test
    fun `cuerpo HTML del tunel - codigo y mensaje por estado`() {
        val fallo = c(502, "<html><body>Bad Gateway</body></html>")!!
        assertEquals("http_502", fallo.codigo)
        assertEquals("Falla del servidor", fallo.mensaje)
    }

    @Test
    fun `mensajes por estado cuando no hay cuerpo`() {
        val esperado = mapOf(
            401 to "Sesión vencida",
            403 to "Sin permiso",
            404 to "No encontrado",
            408 to "Tiempo agotado",
            409 to "Conflicto en el servidor",
            413 to "Envío muy grande",
            422 to "Datos rechazados",
            425 to "Demasiados intentos",
            429 to "Demasiados intentos",
            400 to "Rechazado por el servidor",
            500 to "Falla del servidor",
            503 to "Falla del servidor",
            302 to "Respuesta inesperada",
            100 to "Respuesta inesperada"
        )
        esperado.forEach { (estado, mensaje) ->
            val fallo = c(estado)!!
            assertEquals("estado $estado", mensaje, fallo.mensaje)
            assertEquals("http_$estado", fallo.codigo)
            assertEquals(estado, fallo.http)
        }
    }

    @Test
    fun `codigo del servidor se sanea y se acota`() {
        assertEquals("visita_fecha_futura", c(422, """{"code":"Visita-Fecha Futura"}""")!!.codigo)
        val largo = "a".repeat(500)
        assertEquals(
            ClasificadorDeFalloDeEnvio.MAX_CODIGO,
            c(422, """{"code":"$largo"}""")!!.codigo.length
        )
        // Un code que se sanea a nada cae al codigo por estado.
        assertEquals("http_422", c(422, """{"code":"---"}""")!!.codigo)
    }

    @Test
    fun `mensaje con saltos y espacios se limpia y se recorta con elipsis`() {
        val fallo = c(500, """{"detail":"  linea uno\n\n\tlinea   dos  "}""")!!
        assertEquals("Linea uno linea dos", fallo.mensaje)
        val largo = c(500, """{"detail":"${"x".repeat(1000)}"}""")!!
        assertEquals(ClasificadorDeFalloDeEnvio.MAX_MENSAJE, largo.mensaje.length)
        assertTrue(largo.mensaje.endsWith("…"))
    }

    @Test
    fun `cuerpo recortado por el vistazo rescata code y detail del texto`() {
        val recortado = """{"code":"saldo_insuficiente","detail":"el saldo no alcanza","errors":[{"message":"xx"""
        val fallo = c(422, recortado)!!
        assertEquals("saldo_insuficiente", fallo.codigo)
        assertEquals("El saldo no alcanza", fallo.mensaje)
    }

    @Test
    fun `cuerpos raros no truenan`() {
        listOf(
            "", " ", "null", "[]", "[1,2]", "42", "\"texto\"", "{", "{\"code\":5}",
            "{\"code\":null,\"detail\":{}}", "{\"errors\":\"no\"}", "{\"errors\":[1,{\"message\":3}]}"
        ).forEach { cuerpo ->
            val fallo = c(400, cuerpo)
            assertNotNull("cuerpo <$cuerpo>", fallo)
            assertEquals("http_400", fallo!!.codigo)
        }
    }

    // ─── propiedades (aleatorio con semilla fija) ──────────────────────────────

    private val codigoValido = Regex("^[a-z0-9_]{1,${ClasificadorDeFalloDeEnvio.MAX_CODIGO}}$")

    @Test
    fun `E-APP-066 propiedades - total, codigo seguro, mensaje acotado, http preservado`() {
        val r = Random(20261016)
        val excepciones = listOf<() -> Throwable?>(
            { null },
            { IOException(r.nextString()) },
            { SocketTimeoutException() },
            { UnknownHostException() },
            { RuntimeException(r.nextString()) },
            { Error() }
        )
        repeat(20_000) {
            val http = if (r.nextInt(10) == 0) null else r.nextInt(-5, 700)
            val cuerpo = cuerpoAleatorio(r)
            val error = excepciones[r.nextInt(excepciones.size)]()
            val fallo = c(http, cuerpo, error)
            when {
                error != null -> {
                    assertNotNull(fallo)
                    assertEquals(0, fallo!!.http)
                }
                http != null && http in 200..299 -> assertNull(fallo)
                else -> {
                    assertNotNull(fallo)
                    assertEquals(http ?: 0, fallo!!.http)
                }
            }
            if (fallo != null) {
                assertTrue("codigo <${fallo.codigo}>", codigoValido.matches(fallo.codigo))
                assertTrue(fallo.mensaje.isNotBlank())
                assertTrue(fallo.mensaje.length <= ClasificadorDeFalloDeEnvio.MAX_MENSAJE)
                assertTrue(fallo.mensaje.none { it.isISOControl() })
                // Determinista: misma entrada, misma salida.
                assertEquals(fallo, c(http, cuerpo, error))
            }
        }
    }

    private fun Random.nextString(max: Int = 40): String =
        (0 until nextInt(max)).map { (nextInt(0, 0x2FF)).toChar() }.joinToString("")

    private fun cuerpoAleatorio(r: Random): String? = when (r.nextInt(6)) {
        0 -> null
        1 -> r.nextString(300)
        2 -> """{"code":"${r.nextString().replace("\"", "").replace("\\", "")}"}"""
        3 -> """{"detail":"${r.nextString(400).replace("\"", "").replace("\\", "")}"}"""
        4 -> """{"code":${r.nextInt()},"errors":[{"message":"${r.nextInt()}"}]}"""
        else -> "<html>${r.nextString()}</html>"
    }
}
