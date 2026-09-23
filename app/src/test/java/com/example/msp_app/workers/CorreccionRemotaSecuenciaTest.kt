package com.example.msp_app.workers

import com.example.msp_app.data.api.services.ventas.VentaSituacionDTO
import com.example.msp_app.feature.ventacorreccion.domain.CorreccionRemotaTerminal
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/**
 * La secuencia de TRES peticiones con la que se entrega una corrección remota
 * —`PATCH /v2/ventas/{id}`, `PATCH /v2/ventas/{id}/cliente`,
 * `PUT /v2/ventas/{id}/lineas`—, medida entera con fakes: sin red, sin base,
 * sin WorkManager.
 *
 * Lo que estas pruebas existen para impedir, en orden de daño:
 *
 * 1. **Cerrar la cola con la corrección a medio entregar.** Antes de la
 *    secuencia el worker mandaba sólo las líneas y decía "Corrección
 *    guardada"; el teléfono y el servidor se separaban en silencio (medido el
 *    2026-09-22: teléfono `2385550000`, servidor `2385559876`).
 * 2. **Contarle al cobrador "no entró nada" cuando entró parte.** Un 409
 *    `venta_no_editable` en la segunda o la tercera llega con la anterior YA
 *    aplicada. Ahí la marca es [CorreccionRemotaTerminal.APLICADA_PARCIAL], no
 *    `RECHAZADA_ESTADO`.
 * 3. **Dejar caducar el candado a media secuencia.** El latido tiene que
 *    cubrir las tres peticiones, no la primera.
 */
class CorreccionRemotaSecuenciaTest {

    // ── Andamiaje ────────────────────────────────────────────────────────

    private val respuestaHeader = VentaSituacionDTO("borrador", "pendiente")
    private val respuestaCliente = VentaSituacionDTO("borrador", "pendiente")
    private val respuestaLineas = VentaSituacionDTO("borrador", "en_proceso")

    /**
     * Arma las tres peticiones anotando en [registro] cuáles se llegaron a
     * mandar y en qué orden. Cada lambda se puede sustituir por una que falle.
     */
    private fun secuencia(
        registro: MutableList<PasoCorreccionRemota>,
        header: suspend () -> VentaSituacionDTO = { respuestaHeader },
        cliente: suspend () -> VentaSituacionDTO = { respuestaCliente },
        lineas: suspend () -> VentaSituacionDTO = { respuestaLineas }
    ): List<PeticionDeCorreccion> = listOf(
        PeticionDeCorreccion(PasoCorreccionRemota.HEADER) {
            registro += PasoCorreccionRemota.HEADER
            header()
        },
        PeticionDeCorreccion(PasoCorreccionRemota.CLIENTE) {
            registro += PasoCorreccionRemota.CLIENTE
            cliente()
        },
        PeticionDeCorreccion(PasoCorreccionRemota.LINEAS) {
            registro += PasoCorreccionRemota.LINEAS
            lineas()
        }
    )

    /**
     * Un [HttpException] como el que arma Retrofit, con el código de negocio
     * donde Huma lo entierra de verdad (`errors[].message`, con la forma
     * `code=...`) y no en un campo `code` que no existe.
     */
    private fun errorHttp(codigo: Int, codigoDeNegocio: String? = null): HttpException {
        val cuerpo = if (codigoDeNegocio == null) {
            """{"status":$codigo,"detail":"sin código"}"""
        } else {
            """{"status":$codigo,"errors":[{"message":"code=$codigoDeNegocio"}]}"""
        }
        return HttpException(
            Response.error<Unit>(
                codigo,
                cuerpo.toResponseBody("application/json".toMediaTypeOrNull())
            )
        )
    }

    private val lasTres = listOf(
        PasoCorreccionRemota.HEADER,
        PasoCorreccionRemota.CLIENTE,
        PasoCorreccionRemota.LINEAS
    )

    // ── 1. El caso feliz: las tres pasan ─────────────────────────────────

    @Test
    fun `las tres pasan en orden y la secuencia se declara entregada`() = runTest {
        val registro = mutableListOf<PasoCorreccionRemota>()

        val resultado = ejecutarSecuenciaCorreccionRemota(secuencia(registro))

        assertEquals(
            "el orden es header → cliente → líneas, y las tres se mandan",
            lasTres,
            registro
        )
        val entregada = resultado as ResultadoDeLaSecuencia.Entregada
        assertEquals(
            "la respuesta que se guarda es la de la ÚLTIMA petición",
            respuestaLineas,
            entregada.ultimaRespuesta
        )
    }

