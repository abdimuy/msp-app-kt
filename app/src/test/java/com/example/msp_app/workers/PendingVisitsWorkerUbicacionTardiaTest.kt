package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.data.local.datasource.visit.VisitsLocalDataSource
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * E-APP-129, la carrera: la visita viaja con el 0,0 de la fila que leyó el
 * worker; si en Room ya hay ubicación cuando el servidor contesta, el PATCH la
 * lleva. Aquí se fija solo el disparo: una visita que salió en 0,0 pide la
 * subida tardía; una que salió con ubicación, no.
 */
class PendingVisitsWorkerUbicacionTardiaTest : RoomTestBase() {

    private lateinit var context: Context
    private val pedidas = mutableListOf<String>()

    @Before
    fun setUpContext() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun correr(lat: Double, lng: Double, encolar: (String) -> Unit = { pedidas += it }) {
        runBlocking {
            VisitsLocalDataSource(context).saveVisit(pendingVisit().copy(LAT = lat, LNG = lng))
        }
        val worker = TestListenableWorkerBuilder<PendingVisitsWorker>(
            context,
            Data.Builder().putString("visit_id", "visita-001").build()
        ).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = PendingVisitsWorker(
                appContext = appContext,
                workerParams = workerParameters,
                visitsStore = VisitsLocalDataSource(appContext),
                v2Api = happyApi(),
                legacyApi = throwingLegacyApi(),
                useV2 = true,
                imagenes = db.visitImageDao(),
                clock = FakeClock(Instant.parse("2026-10-27T18:00:00Z")),
                modo = { ModoEnvioVisitas.V3 },
                visitDao = db.visitDao(),
                encolarUbicacion = encolar
            )
        }).build() as PendingVisitsWorker
        runBlocking { worker.doWork() }
    }

    @Test
    fun `una visita que sale en 0,0 pide la subida de la ubicacion tardia`() = runTest {
        correr(0.0, 0.0)
        assertEquals(listOf("visita-001"), pedidas)
    }

    @Test
    fun `una visita que sale con ubicacion no pide nada`() = runTest {
        correr(18.4617, -97.3928)
        assertEquals(emptyList<String>(), pedidas)
    }

    @Test
    fun `si encolar lanza el envio de la visita sigue igual`() = runTest {
        correr(0.0, 0.0) { throw IllegalStateException("WorkManager no inicializado") }
        assertEquals(1, db.visitDao().findVisitById("visita-001")!!.GUARDADO_EN_MICROSIP)
    }
}
