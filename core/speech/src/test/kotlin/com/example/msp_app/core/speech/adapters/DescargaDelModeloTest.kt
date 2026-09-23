package com.example.msp_app.core.speech.adapters

import com.example.msp_app.core.speech.application.SpeechTelemetria
import com.example.msp_app.core.speech.domain.EstadoDelModelo
import com.example.msp_app.core.speech.domain.ModeloDeDictado
import com.example.msp_app.core.speech.fake.MODELO_DE_PRUEBA
import com.example.msp_app.core.speech.fake.NativoFalso
import com.example.msp_app.core.speech.fake.PlanificadorFalso
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * La descarga del modelo contra un servidor **de verdad** (`MockWebServer`),
 * porque lo que se prueba —el `Range`, el `206` contra el `200`, el checksum—
 * es exactamente lo que un fake de HTTP no puede probar.
 *
 * Es la misma receta que `ApkDownloaderTest` de `:core:appgate`: un servidor
 * levantado por la clase de test, sin helper compartido (no existe y no se
 * crea, global-constraints §HTTP falso).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DescargaDelModeloTest {

    @get:Rule
    val carpeta: TemporaryFolder = TemporaryFolder()

    private lateinit var servidor: MockWebServer

    private val telemetry = RecordingTelemetry()

    /** El contenido del "modelo": 1 000 bytes que se pueden verificar de verdad. */
    private val contenido = ByteArray(MIL) { (it % BYTE).toByte() }

    @Before
    fun levantarElServidor() {
        servidor = MockWebServer()
        servidor.start()
    }

    @After
    fun bajarElServidor() {
        servidor.shutdown()
    }

    private fun almacen() = AlmacenDelModelo(carpeta.root, telemetry)

    private fun modelo(sha: String = sha256De(contenido)) = ModeloDeDictado(
        url = servidor.url("/modelo.bin").toString(),
        tamanoBytes = contenido.size.toLong(),
        sha256 = sha
    )

    /** Mismo motivo que en `WhisperDictadoAdapterTest`: el dispatcher es el del test. */
    private fun TestScope.descarga(almacen: AlmacenDelModelo) = DescargaDelModelo(
        llamadas = OkHttpClient(),
        almacen = almacen,
        telemetry = telemetry,
        dispatcher = StandardTestDispatcher(testScheduler)
    )

    @Test
    fun `baja entero y promueve el archivo verificado`() = runTest(StandardTestDispatcher()) {
        servidor.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        val almacen = almacen()

        val desenlace = descarga(almacen).bajar(modelo())
        advanceUntilIdle()

        assertEquals(DesenlaceDeLaDescarga.Listo, desenlace)
        assertNotNull(almacen.rutaDelModeloListo())
        assertFalse("el parcial debio desaparecer", almacen.parcial.exists())
    }

    /**
     * **La reanudación.** Con 400 bytes en disco, la petición lleva
     * `Range: bytes=400-` y lo que llega se **agrega**. Sin esto, cada corte de
     * red costaría los megas ya bajados.
     */
    @Test
    fun `reanuda con Range y agrega lo que falta`() = runTest(StandardTestDispatcher()) {
        val almacen = almacen()
        almacen.prepararCarpeta()
        almacen.parcial.writeBytes(contenido.copyOfRange(0, CORTE))
        servidor.enqueue(
            MockResponse()
                .setResponseCode(CONTENIDO_PARCIAL)
                .setBody(Buffer().write(contenido.copyOfRange(CORTE, contenido.size)))
        )

        val desenlace = descarga(almacen).bajar(modelo())
        advanceUntilIdle()

        assertEquals("bytes=$CORTE-", servidor.takeRequest().getHeader("Range"))
        assertEquals(DesenlaceDeLaDescarga.Listo, desenlace)
    }

    /**
     * El servidor que **ignora** el `Range` y contesta `200` con el archivo
     * entero: hay que truncar. Sin truncar, el archivo quedaría con los 400
     * bytes viejos por delante y el checksum no coincidiría nunca.
     */
    @Test
    fun `si el servidor ignora el Range trunca y baja de cero`() =
        runTest(StandardTestDispatcher()) {
            val almacen = almacen()
            almacen.prepararCarpeta()
            almacen.parcial.writeBytes(contenido.copyOfRange(0, CORTE))
            servidor.enqueue(MockResponse().setBody(Buffer().write(contenido)))

            val desenlace = descarga(almacen).bajar(modelo())
            advanceUntilIdle()

            assertEquals(DesenlaceDeLaDescarga.Listo, desenlace)
        }

    /**
     * **El checksum que no coincide borra el parcial.** Un `.bin` cortado que
     * se quedara en disco haría que whisper produzca basura plausible — el peor
     * desenlace posible, porque nadie lo detecta leyéndolo.
     */
    @Test
    fun `un checksum que no coincide borra lo bajado y lo reporta`() =
        runTest(StandardTestDispatcher()) {
            servidor.enqueue(MockResponse().setBody(Buffer().write(contenido)))
            val almacen = almacen()

            val desenlace = descarga(almacen).bajar(modelo(sha = "0".repeat(LARGO_DEL_SHA)))
            advanceUntilIdle()

            assertEquals(DesenlaceDeLaDescarga.Corrupta, desenlace)
            assertNull(almacen.rutaDelModeloListo())
            assertFalse("el parcial corrupto se quedo en disco", almacen.parcial.exists())
            assertTrue(
                telemetry.recorded.any { it.name == SpeechTelemetria.CODE_MODELO_CORRUPTO }
            )
        }

    /** Un 4xx/5xx corta, **conserva** lo bajado y lo reporta con el código HTTP. */
    @Test
    fun `un error http corta, conserva y reporta`() = runTest(StandardTestDispatcher()) {
        servidor.enqueue(MockResponse().setResponseCode(NO_ENCONTRADO))
        val almacen = almacen()
        almacen.prepararCarpeta()
        almacen.parcial.writeBytes(contenido.copyOfRange(0, CORTE))

        val desenlace = descarga(almacen).bajar(modelo())
        advanceUntilIdle()

        assertTrue(desenlace is DesenlaceDeLaDescarga.Cortada)
        assertEquals(
            "lo bajado no se conservo",
            CORTE.toLong(),
            almacen.bytesBajados()
        )
        val evento = telemetry.recorded.single { it.name == SpeechTelemetria.CODE_DESCARGA_CORTADA }
        assertEquals(NO_ENCONTRADO.toString(), evento.props[SpeechTelemetria.PROP_HTTP])
    }

    /** El avance que pinta la barra llega en bytes y termina en el total. */
    @Test
    fun `informa el avance hasta el total`() = runTest(StandardTestDispatcher()) {
        servidor.enqueue(MockResponse().setBody(Buffer().write(contenido)))
        val avances = mutableListOf<Long>()

        descarga(almacen()).bajar(modelo()) { avances += it.bytesBajados }
        advanceUntilIdle()

        assertEquals(0L, avances.first())
        assertEquals(contenido.size.toLong(), avances.last())
    }

    /**
     * El puerto: encolar deja "esperando wifi" —no "descargando"— y le pasa el
     * modelo al planificador.
     */
    @Test
    fun `pedir la descarga deja esperando wifi y encola`() = runTest(StandardTestDispatcher()) {
        val planificador = PlanificadorFalso()
        val puerto = ModeloDeDictadoAdapter(
            almacen = almacen(),
            planificador = planificador,
            estado = EstadoDelModeloEnMemoria(),
            modelo = MODELO_DE_PRUEBA,
            nativo = NativoFalso()
        )

        puerto.pedirLaDescarga()
        advanceUntilIdle()

        assertEquals(EstadoDelModelo.EsperandoWifi, puerto.estado().first())
        assertEquals(listOf(MODELO_DE_PRUEBA), planificador.encolados)
    }

    /**
     * **El estado se siembra desde el disco.** Sin esto, un cobrador que bajó el
     * modelo ayer abriría la pantalla hoy y volvería a bajar 44 MB.
     */
    @Test
    fun `al construirse ve el modelo que ya estaba en disco`() = runTest(StandardTestDispatcher()) {
        File(carpeta.root, "modelo.bin").writeText("ya estaba")

        val puerto = ModeloDeDictadoAdapter(
            almacen = almacen(),
            planificador = PlanificadorFalso(),
            estado = EstadoDelModeloEnMemoria(),
            modelo = MODELO_DE_PRUEBA,
            nativo = NativoFalso()
        )

        assertEquals(EstadoDelModelo.Listo, puerto.estado().first())
    }

    /** Borrar recupera los megas y cancela el trabajo encolado. */
    @Test
    fun `cancelar y borrar deja el modelo ausente`() = runTest(StandardTestDispatcher()) {
        File(carpeta.root, "modelo.bin").writeText("ya estaba")
        val planificador = PlanificadorFalso()
        val almacen = almacen()
        val puerto =
            ModeloDeDictadoAdapter(
                almacen,
                planificador,
                EstadoDelModeloEnMemoria(),
                MODELO_DE_PRUEBA,
                NativoFalso()
            )

        puerto.cancelarYBorrar()
        advanceUntilIdle()

        assertEquals(EstadoDelModelo.Ausente, puerto.estado().first())
        assertNull(almacen.rutaDelModeloListo())
        assertEquals(1, planificador.cancelaciones)
    }

    private fun sha256De(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            ((byte.toInt() and BYTE_MASCARA) + RELLENO).toString(BASE).substring(1)
        }

    private companion object {
        const val MIL = 1_000
        const val BYTE = 251
        const val CORTE = 400
        const val CONTENIDO_PARCIAL = 206
        const val NO_ENCONTRADO = 404
        const val LARGO_DEL_SHA = 64
        const val BYTE_MASCARA = 0xFF
        const val RELLENO = 0x100
        const val BASE = 16
    }
}
