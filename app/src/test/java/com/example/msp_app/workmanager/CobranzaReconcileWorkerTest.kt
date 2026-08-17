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
import com.example.msp_app.core.sync.cobranza.ReconcileOutcome
import com.example.msp_app.workers.CobranzaReconcileWorker
import java.io.IOException
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
 * D1 — el reconciliador **nunca corrió en la flota**.
 *
 * Vivía en un bucle dentro de `CobranzaSyncObserver` con el `delay(5 min)`
 * **antes** de la primera vuelta, y el job moría en `ON_STOP`. Para producir una
 * sola corrida hacía falta que la app se quedara cinco minutos ininterrumpidos
 * en primer plano; el uso real de un cobrador son ráfagas de segundos. Con él
 * nunca corrió el rescate por `by-ids`, que es el único canal **sin watermark**
 * y por tanto la defensa contra un sync congelado del servidor.
 *
 * La señal en campo es `reconcileNow start zona=` en logcat **al abrir la app**,
 * no a los cinco minutos. Aquí eso se traduce a lo único que un test puede
 * observar: el trabajo corre **sin que ningún reloj avance**.
 *
 * `TestScheduler` de WorkManager modela las tres compuertas por separado
 * (`mConstraintsMet`, `mInitialDelayMet`, `mPeriodDelayMet`) y sólo ejecuta
 * cuando las tres están abiertas. `setAllConstraintsMet` abre la de la red y
 * **no** toca la del retraso inicial — por eso reintroducir el `delay` deja el
 * conteo en cero y pone estas pruebas en rojo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class CobranzaReconcileWorkerTest {

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
    fun `el reconciliador corre al encolarlo, sin esperar ningun retraso`() {
        enqueueCobranzaReconcileNowWorker(context)

        abrirLaCompuertaDeRed(COBRANZA_RECONCILE_NOW_WORK)

        // Ni un tick de reloj de por medio. Si alguien vuelve a poner el retraso
        // antes de la primera ejecución (un `setInitialDelay` en el request o el
        // viejo `delay(...)` al principio del bucle), el trabajo se queda en
        // ENQUEUED y esto da 0.
        assertEquals(
            "el reconciliador debe correr al abrir la app, no tras una espera",
            1,
            corridas.get()
        )
    }

    @Test
    fun `el trabajo periodico sostiene la cadencia de respaldo fuera del ciclo de vida`() {
        enqueueCobranzaReconcilePeriodicWorker(context)

        val info = trabajosDe(COBRANZA_RECONCILE_PERIODIC_WORK).single()

        // Encolado, no atado a que la pantalla siga viva: sobrevive al ON_STOP
        // que antes cancelaba el job del reconciliador.
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        val periodicidad = info.periodicityInfo
        assertNotNull("el trabajo de respaldo debe ser periódico", periodicidad)
        assertEquals(
            TimeUnit.MINUTES.toMillis(COBRANZA_RECONCILE_PERIOD_MINUTES),
            periodicidad!!.repeatIntervalMillis
        )
    }

    @Test
    fun `un fallo del reconciliador se reintenta en vez de perderse`() {
        val worker = TestListenableWorkerBuilder<CobranzaReconcileWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters
                    ): ListenableWorker = CobranzaReconcileWorker(
                        appContext,
                        workerParameters
                    ) { ReconcileOutcome.Error(IOException("sin red")) }
                }
            )
            .build()

        val resultado = runBlocking { worker.doWork() }

        // Soltar la corrida dejaría pagos sin rescatar hasta la próxima
        // apertura: un cobrador no vería un pago y volvería a cobrarlo.
        assertEquals(ListenableWorker.Result.retry(), resultado)
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    /**
     * Sustituye el reconciliador real (que abriría la base y la red) por un
     * contador. El worker de producción lo resuelve por su argumento por
     * defecto, así que la forma que WorkManager ve es idéntica.
     */
    private fun fabricaQueCuentaCorridas(): WorkerFactory = object : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? {
            if (workerClassName != CobranzaReconcileWorker::class.java.name) return null
            return CobranzaReconcileWorker(appContext, workerParameters) {
                corridas.incrementAndGet()
                ReconcileOutcome.Ok(0, 0, 0, 0)
            }
        }
    }

    private fun trabajosDe(nombre: String): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(nombre).get()

    /**
     * Abre sólo la compuerta de la constraint de red. La del retraso inicial es
     * otra (`setInitialDelayMet`) y **no** se toca a propósito: es justo la que
     * debe seguir cerrada si alguien reintroduce el defecto.
     */
    private fun abrirLaCompuertaDeRed(nombre: String) {
        val driver = WorkManagerTestInitHelper.getTestDriver(context) ?: return
        trabajosDe(nombre)
            .filterNot { it.state.isFinished }
            .forEach { runCatching { driver.setAllConstraintsMet(it.id) } }
    }
}
