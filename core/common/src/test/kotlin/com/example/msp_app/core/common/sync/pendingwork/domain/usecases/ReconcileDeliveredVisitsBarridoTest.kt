package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.DineroPendiente
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.FotoSinEnviar
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_BARRIDO_ILEGIBLE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_BARRIDO_POSPUESTO_POR_DINERO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVUELTA_A_PENDIENTE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVUELTA_POR_FOTO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DINERO_ILEGIBLE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_FOTO_SIN_ENVIAR_VIEJA
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeDeliveredVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeVisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.fakes.RecordingSyncErrorReporter
import com.example.msp_app.core.common.visitas.envio.ReglaDePoda
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Barrido de fotos sin enviar en la reconciliación inversa (plan visitas P1,
 * cierre del HALLAZGO de E-APP-076; E-APP-079).
 *
 * Una visita ENTREGADA (confirmada o legada) con una foto sin `SUBIDA_EN`
 * cuyo archivo local existe no la vuelve a mandar nadie. El barrido la
 * devuelve a pendiente; el reenvío idempotente repara la foto en el servidor
 * (`planImagenes`, E-VIS-004).
 *
 * E-VIS-032: el barrido NUNCA abandona una foto. Antes una foto con más de
 * [ReglaDePoda.FOTOS_SIN_ENVIAR_DIAS] días según el reloj del teléfono dejaba
 * de devolver su visita; ese umbral ahora solo separa, en la telemetría, las
 * fotos "viejas" (alerta) de las demás.
 */
class ReconcileDeliveredVisitsBarridoTest {

    private val ahora = Instant.parse("2026-10-17T15:00:00Z")
    private fun u(n: Int) = "00000000-0000-4000-8000-%012d".format(n)
    private val a = u(1)
    private val b = u(2)
    private val c = u(3)

    private fun uc(
        store: FakeDeliveredVisitsStore,
        rep: RecordingSyncErrorReporter = RecordingSyncErrorReporter(),
        dinero: DineroPendiente = DineroPendiente.NINGUNO,
        conocidas: Set<String> = setOf(a, b, c)
    ) = ReconcileDeliveredVisitsUseCase(
        store,
        FakeVisitCustodyRegistry(known = conocidas),
        rep,
        dinero
    ) {
        ahora
    }

    private fun haceDias(d: Long) = ahora.minusSeconds(d * 86_400).toString()

    @Test
    fun `E-APP-079 la confirmada con una foto vigente vuelve a pendiente y no cuenta como faltante`() =
        runTest {
            val store = FakeDeliveredVisitsStore(
                entregadas = listOf(a, b),
                fotosSinEnviar = listOf(FotoSinEnviar(a, haceDias(1)))
            )
            val rep = RecordingSyncErrorReporter()
            val r = uc(store, rep).execute()

            assertEquals(listOf(a), store.devueltas)
            assertEquals(1, r.devueltasPorFoto)
            assertEquals("el servidor sí la tiene: no es faltante", 0, r.faltantes)
            assertEquals(listOf(b), store.confirmadas)
            assertTrue(ERROR_CODE_DEVUELTA_POR_FOTO in rep.codes)
            assertTrue(
                "no dispara la alerta de faltantes",
                ERROR_CODE_DEVUELTA_A_PENDIENTE !in rep.codes
            )
            assertEquals(
                "1",
                rep.reported.single {
                    it.code == ERROR_CODE_DEVUELTA_POR_FOTO
                }.props["devueltas"]
            )
        }

    @Test
    fun `E-APP-079 dos fotos de la misma visita la devuelven una vez`() = runTest {
        val store = FakeDeliveredVisitsStore(
            entregadas = listOf(a),
            fotosSinEnviar = listOf(FotoSinEnviar(a, haceDias(1)), FotoSinEnviar(a, haceDias(2)))
        )
        val r = uc(store).execute()
        assertEquals(listOf(a), store.devueltas)
        assertEquals(1, r.devueltasPorFoto)
    }

