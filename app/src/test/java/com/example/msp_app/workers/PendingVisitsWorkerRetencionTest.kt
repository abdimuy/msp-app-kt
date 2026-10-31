package com.example.msp_app.workers

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.location.captura.EsperaDeEnvio
import com.example.msp_app.core.common.location.captura.RelojMonotono
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-148 (K6): el envio de la visita se RETIENE en el worker hasta tener ubicacion o 30 s desde
 * que se guardo, igual que el del pago: antes de la bifurcacion de modos (v3, v2 actual), sin efectos,
 * una vez por lote y sin esperar por una visita ya confirmada o resguardada. Simulacion determinista
 * (relojes falsos, tiempo virtual, Room con ejecutores directos). t = 0 es el guardado.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingVisitsWorkerRetencionTest : RoomTestBase() {

    private lateinit var context: Context
    private var desfaseDeLaPared = 0L
    private var tomaNoVaALlegar = false

    private class Envio(val ms: Long, val id: String, val lat: Double, val lng: Double)

    private val envios = mutableListOf<Envio>()

    @Before
    fun preparar() {
        context = ApplicationProvider.getApplicationContext()
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        AppDatabase.setInstanceForTesting(db)
    }

    private fun TestScope.pared() = object : AppClock {
        override fun now(): Instant = Instant.ofEpochMilli(T0 + currentTime + desfaseDeLaPared)
    }

    private fun TestScope.monotono() = RelojMonotono { BASE + currentTime * 1_000_000L }

    private fun TestScope.api() = fakeV2Api { key, body: CrearVisitaBody ->
        envios += Envio(currentTime, key, body.lat, body.lng)
        VisitaDTO(id = key)
    }

    private fun TestScope.worker(id: String, modo: ModoEnvioVisitas): PendingVisitsWorker {
        val pared = pared()
        val prueba = this
        return TestListenableWorkerBuilder<PendingVisitsWorker>(
            context,
            Data.Builder().putString("visit_id", id).build()
        ).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = PendingVisitsWorker(
                appContext = appContext,
                workerParams = workerParameters,
                visitsStore = VisitsLocalDataSource(appContext),
                v2Api = prueba.api(),
                legacyApi = throwingLegacyApi(),
                useV2 = true,
                imagenes = db.visitImageDao(),
                clock = pared,
                modo = { modo },
                visitDao = db.visitDao(),
                plazos = db.plazoDeEnvioDao(),
                espera = EsperaDeEnvio(pared, prueba.monotono()),
                tomaNoVaALlegar = { tomaNoVaALlegar }
            )
        }).build() as PendingVisitsWorker
    }

    private suspend fun guardar(
        id: String,
        conPlazo: Boolean = true,
        lat: Double = 0.0,
        lng: Double = 0.0
    ) {
        db.visitDao().insertVisit(pendingVisit(id).copy(LAT = lat, LNG = lng))
        if (conPlazo) {
            db.plazoDeEnvioDao().anotar(
                PlazoDeEnvioEntity(
                    id,
                    UbicacionDeCapturaEntity.TIPO_VISITA,
                    T0,
                    PlazoDeEnvioEntity.PLAZO_MS
                )
            )
        }
    }

    private fun TestScope.arrancar(
        id: String,
        modo: ModoEnvioVisitas = ModoEnvioVisitas.V3
    ): Pair<Job, () -> ListenableWorker.Result?> {
        var resultado: ListenableWorker.Result? = null
        val w = worker(id, modo)
        val job = launch { resultado = w.doWork() }
        runCurrent()
        return job to { resultado }
    }

    @Test
    fun `E-APP-148 fix antes de guardar - la visita ya trae ubicacion y sale sin esperar`() =
        runTest {
            guardar("v-1", lat = LAT, lng = LON)
            val (job, resultado) = arrancar("v-1")
            assertEquals(ListenableWorker.Result.success(), resultado())
            assertEquals(0L, envios.single().ms)
            assertEquals(LAT, envios.single().lat, 0.0)
            job.cancel()
        }

    @Test
    fun `E-APP-148 fix durante la espera - sale de inmediato con la ubicacion, un solo POST`() =
        runTest {
            guardar("v-1")
            val (job, resultado) = arrancar("v-1")
            advanceTimeBy(6_000)
            assertTrue("retenida", envios.isEmpty())

            db.visitDao().updateLocationIfEmpty("v-1", LAT, LON)
            advanceTimeBy(300)
            runCurrent()

            assertEquals(ListenableWorker.Result.success(), resultado())
            assertEquals(1, envios.size)
            assertTrue("sale al leerla, no a los 30 s", envios.single().ms in 6_000..6_300)
            assertEquals("lat y lng juntas", LAT, envios.single().lat, 0.0)
            assertEquals(LON, envios.single().lng, 0.0)
            job.cancel()
        }

    @Test
    fun `E-APP-148 nunca llega - sale a los 30 s con 0,0 como hoy`() = runTest {
        guardar("v-1")
        val (job, resultado) = arrancar("v-1")
        advanceTimeBy(29_900)
        assertTrue(envios.isEmpty())
        advanceTimeBy(200)
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(30_000L, envios.single().ms)
        assertEquals(0.0, envios.single().lat, 0.0)
        assertEquals(0.0, envios.single().lng, 0.0)
        job.cancel()
    }

    @Test
    fun `E-APP-148 el modo v2 actual espera igual que el v3`() = runTest {
        guardar("v-1")
        val (job, resultado) = arrancar("v-1", ModoEnvioVisitas.V2_ACTUAL)
        advanceTimeBy(5_000)
        assertTrue(envios.isEmpty())

        db.visitDao().updateLocationIfEmpty("v-1", LAT, LON)
        advanceTimeBy(300)
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(LAT, envios.single().lat, 0.0)
        job.cancel()
    }

    @Test
    fun `E-APP-148 un lote de varias cuentas espera una vez y la misma ubicacion llega a todas`() =
        runTest {
            guardar("v-1")
            guardar("v-2")
            val (job1, r1) = arrancar("v-1")
            val (job2, r2) = arrancar("v-2")
            advanceTimeBy(8_000)
            assertTrue("las dos retenidas", envios.isEmpty())

            // El tomador escribe la misma ubicacion en las dos cuentas del lote.
            db.visitDao().updateLocationIfEmpty("v-1", LAT, LON)
            db.visitDao().updateLocationIfEmpty("v-2", LAT, LON)
            advanceTimeBy(300)
            runCurrent()

            assertEquals(ListenableWorker.Result.success(), r1())
            assertEquals(ListenableWorker.Result.success(), r2())
            assertEquals(listOf("v-1", "v-2"), envios.map { it.id }.sorted())
            assertTrue(envios.all { it.lat == LAT && it.lng == LON })
            assertTrue("salieron juntas, no una a los 30 s", envios.all { it.ms <= 8_300 })
            job1.cancel()
            job2.cancel()
        }

    @Test
    fun `E-APP-148 un lote que no recibe ubicacion sale completo a los 30 s del guardado mas antiguo`() =
        runTest {
            guardar("v-1")
            guardar("v-2")
            val (job1, _) = arrancar("v-1")
            val (job2, _) = arrancar("v-2")
            advanceTimeBy(30_100)
            runCurrent()

            assertEquals(2, envios.size)
            assertTrue(envios.all { it.ms == 30_000L })
            job1.cancel()
            job2.cancel()
        }

    @Test
    fun `E-APP-148 una visita ya confirmada no espera ni manda`() = runTest {
        guardar("v-1")
        db.visitDao().confirmar(listOf("v-1"), "2026-10-30T18:00:00Z")

        val (job, resultado) = arrancar("v-1")

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertTrue(envios.isEmpty())
        assertEquals("sin esperar", 0L, currentTime)
        job.cancel()
    }

    @Test
    fun `E-APP-148 permiso negado o GPS apagado - no espera`() = runTest {
        guardar("v-1")
        tomaNoVaALlegar = true
        val (job, resultado) = arrancar("v-1")
        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(0L, envios.single().ms)
        job.cancel()
    }

    @Test
    fun `E-APP-148 una visita sin fila de plazo no se retiene`() = runTest {
        guardar("v-1", conPlazo = false)
        val (job, resultado) = arrancar("v-1")
        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(0L, envios.single().ms)
        job.cancel()
    }

    @Test
    fun `E-APP-148 reloj de pared atrasado 1 h - no retiene 1 h`() = runTest {
        guardar("v-1")
        desfaseDeLaPared = -3_600_000
        val (job, resultado) = arrancar("v-1")
        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(0L, envios.single().ms)
        job.cancel()
    }

    @Test
    fun `E-APP-148 parada durante la espera no manda y el reinicio manda una vez`() = runTest {
        guardar("v-1")
        val (job, resultado) = arrancar("v-1")
        advanceTimeBy(10_000)
        job.cancel()
        runCurrent()
        assertEquals(null, resultado())
        assertTrue(envios.isEmpty())

        val (job2, resultado2) = arrancar("v-1")
        advanceTimeBy(20_100)
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), resultado2())
        assertEquals(1, envios.size)
        assertEquals(30_000L, envios.single().ms)
        job2.cancel()
    }

    private companion object {
        const val T0 = 1_793_383_200_000L
        const val BASE = 9_000_000_000_000L
        const val LAT = 20.5888
        const val LON = -100.3899
    }
}
