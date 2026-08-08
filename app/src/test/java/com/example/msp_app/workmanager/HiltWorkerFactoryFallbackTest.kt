package com.example.msp_app.workmanager

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.workers.ClienteSyncWorker
import com.example.msp_app.workers.DbExportWorker
import com.example.msp_app.workers.PendingGuaranteeEventsWorker
import com.example.msp_app.workers.PendingGuaranteesWorker
import com.example.msp_app.workers.PendingLocalSalesWorker
import com.example.msp_app.workers.PendingPaymentsWorker
import com.example.msp_app.workers.PendingVisitsWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 6 (Plan 1 — cimiento): proves the exact invariant the brief is built
 * on — a REAL [HiltWorkerFactory] (the same final class WorkManager uses in
 * production, built here with an empty worker-bindings map to match the
 * current state where none of the 7 production Workers are `@HiltWorker`
 * yet) causes WorkManager to fall back to its default reflective
 * `(Context, WorkerParameters)` constructor, and the Worker still runs to
 * completion.
 *
 * [PlainConstructorProbeWorker] stands in for the 7 real Workers instead of
 * running one of them directly: those construct real repositories/network
 * clients from their constructor bodies (see [ClienteSyncWorker]), so
 * driving their actual `doWork()` here would hit the real network from a
 * unit test. The two are structurally identical from WorkManager's point of
 * view — a plain `(Context, WorkerParameters)` constructor, no
 * `@HiltWorker` — which is exactly the property under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class HiltWorkerFactoryFallbackTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        val config = Configuration.Builder()
            .setWorkerFactory(emptyHiltWorkerFactory())
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `the 7 existing Workers remain un-annotated so Hilt has nothing to resolve them`() {
        val realWorkers = listOf(
            ClienteSyncWorker::class.java,
            PendingGuaranteeEventsWorker::class.java,
            PendingGuaranteesWorker::class.java,
            PendingPaymentsWorker::class.java,
            PendingVisitsWorker::class.java,
            PendingLocalSalesWorker::class.java,
            DbExportWorker::class.java
        )

        realWorkers.forEach { workerClass ->
            assertTrue(
                "$workerClass no debe convertirse a @HiltWorker en esta tarea " +
                    "(la conversión per-worker queda para planes siguientes)",
                workerClass.annotations.none {
                    it.annotationClass.qualifiedName == "androidx.hilt.work.HiltWorker"
                }
            )
        }
    }

    @Test
    fun `WorkManager falls back to the default constructor and the Worker completes`() {
        val request = OneTimeWorkRequestBuilder<PlainConstructorProbeWorker>().build()
        val workManager = WorkManager.getInstance(context)

        workManager.enqueue(request).result.get()

        val info = workManager.getWorkInfoById(request.id).get()
            ?: error("no se encontró WorkInfo para el probe worker recién encolado")
        assertEquals(WorkInfo.State.SUCCEEDED, info.state)
    }

    /**
     * Builds the real `HiltWorkerFactory` used in production, without
     * standing up the full Dagger/Hilt component graph (this module has no
     * Hilt test infrastructure yet). Its constructor takes the multibinding
     * map of `@HiltWorker` factories and is package-private by design; an
     * empty map faithfully mirrors today's reality (0 Workers converted).
     */
    private fun emptyHiltWorkerFactory(): HiltWorkerFactory {
        val ctor = HiltWorkerFactory::class.java.getDeclaredConstructor(Map::class.java)
        ctor.isAccessible = true
        val emptyBindings: Map<String, Any> = emptyMap()
        return ctor.newInstance(emptyBindings)
    }
}

/** Same shape as the 7 real Workers: plain constructor, no `@HiltWorker`. */
class PlainConstructorProbeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result = Result.success()
}