    /**
     * E-VIS-032 (reemplaza "la foto mas vieja que la ventana no devuelve la
     * visita y se reporta abandonada" de E-APP-079): la vieja y la ilegible
     * devuelven su visita igual; solo se cuentan como viejas.
     */
    @Test
    fun `E-VIS-032 la foto vieja o con fecha ilegible devuelve su visita igual y solo se cuenta`() =
        runTest {
            val dias = ReglaDePoda.FOTOS_SIN_ENVIAR_DIAS.toLong()
            val store = FakeDeliveredVisitsStore(
                entregadas = listOf(a, b, c),
                fotosSinEnviar = listOf(
                    FotoSinEnviar(a, haceDias(dias + 1)),
                    FotoSinEnviar(b, haceDias(dias - 1)),
                    FotoSinEnviar(c, "no-es-fecha")
                )
            )
            val rep = RecordingSyncErrorReporter()
            val r = uc(store, rep).execute()

            assertEquals(
                "ninguna se abandona; la ilegible primero, luego de la más vieja a la más nueva",
                listOf(c, a, b),
                store.devueltas
            )
            assertEquals(3, r.devueltasPorFoto)
            assertEquals(3, r.fotosSinEnviar)
            assertEquals("la vieja y la ilegible", 2, r.fotosSinEnviarViejas)
            assertEquals((dias + 1) * MINUTOS_POR_DIA, r.fotoSinEnviarMasViejaMin)
            assertEquals(
                "2",
                rep.reported.single { it.code == ERROR_CODE_FOTO_SIN_ENVIAR_VIEJA }.props["fotos"]
            )
        }

    /**
     * E-VIS-032 (reemplaza "el limite de la ventana es exacto" de E-APP-079):
     * el umbral de la semana ya no decide la devolución, solo la alerta. Sigue
     * siendo exacto e inclusivo.
     */
    @Test
    fun `E-VIS-032 el umbral de la semana solo mueve la alerta y es exacto`() = runTest {
        val dias = ReglaDePoda.FOTOS_SIN_ENVIAR_DIAS.toLong()
        val enElLimite = uc(
            FakeDeliveredVisitsStore(
                entregadas = listOf(a),
                fotosSinEnviar = listOf(FotoSinEnviar(a, haceDias(dias)))
            )
        ).execute()
        assertEquals(1, enElLimite.devueltasPorFoto)
        assertEquals("en el límite todavía no es vieja", 0, enElLimite.fotosSinEnviarViejas)

        val unSegundoDespues = uc(
            FakeDeliveredVisitsStore(
                entregadas = listOf(a),
                fotosSinEnviar = listOf(
                    FotoSinEnviar(a, ahora.minusSeconds(dias * 86_400 + 1).toString())
                )
            )
        ).execute()
        assertEquals("se devuelve igual", 1, unSegundoDespues.devueltasPorFoto)
        assertEquals(1, unSegundoDespues.fotosSinEnviarViejas)
    }

    /**
     * E-VIS-032: el reloj de pared no decide. Una `CREADA_EN` en el futuro
     * (reloj adelantado al capturar) o de hace más de un año (reloj atrasado y
     * corregido) devuelve su visita igual; la edad nunca sale negativa.
     */
    @Test
    fun `E-VIS-032 un reloj adelantado o atrasado no cambia la devolucion`() = runTest {
        val futura = uc(
            FakeDeliveredVisitsStore(
                entregadas = listOf(a),
                fotosSinEnviar = listOf(FotoSinEnviar(a, ahora.plusSeconds(3 * 86_400).toString()))
            )
        ).execute()
        assertEquals(1, futura.devueltasPorFoto)
        assertEquals(0, futura.fotosSinEnviarViejas)
        assertEquals("nunca negativa", 0L, futura.fotoSinEnviarMasViejaMin)

        val antigua = uc(
            FakeDeliveredVisitsStore(
                entregadas = listOf(a),
                fotosSinEnviar = listOf(FotoSinEnviar(a, haceDias(400)))
            )
        ).execute()
        assertEquals(1, antigua.devueltasPorFoto)
        assertEquals(1, antigua.fotosSinEnviarViejas)
    }

    /**
     * Tope por pasada (E-APP-079, adenda): a lo más
     * [ReconcileDeliveredVisitsUseCase.MAX_DEVUELTAS_POR_PASADA] visitas, de la
     * foto más vieja a la más nueva; el resto queda en espera para la
     * siguiente pasada y ninguna se abandona.
     */
    @Test
    fun `E-APP-079 el barrido reabre a lo mas el tope por pasada de la foto mas vieja a la mas nueva`() =
        runTest {
            val tope = ReconcileDeliveredVisitsUseCase.MAX_DEVUELTAS_POR_PASADA
            val ids = (1..25).map { u(100 + it) }
            // La visita i tiene una foto de hace (30 - i) días: la 1 es la más vieja.
            val store = FakeDeliveredVisitsStore(
                entregadas = ids,
                fotosSinEnviar = ids.mapIndexed { i, id ->
                    FotoSinEnviar(
                        id,
                        haceDias(30L - i)
                    )
                }.reversed()
            )
            // El servidor las tiene todas: la parte by-ids no devuelve ninguna como faltante.
            val r = uc(store, conocidas = ids.toSet()).execute()
            assertEquals("las más viejas primero", ids.take(tope), store.devueltas)
            assertEquals(tope, r.devueltasPorFoto)
            assertEquals(25 - tope, r.visitasConFotoEnEspera)
        }

