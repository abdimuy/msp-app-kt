package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.models.VisitReconcileResult
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry.Companion.MAX_IDS_PER_REQUEST
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase.Companion.ERROR_CODE_CONSULTA_FALLIDA
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase.Companion.ERROR_CODE_MARCADO_FALLIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase.Companion.ERROR_CODE_PENDIENTES_ILEGIBLES
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileVisitsUseCase.Companion.ERROR_CODE_RESPUESTA_AJENA
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakePendingVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeVisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.fakes.RecordingSyncErrorReporter
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Robustness suite for the visitas reconciler.
 *
 * The money-shaped invariant under test, stated once so every case below can be
 * read against it: **a visita is marked synced only when `by-ids` names it in
 * the answer to a chunk it was actually in.** Every other outcome — no network,
 * a partial answer, an id the server does not know, a failed local write, an id
 * the server volunteered — must leave it pending.
 */
class ReconcileVisitsUseCaseTest {

    private fun ids(count: Int, prefix: String = "v") = List(count) { "$prefix-${it + 1}" }

    private fun useCase(
        store: FakePendingVisitsStore,
        registry: FakeVisitCustodyRegistry,
        reporter: RecordingSyncErrorReporter = RecordingSyncErrorReporter()
    ) = ReconcileVisitsUseCase(store = store, registry = registry, errorReporter = reporter)

    // ---------------------------------------------------------------- lote vacío

    @Test
    fun `lote vacio no hace NINGUNA peticion (no una peticion con lista vacia)`() = runTest {
        val store = FakePendingVisitsStore(pending = emptyList())
        val registry = FakeVisitCustodyRegistry()
        val reporter = RecordingSyncErrorReporter()

        val result = useCase(store, registry, reporter).execute()

        assertEquals(VisitReconcileResult.NothingPending, result)
        assertEquals(emptyList<List<String>>(), registry.requests)
        assertEquals(emptyList<String>(), store.synced)
        assertEquals(emptyList<String>(), reporter.codes)
    }

    // ------------------------------------------------------------- tope exacto

    @Test
    fun `el tope es exactamente 100, el numero que el servidor declara`() {
        assertEquals(100, MAX_IDS_PER_REQUEST)
    }

    @Test
    fun `justo en el tope (100) va en UNA sola peticion`() = runTest {
        val pending = ids(MAX_IDS_PER_REQUEST)
        val store = FakePendingVisitsStore(pending = pending)
        val registry = FakeVisitCustodyRegistry(known = pending.toSet())

        val result = useCase(store, registry).execute()

        assertEquals(1, registry.requests.size)
        assertEquals(MAX_IDS_PER_REQUEST, registry.requests.single().size)
        assertEquals(pending, registry.requests.single())
        assertEquals(pending, store.synced)
        assertEquals(
            VisitReconcileResult.Reconciled(
                pendingCount = 100,
                requestCount = 1,
                confirmedCount = 100,
                failedRequestCount = 0
            ),
            result
        )
    }

    // ------------------------------------------------------------ sobre el tope

    @Test
    fun `101 se trocea en 100 mas 1, no en una sola peticion de 101`() = runTest {
        val pending = ids(MAX_IDS_PER_REQUEST + 1)
        val store = FakePendingVisitsStore(pending = pending)
        val registry = FakeVisitCustodyRegistry(known = pending.toSet())

        useCase(store, registry).execute()

        assertEquals(listOf(100, 1), registry.requests.map { it.size })
        // El troceo respeta el orden y no pierde ni duplica ningun id.
        assertEquals(pending, registry.requests.flatten())
        assertEquals(pending, store.synced)
    }

    @Test
    fun `250 se trocea en 100 mas 100 mas 50 — el tamano es 100, no algun troceo cualquiera`() =
        runTest {
            val pending = ids(250)
            val store = FakePendingVisitsStore(pending = pending)
            val registry = FakeVisitCustodyRegistry(known = pending.toSet())

            val result = useCase(store, registry).execute()

            assertEquals(listOf(100, 100, 50), registry.requests.map { it.size })
            registry.requests.forEach { chunk ->
                assertTrue(
                    "ninguna peticion puede exceder el tope de 100 (el servidor responde 422 ids_too_many)",
                    chunk.size <= MAX_IDS_PER_REQUEST
                )
            }
            assertEquals(pending, registry.requests.flatten())
            assertEquals(3, (result as VisitReconcileResult.Reconciled).requestCount)
            assertEquals(250, result.confirmedCount)
            assertEquals(0, result.stillPendingCount)
        }

