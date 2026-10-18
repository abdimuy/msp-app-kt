package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DeliveredVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.FotoSinEnviar
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_CONFIRMAR_FALLIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_CONSULTA_FALLIDA
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVOLVER_FALLIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVUELTA_A_PENDIENTE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_ENTREGADAS_ILEGIBLES
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_RETENIDAS_POR_COMPROBANTE
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeDeliveredVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeVisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.fakes.RecordingSyncErrorReporter
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pruebas que matan los mutantes que PIT dejó vivos o sin cubrir en
 * [ReconcileDeliveredVisitsUseCase] (plan visitas P1, Tarea 17; E-APP-076).
 *
 * Los fakes de [ReconcileDeliveredVisitsUseCaseTest] contestan sin suspender,
 * así que el código de reanudación que Kotlin genera para cada `suspend`
 * (`ResultKt.throwOnFailure` y el valor que devuelve la máquina de estados)
 * nunca corría: PIT lo marcaba NO_COVERAGE. Aquí cada puerto hace `yield()`
 * antes de contestar —o de fallar—, que es lo que hace Room o Retrofit de
 * verdad, y se exige el mismo resultado.
 */
class ReconcileDeliveredVisitsUseCaseMutantesTest {

    private val ahora = Instant.parse("2026-10-17T15:00:00Z")

    private fun u(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private val a = u(9001)
    private val b = u(9002)

    /** Suspende de verdad y luego delega (o falla, si el fake falla). */
    private class StoreQueSuspende(private val real: DeliveredVisitsStore) : DeliveredVisitsStore {
        override suspend fun entregadasDesde(desde: String): List<String> {
            yield()
            return real.entregadasDesde(desde)
        }

        override suspend fun confirmar(ids: List<String>, ahora: String): Int {
            yield()
            return real.confirmar(ids, ahora)
        }

        override suspend fun devolverAPendiente(ids: List<String>): Int {
            yield()
            return real.devolverAPendiente(ids)
        }

        override suspend fun fotosSinEnviarDeEntregadas(): List<FotoSinEnviar> {
            yield()
            return real.fotosSinEnviarDeEntregadas()
        }
    }

    private class RegistroQueSuspende(private val real: VisitCustodyRegistry) : VisitCustodyRegistry {
        override suspend fun findExisting(visitIds: List<String>): List<String> {
            yield()
            return real.findExisting(visitIds)
        }
    }

    private fun uc(
        store: DeliveredVisitsStore,
        reg: VisitCustodyRegistry,
        rep: RecordingSyncErrorReporter
    ) = ReconcileDeliveredVisitsUseCase(
        StoreQueSuspende(store),
        RegistroQueSuspende(reg),
        rep
    ) { ahora }

    @Test
    fun `E-APP-076 con puertos que suspenden confirma y devuelve igual`() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a, b))
        val rep = RecordingSyncErrorReporter()
        val r = uc(store, FakeVisitCustodyRegistry(known = setOf(a)), rep).execute()
        assertEquals(
            ConciliacionInversa(revisadas = 2, faltantes = 1, confirmadas = 1, tandasFallidas = 0),
            r
        )
        assertEquals(listOf(a), store.confirmadas)
        assertEquals(listOf(b), store.devueltas)
        assertEquals(listOf(ERROR_CODE_DEVUELTA_A_PENDIENTE), rep.codes)
    }

    @Test
    fun `E-APP-076 con puertos que suspenden cada falla se reporta y no decide`() = runTest {
        val rep1 = RecordingSyncErrorReporter()
        val r1 = uc(
            FakeDeliveredVisitsStore(failOnRead = IOException("x")),
            FakeVisitCustodyRegistry(),
            rep1
        ).execute()
        assertEquals(true, r1.entregadasIlegibles)
        assertEquals(listOf(ERROR_CODE_ENTREGADAS_ILEGIBLES), rep1.codes)
        assertEquals(
            "IOException en el mensaje",
            "ReconcileDeliveredVisitsUseCase.entregadasDesde: IOException",
            rep1.reported.single().message
        )

        val rep2 = RecordingSyncErrorReporter()
        val store2 = FakeDeliveredVisitsStore(entregadas = listOf(a))
        val r2 = uc(
            store2,
            FakeVisitCustodyRegistry(known = setOf(a), failOnCallIndex = 0),
            rep2
        ).execute()
        assertEquals(1, r2.tandasFallidas)
        assertEquals(listOf(ERROR_CODE_CONSULTA_FALLIDA), rep2.codes)
        assertEquals(emptyList<String>(), store2.confirmadas + store2.devueltas)

        val rep3 = RecordingSyncErrorReporter()
        val store3 =
            FakeDeliveredVisitsStore(
                entregadas = listOf(a, b),
                failOnConfirmar = IOException("c"),
                failOnDevolver = IOException("d")
            )
        val r3 = uc(store3, FakeVisitCustodyRegistry(known = setOf(a)), rep3).execute()
        assertEquals(0, r3.confirmadas)
        assertEquals(0, r3.faltantes)
        assertEquals(listOf(ERROR_CODE_CONFIRMAR_FALLIDO, ERROR_CODE_DEVOLVER_FALLIDO), rep3.codes)
    }

    /** Mata MathMutator en `confirmar` (`ids.size - cambiadas`): el reporte dice cuántas retuvo la foto. */
    @Test
    fun `E-APP-076 el reporte de retenidas dice cuantas y de que lote`() = runTest {
        val store =
            FakeDeliveredVisitsStore(entregadas = listOf(a, b, u(9003)), retenidas = setOf(b))
        val rep = RecordingSyncErrorReporter()
        uc(store, FakeVisitCustodyRegistry(known = setOf(a, b, u(9003))), rep).execute()
        val r = rep.reported.single { it.code == ERROR_CODE_RETENIDAS_POR_COMPROBANTE }
        assertEquals("1", r.props["retenidas"])
        assertEquals("3", r.props["tamano_lote"])
    }
}
