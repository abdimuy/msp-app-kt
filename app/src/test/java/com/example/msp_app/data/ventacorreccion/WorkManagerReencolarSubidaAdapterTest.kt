package com.example.msp_app.data.ventacorreccion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.msp_app.workmanager.enqueuePendingLocalSalesWorker
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val SALE_ID = "sale-reencolar-adapter-001"
private const val EMAIL = "cobrador.pruebas@muebleriamsp.mx"

/**
 * Minor #4 de la ronda 1 de arreglo de Task 3: `WorkManagerReencolarSubidaAdapter.cancelarTrabajoEncolado`
 * y `enqueuePendingLocalSalesWorker` compartían el literal `"sync_pending_local_sale_$id"` en dos
 * archivos — renombrar uno sin el otro dejaba de cancelar SIN que nada fallara. Esta prueba
 * verifica que de verdad apuntan al MISMO trabajo: lo que uno encola, el otro lo cancela.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE, application = android.app.Application::class)
class WorkManagerReencolarSubidaAdapterTest {

    private lateinit var context: Context
    private lateinit var adapter: WorkManagerReencolarSubidaAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setTaskExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        adapter = WorkManagerReencolarSubidaAdapter(context)
    }

    @Test
    fun `cancelarTrabajoEncolado cancela EXACTAMENTE el trabajo que enqueuePendingLocalSalesWorker encolo`() {
        enqueuePendingLocalSalesWorker(context, SALE_ID, EMAIL)
        val antes = estadoDelTrabajo()
        assertEquals(WorkInfo.State.ENQUEUED, antes)

        adapter.cancelarTrabajoEncolado(SALE_ID)

        val despues = estadoDelTrabajo()
        assertEquals(
            "si los nombres no coincidieran, cancelar no tocaria este trabajo y seguiria ENQUEUED",
            WorkInfo.State.CANCELLED,
            despues
        )
    }

    private fun estadoDelTrabajo(): WorkInfo.State? = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork("sync_pending_local_sale_$SALE_ID")
        .get()
        .firstOrNull()
        ?.state
}
