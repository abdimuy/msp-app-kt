package com.example.msp_app.core.common.sync.pendingwork.domain.usecases

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.FotoSinEnviar
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_BARRIDO_ILEGIBLE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVUELTA_A_PENDIENTE
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_DEVUELTA_POR_FOTO
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase.Companion.ERROR_CODE_FOTO_ABANDONADA
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeDeliveredVisitsStore
import com.example.msp_app.core.common.sync.pendingwork.fakes.FakeVisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.fakes.RecordingSyncErrorReporter
import com.example.msp_app.core.common.visitas.envio.ReglaDePoda
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Barrido de fotos sin enviar en la reconciliación inversa (plan visitas P1,
 * cierre del HALLAZGO de E-APP-076; E-APP-079).
 *
 * Una visita CONFIRMADA con una foto sin `SUBIDA_EN` cuyo archivo local
 * existe (la foto entró después de que la visita se confirmó, por otro camino
 * o por el propio worker) no la vuelve a mandar nadie. El barrido la devuelve
 * a pendiente; el reenvío idempotente repara la foto en el servidor
 * (`planImagenes`, E-VIS-004). Acotado por la edad de la foto
 * ([ReglaDePoda.FOTOS_SIN_ENVIAR_DIAS]): una foto que el servidor nunca toma
 * no devuelve su visita cada día para siempre; se reporta como abandonada.
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

    /** El tope: una foto más vieja que la ventana ya no devuelve su visita; se reporta. */
    @Test
    fun `E-APP-079 la foto mas vieja que la ventana no devuelve la visita y se reporta abandonada`() =
        runTest {
            val dias = ReglaDePoda.FOTOS_SIN_ENVIAR_DIAS.toLong()
            val store = FakeDeliveredVisitsStore(
                entregadas = listOf(a, b),
                fotosSinEnviar = listOf(
                    FotoSinEnviar(a, haceDias(dias + 1)),
                    FotoSinEnviar(b, haceDias(dias - 1)),
                    FotoSinEnviar(c, "no-es-fecha")
                )
            )
            val rep = RecordingSyncErrorReporter()
            val r = uc(store, rep).execute()

            assertEquals("solo la vigente", listOf(b), store.devueltas)
            assertEquals(1, r.devueltasPorFoto)
            assertEquals("la vieja y la ilegible se abandonan", 2, r.fotosAbandonadas)
            assertEquals(
                "2",
                rep.reported.single { it.code == ERROR_CODE_FOTO_ABANDONADA }.props["fotos"]
            )
        }

    @Test
    fun `E-APP-079 el limite de la ventana es exacto`() = runTest {
        val dias = ReglaDePoda.FOTOS_SIN_ENVIAR_DIAS.toLong()
        val enElLimite =
            FakeDeliveredVisitsStore(
                entregadas = listOf(a),
                fotosSinEnviar = listOf(FotoSinEnviar(a, haceDias(dias)))
            )
        assertEquals(
            "en el límite todavía se devuelve",
            1,
            uc(enElLimite).execute().devueltasPorFoto
        )
        val unSegundoDespues = FakeDeliveredVisitsStore(
            entregadas = listOf(a),
            fotosSinEnviar = listOf(
                FotoSinEnviar(a, ahora.minusSeconds(dias * 86_400 + 1).toString())
            )
        )
        val r = uc(unSegundoDespues).execute()
        assertEquals(0, r.devueltasPorFoto)
        assertEquals(1, r.fotosAbandonadas)
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
        assertEquals(0, r.fotosAbandonadas)
        assertEquals(emptyList<String>(), rep.codes)
    }
}
