package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_CONFIRMAR_FALLIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_CONSULTA_FALLIDA
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVOLVER_FALLIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVUELTA_A_PENDIENTE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_ENTREGADAS_ILEGIBLES
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_ID_INVALIDO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_RESPUESTA_AJENA
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_RETENIDAS_POR_COMPROBANTE
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeDeliveredVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeVisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.fakes.RecordingSyncErrorReporter
import java.io.IOException
import java.time.Instant
import java.util.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Reconciliación inversa diaria (plan visitas P1, Tarea 13; spec §4.6-1;
 * E-VIS-009). El invariante, dicho una vez: **una visita soltada solo cambia
 * de estado si el servidor CONTESTÓ la tanda en la que se preguntó**. Si la
 * nombró, se confirma; si no, vuelve a pendiente. Una tanda que falla no
 * decide nada, y un id que no se preguntó no se confirma (Review Focus 3).
 */
class ReconcileDeliveredVisitsUseCaseTest {

    private val ahora = Instant.parse("2026-10-17T15:00:00Z")

    private fun uc(
        store: FakeDeliveredVisitsStore,
        registry: VisitCustodyRegistry,
        reporter: RecordingSyncErrorReporter = RecordingSyncErrorReporter()
    ) = ReconcileDeliveredVisitsUseCase(
        store = store,
        registry = registry,
        errorReporter = reporter,
        clock = { ahora }
    )

    /** Ids con forma de UUID canónico: los únicos que se le preguntan al servidor. */
    private fun u(n: Int) = "00000000-0000-4000-8000-%012d".format(n)

    private val a = u(9001)
    private val b = u(9002)
    private val intruso = u(9999)

    private fun ids(n: Int, from: Int = 1) = (from until from + n).map { u(it) }