    @Test
    fun `los duplicados se colapsan antes de gastar cupo del tope`() = runTest {
        // 100 unicos repetidos dos veces: sin `distinct` serian 200 ids y DOS
        // peticiones, la segunda preguntando por lo mismo que la primera.
        val unique = ids(MAX_IDS_PER_REQUEST)
        val store = FakePendingVisitsStore(pending = unique + unique)
        val registry = FakeVisitCustodyRegistry(known = unique.toSet())

        val result = useCase(store, registry).execute()

        assertEquals(1, registry.requests.size)
        assertEquals(unique, registry.requests.single())
        assertEquals(100, (result as VisitReconcileResult.Reconciled).pendingCount)
    }

    // ---------------------------------------------------------- respuesta parcial

    @Test
    fun `respuesta parcial solo marca lo que el servidor nombra y deja el resto pendiente`() =
        runTest {
            val pending = ids(5)
            val store = FakePendingVisitsStore(pending = pending)
            val registry = FakeVisitCustodyRegistry(known = setOf("v-2", "v-4"))

            val result = useCase(store, registry).execute()

            assertEquals(listOf("v-2", "v-4"), store.synced)
            assertEquals(listOf(listOf("v-2", "v-4")), store.markSyncedCalls)
            result as VisitReconcileResult.Reconciled
            assertEquals(2, result.confirmedCount)
            assertEquals(3, result.stillPendingCount)
        }

    @Test
    fun `respuesta parcial a traves de varios lotes solo marca lo confirmado en cada uno`() =
        runTest {
            val pending = ids(150)
            val store = FakePendingVisitsStore(pending = pending)
            val registry = FakeVisitCustodyRegistry(known = setOf("v-1", "v-101"))

            val result = useCase(store, registry).execute()

            assertEquals(listOf(100, 50), registry.requests.map { it.size })
            assertEquals(listOf("v-1", "v-101"), store.synced)
            assertEquals(2, (result as VisitReconcileResult.Reconciled).confirmedCount)
            assertEquals(148, result.stillPendingCount)
        }

    // --------------------------------------- ids que el servidor NO conoce

    @Test
    fun `ids que el servidor no conoce siguen pendientes y NO se reportan como error`() = runTest {
        val pending = ids(3)
        val store = FakePendingVisitsStore(pending = pending)
        val registry = FakeVisitCustodyRegistry(known = emptySet())
        val reporter = RecordingSyncErrorReporter()

        val result = useCase(store, registry, reporter).execute()

        assertEquals(1, registry.requests.size)
        assertEquals(emptyList<String>(), store.synced)
        // Ni una sola escritura: un `markSynced(emptyList())` seria una
        // transaccion inutil, y peor, una que un futuro cambio podria
        // convertir en un UPDATE sin WHERE.
        assertEquals(emptyList<List<String>>(), store.markSyncedCalls)
        assertEquals(3, (result as VisitReconcileResult.Reconciled).stillPendingCount)
        // La ausencia es LA respuesta esperada de este endpoint, no una falla.
        assertEquals(emptyList<String>(), reporter.codes)
    }

    // ------------------------------------------------------------- error de red

    @Test
    fun `error de red no marca NADA, lo reporta por telemetria y deja todo pendiente`() = runTest {
        val pending = ids(3)
        val store = FakePendingVisitsStore(pending = pending)
        val registry = FakeVisitCustodyRegistry(
            known = pending.toSet(),
            failOnCallIndex = 0,
            failure = IOException("sin red")
        )
        val reporter = RecordingSyncErrorReporter()

        val result = useCase(store, registry, reporter).execute()

        assertEquals(emptyList<String>(), store.synced)
        assertEquals(emptyList<List<String>>(), store.markSyncedCalls)
        result as VisitReconcileResult.Reconciled
        assertEquals(1, result.failedRequestCount)
        assertEquals(0, result.confirmedCount)
        assertEquals(3, result.stillPendingCount)

        val error = reporter.reported.single()
        assertEquals(ERROR_CODE_CONSULTA_FALLIDA, error.code)
        // Nombre de la clase de excepcion + contexto estatico: NUNCA `e.message`
        // crudo, que puede arrastrar datos de negocio a telemetria.
        assertEquals("ReconcileVisitsUseCase.findExisting: IOException", error.message)
        assertEquals("3", error.props["tamano_lote"])
    }

