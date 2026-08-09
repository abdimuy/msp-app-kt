package com.example.msp_app.feature.collectionreport.ui.actions

import android.content.Intent
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.ui.ChipUi
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.TileUi
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de [ReportActionsController] — Compartir/PDF/ticket de impresión (Task 8, AUDIT
 * +REWRITE de `PdfGenerator`/`ReportActions` viejos). **Dinero-seguro:** todo assert de monto
 * compara contra [formatMoneyMxn] (nunca `Double.toCurrency`) — es exactamente el bug que este
 * archivo corrige (ver su KDoc).
 */
class ReportActionsControllerTest : RobolectricTestBase() {

    private val clock = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))

    @Test
    fun `el texto para compartir trae cobrador, rango y los montos formateados sin truncar centavos`() {
        val state = MockupFixtures.stateDia()

        val text = ReportActionsController.buildShareText(state)

        assertTrue(text.contains(MockupFixtures.COBRADOR))
        assertTrue(text.contains(state.rangeLabel))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("18300"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("12100"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("6200"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("1400"))))
        assertTrue(text.contains("14"))
    }

    @Test
    fun `el intent de compartir es ACTION_SEND de texto plano con el resumen`() {
        val intent = ReportActionsController.buildShareIntent(MockupFixtures.stateDia())

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertTrue(intent.getStringExtra(Intent.EXTRA_TEXT)!!.isNotBlank())
    }

    @Test
    fun `el ticket de impresion desglosa cada pago del dia con su monto exacto`() {
        val ticket = ReportActionsController.buildTicketText(MockupFixtures.stateDia(), clock)

        assertTrue(ticket.contains("María López Hernández"))
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("1200"))))
        assertTrue(ticket.contains("Total cobrado: " + formatMoneyMxn(BigDecimal("18300"))))
    }

    @Test
    fun `el ticket en Semana no truena sin desglose por pago, solo totales`() {
        val ticket = ReportActionsController.buildTicketText(MockupFixtures.stateSemana(), clock)

        assertTrue(ticket.contains("Total cobrado: " + formatMoneyMxn(BigDecimal("118400"))))
    }

    // region — fix round 1 (minor): dinero cero / grande / multi-monto ------------------

    @Test
    fun `el ticket con todo en cero formatea el total como $0-00, no vacio ni truncado`() {
        val zeroState = MockupFixtures.stateDia().copy(
            hero = MockupFixtures.stateDia().hero.copy(monto = Money.ZERO),
            efectivo = TileUi("Efectivo", Money.ZERO, 0),
            transferencia = TileUi("Transferencia", Money.ZERO, 0),
            condonado = ChipUi("Condonado", amount = Money.ZERO),
            visitas = ChipUi("Visitas", count = 0),
            detail = DetailUi.Payments(emptyList())
        )

        val ticket = ReportActionsController.buildTicketText(zeroState, clock)

        assertEquals(formatMoneyMxn(BigDecimal.ZERO), "$0.00")
        assertTrue(ticket.contains("Total cobrado: \$0.00"))
        assertTrue(ticket.contains("Efectivo (0): \$0.00"))
        assertTrue(ticket.contains("Transferencia (0): \$0.00"))
        assertTrue(ticket.contains("Condonado: \$0.00"))
        assertTrue(ticket.contains("Visitas: 0"))
    }

    @Test
    fun `el ticket con un monto grande de 8 cifras no trunca ni redondea digitos`() {
        val largeAmount = Money.of(BigDecimal("12345678.90"))
        val largeState = MockupFixtures.stateDia()
            .let { it.copy(hero = it.hero.copy(monto = largeAmount)) }

        val ticket = ReportActionsController.buildTicketText(largeState, clock)
        val expected = formatMoneyMxn(BigDecimal("12345678.90"))

        assertEquals("$12,345,678.90", expected)
        assertTrue(ticket.contains("Total cobrado: $expected"))
        // Nunca una versión corta/redondeada del monto (p. ej. "$12,345,679" sin centavos, o
        // "$12.3M" abreviado) — el string completo, exacto, debe aparecer tal cual.
        assertFalse(ticket.contains("Total cobrado: \$12,345,679"))
    }

    @Test
    fun `el ticket con multiples pagos muestra cada monto exacto y los totales coinciden con el estado`() {
        val state = MockupFixtures.stateDia()

        val ticket = ReportActionsController.buildTicketText(state, clock)

        // Los 4 pagos EXACTOS de MockupFixtures.paymentsDia() — ninguno se pierde ni se
        // redondea al desglosar.
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("1200")))) // María López
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("850")))) // Juan Pérez
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("1500")))) // Rosa Martínez
        assertTrue(ticket.contains(formatMoneyMxn(BigDecimal("2000")))) // Pedro Sánchez
        // Los totales del ticket son EXACTAMENTE los del estado (mismo `Money`, no una suma
        // recalculada aparte que pudiera divergir).
        assertTrue(
            ticket.contains(
                "Total cobrado: " + formatMoneyMxn(state.hero.monto.amount)
            )
        )
        assertTrue(
            ticket.contains(
                "Efectivo (${state.efectivo.count}): " + formatMoneyMxn(state.efectivo.amount.amount)
            )
        )
        assertTrue(
            ticket.contains(
                "Transferencia (${state.transferencia.count}): " +
                    formatMoneyMxn(state.transferencia.amount.amount)
            )
        )
    }

    // endregion

    @Test
    fun `el intent del ticket es ACTION_SEND de texto plano con el ticket completo`() {
        val state = MockupFixtures.stateDia()
        val intent = ReportActionsController.buildTicketShareIntent(state, clock)

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals(
            ReportActionsController.buildTicketText(state, clock),
            intent.getStringExtra(Intent.EXTRA_TEXT)
        )
    }

    // `generatePdf` (android.graphics.pdf.PdfDocument real) queda SIN unit test: el shadow de
    // Robolectric para PdfDocument no soporta `startPage`/escritura de contenido en esta
    // versión (`IllegalStateException: document is closed!` incluso con
    // `@GraphicsMode(NATIVE)`) — limitación conocida de Robolectric, no un bug de este
    // código. Mismo criterio que el `PdfGenerator` viejo (`:app`), que tampoco tenía
    // cobertura de su render real — lo que SÍ queda probado (arriba) es
    // [ReportActionsController.buildTicketText], la única fuente de contenido que
    // [ReportActionsController.generatePdf] escribe línea por línea al PDF.

    @Test
    fun `pdfFileName es determinista por periodo`() {
        assertEquals(
            "reporte_cobranza_dia.pdf",
            ReportActionsController.pdfFileName(MockupFixtures.stateDia())
        )
        assertEquals(
            "reporte_cobranza_semana.pdf",
            ReportActionsController.pdfFileName(MockupFixtures.stateSemana())
        )
    }
}
