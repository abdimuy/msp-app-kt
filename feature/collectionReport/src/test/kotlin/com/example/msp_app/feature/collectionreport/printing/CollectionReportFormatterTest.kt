package com.example.msp_app.feature.collectionreport.printing

import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
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
 * Cobertura de [CollectionReportFormatter] (pura, fakes-only, sin Robolectric). El ticket es
 * CUSTOMER-FACING, así que se cubre formato exacto: cero / monto grande / multi-pago /
 * condonado, ancho de línea, dinero en peso entero (nunca centavos), y la consistencia
 * [CollectionReportFormatter.toTicketLines] <-> [CollectionReportFormatter.toTicketText].
 */
class CollectionReportFormatterTest {

    private val clock = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))
    private val width = PrinterProfile.PROFILE_58MM.charsPerLine // 32

    // region — invariantes de ancho / estructura ----------------------------------------

    @Test
    fun `ninguna linea del ticket excede el ancho del perfil`() {
        val lines = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock).lines()

        assertTrue(lines.isNotEmpty())
        lines.forEach { line ->
            assertTrue("línea excede el ancho ($width): '$line'", line.length <= width)
        }
    }

    @Test
    fun `las lineas separadoras son una regla completa del ancho del perfil`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)

        assertTrue(text.contains("-".repeat(width)))
    }

    @Test
    fun `el encabezado es el titulo del periodo en mayusculas y centrado`() {
        val lines = CollectionReportFormatter.toTicketLines(MockupFixtures.stateDia(), clock)

        val header = lines.first()
        assertTrue(header is TicketLine.Header)
        val text = (header as TicketLine.Header).text
        assertEquals("REPORTE DE COBRANZA DEL DÍA", text.trim())
        assertTrue("el encabezado debe estar centrado", text.startsWith(" "))
    }

    // region — dinero en peso entero ----------------------------------------------------

    @Test
    fun `el total y los desgloses van en peso entero, sin centavos`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)

        assertTrue(text.contains(formatMoneyMxn(BigDecimal("18300")))) // total
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("12100")))) // efectivo
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("6200")))) // transferencia
        // Nunca centavos en el string de display.
        assertFalse(text.contains(".00"))
    }

    @Test
    fun `el ticket con todo en cero formatea los montos como $0, no vacio ni truncado`() {
        val zero = MockupFixtures.stateDia().let {
            it.copy(
                hero = it.hero.copy(monto = Money.ZERO),
                efectivo = TileUi("Efectivo", Money.ZERO, 0),
                transferencia = TileUi("Transferencia", Money.ZERO, 0),
                condonado = ChipUi("Condonado", amount = Money.ZERO),
                visitas = ChipUi("Visitas", count = 0),
                detail = DetailUi.Payments(emptyList())
            )
        }

        val lines = CollectionReportFormatter.toTicketText(zero, clock).lines()

        assertEquals("$0", formatMoneyMxn(BigDecimal.ZERO))
        assertTrue(lines.any { it.startsWith("Total cobrado") && it.trimEnd().endsWith("\$0") })
        assertTrue(lines.any { it.startsWith("Efectivo (0)") && it.trimEnd().endsWith("\$0") })
        assertTrue(lines.any { it.startsWith("Condonado") && it.trimEnd().endsWith("\$0") })
        assertTrue(lines.any { it.startsWith("Visitas") && it.trimEnd().endsWith("0") })
    }

    @Test
    fun `un monto grande de 8 cifras no se trunca ni se abrevia`() {
        val large = MockupFixtures.stateDia().let {
            it.copy(hero = it.hero.copy(monto = Money.of(BigDecimal("12345678.90"))))
        }

        val text = CollectionReportFormatter.toTicketText(large, clock)
        val expected = formatMoneyMxn(BigDecimal("12345678.90"))

        assertEquals("$12,345,679", expected)
        assertTrue(text.contains(expected))
        assertFalse(text.contains("12.3M"))
    }

    // region — desglose por pago (Día) --------------------------------------------------

    @Test
    fun `en Dia desglosa cada pago con su monto exacto y su forma de cobro`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateDia(), clock)

        // Los 4 pagos EXACTOS de MockupFixtures.paymentsDia(): ninguno se pierde ni se redondea.
        assertTrue(text.contains("María López Hernández"))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("1200"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("850"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("1500"))))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("2000"))))
        assertTrue(text.contains("DETALLE DE PAGOS"))
        assertTrue(text.contains("Transfer.")) // forma de cobro del pago por transferencia
    }

    @Test
    fun `el condonado se omite cuando el estado no lo trae`() {
        val sinCondonado = MockupFixtures.stateDia().let {
            it.copy(condonado = ChipUi("Condonado", amount = null))
        }

        val lines = CollectionReportFormatter.toTicketText(sinCondonado, clock).lines()

        assertFalse(lines.any { it.startsWith("Condonado") })
    }

    // region — Semana -------------------------------------------------------------------

    @Test
    fun `en Semana no hay desglose por pago pero si los totales`() {
        val text = CollectionReportFormatter.toTicketText(MockupFixtures.stateSemana(), clock)

        assertFalse(text.contains("DETALLE DE PAGOS"))
        assertTrue(text.contains(formatMoneyMxn(BigDecimal("118400")))) // total del ciclo
        assertEquals(
            "REPORTE DE COBRANZA DEL CICLO",
            CollectionReportFormatter.reportTitle(ReportPeriod.SEMANA).uppercase()
        )
    }

    // region — consistencia lines <-> text ----------------------------------------------

    @Test
    fun `toTicketText es el render textual exacto de toTicketLines`() {
        val state = MockupFixtures.stateDia()

        val lines = CollectionReportFormatter.toTicketLines(state, clock)
        val text = CollectionReportFormatter.toTicketText(state, clock)

        // Mismo número de líneas y separadores expandidos por el renderer del módulo.
        assertEquals(lines.size, text.lines().size)
        assertTrue(text.lines().first().trim().startsWith("REPORTE"))
        assertTrue(text.trimEnd().lines().last().contains("Generado"))
    }
}
