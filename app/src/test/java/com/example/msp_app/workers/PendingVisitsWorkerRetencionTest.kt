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
import com.example.msp_app.core.database.dao.ubicacion.PlazoDeEnvioDao
import com.example.msp_app.core.database.entities.PlazoDeEnvioEntity
import com.example.msp_app.core.database.entities.UbicacionDeCapturaEntity
import com.example.msp_app.core.diagnosticoenvio.EnvioDiagnostico
import com.example.msp_app.core.network.diagnostico.EnvioDiagnosticoSink
import com.example.msp_app.core.network.diagnostico.EnvioObservado
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.api.services.visits.CrearVisitaBody
import com.example.msp_app.data.api.services.visits.VisitaDTO
import com.example.msp_app.data.api.services.visits.VisitsApi
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import com.example.msp_app.data.models.visit.Visit
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
import org.junit.Assert.assertNotNull
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

    private fun TestScope.worker(
        id: String,
        modo: ModoEnvioVisitas,
        useV2: Boolean = true,
        plazos: PlazoDeEnvioDao = db.plazoDeEnvioDao(),
        diagnostico: EnvioDiagnosticoSink = EnvioDiagnostico.sink,
        encolarUbicacion: ((String) -> Unit)? = null
    ): PendingVisitsWorker {
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
                legacyApi = object : VisitsApi {
                    override suspend fun saveVisit(visit: Visit) {
                        envios += Envio(prueba.currentTime, visit.ID, visit.LAT, visit.LNG)
                    }
                },
                useV2 = useV2,
                imagenes = db.visitImageDao(),
                clock = pared,
                diagnostico = diagnostico,
                modo = { modo },
                visitDao = db.visitDao(),
                encolarUbicacion = encolarUbicacion ?: { },
                plazos = plazos,
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
        modo: ModoEnvioVisitas = ModoEnvioVisitas.V3,
        useV2: Boolean = true,
        plazos: PlazoDeEnvioDao = db.plazoDeEnvioDao(),
        diagnostico: EnvioDiagnosticoSink = EnvioDiagnostico.sink,
        encolarUbicacion: ((String) -> Unit)? = null
    ): Pair<Job, () -> ListenableWorker.Result?> {
        var resultado: ListenableWorker.Result? = null
        val w = worker(id, modo, useV2, plazos, diagnostico, encolarUbicacion)
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

    // ─── La visita se entrega por otro camino MIENTRAS el worker espera la ubicacion ──────────────────────────────

    private enum class OtroCamino { CONFIRMADA, RESGUARDADA, SOLO_GUARDADO }

    /** Lo que hace otro camino (reconciliador, otro worker del lote) con la visita. */
    private suspend fun entregarPorOtroCamino(id: String, camino: OtroCamino) {
        when (camino) {
            OtroCamino.CONFIRMADA -> db.visitDao().confirmar(listOf(id), "2026-10-30T18:00:00Z")
            OtroCamino.RESGUARDADA -> db.visitDao().resguardar(id, "2026-10-30T18:00:00Z")
            OtroCamino.SOLO_GUARDADO -> db.visitDao().markSyncedByIds(listOf(id))
        }
    }

    /** Arranca, retiene 5 s, entrega por otro camino (con o sin ubicacion), deja correr 40 s; devuelve los POST. */
    private suspend fun TestScope.entregadaDurante(
        modo: ModoEnvioVisitas,
        useV2: Boolean,
        camino: OtroCamino,
        conUbicacion: Boolean
    ): List<Envio> {
        guardar("v-1")
        val (job, resultado) = arrancar("v-1", modo, useV2)
        advanceTimeBy(5_000)
        assertTrue("retenida antes de la entrega ajena", envios.isEmpty())
        if (conUbicacion) db.visitDao().updateLocationIfEmpty("v-1", LAT, LON)
        entregarPorOtroCamino("v-1", camino)
        advanceTimeBy(40_000)
        runCurrent()
        assertEquals(ListenableWorker.Result.success(), resultado())
        job.cancel()
        return envios.toList()
    }

    // v3: la guarda de `enviarV3` (E-APP-156) ya suelta lo confirmado o resguardado; la espera relee la fila.

    @Test
    fun `E-APP-156 v3 confirmada durante la espera sin llegar la ubicacion - cero POST`() =
        runTest {
            assertEquals(
                0,
                entregadaDurante(ModoEnvioVisitas.V3, true, OtroCamino.CONFIRMADA, false).size
            )
        }

    @Test
    fun `E-APP-156 v3 confirmada durante la espera con ubicacion - cero POST`() = runTest {
        assertEquals(
            0,
            entregadaDurante(ModoEnvioVisitas.V3, true, OtroCamino.CONFIRMADA, true).size
        )
    }

    @Test
    fun `E-APP-156 v3 resguardada durante la espera sin llegar la ubicacion - cero POST`() =
        runTest {
            assertEquals(
                0,
                entregadaDurante(ModoEnvioVisitas.V3, true, OtroCamino.RESGUARDADA, false).size
            )
        }

    @Test
    fun `E-APP-156 v3 resguardada durante la espera con ubicacion - cero POST`() = runTest {
        assertEquals(
            0,
            entregadaDurante(ModoEnvioVisitas.V3, true, OtroCamino.RESGUARDADA, true).size
        )
    }

    @Test
    fun `E-APP-156 lote de dos cuentas v3 - la entregada por otro camino no sale y la otra si`() =
        runTest {
            guardar("v-1")
            guardar("v-2")
            val (job1, r1) = arrancar("v-1")
            val (job2, r2) = arrancar("v-2")
            advanceTimeBy(5_000)
            db.visitDao().confirmar(listOf("v-1"), "2026-10-30T18:00:00Z")
            db.visitDao().updateLocationIfEmpty("v-2", LAT, LON)
            advanceTimeBy(40_000)
            runCurrent()

            assertEquals(ListenableWorker.Result.success(), r1())
            assertEquals(ListenableWorker.Result.success(), r2())
            assertEquals(listOf("v-2"), envios.map { it.id })
            job1.cancel()
            job2.cancel()
        }

    /**
     * El matiz de E-APP-156: en v3 `GUARDADO = 1` SIN `CONFIRMADA_EN` ni `RESGUARDADA_EN` no es prueba
     * del servidor (la prueba es la marca), asi que v3 manda UNA vez y estampa `CONFIRMADA_EN`. Es la
     * conducta de la base y el envio blindado la exige: no se suelta una visita sin la prueba.
     */
    @Test
    fun `E-APP-156 v3 marcada solo como guardada durante la espera - un POST y se estampa CONFIRMADA_EN`() =
        runTest {
            val posts = entregadaDurante(ModoEnvioVisitas.V3, true, OtroCamino.SOLO_GUARDADO, false)

            assertEquals(1, posts.size)
            val fila = db.visitDao().findVisitById("v-1")!!
            assertEquals(1, fila.GUARDADO_EN_MICROSIP)
            assertNotNull("la prueba del servidor quedo estampada", fila.CONFIRMADA_EN)
        }

    // ─── E-APP-154: v2 actual y legado no reenvian una visita con GUARDADO_EN_MICROSIP != 0 ───────────────────────

    @Test
    fun `E-APP-154 v2 actual confirmada durante la espera sin llegar la ubicacion - cero POST`() =
        runTest {
            assertEquals(
                0,
                entregadaDurante(
                    ModoEnvioVisitas.V2_ACTUAL,
                    true,
                    OtroCamino.CONFIRMADA,
                    false
                ).size
            )
        }

    @Test
    fun `E-APP-154 v2 actual confirmada durante la espera con ubicacion - cero POST`() = runTest {
        assertEquals(
            0,
            entregadaDurante(ModoEnvioVisitas.V2_ACTUAL, true, OtroCamino.CONFIRMADA, true).size
        )
    }

    @Test
    fun `E-APP-154 v2 actual resguardada durante la espera - cero POST`() = runTest {
        assertEquals(
            0,
            entregadaDurante(ModoEnvioVisitas.V2_ACTUAL, true, OtroCamino.RESGUARDADA, false).size
        )
    }

    @Test
    fun `E-APP-154 v2 actual marcada solo como guardada durante la espera - cero POST`() = runTest {
        assertEquals(
            0,
            entregadaDurante(ModoEnvioVisitas.V2_ACTUAL, true, OtroCamino.SOLO_GUARDADO, false).size
        )
    }

    @Test
    fun `E-APP-154 legado marcada solo como guardada durante la espera - cero POST`() = runTest {
        assertEquals(
            0,
            entregadaDurante(ModoEnvioVisitas.V3, false, OtroCamino.SOLO_GUARDADO, false).size
        )
    }

    @Test
    fun `E-APP-154 legado confirmada durante la espera - cero POST`() = runTest {
        assertEquals(
            0,
            entregadaDurante(ModoEnvioVisitas.V3, false, OtroCamino.CONFIRMADA, false).size
        )
    }

    @Test
    fun `E-APP-154 lote de dos cuentas v2 actual - la entregada por otro camino no sale y la otra si`() =
        runTest {
            guardar("v-1")
            guardar("v-2")
            val (job1, r1) = arrancar("v-1", ModoEnvioVisitas.V2_ACTUAL)
            val (job2, r2) = arrancar("v-2", ModoEnvioVisitas.V2_ACTUAL)
            advanceTimeBy(5_000)
            db.visitDao().markSyncedByIds(listOf("v-1"))
            db.visitDao().updateLocationIfEmpty("v-2", LAT, LON)
            advanceTimeBy(40_000)
            runCurrent()

            assertEquals(ListenableWorker.Result.success(), r1())
            assertEquals(ListenableWorker.Result.success(), r2())
            assertEquals(listOf("v-2"), envios.map { it.id })
            job1.cancel()
            job2.cancel()
        }

    /** Casos G y H de la compuerta: la visita YA estaba entregada antes de que el worker arrancara. */
    @Test
    fun `E-APP-154 v2 actual ya guardada antes de arrancar con ubicacion - cero POST`() = runTest {
        guardar("v-1", lat = LAT, lng = LON)
        db.visitDao().markSyncedByIds(listOf("v-1"))
        val (job, resultado) = arrancar("v-1", ModoEnvioVisitas.V2_ACTUAL)
        advanceTimeBy(40_000)
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertTrue("ya estaba entregada: no se manda otra vez", envios.isEmpty())
        job.cancel()
    }

    @Test
    fun `E-APP-154 v2 actual ya guardada antes de arrancar en 0,0 - cero POST`() = runTest {
        guardar("v-1")
        db.visitDao().markSyncedByIds(listOf("v-1"))
        val (job, resultado) = arrancar("v-1", ModoEnvioVisitas.V2_ACTUAL)
        advanceTimeBy(40_000)
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertTrue("ya estaba entregada: no se manda otra vez", envios.isEmpty())
        job.cancel()
    }

    @Test
    fun `E-APP-154 legado ya guardada antes de arrancar - cero POST`() = runTest {
        guardar("v-1", lat = LAT, lng = LON)
        db.visitDao().markSyncedByIds(listOf("v-1"))
        val (job, resultado) = arrancar("v-1", ModoEnvioVisitas.V3, useV2 = false)
        advanceTimeBy(40_000)
        runCurrent()

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertTrue(envios.isEmpty())
        job.cancel()
    }

    /** Mismo caso, y la salida anota su motivo como la guarda de `enviarV3` (E-APP-065): una vez, sin fallo. */
    @Test
    fun `E-APP-154 v2 actual ya guardada - la salida anota una vez sin fallo`() = runTest {
        guardar("v-1", lat = LAT, lng = LON)
        db.visitDao().markSyncedByIds(listOf("v-1"))
        val anotadas = mutableListOf<EnvioObservado>()
        val sink = EnvioDiagnosticoSink { anotadas += it }
        val (job, resultado) = arrancar("v-1", ModoEnvioVisitas.V2_ACTUAL, diagnostico = sink)

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertTrue("no manda", envios.isEmpty())
        assertEquals(1, anotadas.size)
        assertEquals("v-1", anotadas.single().filaId)
        assertEquals(null, anotadas.single().fallo)
        job.cancel()
    }

    // Controles: lo que SI debe salir sigue saliendo, exactamente una vez.

    @Test
    fun `E-APP-154 control v2 actual pendiente normal sale una vez y queda guardada`() = runTest {
        guardar("v-1", lat = LAT, lng = LON)
        val (job, resultado) = arrancar("v-1", ModoEnvioVisitas.V2_ACTUAL)

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(1, envios.size)
        assertEquals(1, db.visitDao().findVisitById("v-1")!!.GUARDADO_EN_MICROSIP)
        job.cancel()
    }

    @Test
    fun `E-APP-154 control legado pendiente normal sale una vez y queda guardada`() = runTest {
        guardar("v-1", lat = LAT, lng = LON)
        val (job, resultado) = arrancar("v-1", ModoEnvioVisitas.V3, useV2 = false)

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(1, envios.size)
        assertEquals(1, db.visitDao().findVisitById("v-1")!!.GUARDADO_EN_MICROSIP)
        job.cancel()
    }

    /**
     * El reconciliador inverso devuelve una visita que el servidor no tiene (`devolverAPendiente`:
     * `GUARDADO = 0`, marcas en nulo): el worker la reenvia. Es el unico camino que reabre una entregada.
     */
    @Test
    fun `E-APP-154 control v2 actual devuelta a pendiente por el reconciliador sale una vez`() =
        runTest {
            guardar("v-1", lat = LAT, lng = LON)
            db.visitDao().confirmar(listOf("v-1"), "2026-10-30T18:00:00Z")
            db.visitDao().devolverAPendiente(listOf("v-1"))
            val (job, resultado) = arrancar("v-1", ModoEnvioVisitas.V2_ACTUAL)

            assertEquals(ListenableWorker.Result.success(), resultado())
            assertEquals(1, envios.size)
            job.cancel()
        }

    /** La ubicacion tardia de una visita ya enviada no es del worker de envio: la lleva su PATCH propio. */
    @Test
    fun `E-APP-154 control v2 actual sale en 0,0 y encola la ubicacion tardia una vez`() = runTest {
        guardar("v-1", conPlazo = false)
        val encoladas = mutableListOf<String>()
        val (job, resultado) = arrancar(
            "v-1",
            ModoEnvioVisitas.V2_ACTUAL,
            encolarUbicacion = { encoladas += it }
        )

        assertEquals(ListenableWorker.Result.success(), resultado())
        assertEquals(1, envios.size)
        assertEquals(listOf("v-1"), encoladas)
        job.cancel()
    }

    // ─── E-APP-155 (compuerta 2026-11-06, H-1): leer el plazo falla ───────────────────────────────────────────────

    private fun plazosQueFallan(error: Throwable) = object : PlazoDeEnvioDao {
        override suspend fun anotar(fila: PlazoDeEnvioEntity): Long = error("no se escribe aqui")
        override suspend fun obtener(id: String): PlazoDeEnvioEntity? = throw error
    }

    @Test
    fun `E-APP-155 si leer el plazo falla - sale exactamente un POST sin retener y la visita no se pierde`() =
        runTest {
            guardar("v-1", conPlazo = false)
            val (job, resultado) = arrancar(
                "v-1",
                plazos = plazosQueFallan(IllegalStateException("room roto"))
            )

            assertEquals(ListenableWorker.Result.success(), resultado())
            assertEquals(1, envios.size)
            assertEquals("sin retencion", 0L, envios.single().ms)
            assertEquals(0L, currentTime)
            val fila = db.visitDao().findVisitById("v-1")!!
            assertEquals("la visita sigue en Room", "v-1", fila.ID)
            assertEquals("quedo entregada, no perdida", 1, fila.GUARDADO_EN_MICROSIP)
            job.cancel()
        }

    @Test
    fun `E-APP-155 si leer el plazo falla - en v2 actual tambien sale un solo POST sin retener`() =
        runTest {
            guardar("v-1", conPlazo = false)
            val (job, resultado) = arrancar(
                "v-1",
                ModoEnvioVisitas.V2_ACTUAL,
                plazos = plazosQueFallan(IllegalStateException("room roto"))
            )
            assertEquals(ListenableWorker.Result.success(), resultado())
            assertEquals(1, envios.size)
            assertEquals(0L, envios.single().ms)
            job.cancel()
        }

    @Test
    fun `E-APP-155 si leer el plazo se cancela - la cancelacion se propaga y no se manda nada`() =
        runTest {
            guardar("v-1", conPlazo = false)
            val w = worker(
                "v-1",
                ModoEnvioVisitas.V3,
                plazos = plazosQueFallan(kotlinx.coroutines.CancellationException("parada"))
            )
            var resultado: ListenableWorker.Result? = null
            val job = launch { resultado = w.doWork() }
            runCurrent()

            assertTrue("la corrutina termino cancelada", job.isCancelled)
            assertEquals(null, resultado)
            assertTrue("una cancelacion al leer el plazo no manda", envios.isEmpty())
        }

    private companion object {
        const val T0 = 1_793_383_200_000L
        const val BASE = 9_000_000_000_000L
        const val LAT = 20.5888
        const val LON = -100.3899
    }
}