    // ── 2. Falla la 2ª con red: se reintenta, y se repiten LAS TRES ──────

    @Test
    fun `un fallo de red en la segunda reintenta, y el reintento repite las tres`() = runTest {
        val registro = mutableListOf<PasoCorreccionRemota>()
        var primeraCorrida = true

        val clienteQueFallaUnaVez: suspend () -> VentaSituacionDTO = {
            if (primeraCorrida) throw IOException("sin señal") else respuestaCliente
        }

        val primera = ejecutarSecuenciaCorreccionRemota(
            secuencia(registro, cliente = clienteQueFallaUnaVez)
        )

        val reintento = primera as ResultadoDeLaSecuencia.Reintentar
        assertEquals(PasoCorreccionRemota.CLIENTE, reintento.paso)
        assertEquals(
            "la de header ya entró; eso es lo que hay que poder diagnosticar",
            1,
            reintento.pasosAplicados
        )
        assertEquals(
            "la tercera NO se manda: la secuencia se corta en la que falló",
            listOf(PasoCorreccionRemota.HEADER, PasoCorreccionRemota.CLIENTE),
            registro
        )

        // La corrida siguiente — la que haría WorkManager con su backoff.
        primeraCorrida = false
        registro.clear()
        val segunda = ejecutarSecuenciaCorreccionRemota(
            secuencia(registro, cliente = clienteQueFallaUnaVez)
        )

        assertTrue(
            "el reintento tiene que entregar la corrección completa",
            segunda is ResultadoDeLaSecuencia.Entregada
        )
        assertEquals(
            "al reintentar se repiten LAS TRES, incluida la que ya había entrado: " +
                "las tres son reemplazos, así que repetirlas converge",
            lasTres,
            registro
        )
    }

    // ── 3. 409 en la PRIMERA: nada aplicado, terminal de siempre ─────────

    @Test
    fun `un 409 en la primera es RECHAZADA_ESTADO y no manda nada mas`() = runTest {
        val registro = mutableListOf<PasoCorreccionRemota>()

        val resultado = ejecutarSecuenciaCorreccionRemota(
            secuencia(registro, header = { throw errorHttp(409, "venta_no_editable") })
        )

        val terminal = resultado as ResultadoDeLaSecuencia.Terminal
        assertEquals(
            "el servidor quedó INTACTO: no hay nada parcial que contarle al cobrador",
            CorreccionRemotaTerminal.RECHAZADA_ESTADO,
            terminal.estado
        )
        assertEquals(0, terminal.pasosAplicados)
        assertEquals(PasoCorreccionRemota.HEADER, terminal.paso)
        assertEquals(409, terminal.codigoHttp)
        assertEquals("venta_no_editable", terminal.codigoDeError)
        assertEquals(
            "cliente y líneas no se mandan tras un terminal",
            listOf(PasoCorreccionRemota.HEADER),
            registro
        )
    }

    // ── 4. 409 en la SEGUNDA y en la TERCERA: aplicada a medias ──────────

    @Test
    fun `un 409 en la segunda deja APLICADA_PARCIAL porque el header ya entro`() = runTest {
        val registro = mutableListOf<PasoCorreccionRemota>()

        val resultado = ejecutarSecuenciaCorreccionRemota(
            secuencia(registro, cliente = { throw errorHttp(409, "venta_no_editable") })
        )

        val terminal = resultado as ResultadoDeLaSecuencia.Terminal
        assertEquals(
            "reportar RECHAZADA_ESTADO aquí le diría al cobrador que no entró nada, " +
                "y la dirección y la fecha SÍ entraron",
            CorreccionRemotaTerminal.APLICADA_PARCIAL,
            terminal.estado
        )
        assertEquals(
            "el motivo original no se pierde: va a los registros",
            CorreccionRemotaTerminal.RECHAZADA_ESTADO,
            terminal.estadoSinParcial
        )
        assertEquals(PasoCorreccionRemota.CLIENTE, terminal.paso)
        assertEquals(1, terminal.pasosAplicados)
        assertEquals(
            listOf(PasoCorreccionRemota.HEADER, PasoCorreccionRemota.CLIENTE),
            registro
        )
    }

