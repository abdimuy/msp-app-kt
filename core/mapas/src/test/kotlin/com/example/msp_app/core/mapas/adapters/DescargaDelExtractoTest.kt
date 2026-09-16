package com.example.msp_app.core.mapas.adapters

import com.example.msp_app.core.mapas.application.MapasTelemetria
import com.example.msp_app.core.mapas.domain.AvanceDeLaDescarga
import com.example.msp_app.core.mapas.domain.ExtractoDeMapa
import com.example.msp_app.core.mapas.fake.ExtractoReal
import com.example.msp_app.core.mapas.fake.archivoPmtilesValido
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * La descarga del extracto, contra un **servidor HTTP de verdad**
 * (`MockWebServer`): los tres caminos del `Range` que `:core:appgate` ya pagó,
 * más la verificación por cabecera que es propia de este módulo.
 */
class DescargaDelExtractoTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private lateinit var servidor: MockWebServer
    private val telemetry = RecordingTelemetry()

    @Before
    fun levantarElServidor() {
        servidor = MockWebServer()
        servidor.start()
    }

    @After
    fun bajarElServidor() {
        servidor.shutdown()
    }

    private fun almacen() = AlmacenDelExtracto(carpeta.root, telemetry)

    /**
     * El dispatcher es **el del test**: `runTest` y la descarga comparten
     * `testScheduler`, que es lo que exige `kotlinx-coroutines-test` y lo que ya
     * hace `DescargaDelModeloTest` de `:core:speech`. `UnconfinedTestDispatcher`
     * está prohibido (global-constraints §Dispatchers).
     */
    private fun TestScope.descarga(almacen: AlmacenDelExtracto) = DescargaDelExtracto(
        llamadas = OkHttpClient.Builder().build(),
        almacen = almacen,
        telemetry = telemetry,
        dispatcher = StandardTestDispatcher(testScheduler)
    )

    private fun paquete(bytes: Long) =
        ExtractoDeMapa(url = servidor.url("/ruta.pmtiles").toString(), tamanoBytes = bytes)

    private fun parcial() = File(carpeta.root, "ruta.parcial")

    private fun definitivo() = File(carpeta.root, "ruta.pmtiles")

    /** Un `.pmtiles` sintético completo, como bytes para servir. */
    private fun bytesValidos(): ByteArray =
        archivoPmtilesValido(File(carpeta.root, "molde.pmtiles"), bytesDeDatos = 512).readBytes()
            .also { File(carpeta.root, "molde.pmtiles").delete() }

    @Test
    fun `baja de cero, verifica y promueve`() = runTest(StandardTestDispatcher()) {
        val bytes = bytesValidos()
        servidor.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val almacen = almacen()

        val desenlace = descarga(almacen).bajar(paquete(bytes.size.toLong()))

        assertEquals(DesenlaceDeLaDescarga.Listo, desenlace)
        assertTrue(definitivo().isFile)
        assertFalse(parcial().exists())
    }

    /**
     * **206: se reanuda.** Es la diferencia entre que el cobrador pierda los
     * megas que ya bajó y que no los pierda, y es lo que hace que vuelva a
     * intentar en vez de rendirse.
     */
    @Test
    fun `un 206 agrega a lo que ya habia`() = runTest(StandardTestDispatcher()) {
        val bytes = bytesValidos()
        val yaTengo = 200
        parcial().writeBytes(bytes.copyOf(yaTengo))
        servidor.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setBody(Buffer().write(bytes.copyOfRange(yaTengo, bytes.size)))
        )
        val almacen = almacen()

        val desenlace = descarga(almacen).bajar(paquete(bytes.size.toLong()))

        assertEquals(DesenlaceDeLaDescarga.Listo, desenlace)
        assertEquals("bytes=$yaTengo-", servidor.takeRequest().getHeader("Range"))
        assertTrue(definitivo().readBytes().contentEquals(bytes))
    }

    /**
     * **200 con `Range` pedido: el servidor lo ignoró y mandó todo.** Sin
     * truncar, el archivo quedaría con los 200 bytes viejos pegados adelante:
     * la cabecera seguiría estando y el archivo mediría de más. Lo atraparía la
     * verificación, pero se habrían bajado 25 MB para tirarlos.
     */
    @Test
    fun `un 200 sobre un parcial trunca y baja de cero`() = runTest(StandardTestDispatcher()) {
        val bytes = bytesValidos()
        parcial().writeBytes(bytes.copyOf(200))
        servidor.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(bytes)))
        val almacen = almacen()

        val desenlace = descarga(almacen).bajar(paquete(bytes.size.toLong()))

        assertEquals(DesenlaceDeLaDescarga.Listo, desenlace)
        assertTrue(definitivo().readBytes().contentEquals(bytes))
    }

    @Test
    fun `un servidor que no contesta 2xx corta y lo reporta con el codigo http`() = runTest(
        StandardTestDispatcher()
    ) {
        servidor.enqueue(MockResponse().setResponseCode(503))

        val desenlace = descarga(almacen()).bajar(paquete(1_000L))

        assertTrue(desenlace is DesenlaceDeLaDescarga.Cortada)
        val evento = telemetry.recorded.single()
        assertEquals(MapasTelemetria.CODE_DESCARGA_CORTADA, evento.name)
        assertEquals("503", evento.props[MapasTelemetria.PROP_HTTP])
    }

    /** Lo que baja no es un pmtiles: se descarta entero y se vuelve a cero. */
    @Test
    fun `lo que baja y no verifica se borra`() = runTest(StandardTestDispatcher()) {
        val basura = ByteArray(300) { 7 }
        servidor.enqueue(MockResponse().setBody(Buffer().write(basura)))

        val desenlace = descarga(almacen()).bajar(paquete(basura.size.toLong()))

        assertEquals(DesenlaceDeLaDescarga.Invalida, desenlace)
        assertFalse(parcial().exists())
        assertFalse(definitivo().exists())
        assertTrue(telemetry.recorded.any { it.name == MapasTelemetria.CODE_EXTRACTO_INVALIDO })
    }

    @Test
    fun `una conexion que se corta conserva lo bajado`() = runTest(StandardTestDispatcher()) {
        val bytes = bytesValidos()
        servidor.enqueue(
            MockResponse()
                .setBody(Buffer().write(bytes))
                .setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
        )

        val desenlace = descarga(almacen()).bajar(paquete(bytes.size.toLong()))

        assertTrue(desenlace is DesenlaceDeLaDescarga.Cortada)
        assertEquals(
            MapasTelemetria.CODE_DESCARGA_CORTADA,
            telemetry.recorded.first().name
        )
    }

    // ── El extracto de verdad, servido por HTTP ─────────────────────────────

    /**
     * **Los 25 MB reales, bajados por HTTP, cortados a la mitad, reanudados con
     * `Range` y verificados.**
     *
     * Es el camino completo del módulo sobre el archivo de verdad, y es lo más
     * lejos que se puede llegar sin un dispositivo: lo único que queda sin
     * cubrir después de esto es el render, que necesita GL.
     */
    @Test
    fun `el extracto real se baja en dos tramos y queda listo`() = runTest(
        StandardTestDispatcher()
    ) {
        assumeTrue("no esta el extracto real: ${ExtractoReal.archivo}", ExtractoReal.disponible)
        val bytes = ExtractoReal.archivo.readBytes()
        val corte = bytes.size / 2
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val rango = request.getHeader("Range")
                return if (rango == null) {
                    // Primer tramo: se manda media respuesta y se corta el socket.
                    MockResponse()
                        .setBody(Buffer().write(bytes.copyOf(corte)))
                        .setSocketPolicy(
                            okhttp3.mockwebserver.SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY
                        )
                } else {
                    val desde = rango.removePrefix("bytes=").removeSuffix("-").toInt()
                    MockResponse()
                        .setResponseCode(206)
                        .setBody(Buffer().write(bytes.copyOfRange(desde, bytes.size)))
                }
            }
        }
        val almacen = almacen()
        val descarga = descarga(almacen)

        val primero = descarga.bajar(paquete(bytes.size.toLong()))
        assertTrue("el primer tramo tenia que cortarse", primero is DesenlaceDeLaDescarga.Cortada)
        assertTrue("no se conservo nada de lo bajado", almacen.bytesBajados() > 0L)

        val avances = mutableListOf<AvanceDeLaDescarga>()
        val segundo = descarga.bajar(paquete(bytes.size.toLong())) { avances += it }

        assertEquals(DesenlaceDeLaDescarga.Listo, segundo)
        assertEquals(ExtractoReal.TAMANO_MEDIDO, definitivo().length())
        assertEquals(
            ExtractoReal.ZOOM_MAXIMO_MEDIDO,
            almacen.mapaListo()!!.cabecera.zoomMaximo
        )
        assertTrue("la barra tiene que arrancar donde quedo", avances.first().bytesBajados > 0L)
        assertEquals(1f, avances.last().fraccion)
    }
}
