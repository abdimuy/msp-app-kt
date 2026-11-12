package com.example.msp_app.data.diadecobro

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroApi
import com.example.msp_app.data.api.services.diadecobro.DiaDeCobroResponse
import com.example.msp_app.workmanager.enqueueDiaDeCobro
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response

/**
 * Los arreglos de la revisión independiente de 66eb5386: zona que cambia con el
 * trabajo único pendiente, escrituras concurrentes, zona del adaptador y
 * telemetría.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class DiaDeCobroZonaTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val gson = Gson()

    private fun respuesta(id: Int = 7) = gson.fromJson(
        """{"calculado_en":"2026-11-11T18:40:00Z","ventas":[{"docto_cc_id":$id,
           "acordado":{"tipo":"semana","dia":"SABADO"}}]}""",
        DiaDeCobroResponse::class.java
    )

    private fun archivo() = File(carpeta.root, "dia_de_cobro.json")

    // --- 1. Zona que cambia -----------------------------------------------------------------

    @Test
    fun `la zona actual manda sobre la del trabajo y sobre la guardada`() {
        assertEquals(22, zonaDelDiaDeCobro(actual = 22, delTrabajo = 21, guardada = 20))
        assertEquals(21, zonaDelDiaDeCobro(actual = null, delTrabajo = 21, guardada = 20))
        assertEquals(20, zonaDelDiaDeCobro(actual = null, delTrabajo = 0, guardada = 20))
        assertNull(zonaDelDiaDeCobro(actual = null, delTrabajo = 0, guardada = null))
        assertEquals(21, zonaDelDiaDeCobro(actual = 0, delTrabajo = 21, guardada = null))
    }

    @Test
    fun `encolar con otra zona reemplaza el trabajo pendiente, no lo descarta`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build()
        )
        val wm = WorkManager.getInstance(context)

        // Sin red (la compuerta no se abre): el trabajo se queda ENQUEUED.
        enqueueDiaDeCobro(context, 21)
        val primero = vivo(wm)!!
        enqueueDiaDeCobro(context, 22)
        val despues = vivo(wm)!!

        // Con KEEP el id seguiría siendo el del trabajo de la zona 21 (y la 22 se
        // perdería); con REPLACE el primero se cancela y queda uno nuevo.
        assertTrue("el segundo encolado no reemplazó al primero", primero != despues)
        val estadoDelPrimero = wm.getWorkInfoById(primero).get()?.state
        assertTrue(estadoDelPrimero == null || estadoDelPrimero == WorkInfo.State.CANCELLED)
    }

    private fun vivo(wm: WorkManager) = wm.getWorkInfosForUniqueWork("dia_de_cobro_ahora").get()
        .firstOrNull { it.state == WorkInfo.State.ENQUEUED }?.id

    // --- 2. Escrituras concurrentes ---------------------------------------------------------

    @Test
    fun `dos escrituras concurrentes dejan un JSON legible`() {
        val almacen = DiaDeCobroAlmacen(archivo())
        val hilos = 8
        val listos = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(hilos)
        val futuros = (1..hilos).map { n ->
            pool.submit {
                listos.await()
                repeat(20) {
                    almacen.guardar(
                        DiaDeCobroGuardado(n, Instant.parse("2026-11-11T19:00:00Z"), respuesta(n))
                    )
                }
            }
        }
        listos.countDown()
        futuros.forEach { it.get() } // si alguna escritura lanzó, aquí revienta
        pool.shutdown()

        val leido = almacen.leer()
        assertNotNull("el archivo quedó ilegible tras escrituras concurrentes", leido)
        assertEquals(leido!!.zona, leido.respuesta.ventas!!.single().docto_cc_id)
        // Y no quedaron temporales regados.
        assertEquals(listOf("dia_de_cobro.json"), carpeta.root.list()!!.toList())
    }

    // --- 3. El adaptador compara la zona ----------------------------------------------------

    @Test
    fun `lo guardado de otra zona no se enseña`() = runTest {
        val almacen = DiaDeCobroAlmacen(archivo())
        almacen.guardar(DiaDeCobroGuardado(21, Instant.parse("2026-11-11T19:00:00Z"), respuesta(7)))

        assertNotNull(DiaDeCobroAdapter(almacen) { 21 }.deLaVenta(7))
        assertNull(DiaDeCobroAdapter(almacen) { 22 }.deLaVenta(7))
        // Sin zona conocida todavía no hay con qué comparar: se enseña lo guardado.
        assertNotNull(DiaDeCobroAdapter(almacen) { null }.deLaVenta(7))
    }

    // --- 4. Telemetría ----------------------------------------------------------------------

    private class ApiQueFalla(val falla: Exception) : DiaDeCobroApi {
        override suspend fun diaDeCobro(zonaId: Int): DiaDeCobroResponse = throw falla
    }

    @Test
    fun `una descarga que falla por HTTP emite el codigo`() = runTest {
        val telemetria = RecordingTelemetry()
        val http = HttpException(
            Response.error<Any>(503, "".toResponseBody("application/json".toMediaType()))
        )
        val r = DescargarDiaDeCobro(
            ApiQueFalla(http),
            DiaDeCobroAlmacen(archivo()),
            FakeClock.at("2026-11-11T14:00:00Z"),
            telemetria
        )(21)

        assertTrue(r is ResultadoDeDiaDeCobro.Fallo)
        val e = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == DiaDeCobroTelemetria.CODE_DESCARGA_FALLO
        }
        assertEquals("503", e.props[DiaDeCobroTelemetria.PROP_HTTP])
    }

    @Test
    fun `una descarga que falla por excepcion emite su clase`() = runTest {
        val telemetria = RecordingTelemetry()
        DescargarDiaDeCobro(
            ApiQueFalla(IOException("sin red")),
            DiaDeCobroAlmacen(archivo()),
            FakeClock.at("2026-11-11T14:00:00Z"),
            telemetria
        )(21)

        val e = telemetria.recorded.single { it.name == DiaDeCobroTelemetria.CODE_DESCARGA_FALLO }
        assertEquals("IOException", e.props[DiaDeCobroTelemetria.PROP_EXCEPCION])
    }

    @Test
    fun `un archivo ilegible se reporta, y uno que no existe no`() {
        val telemetria = RecordingTelemetry()
        val almacen = DiaDeCobroAlmacen(archivo(), telemetry = telemetria)

        assertNull(almacen.leer()) // no existe: no es un fallo
        assertTrue(telemetria.recorded.isEmpty())

        archivo().writeText("{ roto")
        assertNull(almacen.leer())
        assertTrue(
            telemetria.recorded.any { it.name == DiaDeCobroTelemetria.CODE_ARCHIVO_ILEGIBLE }
        )
    }
}