    @Test
    fun `un 409 en la tercera deja APLICADA_PARCIAL con dos pasos aplicados`() = runTest {
        val registro = mutableListOf<PasoCorreccionRemota>()

        val resultado = ejecutarSecuenciaCorreccionRemota(
            secuencia(registro, lineas = { throw errorHttp(409, "venta_no_editable") })
        )

        val terminal = resultado as ResultadoDeLaSecuencia.Terminal
        assertEquals(CorreccionRemotaTerminal.APLICADA_PARCIAL, terminal.estado)
        assertEquals(PasoCorreccionRemota.LINEAS, terminal.paso)
        assertEquals(
            "header y cliente entraron; lo único que falta en el servidor son los productos",
            2,
            terminal.pasosAplicados
        )
        assertEquals(lasTres, registro)
    }

    // ── 5. Los demás terminales, con y sin nada aplicado ─────────────────

    @Test
    fun `403, 404 y 422 en la primera se comportan como siempre`() = runTest {
        listOf(403 to "forbidden", 404 to "venta_not_found", 422 to "venta_productos_vacios")
            .forEach { (codigo, negocio) ->
                val registro = mutableListOf<PasoCorreccionRemota>()
                val resultado = ejecutarSecuenciaCorreccionRemota(
                    secuencia(registro, header = { throw errorHttp(codigo, negocio) })
                )

                val terminal = resultado as ResultadoDeLaSecuencia.Terminal
                assertEquals(
                    "HTTP $codigo sin nada aplicado",
                    CorreccionRemotaTerminal.RECHAZADA_ESTADO,
                    terminal.estado
                )
                assertEquals(0, terminal.pasosAplicados)
            }
    }

    @Test
    fun `403, 404 y 422 DESPUES de que algo entro tambien son APLICADA_PARCIAL`() = runTest {
        // Lo que importa no es por qué el servidor cerró la puerta, sino que el
        // teléfono y el servidor ya no coinciden y nadie más se va a enterar.
        listOf(403 to "forbidden", 404 to "venta_not_found", 422 to "monto_invalido")
            .forEach { (codigo, negocio) ->
                val registro = mutableListOf<PasoCorreccionRemota>()
                val resultado = ejecutarSecuenciaCorreccionRemota(
                    secuencia(registro, cliente = { throw errorHttp(codigo, negocio) })
                )

                val terminal = resultado as ResultadoDeLaSecuencia.Terminal
                assertEquals(
                    "HTTP $codigo con el header ya aplicado",
                    CorreccionRemotaTerminal.APLICADA_PARCIAL,
                    terminal.estado
                )
                assertEquals(
                    "el motivo real sigue disponible para diagnosticar",
                    CorreccionRemotaTerminal.RECHAZADA_ESTADO,
                    terminal.estadoSinParcial
                )
                assertEquals(1, terminal.pasosAplicados)
            }
    }

    @Test
    fun `el conflicto de version tras un paso aplicado tampoco se reporta como CONFLICTO`() =
        runTest {
            val registro = mutableListOf<PasoCorreccionRemota>()

            val resultado = ejecutarSecuenciaCorreccionRemota(
                secuencia(registro, lineas = { throw errorHttp(412, "venta_version_conflicto") })
            )

            val terminal = resultado as ResultadoDeLaSecuencia.Terminal
            assertEquals(CorreccionRemotaTerminal.APLICADA_PARCIAL, terminal.estado)
            assertEquals(
                "que ganara la oficina se conserva, pero no es lo que el cobrador necesita ver",
                CorreccionRemotaTerminal.CONFLICTO,
                terminal.estadoSinParcial
            )
            assertEquals(2, terminal.pasosAplicados)
        }

