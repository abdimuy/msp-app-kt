package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.testing.RoomTestBase
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E-APP-101 (plan visitas P2, Tarea 16): el reconciliador periódico baja la
 * agenda de la zona DESPUÉS del reencolado de lo pendiente y antes de
 * reconciliar, y una bajada que falla no impide reconciliar.
 */
class VisitsReconcileWorkerBajadaTest : RoomTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun construir(
        eventos: MutableList<String>,
        bajar: suspend () -> Unit
    ): VisitsReconcileWorker = TestListenableWorkerBuilder<VisitsReconcileWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = VisitsReconcileWorker(
                appContext,
                workerParameters,
                pendientes = { listOf("p-1") },
                encolar = { eventos += "encola $it" },
                trigger = { eventos += "reconcilia" },
                refrescarCapacidades = {},
                rellenoActivo = { false },
                compromisosPorRellenar = { emptyList() },
                reportarEstado = {},
                bajarAgenda = bajar
            )
        })
        .build()

    @Test
    fun bajaLaAgendaDespuesDelReencoladoYAntesDeReconciliar_EAPP101() = runBlocking {
        val eventos = mutableListOf<String>()

        val r = construir(eventos) { eventos += "baja" }.doWork()

        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(listOf("encola p-1", "baja", "reconcilia"), eventos)
    }

    @Test
    fun unaBajadaQueFallaNoImpideReconciliar_EAPP101() = runBlocking {
        val eventos = mutableListOf<String>()

        val r = construir(eventos) { throw IOException("red caída") }.doWork()

        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(listOf("encola p-1", "reconcilia"), eventos)
    }
}
