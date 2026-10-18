package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.common.sync.pendingwork.domain.ports.VisitCustodyRegistry
import com.example.msp_app.core.common.sync.pendingwork.domain.usecases.ReconcileDeliveredVisitsUseCase
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.visitas.envio.ReglaDePoda
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.workers.pendingVisit
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * E-VIS-028 (plan visitas P1, Tarea 13, hallazgo C1 de la revisión): la
 * reconciliación inversa pregunta cada día por las confirmadas de los últimos
 * 30 días; si al confirmarlas otra vez reescribía `CONFIRMADA_EN` con la hora
 * de la corrida, la visita nunca envejecía y la poda de la Tarea 12 no la
 * borraba jamás (la tabla y el tráfico de by-ids crecían sin tope).
 *
 * Forma de la medición del revisor: confirmada el día D, N corridas diarias
 * con el servidor nombrándola, y en D+31 sigue podable.
 */
class InversaNoRejuveneceLaConfirmadaTest : RoomTestBase() {

    private val id = "6f1c2a3b-0000-4000-8000-0000000000c1"
    private val diaD = Instant.parse("2026-09-20T16:00:00Z")

    private val servidorQueLaTiene = object : VisitCustodyRegistry {
        override suspend fun findExisting(visitIds: List<String>): List<String> = visitIds
    }

    private val sinReporte = object : SyncErrorReporter {
        override fun report(code: String, message: String, props: Map<String, String>) = Unit
    }

    private suspend fun corridaDiaria(cuando: Instant) = ReconcileDeliveredVisitsUseCase(
        store = RoomDeliveredVisitsStore(db.visitDao(), db.visitImageDao()),
        registry = servidorQueLaTiene,
        errorReporter = sinReporte,
        clock = { cuando }
    ).execute()

    private suspend fun podarEn(cuando: Instant) = db.visitDao().deleteUploadedVisits(
        confirmadasAntesDe = ReglaDePoda.limiteConfirmadas(cuando),
        resguardadasAntesDe = ReglaDePoda.limiteResguardadas(cuando),
        comprobantesDesde = AppTime.toWireFormat(cuando),
        conservarCompromisosDesde = "2000-01-01"
    )

    private suspend fun sembrarConfirmadaEnD() {
        db.visitDao().insertVisit(pendingVisit(id = id))
        db.visitDao().confirmar(listOf(id), AppTime.toWireFormat(diaD))
    }

    @Test
    fun `E-VIS-028 confirmada el dia D sigue podable en D+31 tras corridas diarias`() = runTest {
        sembrarConfirmadaEnD()

        // Una corrida por día, D+1 … D+30: dentro de la ventana, así que el
        // servidor la nombra todos los días.
        var preguntada = 0
        (1L..30L).forEach { dia ->
            preguntada += corridaDiaria(diaD.plus(Duration.ofDays(dia))).revisadas
        }

        assertEquals("se preguntó por ella las 30 veces", 30, preguntada)
        assertEquals(AppTime.toWireFormat(diaD), db.visitDao().findVisitById(id)!!.CONFIRMADA_EN)

        podarEn(diaD.plus(Duration.ofDays(31)))
        assertNull(db.visitDao().findVisitById(id))
    }

    /** Control positivo: en D+29 la poda todavía no puede borrarla. */
    @Test
    fun `control positivo la poda respeta la ventana de 30 dias`() = runTest {
        sembrarConfirmadaEnD()

        podarEn(diaD.plus(Duration.ofDays(29)))

        assertNotNull(db.visitDao().findVisitById(id))
    }
}