    @Test
    fun `toda marca terminal de la secuencia usa el vocabulario cerrado de la columna`() = runTest {
        val codigos = listOf(400, 403, 404, 405, 409, 412, 422)
        val terminales = mutableListOf<ResultadoDeLaSecuencia.Terminal>()

        // Los mismos códigos en los tres pasos: sin nada aplicado y con algo
        // aplicado, para recorrer también la rama de APLICADA_PARCIAL.
        codigos.forEach { codigo ->
            val enLaPrimera = ejecutarSecuenciaCorreccionRemota(
                secuencia(mutableListOf(), header = { throw errorHttp(codigo) })
            )
            val enLaSegunda = ejecutarSecuenciaCorreccionRemota(
                secuencia(mutableListOf(), cliente = { throw errorHttp(codigo) })
            )
            terminales += listOf(enLaPrimera, enLaSegunda)
                .filterIsInstance<ResultadoDeLaSecuencia.Terminal>()
        }

        assertEquals(
            "el recorrido no produjo los terminales esperados; sin eso esto sale verde sin medir",
            codigos.size * 2,
            terminales.size
        )
        terminales.forEach { terminal ->
            assertTrue(
                "estado fuera del vocabulario: ${terminal.estado}",
                terminal.estado in CorreccionRemotaTerminal.CONOCIDOS
            )
            assertTrue(
                "estadoSinParcial fuera del vocabulario: ${terminal.estadoSinParcial}",
                terminal.estadoSinParcial in CorreccionRemotaTerminal.CONOCIDOS
            )
        }
    }

    // ── 6. Lo reintentable NO deja marca ─────────────────────────────────

    @Test
    fun `un 500 a media secuencia se reintenta y no deja ninguna marca terminal`() = runTest {
        val registro = mutableListOf<PasoCorreccionRemota>()

        val resultado = ejecutarSecuenciaCorreccionRemota(
            secuencia(registro, cliente = { throw errorHttp(500) })
        )

        val reintento = resultado as ResultadoDeLaSecuencia.Reintentar
        assertEquals(PasoCorreccionRemota.CLIENTE, reintento.paso)
        assertEquals(1, reintento.pasosAplicados)
    }

    @Test
    fun `un 401 a media secuencia se reintenta - es el parpadeo del token`() = runTest {
        val resultado = ejecutarSecuenciaCorreccionRemota(
            secuencia(mutableListOf(), lineas = { throw errorHttp(401) })
        )
        assertTrue(resultado is ResultadoDeLaSecuencia.Reintentar)
    }

    @Test
    fun `la cancelacion del worker se propaga, no se traga como reintento`() = runTest {
        var capturada: CancellationException? = null
        try {
            ejecutarSecuenciaCorreccionRemota(
                secuencia(mutableListOf(), cliente = { throw CancellationException("stop") })
            )
        } catch (e: CancellationException) {
            capturada = e
        }
        assertNotNull(
            "detener el worker no es un desenlace de la corrección",
            capturada
        )
    }

    @Test
    fun `una secuencia vacia es un error de programacion, no un exito silencioso`() = runTest {
        var fallo: IllegalArgumentException? = null
        try {
            ejecutarSecuenciaCorreccionRemota(emptyList())
        } catch (e: IllegalArgumentException) {
            fallo = e
        }
        assertNotNull(
            "una lista vacía no puede declararse entregada: nada se mandó",
            fallo
        )
    }

    // ── 7. El latido cubre LAS TRES ──────────────────────────────────────

    @Test
    fun `el latido sigue vivo durante las tres peticiones, no solo la primera`() = runTest {
        val periodo = 1_000L
        val latidosPorPaso = mutableListOf<PasoCorreccionRemota?>()
        var enCurso: PasoCorreccionRemota? = null
        var renovaciones = 0

        // Cada petición tarda más de dos períodos: si el latido sólo cubriera
        // la primera, no habría ni una renovación durante cliente ni líneas y
        // el arrendamiento caducaría con la secuencia en vuelo.
        val lenta: suspend (PasoCorreccionRemota) -> VentaSituacionDTO = { paso ->
            enCurso = paso
            delay(periodo * 2 + 1)
            respuestaHeader
        }

        val resultado = conLatidoDelArrendamiento(
            periodoMs = periodo,
            renovar = {
                renovaciones++
                latidosPorPaso += enCurso
                1
            }
        ) {
            ejecutarSecuenciaCorreccionRemota(
                listOf(
                    PeticionDeCorreccion(PasoCorreccionRemota.HEADER) {
                        lenta(PasoCorreccionRemota.HEADER)
                    },
                    PeticionDeCorreccion(PasoCorreccionRemota.CLIENTE) {
                        lenta(PasoCorreccionRemota.CLIENTE)
                    },
                    PeticionDeCorreccion(PasoCorreccionRemota.LINEAS) {
                        lenta(PasoCorreccionRemota.LINEAS)
                    }
                )
            )
        }

        assertTrue(
            "la secuencia tiene que terminar entregada",
            resultado is ResultadoDeLaSecuencia.Entregada
        )
        lasTres.forEach { paso ->
            assertTrue(
                "no hubo ni un latido mientras corría $paso; " +
                    "el arrendamiento caducaría a media secuencia",
                latidosPorPaso.contains(paso)
            )
        }
        assertTrue("se esperaban al menos seis latidos, hubo $renovaciones", renovaciones >= 6)
    }

