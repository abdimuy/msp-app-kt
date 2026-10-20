package com.example.msp_app.core.fcm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-130 · el trabajo único `sync_tareas_zona` que despierta el push. */
class SyncTareasWorkerTest : RobolectricTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun iniciarWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .setTaskExecutor(SynchronousExecutor())
                .build()
        )
    }

    @Test
    fun `payloadSyncEncolaUnSoloTrabajoUnico - E-APP-130`() {
        iniciarWorkManager()
        val d = DespachadorDePush(fcmActivo = { true }, encolar = { encolarSyncTareas(context) })

        d.alMensaje(mapOf("tipo" to "sync"))
        val primero = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncTareasWorker.NOMBRE_UNICO).get()
        d.alMensaje(mapOf("tipo" to "sync"))
        val segundo = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(SyncTareasWorker.NOMBRE_UNICO).get()

        assertEquals("sync_tareas_zona", SyncTareasWorker.NOMBRE_UNICO)
        assertEquals(1, primero.size)
        assertEquals(1, segundo.size)
        assertEquals("KEEP: sobrevive el primero", primero[0].id, segundo[0].id)
        assertTrue(
            segundo[0].state == WorkInfo.State.ENQUEUED || segundo[0].state == WorkInfo.State.RUNNING
        )
    }

    @Test
    fun `nuncaUsaDropWorkRequest - E-APP-130`() {
        val spec = solicitudDeSyncTareas().workSpec
        assertTrue(spec.expedited)
        assertEquals(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST, spec.outOfQuotaPolicy)
        assertNotEquals(OutOfQuotaPolicy.DROP_WORK_REQUEST, spec.outOfQuotaPolicy)
    }

    private fun construir(sincronizar: suspend () -> Unit, reprogramar: suspend () -> Unit) =
        TestListenableWorkerBuilder<SyncTareasWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker =
                    SyncTareasWorker(appContext, workerParameters, sincronizar, reprogramar)
            })
            .build()

    @Test
    fun `sincroniza y luego reprograma las alarmas - E-APP-130`() = runBlocking {
        val orden = mutableListOf<String>()
        val r = construir({ orden += "sync" }, { orden += "alarmas" }).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(listOf("sync", "alarmas"), orden)
    }

    @Test
    fun `siempre success aunque el sync o las alarmas revienten - E-APP-130`() = runBlocking {
        var alarmas = false
        val r = construir({ error("sin red") }, { alarmas = true }).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        assertTrue("las alarmas se reprograman aunque el sync falle", alarmas)
        val r2 = construir({}, { error("sin permiso") }).doWork()
        assertEquals(ListenableWorker.Result.success(), r2)
    }

    @Test
    fun `getForegroundInfo esta implementado para expedited en API menor a 31 - E-APP-130`() =
        runBlocking {
            val info = construir({}, {}).getForegroundInfo()
            assertTrue(info.notificationId != 0)
        }
}