    @Test
    fun `un lote que falla no cancela los que ya se marcaron ni los siguientes`() = runTest {
        val pending = ids(250)
        val store = FakePendingVisitsStore(pending = pending)
        val registry = FakeVisitCustodyRegistry(
            known = pending.toSet(),
            failOnCallIndex = 1,
            failure = IOException("se cayo a mitad")
        )
        val reporter = RecordingSyncErrorReporter()

        val result = useCase(store, registry, reporter).execute()

        // Lote 1 (1..100) marcado, lote 2 (101..200) perdido, lote 3 (201..250) marcado.
        assertEquals(3, registry.requests.size)
        assertEquals(ids(100) + ids(250).subList(200, 250), store.synced)
        result as VisitReconcileResult.Reconciled
        assertEquals(150, result.confirmedCount)
        assertEquals(1, result.failedRequestCount)
        assertEquals(100, result.stillPendingCount)
        assertEquals(listOf(ERROR_CODE_CONSULTA_FALLIDA), reporter.codes)
    }

    @Test
    fun `servidor inalcanzable con UN solo lote no marca ni una visita`() = runTest {
        val pending = ids(3)
        val store = FakePendingVisitsStore(pending = pending)
        // Con 3 ids hay un solo lote, asi que fallar el indice 0 es fallar todo.
        val registry = FakeVisitCustodyRegistry(
            known = pending.toSet(),
            failOnCallIndex = 0,
            failure = IOException("servidor inalcanzable")
        )

        val result = useCase(store, registry).execute()

        assertEquals(emptyList<String>(), store.synced)
        result as VisitReconcileResult.Reconciled
        assertEquals(0, result.confirmedCount)
        assertEquals(3, result.stillPendingCount)
    }

    // ---------------------------------------------- lectura local ilegible

    @Test
    fun `si no se puede leer lo pendiente no se hace peticion y se reporta`() = runTest {
        val store = FakePendingVisitsStore(
            pending = ids(3),
            failOnRead = IllegalStateException("db cerrada")
        )
        val registry = FakeVisitCustodyRegistry(known = ids(3).toSet())
        val reporter = RecordingSyncErrorReporter()

        val result = useCase(store, registry, reporter).execute()

        assertEquals(VisitReconcileResult.PendingUnreadable, result)
        assertEquals(emptyList<List<String>>(), registry.requests)
        assertEquals(emptyList<String>(), store.synced)
        assertEquals(listOf(ERROR_CODE_PENDIENTES_ILEGIBLES), reporter.codes)
        assertEquals(
            "ReconcileVisitsUseCase.pendingVisitIds: IllegalStateException",
            reporter.reported.single().message
        )
    }

    // ----------------------------------------------- escritura local fallida

    @Test
    fun `si el marcado local falla se reporta y las visitas siguen contando como pendientes`() =
        runTest {
            val pending = ids(2)
            val store = FakePendingVisitsStore(
                pending = pending,
                failOnMark = IllegalStateException("disco lleno")
            )
            val registry = FakeVisitCustodyRegistry(known = pending.toSet())
            val reporter = RecordingSyncErrorReporter()

            val result = useCase(store, registry, reporter).execute()

            assertEquals(emptyList<String>(), store.synced)
            result as VisitReconcileResult.Reconciled
            assertEquals(0, result.confirmedCount)
            assertEquals(2, result.stillPendingCount)
            assertEquals(listOf(ERROR_CODE_MARCADO_FALLIDO), reporter.codes)
            assertEquals("2", reporter.reported.single().props["tamano_lote"])
        }

    // ------------------------------------------------- respuesta con ids ajenos

    @Test
    fun `un id que no se pregunto NUNCA se marca, se descarta y se reporta`() = runTest {
        val pending = ids(2)
        val store = FakePendingVisitsStore(pending = pending)
        val registry = FakeVisitCustodyRegistry(
            known = setOf("v-1"),
            volunteeredIds = listOf("visita-de-otro-telefono")
        )
        val reporter = RecordingSyncErrorReporter()

        useCase(store, registry, reporter).execute()

        assertEquals(listOf("v-1"), store.synced)
        assertTrue("un id ajeno jamas se escribe", "visita-de-otro-telefono" !in store.synced)
        val error = reporter.reported.single()
        assertEquals(ERROR_CODE_RESPUESTA_AJENA, error.code)
        assertEquals("1", error.props["descartados"])
        assertEquals("2", error.props["tamano_lote"])
    }

    // ------------------------------------------------------------ cancelacion

    @Test(expected = CancellationException::class)
    fun `la cancelacion se propaga, no se convierte en un error reportado`() = runTest {
        val store = FakePendingVisitsStore(pending = ids(1))
        val registry = FakeVisitCustodyRegistry(
            known = emptySet(),
            failOnCallIndex = 0,
            failure = CancellationException("scope cancelado")
        )

        useCase(store, registry).execute()
    }
}
