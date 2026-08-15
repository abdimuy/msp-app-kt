package com.example.msp_app.core.appgate.download

import com.example.msp_app.core.appgate.UpdatePackage
import com.example.msp_app.core.testing.RobolectricTestBase
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val HTTP_OK = 200
private const val HTTP_PARTIAL = 206
private const val HTTP_SERVER_ERROR = 500

/** APK de juguete: determinista, para poder calcular su SHA-256 en el test. */
private val APK_BYTES = ByteArray(4_096) { (it % 251).toByte() }

/** Cuántos bytes se dejan "ya bajados" en los casos de reanudación. */
private const val ALREADY_HAVE = 1_500

/**
 * El descargador contra un servidor real ([MockWebServer]) — no un doble de
 * OkHttp: lo que se prueba es precisamente el diálogo HTTP (`Range`, `206` vs
 * `200`) y un mock del cliente lo daría por bueno sin ejercerlo.
 *
 * Robolectric porque las rutas de fallo llaman a `android.util.Log`, que en
 * JVM plano lanza "not mocked".
 *
 * Las dos promesas que el mockup le hace al usuario y que aquí se verifican:
 * **"al reintentar continúa desde ahí, no vuelve a empezar"** y **"si el
 * archivo llegó mal, se vuelve a bajar en vez de fallar al instalar"**.
 */
class ApkDownloaderTest : RobolectricTestBase() {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: ApkDownloader
    private lateinit var destination: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        downloader = ApkDownloader(OkHttpClient())
        destination = File(tempFolder.newFolder(), "msp-app.apk")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun update(sha256: String = sha256Of(APK_BYTES), size: Long = APK_BYTES.size.toLong()) =
        UpdatePackage(
            url = server.url("/msp-app.apk").toString(),
            sizeBytes = size,
            sha256 = sha256
        )

    private fun body(bytes: ByteArray) = Buffer().write(bytes)

    // --- Descarga limpia ---------------------------------------------------------

    @Test
    fun `descarga completa y verifica el checksum`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(body(APK_BYTES)))

        val outcome = downloader.download(update(), destination)

        assertTrue("se esperaba Completed, llegó $outcome", outcome is DownloadOutcome.Completed)
        assertArrayEquals(APK_BYTES, destination.readBytes())
    }

    @Test
    fun `el progreso termina en el total, en bytes que la UI muestra en megas`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(body(APK_BYTES)))
        val vistos = mutableListOf<DownloadProgress>()

        downloader.download(update(), destination) { vistos += it }

        assertTrue("no se informó ningún progreso", vistos.isNotEmpty())
        assertEquals(APK_BYTES.size.toLong(), vistos.last().downloadedBytes)
        assertTrue(vistos.last().complete)
    }

    // --- Reanudación --------------------------------------------------------------

    @Test
    fun `reanuda desde lo que ya hay en disco y pide solo el resto`() = runTest {
        destination.parentFile?.mkdirs()
        destination.writeBytes(APK_BYTES.copyOfRange(0, ALREADY_HAVE))
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_PARTIAL)
                .setBody(body(APK_BYTES.copyOfRange(ALREADY_HAVE, APK_BYTES.size)))
        )

        val outcome = downloader.download(update(), destination)

        assertEquals("bytes=$ALREADY_HAVE-", server.takeRequest().getHeader("Range"))
        assertTrue("se esperaba Completed, llegó $outcome", outcome is DownloadOutcome.Completed)
        // No se duplicó ni se perdió nada: el archivo final es el APK entero.
        assertArrayEquals(APK_BYTES, destination.readBytes())
    }

    @Test
    fun `el progreso de una reanudacion arranca en lo ya bajado, no en cero`() = runTest {
        destination.parentFile?.mkdirs()
        destination.writeBytes(APK_BYTES.copyOfRange(0, ALREADY_HAVE))
        server.enqueue(
            MockResponse()
                .setResponseCode(HTTP_PARTIAL)
                .setBody(body(APK_BYTES.copyOfRange(ALREADY_HAVE, APK_BYTES.size)))
        )
        val vistos = mutableListOf<DownloadProgress>()

        downloader.download(update(), destination) { vistos += it }

        assertEquals(ALREADY_HAVE.toLong(), vistos.first().downloadedBytes)
    }

    @Test
    fun `si el servidor ignora el Range y responde 200, se trunca y se baja de cero`() = runTest {
        destination.parentFile?.mkdirs()
        destination.writeBytes(APK_BYTES.copyOfRange(0, ALREADY_HAVE))
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(body(APK_BYTES)))

        val outcome = downloader.download(update(), destination)

        // Sin el truncado, el archivo quedaría con los 1500 bytes viejos ANTES
        // del APK completo y el checksum fallaría por una razón equivocada.
        assertTrue("se esperaba Completed, llegó $outcome", outcome is DownloadOutcome.Completed)
        assertArrayEquals(APK_BYTES, destination.readBytes())
    }

    @Test
    fun `un archivo ya completo en disco no vuelve a pedirse a la red`() = runTest {
        destination.parentFile?.mkdirs()
        destination.writeBytes(APK_BYTES)

        val outcome = downloader.download(update(), destination)

        assertTrue("se esperaba Completed, llegó $outcome", outcome is DownloadOutcome.Completed)
        assertEquals("no debió haber ninguna petición", 0, server.requestCount)
    }

    @Test
    fun `un corte a media descarga conserva lo bajado`() = runTest {
        destination.parentFile?.mkdirs()
        destination.writeBytes(APK_BYTES.copyOfRange(0, ALREADY_HAVE))
        server.enqueue(MockResponse().setResponseCode(HTTP_SERVER_ERROR))

        val outcome = downloader.download(update(), destination)

        assertTrue("se esperaba Failed, llegó $outcome", outcome is DownloadOutcome.Failed)
        // Es la promesa del mockup: "al reintentar continúa desde ahí".
        assertEquals(ALREADY_HAVE.toLong(), destination.length())
    }

    // --- Integridad ----------------------------------------------------------------

    @Test
    fun `un checksum que no coincide descarta el archivo`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(body(APK_BYTES)))

        val outcome = downloader.download(update(sha256 = "0".repeat(64)), destination)

        assertEquals(DownloadOutcome.IntegrityFailed, outcome)
        assertFalse("un APK corrupto no puede quedarse en disco", destination.exists())
    }

    @Test
    fun `un cuerpo alterado tampoco pasa la verificacion`() = runTest {
        val corrupto = APK_BYTES.copyOf().also { it[10] = (it[10] + 1).toByte() }
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(body(corrupto)))

        val outcome = downloader.download(update(), destination)

        assertEquals(DownloadOutcome.IntegrityFailed, outcome)
    }

    @Test
    fun `el checksum se compara sin distinguir mayusculas`() = runTest {
        server.enqueue(MockResponse().setResponseCode(HTTP_OK).setBody(body(APK_BYTES)))

        val outcome = downloader.download(
            update(sha256 = sha256Of(APK_BYTES).uppercase()),
            destination
        )

        assertTrue("se esperaba Completed, llegó $outcome", outcome is DownloadOutcome.Completed)
    }

    @Test
    fun `un archivo completo en disco pero corrupto se descarta en vez de darse por bueno`() =
        runTest {
            destination.parentFile?.mkdirs()
            destination.writeBytes(ByteArray(APK_BYTES.size) { 0 })

            val outcome = downloader.download(update(), destination)

            assertEquals(DownloadOutcome.IntegrityFailed, outcome)
            assertFalse(destination.exists())
        }
}

private fun sha256Of(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
        ((byte.toInt() and 0xFF) + 0x100).toString(16).substring(1)
    }
