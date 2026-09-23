package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.models.VisitReconcileResult
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.HandleVisitsPushEventUseCase.Companion.EVENT_VISITAS_CONFIRMADAS
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakePendingVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeVisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.fakes.RecordingSyncErrorReporter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The push event wired through the whole chain it drives in production:
 *
 * ```
 * HandleVisitsPushEventUseCase   (does this event mean anything?)
 *   → TriggerVisitsReconciliationUseCase  (is another trigger already running?)
 *     → ReconcileVisitsUseCase            (ask by-ids, mark what came back)
 * ```
 *
 * The individual classes have their own unit tests. What only shows up here is
 * what the four do *together*, which is where the two rulings this task rests
 * on can actually be checked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VisitsPushTriggerChainTest {

    private class Chain(
        pending: List<String> = emptyList(),
        known: Set<String> = emptySet()
    ) {
        val store = FakePendingVisitsStore(pending = pending)
        val registry = FakeVisitCustodyRegistry(known = known)
        val reporter = RecordingSyncErrorReporter()

        var lastResult: VisitReconcileResult? = null
            private set

        private val reconcile = ReconcileVisitsUseCase(store, registry, reporter)
        private val trigger = TriggerVisitsReconciliationUseCase(
            reconcileVisits = { reconcile.execute().also { lastResult = it } },
            errorReporter = reporter
        )
        val handler = HandleVisitsPushEventUseCase(
            triggerReconciliation = { trigger.execute() },
            errorReporter = reporter
        )
    }

    // ─── Ruling P: el fan-out se auto-limita ─────────────────────────────────

    /**
     * **Ruling P.** El evento despierta a TODA la flota — el topic del bus es
     * global — y eso se aceptó por una razón concreta: un teléfono sin visitas
     * pendientes no hace ninguna petición.
     *
     * `ReconcileVisitsUseCase` corta en `NothingPending` **antes** de tocar la
     * red; no manda un `by-ids` con lista vacía. Este test es lo que mantiene
     * cierta esa ruling: si alguien cambia ese short-circuit, el costo del
     * push pasa de "solo los teléfonos con algo que reconciliar" a "toda la
     * flota, en cada visita", y este test se pone rojo en vez de que se note
     * en la factura de datos de los cobradores.
     */
    @Test
    fun `un despertar sin pendientes no hace NINGUNA peticion de red`() = runTest {
        val chain = Chain(pending = emptyList())

        chain.handler.onEvent(EVENT_VISITAS_CONFIRMADAS)

        assertEquals(
            "un teléfono sin pendientes no debe pedir nada — ni siquiera un by-ids vacío",
            emptyList<List<String>>(),
            chain.registry.requests
        )
        assertEquals(VisitReconcileResult.NothingPending, chain.lastResult)
    }

    @Test
    fun `un despertar con pendientes si consulta by-ids`() = runTest {
        val pendiente = "6f1c9e2a-0000-4000-8000-000000000001"
        val chain = Chain(pending = listOf(pendiente), known = setOf(pendiente))

        chain.handler.onEvent(EVENT_VISITAS_CONFIRMADAS)

        assertEquals(
            "el control positivo del test de arriba: el método SÍ habría detectado " +
                "una petición si la hubiera habido",
            listOf(listOf(pendiente)),
            chain.registry.requests
        )
    }

    // ─── Ruling N: el evento es disparador, no autoridad ─────────────────────

    /**
     * **Ruling N.** El push no marca nada por sí mismo. Lo único que marca
     * sincronizada una visita es que el servidor la nombre en la respuesta de
     * `by-ids` — confirmación positiva, el único camino de escritura que dejó
     * la Task 10.
     */
    @Test
    fun `el evento no marca nada si el servidor no confirma`() = runTest {
        val pendiente = "6f1c9e2a-0000-4000-8000-000000000002"
        // known vacío: el servidor NO tiene la visita.
        val chain = Chain(pending = listOf(pendiente), known = emptySet())

        chain.handler.onEvent(EVENT_VISITAS_CONFIRMADAS)

        assertTrue(
            "recibir el push no es evidencia: sin respuesta de by-ids no se marca nada",
            chain.store.synced.isEmpty()
        )
    }

    @Test
    fun `el evento marca solo lo que by-ids confirmo`() = runTest {
        val confirmada = "6f1c9e2a-0000-4000-8000-000000000003"
        val noConfirmada = "6f1c9e2a-0000-4000-8000-000000000004"
        val chain = Chain(
            pending = listOf(confirmada, noConfirmada),
            known = setOf(confirmada)
        )

        chain.handler.onEvent(EVENT_VISITAS_CONFIRMADAS)

        assertEquals(listOf(confirmada), chain.store.synced)
    }

    // ─── Compatibilidad hacia adelante, extremo a extremo ────────────────────

    /**
     * La mitad que importa, medida sobre la cadena completa: un evento
     * desconocido no llega a tocar la red ni la base local.
     */
    @Test
    fun `un evento desconocido no consulta ni marca nada`() = runTest {
        val pendiente = "6f1c9e2a-0000-4000-8000-000000000005"
        val chain = Chain(pending = listOf(pendiente), known = setOf(pendiente))

        chain.handler.onEvent("un_evento_del_futuro")

        assertEquals(
            "un tipo desconocido no debe generar ni una petición",
            emptyList<List<String>>(),
            chain.registry.requests
        )
        assertTrue("ni marcar nada", chain.store.synced.isEmpty())
        assertEquals("ni siquiera debe leer los pendientes", 0, chain.store.readCallCount)
    }
}