    /** El residuo grande se vacía en varias pasadas, sin abandonar ninguna. */
    @Test
    fun `E-APP-079 un residuo de 1500 se vacia en 150 pasadas de a lo mas el tope`() = runTest {
        val tope = ReconcileDeliveredVisitsUseCase.MAX_DEVUELTAS_POR_PASADA
        val ids = (1..1_500).map { u(100 + it) }
        val store = FakeDeliveredVisitsStore(
            entregadas = ids,
            fotosSinEnviar = ids.map { FotoSinEnviar(it, haceDias(30)) }
        )
        val porPasada = mutableListOf<Int>()
        while (store.fotosSinEnviar.isNotEmpty() && porPasada.size < 1_000) {
            porPasada += uc(store, conocidas = ids.toSet()).execute().devueltasPorFoto
        }
        assertEquals(150, porPasada.size)
        assertTrue(porPasada.all { it <= tope })
        assertEquals(ids.toSet(), store.devueltas.toSet())
        assertEquals(1_500, store.devueltas.size)
    }

    /** Prioridad del dinero: con pagos o ventas por subir no reabre nada. */
    @Test
    fun `E-APP-079 con pagos o ventas por subir el barrido no reabre nada`() = runTest {
        val store = FakeDeliveredVisitsStore(
            entregadas = listOf(a, b),
            fotosSinEnviar = listOf(FotoSinEnviar(a, haceDias(1)), FotoSinEnviar(b, haceDias(9)))
        )
        val rep = RecordingSyncErrorReporter()
        val r = uc(store, rep, dinero = DineroPendiente { true }).execute()
        assertEquals(emptyList<String>(), store.devueltas)
        assertEquals(0, r.devueltasPorFoto)
        assertTrue(r.barridoPospuestoPorDinero)
        assertEquals(2, r.visitasConFotoEnEspera)
        assertEquals("la edad se cuenta igual", 1, r.fotosSinEnviarViejas)
        assertTrue(ERROR_CODE_BARRIDO_POSPUESTO_POR_DINERO in rep.codes)
        assertEquals("la parte by-ids sigue", listOf(a, b), store.confirmadas)
    }

    /** Si no se puede saber si hay dinero por subir, el barrido espera. */
    @Test
    fun `E-APP-079 si no se sabe si hay dinero por subir el barrido espera`() = runTest {
        val store = FakeDeliveredVisitsStore(
            entregadas = listOf(a),
            fotosSinEnviar = listOf(FotoSinEnviar(a, haceDias(1)))
        )
        val rep = RecordingSyncErrorReporter()
        val r = uc(store, rep, dinero = DineroPendiente { throw IOException("x") }).execute()
        assertEquals(emptyList<String>(), store.devueltas)
        assertTrue(r.barridoPospuestoPorDinero)
        assertTrue(ERROR_CODE_DINERO_ILEGIBLE in rep.codes)
    }

    /**
     * E-VIS-031: el barrido incluye legadas, y una legada puede venir de antes
     * de v35. Igual que la parte by-ids, un id sin forma de UUID canónico no se
     * decide: se queda como está.
     */
    @Test
    fun `E-VIS-031 un id que no es uuid canonico no se devuelve`() = runTest {
        val store = FakeDeliveredVisitsStore(
            entregadas = listOf(a),
            fotosSinEnviar = listOf(
                FotoSinEnviar("legada-7", haceDias(1)),
                FotoSinEnviar(a, haceDias(1))
            )
        )
        val r = uc(store).execute()
        assertEquals(listOf(a), store.devueltas)
        assertEquals(1, r.fotosSinEnviar)
    }

    @Test
    fun `E-APP-079 un barrido ilegible se reporta y no tumba la reconciliacion`() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a)).apply {
            failOnFotos = IOException("x")
        }
        val rep = RecordingSyncErrorReporter()
        val r = uc(store, rep).execute()
        assertTrue(ERROR_CODE_BARRIDO_ILEGIBLE in rep.codes)
        assertEquals(0, r.devueltasPorFoto)
        assertEquals("la parte by-ids sigue", listOf(a), store.confirmadas)
    }

    @Test
    fun `E-APP-079 sin fotos sin enviar no reporta nada nuevo`() = runTest {
        val store = FakeDeliveredVisitsStore(entregadas = listOf(a))
        val rep = RecordingSyncErrorReporter()
        val r = uc(store, rep).execute()
        assertEquals(0, r.devueltasPorFoto)
        assertEquals(0, r.fotosSinEnviar)
        assertEquals(0, r.fotosSinEnviarViejas)
        assertNull(r.fotoSinEnviarMasViejaMin)
        assertEquals(emptyList<String>(), rep.codes)
    }

    private companion object {
        const val MINUTOS_POR_DIA = 1_440L
    }
}
