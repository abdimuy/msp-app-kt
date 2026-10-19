package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * E-APP-097/E-APP-098 (plan visitas P2, Tarea 15; spec visitas §5.2.1,
 * §5.2.5): el reconciliador periódico refresca la capacidad (cada 6 h) y, si
 * el relleno está activo, encola por id cada visita con compromiso sin prueba.
 */
class VisitsReconcileWorkerRellenoTest : RoomTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Suppress("LongParameterList")
    private fun construir(
        eventos: MutableList<String>,
        rellenoActivo: () -> Boolean,
        refrescar: suspend () -> Unit = { eventos += "refresca" },
        compromisos: (suspend () -> List<String>)? = null,
        pendientes: suspend () -> List<String> = { emptyList() }
    ): VisitsReconcileWorker = TestListenableWorkerBuilder<VisitsReconcileWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = VisitsReconcileWorker(
                appContext,
                workerParameters,
                pendientes = pendientes,
                encolar = { eventos += "encola $it" },
                trigger = { eventos += "reconcilia" },
                refrescarCapacidades = refrescar,
                rellenoActivo = rellenoActivo,
                compromisosPorRellenar = compromisos ?: { db.visitDao().compromisosPorRellenar() },
                reportarEstado = {}
            )
        })
        .build()

    private suspend fun sembrar() {
        db.visitDao().insertVisit(pendingVisit("sin-compromiso", guardado = 1))
        db.visitDao().insertVisit(
            pendingVisit(
                "b-promesa",
                guardado = 1
            ).copy(FECHA = "2026-10-02T10:00:00Z", PROMESA_FECHA = "2026-10-20")
        )
        db.visitDao().insertVisit(
            pendingVisit(
                "a-cita",
                guardado = 1
            ).copy(FECHA = "2026-10-01T10:00:00Z", CITA_FECHA = "2026-10-21")
        )
        db.visitDao().insertVisit(
            pendingVisit("ya-probada", guardado = 1).copy(PROMESA_FECHA = "2026-10-20")
        )
        db.visitDao().confirmarCompromiso("ya-probada", "2026-10-18T10:00:00Z")
    }

    @Test
    fun `E-APP-098 con capacidad encola el relleno despues de refrescar y antes de reconciliar`() =
        runBlocking {
            sembrar()
            val eventos = mutableListOf<String>()
            val r = construir(eventos, rellenoActivo = { "refresca" in eventos }).doWork()

            assertEquals(ListenableWorker.Result.success(), r)
            assertEquals(
                listOf("refresca", "encola a-cita", "encola b-promesa", "reconcilia"),
                eventos
            )
        }

    @Test
    fun `E-APP-097 sin capacidad no encola relleno`() = runBlocking {
        sembrar()
        val eventos = mutableListOf<String>()
        construir(eventos, rellenoActivo = { false }).doWork()
        assertEquals(listOf("refresca", "reconcilia"), eventos)
    }

    @Test
    fun `si el refresco falla el relleno y la reconciliacion siguen`() = runBlocking {
        sembrar()
        val eventos = mutableListOf<String>()
        val r = construir(
            eventos,
            rellenoActivo = { true },
            refrescar = { error("sin red") }
        ).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(listOf("encola a-cita", "encola b-promesa", "reconcilia"), eventos)
    }

    @Test
    fun `si la lectura del relleno falla la reconciliacion sigue`() = runBlocking {
        val eventos = mutableListOf<String>()
        val r = construir(
            eventos,
            rellenoActivo = { true },
            compromisos = { throw IllegalStateException("base cerrada") }
        ).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        assertTrue("reconcilia" in eventos)
    }

    @Test
    fun `la cancelacion del refresco se propaga`() {
        val worker = construir(
            mutableListOf(),
            rellenoActivo = {
                fail("no debe seguir tras cancelar")
                false
            },
            refrescar = { throw CancellationException("detenido") }
        )
        try {
            runBlocking { worker.doWork() }
            fail("debió propagar la cancelación")
        } catch (_: CancellationException) {
            // esperado
        }
    }
}
