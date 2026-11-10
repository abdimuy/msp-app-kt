package com.example.msp_app.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ConciliacionInversa
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.ModoEnvioVisitas
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Plan visitas P1, Tarea 14 (H14): los dos reconciliadores emiten la
 * telemetría de visitas, y una telemetría que revienta no cambia su resultado.
 */
class VisitsWorkersTelemetriaTest : RoomTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun reconciliador(
        reportarEstado: suspend () -> Unit,
        trigger: suspend () -> Unit = {}
    ) = TestListenableWorkerBuilder<VisitsReconcileWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = VisitsReconcileWorker(
                appContext,
                workerParameters,
                pendientes = { emptyList() },
                encolar = {},
                trigger = trigger,
                refrescarCapacidades = {},
                reportarEstado = reportarEstado
            )
        })
        .build()

    private fun diario(
        conciliar: suspend () -> ConciliacionInversa,
        telemetria: (String, Map<String, Any>) -> Unit,
        modo: ModoEnvioVisitas = ModoEnvioVisitas.V3,
        encoladas: MutableList<String> = mutableListOf(),
        corridas: MutableList<Instant> = mutableListOf()
    ) = TestListenableWorkerBuilder<VisitsDailyReconcileWorker>(context)
        .setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker = VisitsDailyReconcileWorker(
                appContext,
                workerParameters,
                modo = { modo },
                // E-VIS-031: sin esto la prueba dependía del sabor (en devserver
                // VISITAS_USE_V2 = false y el worker no corre). Se fija el envío v2.
                useV2 = true,
                conciliar = conciliar,
                pendientes = { listOf("6f1c2a3b-0000-4000-8000-000000000001") },
                encolar = { encoladas += it },
                anotarCorrida = { corridas += it },
                clock = FakeClock(Instant.parse("2026-10-17T15:00:00Z")),
                telemetria = telemetria
            )
        })
        .build()

    @Test
    fun `H14 cada corrida del reconciliador emite el estado despues de reconciliar`() =
        runBlocking {
            val orden = mutableListOf<String>()
            val r = reconciliador(reportarEstado = {
                orden += "estado"
            }, trigger = { orden += "trigger" }).doWork()
            assertEquals(Result.success(), r)
            assertEquals(listOf("trigger", "estado"), orden)
        }

    @Test
    fun `H14 si el estado revienta el reconciliador sigue en success`() = runBlocking {
        assertEquals(Result.success(), reconciliador(reportarEstado = { error("boom") }).doWork())
    }

    @Test
    fun `H14 el estado se emite aunque el trigger reviente`() = runBlocking {
        var emitido = false
        val r = reconciliador(
            reportarEstado = { emitido = true },
            trigger = { error("boom") }
        ).doWork()
        assertEquals(Result.success(), r)
        assertEquals(true, emitido)
    }

    @Test
    fun `H14 la conciliacion diaria emite todos sus conteos`() = runBlocking {
        val capturado = mutableListOf<Pair<String, Map<String, Any>>>()
        val resultado = ConciliacionInversa(
            revisadas = 10,
            faltantes = 2,
            confirmadas = 7,
            tandasFallidas = 1,
            idsAjenos = 1
        )
        diario(conciliar = { resultado }, telemetria = { e, m -> capturado += e to m }).doWork()
        assertEquals(
            listOf(
                TelemetriaDeVisitas.EVENTO_CONCILIACION to TelemetriaDeVisitas.conciliacion(resultado)
            ),
            capturado
        )
        assertEquals(2, capturado.single().second["visitas.reconcilia.devuelta_a_pendiente"])
    }

    /**
     * Una telemetría que revienta no se lleva el reencolado de las devueltas
     * ni la marca de la corrida: van después de emitir en el mismo `try`.
     */
    @Test
    fun `H14 si la telemetria diaria revienta igual reencola y anota la corrida`() = runBlocking {
        val encoladas = mutableListOf<String>()
        val corridas = mutableListOf<Instant>()
        val r = diario(
            conciliar = {
                ConciliacionInversa(
                    revisadas = 2,
                    faltantes = 1,
                    confirmadas = 1,
                    tandasFallidas = 0
                )
            },
            telemetria = { _, _ -> error("boom") },
            encoladas = encoladas,
            corridas = corridas
        ).doWork()
        assertEquals(Result.success(), r)
        assertEquals(listOf("6f1c2a3b-0000-4000-8000-000000000001"), encoladas)
        assertEquals(1, corridas.size)
    }

    @Test
    fun `H14 en v2_actual la conciliacion no corre ni emite`() = runBlocking {
        val capturado = mutableListOf<String>()
        diario(
            conciliar = { error("no debe correr") },
            telemetria = { e, _ -> capturado += e },
            modo = ModoEnvioVisitas.V2_ACTUAL
        ).doWork()
        assertEquals(emptyList<String>(), capturado)
    }
}