    // Review Focus 2.
    @Test
    fun inversaDevuelveAPendienteLaLegadaQueElServidorNoTiene() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a, b))
        val reg = FakeVisitCustodyRegistry(known = setOf(a))
        val reporter = RecordingSyncErrorReporter()

        val r = uc(store, reg, reporter).execute()

        assertEquals(listOf(b), store.devueltas)
        assertEquals(listOf(a), store.confirmadas)
        assertEquals(
            ConciliacionInversa(revisadas = 2, faltantes = 1, confirmadas = 1, tandasFallidas = 0),
            r
        )
        // Alerta del spec §12-1: devuelta_a_pendiente > 0 se reporta.
        assertEquals(listOf(ERROR_CODE_DEVUELTA_A_PENDIENTE), reporter.codes)
        assertEquals("1", reporter.reported.single().props["devueltas"])
    }

    // Review Focus 3.
    @Test
    fun tandaFallidaNoDevuelveNiConfirma() = runTest {
        val todas = ids(150)
        val store = FakeDeliveredVisitsStore(entregadas = todas)
        val reg = FakeVisitCustodyRegistry(known = setOf(u(120)), failOnCallIndex = 1)
        val reporter = RecordingSyncErrorReporter()

        val r = uc(store, reg, reporter).execute()

        val segunda = ids(50, from = 101)
        assertTrue(store.devueltas.none { it in segunda })
        assertTrue(store.confirmadas.none { it in segunda })
        // La primera tanda sí se contestó (nadie la tiene): decide sola.
        assertEquals(ids(100), store.devueltas)
        assertEquals(1, r.tandasFallidas)
        assertEquals(100, r.faltantes)
        assertTrue(ERROR_CODE_CONSULTA_FALLIDA in reporter.codes)
    }

    // Review Focus 3.
    @Test
    fun idAjenoNoSeConfirma() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a))
        val reg = FakeVisitCustodyRegistry(known = setOf(a), volunteeredIds = listOf(intruso))
        val reporter = RecordingSyncErrorReporter()

        val r = uc(store, reg, reporter).execute()

        assertEquals(listOf(a), store.confirmadas)
        assertEquals(emptyList<String>(), store.devueltas)
        assertEquals(1, r.confirmadas)
        assertEquals(listOf(ERROR_CODE_RESPUESTA_AJENA), reporter.codes)
    }

    /**
     * La comparación de ids es EXACTA, igual que `existenciaCompleta` (Tarea 9):
     * un id que difiere solo en mayúsculas no es el que se preguntó. No se
     * confirma por él; la visita vuelve a pendiente y el servidor la deduplica
     * por UUID al reenviarla. Equivocarse hacia ese lado cuesta un reenvío;
     * hacia el otro, soltar una visita sin prueba.
     */
    @Test
    fun `id que solo difiere en mayusculas no confirma`() = runTest {
        val local = "6f1c2a3b-0000-4000-8000-00000000000a"
        val store = FakeDeliveredVisitsStore(entregadas = listOf(local))
        val reg = FakeVisitCustodyRegistry(volunteeredIds = listOf(local.uppercase()))

        uc(store, reg).execute()

        assertEquals(emptyList<String>(), store.confirmadas)
        assertEquals(listOf(local), store.devueltas)
    }

    @Test
    fun `tandas de a lo mas 100`() = runTest {
        val reg = FakeVisitCustodyRegistry()

        uc(FakeDeliveredVisitsStore(entregadas = ids(250)), reg).execute()

        assertEquals(listOf(100, 100, 50), reg.requests.map { it.size })
    }

    @Test
    fun `ids repetidos se preguntan una vez`() = runTest {
        val reg = FakeVisitCustodyRegistry(known = setOf(a))
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a, a, b))

        val r = uc(store, reg).execute()

        assertEquals(listOf(listOf(a, b)), reg.requests)
        assertEquals(2, r.revisadas)
    }

    @Test
    fun `sin entregadas no hace ninguna peticion`() = runTest {
        val reg = FakeVisitCustodyRegistry()
        val reporter = RecordingSyncErrorReporter()

        val r = uc(FakeDeliveredVisitsStore(), reg, reporter).execute()

        assertEquals(emptyList<List<String>>(), reg.requests)
        assertEquals(ConciliacionInversa(0, 0, 0, 0), r)
        assertEquals(emptyList<String>(), reporter.codes)
    }

    @Test
    fun `la ventana es de 30 dias por omision en formato de cable`() = runTest {
        val store = FakeDeliveredVisitsStore()

        uc(store, FakeVisitCustodyRegistry()).execute()

        assertEquals(listOf("2026-09-17T15:00:00Z"), store.lecturas)
    }

    @Test
    fun `confirmar estampa el instante del reloj`() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a))

        uc(store, FakeVisitCustodyRegistry(known = setOf(a))).execute()

        assertEquals(listOf("2026-10-17T15:00:00Z"), store.confirmadasEn)
    }

    @Test
    fun `lectura local ilegible no pregunta ni decide y se reporta`() = runTest {
        val reg = FakeVisitCustodyRegistry()
        val reporter = RecordingSyncErrorReporter()
        val store = FakeDeliveredVisitsStore(failOnRead = IllegalStateException("db"))

        val r = uc(store, reg, reporter).execute()

        assertEquals(emptyList<List<String>>(), reg.requests)
        assertTrue(r.entregadasIlegibles)
        assertEquals(listOf(ERROR_CODE_ENTREGADAS_ILEGIBLES), reporter.codes)
    }

    @Test
    fun `la retenida por comprobante no cuenta como confirmada y se reporta`() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a, b), retenidas = setOf(b))
        val reporter = RecordingSyncErrorReporter()

        val r = uc(store, FakeVisitCustodyRegistry(known = setOf(a, b)), reporter).execute()

        assertEquals(1, r.confirmadas)
        assertEquals(0, r.faltantes)
        assertEquals(listOf(ERROR_CODE_RETENIDAS_POR_COMPROBANTE), reporter.codes)
    }

    @Test
    fun `escritura local que falla se reporta y no tumba las demas tandas`() = runTest {
        val store = FakeDeliveredVisitsStore(
            entregadas = ids(150),
            failOnConfirmar = IllegalStateException("x"),
            failOnDevolver = IllegalStateException("y")
        )
        val reporter = RecordingSyncErrorReporter()
        val reg = FakeVisitCustodyRegistry(known = setOf(u(1), u(101)))

        val r = uc(store, reg, reporter).execute()

        assertEquals(2, reg.requests.size)
        assertEquals(0, r.confirmadas)
        assertEquals(0, r.faltantes)
        assertEquals(2, reporter.codes.count { it == ERROR_CODE_CONFIRMAR_FALLIDO })
        assertEquals(2, reporter.codes.count { it == ERROR_CODE_DEVOLVER_FALLIDO })
    }

    @Test
    fun `cancelacion se propaga`() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a))
        val reg = FakeVisitCustodyRegistry(
            failOnCallIndex = 0,
            failure = CancellationException("cancelado")
        )
        try {
            uc(store, reg).execute()
            fail("debió relanzar la cancelación")
        } catch (_: CancellationException) {
            // esperado
        }
        assertEquals(emptyList<String>(), store.devueltas)
        assertEquals(emptyList<String>(), store.confirmadas)
    }

    /**
     * Propiedad, 300 casos con semilla fija (el repo no trae biblioteca de
     * propiedades en `:core:common`): para cualquier mezcla de entregadas,
     * servidor, ids ajenos y tandas que fallan,
     *  - confirmada ⇒ se preguntó, el servidor la nombró, y su tanda contestó;
     *  - devuelta ⇒ se preguntó, el servidor NO la nombró, y su tanda contestó;
     *  - ninguna id queda a la vez confirmada y devuelta;
     *  - una id de una tanda fallida no se toca.
     */
    @Test
    fun `propiedad solo decide lo que el servidor contesto`() = runTest {
        val rnd = Random(20261017L)
        repeat(300) { caso ->
            val n = rnd.nextInt(320)
            val entregadas = List(n) { u(rnd.nextInt(400)) }
            val distintas = entregadas.distinct()
            val servidor = distintas.filter { rnd.nextBoolean() }.toSet() +
                List(rnd.nextInt(3)) { "srv$it" }
            val tandas = distintas.chunked(100)
            val falla = if (tandas.isEmpty() || rnd.nextBoolean()) {
                null
            } else {
                rnd.nextInt(
                    tandas.size
                )
            }
            val ajenos = List(rnd.nextInt(3)) { "ajeno$it" }
            val store = FakeDeliveredVisitsStore(entregadas = entregadas)
            val reg = FakeVisitCustodyRegistry(
                known = servidor,
                failOnCallIndex = falla,
                volunteeredIds = ajenos
            )

            val r = uc(store, reg).execute()

            val contestadas = tandas.filterIndexed { i, _ -> i != falla }.flatten().toSet()
            val msg = "caso $caso"
            assertTrue(msg, store.confirmadas.all { it in contestadas && it in servidor })
            assertTrue(msg, store.devueltas.all { it in contestadas && it !in servidor })
            assertTrue(msg, store.confirmadas.intersect(store.devueltas.toSet()).isEmpty())
            assertEquals(
                msg,
                contestadas.filter { it in servidor }.toSet(),
                store.confirmadas.toSet()
            )
            assertEquals(
                msg,
                contestadas.filter { it !in servidor }.toSet(),
                store.devueltas.toSet()
            )
            assertEquals(msg, distintas.size, r.revisadas)
            assertEquals(msg, if (falla == null) 0 else 1, r.tandasFallidas)
            assertTrue(msg, ajenos.none { it in store.confirmadas })
        }
    }

    @Test
    fun `una IOException en la consulta es desconocido no ausente`() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a))
        val reg = FakeVisitCustodyRegistry(failOnCallIndex = 0, failure = IOException("sin red"))

        val r = uc(store, reg).execute()

        assertEquals(emptyList<String>(), store.devueltas)
        assertEquals(1, r.tandasFallidas)
    }

    /**
     * Imita a `GET /v2/visitas/by-ids`: un solo id que `uuid.Parse` rechaza
     * tumba TODA la petición con `422 ids_invalid`
     * (msp-api `handlers_visitas_existentes_por_ids.go:143-145`, guardado por
     * `TestHTTP_VisitasExistentesPorIDs_MalformedUUIDAmongValid_Returns422_AndNothingLeaks`).
     */
    private class ServidorQueRechazaLaTanda(private val tiene: Set<String>) : VisitCustodyRegistry {
        val peticiones = mutableListOf<List<String>>()
        private val canonico =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        override suspend fun findExisting(visitIds: List<String>): List<String> {
            peticiones += visitIds
            if (visitIds.any {
                    !canonico.matches(it.trim())
                }
            ) {
                error("HTTP 422 ids_invalid")
            }
            return visitIds.filter { it in tiene }
        }
    }

    /**
     * Vivacidad: un id del teléfono que no es UUID no puede dejar sin decidir,
     * todos los días, a las otras 99 de su tanda. No se manda ni se decide: se
     * queda como está y se reporta con su propio código.
     */
    @Test
    fun idNoUuidNoTumbaLaTandaYLosValidosSeDeciden() = runTest {
        val raros = listOf("visita-legada-1", "", "a,b", "6f1c2a3b00004000800000000000000a")
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a) + raros + listOf(b))
        val servidor = ServidorQueRechazaLaTanda(tiene = setOf(a))
        val reporter = RecordingSyncErrorReporter()

        val r = uc(store, servidor, reporter).execute()

        assertEquals(listOf(listOf(a, b)), servidor.peticiones)
        assertEquals(listOf(a), store.confirmadas)
        assertEquals(listOf(b), store.devueltas)
        assertTrue(raros.none { it in store.confirmadas || it in store.devueltas })
        assertEquals(0, r.tandasFallidas)
        assertEquals(raros.size, r.idsInvalidos)
        val invalido = reporter.reported.single { it.code == ERROR_CODE_ID_INVALIDO }
        assertEquals(raros.size.toString(), invalido.props["descartados"])
    }

    @Test
    fun `si todas son invalidas no hay ninguna peticion`() = runTest {
        val servidor = ServidorQueRechazaLaTanda(tiene = emptySet())
        val store = FakeDeliveredVisitsStore(entregadas = listOf("x", "y"))

        val r = uc(store, servidor).execute()

        assertEquals(emptyList<List<String>>(), servidor.peticiones)
        assertEquals(2, r.idsInvalidos)
        assertEquals(emptyList<String>(), store.devueltas)
    }

    @Test
    fun `un UUID canonico en mayusculas si se pregunta`() = runTest {
        val mayus = a.uppercase().replace("0", "A")
        val servidor = ServidorQueRechazaLaTanda(tiene = emptySet())

        uc(FakeDeliveredVisitsStore(entregadas = listOf(mayus)), servidor).execute()

        assertEquals(listOf(listOf(mayus)), servidor.peticiones)
    }
}
