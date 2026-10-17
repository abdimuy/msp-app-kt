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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * E-APP-072 (plan visitas P1, Tarea 12; cierra H3, spec §4.2-6): el
 * reconciliador periódico y el de apertura reencolan TODAS las pendientes, sin
 * tope, en orden `FECHA ASC, ID ASC`, antes de reconciliar.
 */
class VisitsReconcileWorkerReencoladoTest : RoomTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun construir(
        pendientes: (suspend () -> List<String>)? = null,
        encolar: (String) -> Unit,
        trigger: suspend () -> Unit
    ): VisitsReconcileWorker = TestListenableWorkerBuilder<VisitsReconcileWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = VisitsReconcileWorker(
                appContext,
                workerParameters,
                pendientes = pendientes ?: { db.visitDao().pendientesOrdenadas() },
                encolar = encolar,
                trigger = trigger
            )
        })
        .build()

    @Test
    fun `E-APP-072 el reconciliador reencola las 60 pendientes en orden FECHA ID`() = runBlocking {
        // FECHA con repetidos (29 - it % 28): el desempate por ID también se prueba.
        val sembradas = (1..60).map {
            pendingVisit(
                id = "v%02d".format(it)
            ).copy(FECHA = "2026-10-%02dT10:00:00Z".format(29 - (it % 28)))
        }
        sembradas.forEach { db.visitDao().insertVisit(it) }
        // Una ya soltada no se reencola.
        db.visitDao().insertVisit(pendingVisit(id = "v00-confirmada", guardado = 1))

        val encoladas = mutableListOf<String>()
        val r = construir(encolar = { encoladas += it }, trigger = {}).doWork()

        val esperado = sembradas.sortedWith(compareBy({ it.FECHA }, { it.ID })).map { it.ID }
        assertEquals(ListenableWorker.Result.success(), r)
        assertEquals(60, encoladas.size)
        assertEquals(esperado, encoladas)
        // Control positivo: el orden esperado NO es el de inserción, así que la
        // igualdad de arriba no pasa por casualidad.
        assertNotEquals(sembradas.map { it.ID }, esperado)
    }

    @Test
    fun `el reencolado ocurre antes de reconciliar`() = runBlocking {
        db.visitDao().insertVisit(pendingVisit(id = "v1"))
        val eventos = mutableListOf<String>()
        construir(
            encolar = { eventos += "encola $it" },
            trigger = { eventos += "reconcilia" }
        ).doWork()
        assertEquals(listOf("encola v1", "reconcilia"), eventos)
    }

    @Test
    fun `si la lectura de pendientes falla el reconciliador sigue y devuelve success`() =
        runBlocking {
            var reconcilio = false
            val r = construir(
                pendientes = { throw IllegalStateException("base cerrada") },
                encolar = { fail("no hay qué encolar") },
                trigger = { reconcilio = true }
            ).doWork()
            assertEquals(ListenableWorker.Result.success(), r)
            assertTrue("el trigger corre aunque el reencolado falle", reconcilio)
        }

    @Test
    fun `si un encolado falla el reconciliador sigue y devuelve success`() = runBlocking {
        db.visitDao().insertVisit(pendingVisit(id = "v1"))
        var reconcilio = false
        val r = construir(encolar = {
            error("WorkManager no listo")
        }, trigger = { reconcilio = true }).doWork()
        assertEquals(ListenableWorker.Result.success(), r)
        assertTrue(reconcilio)
    }

    @Test
    fun `la cancelacion del reencolado se propaga`() {
        val worker = construir(
            pendientes = { throw CancellationException("detenido") },
            encolar = {},
            trigger = { fail("no debe reconciliar tras cancelar") }
        )
        try {
            runBlocking { worker.doWork() }
            fail("debió propagar la cancelación")
        } catch (_: CancellationException) {
            // esperado
        }
    }
}
