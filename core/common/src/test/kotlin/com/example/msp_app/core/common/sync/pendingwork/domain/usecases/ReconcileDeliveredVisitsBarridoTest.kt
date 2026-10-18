package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.FotoSinEnviar
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_BARRIDO_ILEGIBLE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVUELTA_A_PENDIENTE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVUELTA_POR_FOTO
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
        rep: RecordingSyncErrorReporter = RecordingSyncErrorReporter()
    ) = ReconcileDeliveredVisitsUseCase(
        store,
        FakeVisitCustodyRegistry(known = setOf(a, b, c)),
        rep
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

            assertEquals("ninguna se abandona", listOf(a, b, c), store.devueltas)
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
     * Pregunta (1) del arreglo: al dejar de abandonar, el residuo viejo sale
     * entero en una corrida. No hay tope por tanda en el use case (el
     * adaptador trocea el `IN` en 999); el worker diario reencola todas las
     * pendientes con `KEEP`.
     */
    @Test
    fun `E-VIS-032 el residuo viejo se devuelve entero en una corrida sin tope`() = runTest {
        val ids = (1..1_500).map { u(100 + it) }
        val store = FakeDeliveredVisitsStore(
            entregadas = ids,
            fotosSinEnviar = ids.map { FotoSinEnviar(it, haceDias(30)) }
        )
        val r = uc(store).execute()
        assertEquals(ids, store.devueltas)
        assertEquals(1_500, r.devueltasPorFoto)
        assertEquals(1_500, r.fotosSinEnviarViejas)
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
