package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.models.VisitReconcileResult
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.TriggerVisitsReconciliationUseCase.Companion.ERROR_CODE_DISPARO_FALLIDO
import com.example.msp_app.core.common.sync.pendingwork.fakes.RecordingSyncErrorReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bar Task 11 sets for this coordinator, verbatim from the brief: **each
 * trigger fires, and no trigger fires twice for the same event.** The second
 * half is proven here at the one place all three real triggers (app open,
 * periodic, connectivity) are wired to share — see
 * `VisitsReconcileTriggerProvider` in `:app` for the production singleton
 * that makes these scenarios real rather than hypothetical.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TriggerVisitsReconciliationUseCaseTest {

    /** Records every call and lets a test hold one open until it says so. */
    private class RecordingReconciler(
        private val result: VisitReconcileResult = VisitReconcileResult.NothingPending,
        private val gate: CompletableDeferred<Unit>? = null,
        private val failWith: Throwable? = null
    ) {
        var callCount: Int = 0
            private set

        suspend fun invoke(): VisitReconcileResult {
            callCount++
            gate?.await()
            // Solo la PRIMERA llamada falla — modela una falla transitoria de
            // resolucion (p. ej. el grafo de Hilt) y deja probar que el guard
            // se suelta igual y un disparo posterior corre de verdad.
            if (callCount == 1) failWith?.let { throw it }
            return result
        }
    }

    // -------------------------------------------------------------- un disparo

    @Test
    fun `un disparo solo ejecuta la reconciliacion y devuelve su resultado`() = runTest {
        val expected = VisitReconcileResult.Reconciled(
            pendingCount = 3,
            requestCount = 1,
            confirmedCount = 3,
            failedRequestCount = 0
        )
        val reconciler = RecordingReconciler(result = expected)
        val useCase = TriggerVisitsReconciliationUseCase(
            reconcileVisits = { reconciler.invoke() },
            errorReporter = RecordingSyncErrorReporter()
        )

        val result = useCase.execute()

        assertEquals(expected, result)
        assertEquals(1, reconciler.callCount)
    }

    // --------------------------------------------------- disparos que se solapan

    @Test
    fun `abrir la app justo cuando vuelve la conectividad solo corre UNA reconciliacion`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val reconciler = RecordingReconciler(gate = gate)
            val useCase = TriggerVisitsReconciliationUseCase(
                reconcileVisits = { reconciler.invoke() },
                errorReporter = RecordingSyncErrorReporter()
            )

            // "Abrir la app": arranca en su propia corrutina y queda colgado en
            // el gate — modela la corrida en curso.
            val appOpenRun = launch { useCase.execute() }
            runCurrent()

            // "Vuelve la conectividad": dispara en el mismo instante, sobre el
            // MISMO coordinador — es lo que la producción garantiza al usar un
            // único singleton de proceso.
            val connectivityRun = useCase.execute()

            assertEquals(
                "el disparo perdedor no debe haber corrido la reconciliacion",
                1,
                reconciler.callCount
            )
            assertNull("el disparo perdedor se salta, no espera su turno", connectivityRun)

            gate.complete(Unit)
            appOpenRun.join()
            assertEquals(1, reconciler.callCount)
        }

    @Test
    fun `un tick periodico que llega con una corrida en vuelo se salta`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val reconciler = RecordingReconciler(gate = gate)
        val useCase = TriggerVisitsReconciliationUseCase(
            reconcileVisits = { reconciler.invoke() },
            errorReporter = RecordingSyncErrorReporter()
        )

        val inFlightRun = launch { useCase.execute() }
        runCurrent()

        val periodicTick = useCase.execute()

        assertEquals(1, reconciler.callCount)
        assertNull(periodicTick)

        gate.complete(Unit)
        inFlightRun.join()
    }

    @Test
    fun `tres disparos a la vez solo dejan pasar UNO`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val reconciler = RecordingReconciler(gate = gate)
        val useCase = TriggerVisitsReconciliationUseCase(
            reconcileVisits = { reconciler.invoke() },
            errorReporter = RecordingSyncErrorReporter()
        )

        val winner = launch { useCase.execute() }
        runCurrent()

        val loserA = useCase.execute()
        val loserB = useCase.execute()

        assertEquals(1, reconciler.callCount)
        assertNull(loserA)
        assertNull(loserB)

        gate.complete(Unit)
        winner.join()
    }

    // ------------------------------------------------ no es un cooldown permanente

    @Test
    fun `una vez liberado el guard, el siguiente disparo SI corre`() = runTest {
        val reconciler = RecordingReconciler(result = VisitReconcileResult.NothingPending)
        val useCase = TriggerVisitsReconciliationUseCase(
            reconcileVisits = { reconciler.invoke() },
            errorReporter = RecordingSyncErrorReporter()
        )

        val first = useCase.execute()
        val second = useCase.execute()

        assertEquals(VisitReconcileResult.NothingPending, first)
        assertEquals(VisitReconcileResult.NothingPending, second)
        assertEquals(
            "el guard no es un cooldown: dos disparos NO solapados deben correr los dos",
            2,
            reconciler.callCount
        )
    }

    // --------------------------------------------------------------- error norm

    @Test
    fun `si resolver o correr la reconciliacion lanza, se reporta con codigo nombrado y no se pierde el guard`() =
        runTest {
            val boom = IllegalStateException("grafo de Hilt no listo")
            val reconciler = RecordingReconciler(failWith = boom)
            val reporter = RecordingSyncErrorReporter()
            val useCase = TriggerVisitsReconciliationUseCase(
                reconcileVisits = { reconciler.invoke() },
                errorReporter = reporter
            )

            val result = useCase.execute()

            assertNull(result)
            val reported = reporter.reported.single()
            assertEquals(ERROR_CODE_DISPARO_FALLIDO, reported.code)
            // Nombre de clase + contexto estatico, nunca el mensaje crudo de la
            // excepcion (podria arrastrar datos de negocio a telemetria).
            assertEquals(
                "TriggerVisitsReconciliationUseCase.execute: IllegalStateException",
                reported.message
            )

            // El `finally` debe soltar el mutex aunque el bloque haya lanzado:
            // un disparo posterior tiene que poder correr.
            val next = useCase.execute()
            assertEquals(VisitReconcileResult.NothingPending, next)
            assertEquals(2, reconciler.callCount)
        }

    @Test(expected = CancellationException::class)
    fun `la cancelacion se propaga y NO se reporta como error`() = runTest {
        val reconciler = RecordingReconciler(failWith = CancellationException("scope cancelado"))
        val reporter = RecordingSyncErrorReporter()
        val useCase = TriggerVisitsReconciliationUseCase(
            reconcileVisits = { reconciler.invoke() },
            errorReporter = reporter
        )

        try {
            useCase.execute()
        } finally {
            assertTrue(
                "una cancelacion no es un fallo del reconciliador — no se reporta",
                reporter.reported.isEmpty()
            )
        }
    }
}
