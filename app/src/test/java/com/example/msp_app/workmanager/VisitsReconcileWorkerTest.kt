package com.example.msp_app.workmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.workers.VisitsReconcileWorker
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 11 — proves the two `WorkManager`-driven visitas triggers (app open,
 * periodic) actually fire, mirroring [CobranzaReconcileWorkerTest]'s
 * technique: a real [WorkManagerTestInitHelper] test instance, `KEEP` opened
 * only for the network constraint (never the initial delay, since the app-open
 * trigger has none), and a [WorkerFactory] that substitutes the trigger lambda
 * for a counter so the test never touches Hilt, Room or the network.
 *
 * What is NOT tested here — deliberately — is the "no double firing" bar: that
 * is proven once, at the real choke point, in
 * `TriggerVisitsReconciliationUseCaseTest` (`:core:common`). Every real path
 * into that guard (this worker included) resolves the SAME process-wide
 * instance via [com.example.msp_app.core.sync.pendingwork.data.visits
 * .VisitsReconcileTriggerProvider] — proving the guard itself twice would not
 * add coverage, only duplicate the same mutex test with extra ceremony.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class VisitsReconcileWorkerTest {

    private lateinit var context: Context
    private val corridas = AtomicInteger(0)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        corridas.set(0)

        val config = Configuration.Builder()
            .setWorkerFactory(fabricaQueCuentaCorridas())
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `el disparador de apertura corre al encolarlo, sin esperar ningun retraso`() {
        enqueueVisitsReconcileNowWorker(context)

        abrirLaCompuertaDeRed(VISITS_RECONCILE_NOW_WORK)

        assertEquals(
            "el reconciliador de visitas debe correr al abrir la app, no tras una espera",
            1,
            corridas.get()
        )
    }

    @Test
    fun `el trabajo periodico sostiene la cadencia de respaldo cada 15 minutos`() {
        enqueueVisitsReconcilePeriodicWorker(context)

        val info = trabajosDe(VISITS_RECONCILE_PERIODIC_WORK).single()

        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        val periodicidad = info.periodicityInfo
        assertNotNull("el trabajo de respaldo debe ser periodico", periodicidad)
        assertEquals(
            TimeUnit.MINUTES.toMillis(VISITS_RECONCILE_PERIOD_MINUTES),
            periodicidad!!.repeatIntervalMillis
        )
        // Mismo valor que cobranza (15 min) — es el piso de WorkManager, no
        // una eleccion de negocio distinta.
        assertEquals(COBRANZA_RECONCILE_PERIOD_MINUTES, VISITS_RECONCILE_PERIOD_MINUTES)
    }

    @Test
    fun `un disparador que lanza no revienta el worker, y el resultado sigue siendo success`() {
        val worker = TestListenableWorkerBuilder<VisitsReconcileWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker = VisitsReconcileWorker(
                        appContext,
                        workerParameters
                    ) { throw IllegalStateException("grafo de Hilt no listo") }
                }
            )
            .build()

        val resultado = runBlocking { worker.doWork() }

        // No Result.retry(): TriggerVisitsReconciliationUseCase.execute() ya
        // absorbe y reporta todo lo esperable (ver su KDoc); pedir tambien un
        // retry de WorkManager solo competiria con el proximo disparo natural
        // (el periodico, a lo sumo 15 min despues) sin ganar nada.
        assertEquals(ListenableWorker.Result.success(), resultado)
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    private fun fabricaQueCuentaCorridas(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? {
            if (workerClassName != VisitsReconcileWorker::class.java.name) return null
            return VisitsReconcileWorker(appContext, workerParameters) {
                corridas.incrementAndGet()
            }
        }
    }

    private fun trabajosDe(nombre: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(nombre).get()

    private fun abrirLaCompuertaDeRed(nombre: String) {
        val driver = WorkManagerTestInitHelper.getTestDriver(context) ?: return
        trabajosDe(nombre)
            .filterNot { it.state.isFinished }
            .forEach { runCatching { driver.setAllConstraintsMet(it.id) } }
    }
}