    @Test
    fun `el latido se detiene cuando el trabajo termina`() = runTest {
        val periodo = 1_000L
        var renovaciones = 0

        conLatidoDelArrendamiento(
            periodoMs = periodo,
            renovar = {
                renovaciones++
                1
            }
        ) {
            delay(periodo * 3 + 1)
        }

        val alTerminar = renovaciones
        // Si el latido no se cancelara, `conLatidoDelArrendamiento` nunca
        // habría regresado (su `coroutineScope` espera a las hijas) y esta
        // prueba se habría colgado. Este segundo tramo de tiempo virtual
        // confirma además que ya no late.
        delay(periodo * 5)
        assertEquals(
            "el latido siguió corriendo después de la última petición",
            alTerminar,
            renovaciones
        )
    }

    @Test
    fun `un latido que falla no tumba la secuencia`() = runTest {
        val periodo = 1_000L
        val fallos = mutableListOf<Exception>()
        var intentos = 0

        val resultado = conLatidoDelArrendamiento(
            periodoMs = periodo,
            renovar = {
                intentos++
                if (intentos == 1) throw IllegalStateException("SQLite trabado") else 1
            },
            alFallarElLatido = { fallos += it }
        ) {
            delay(periodo * 3 + 1)
            ejecutarSecuenciaCorreccionRemota(secuencia(mutableListOf()))
        }

        assertTrue(
            "un latido perdido no es una corrección perdida",
            resultado is ResultadoDeLaSecuencia.Entregada
        )
        assertEquals(1, fallos.size)
        assertTrue("el latido tiene que seguir latiendo tras el fallo", intentos > 1)
    }

    @Test
    fun `si el candado deja de ser nuestro el latido para, pero no re-reclama`() = runTest {
        val periodo = 1_000L
        var renovaciones = 0
        var perdido = 0

        conLatidoDelArrendamiento(
            periodoMs = periodo,
            renovar = {
                renovaciones++
                0
            },
            alPerderElCandado = { perdido++ }
        ) {
            delay(periodo * 5 + 1)
        }

        assertEquals(
            "el latido tiene que parar en el primer 0: retomarlo le robaría la fila al editor",
            1,
            renovaciones
        )
        assertEquals(1, perdido)
    }

    @Test
    fun `un periodo no positivo apaga el latido`() = runTest {
        var renovaciones = 0

        conLatidoDelArrendamiento(
            periodoMs = 0L,
            renovar = {
                renovaciones++
                1
            }
        ) {
            delay(10_000)
        }

        assertEquals(
            "con el latido apagado nadie renueva; es lo que reproduce el candado que caduca",
            0,
            renovaciones
        )
    }

    // ── 8. El código de negocio se lee de donde Huma lo entierra ─────────

    @Test
    fun `el codigo de negocio del cuerpo llega al desenlace terminal`() = runTest {
        val conCodigo = ejecutarSecuenciaCorreccionRemota(
            secuencia(mutableListOf(), header = { throw errorHttp(409, "venta_no_editable") })
        ) as ResultadoDeLaSecuencia.Terminal
        assertEquals("venta_no_editable", conCodigo.codigoDeError)

        val sinCodigo = ejecutarSecuenciaCorreccionRemota(
            secuencia(mutableListOf(), header = { throw errorHttp(409) })
        ) as ResultadoDeLaSecuencia.Terminal
        assertNull(
            "un cuerpo sin `code=` no inventa un código, y el 409 sigue siendo terminal",
            sinCodigo.codigoDeError
        )
        assertEquals(CorreccionRemotaTerminal.RECHAZADA_ESTADO, sinCodigo.estado)
    }
}
