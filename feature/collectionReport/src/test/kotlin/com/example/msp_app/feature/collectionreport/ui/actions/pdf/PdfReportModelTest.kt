package com.example.msp_app.feature.collectionreport.ui.actions.pdf

import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.collectionreport.ui.DetailSort
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de [buildPdfReportModel] — el mapeo de
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState] al modelo puro del
 * PDF rediseñado. Reusa [MockupFixtures] (misma fuente de datos que el ticket/sheets) para no
 * divergir de lo que YA cubren esos tests — el foco aquí es el contrato NUEVO: orden por
 * [DetailSort], TODOS los pagos en ambos periodos, y las reglas de qué línea de totales se
 * omite.
 */
class PdfReportModelTest {

    private val clock = FakeClock(Instant.parse("2026-08-07T18:00:00Z"))

    @Test
    fun `dia ordena los pagos por hora cuando el sort es HORA`() {
        val state = MockupFixtures.stateDia().copy(sort = DetailSort.HORA)

        val model = buildPdfReportModel(state, clock)

        val expectedOrder =
            listOf(
                "María López Hernández",
                "Juan Pérez Ramírez",
                "Rosa Martínez Cruz",
                "Pedro Sánchez Ortiz"
            )
        assertEquals(expectedOrder, model.payments.map { it.cliente })
    }

    @Test
    fun `dia ordena los pagos por nombre cuando el sort es NOMBRE`() {
        val state = MockupFixtures.stateDia().copy(sort = DetailSort.NOMBRE)

        val model = buildPdfReportModel(state, clock)

        val expected = model.payments.map { it.cliente.lowercase() }
        assertEquals(expected.sorted(), expected)
    }

    @Test
    fun `dia trae los 4 pagos exactos de la fixture, ninguno se pierde`() {
        val model = buildPdfReportModel(MockupFixtures.stateDia(), clock)
        assertEquals(4, model.payments.size)
    }

    @Test
    fun `semana no conserva los pagos en 'detail' (Days) - se aplanan desde dayPayments`() {
        val state = MockupFixtures.stateSemana()
        val expectedCount = MockupFixtures.dayPaymentsSemana().sumOf { it.size }

        val model = buildPdfReportModel(state, clock)

        assertEquals(expectedCount, model.payments.size)
    }

    @Test
    fun `el resumen trae total de pagos, recaudado, visitas y condonado del estado`() {
        val state = MockupFixtures.stateDia()

        val summary = buildPdfReportModel(state, clock).summary

        val byLabel = summary.cells.associate { it.label to it.value }
        assertEquals("4", byLabel["Total pagos"])
        assertEquals(formatMoneyMxn(state.hero.monto.amount), byLabel["Recaudado"])
        assertEquals(state.visitas.count.toString(), byLabel["Visitas"])
        assertEquals(formatMoneyMxn(state.condonado.amount!!.amount), byLabel["Condonado"])
    }

    @Test
    fun `totales incluyen efectivo y transferencia siempre, condonado solo si hay condonaciones`() {
        val withCondonaciones = buildPdfReportModel(MockupFixtures.stateDia(), clock).totals
        assertTrue(withCondonaciones.any { it.label.startsWith("Efectivo") })
        assertTrue(withCondonaciones.any { it.label.startsWith("Transferencia") })
        assertTrue(withCondonaciones.any { it.label.startsWith("Condonado") })

        val withoutCondonaciones = buildPdfReportModel(
            MockupFixtures.stateDia().copy(condonadoRows = emptyList()),
            clock
        ).totals
        assertTrue(withoutCondonaciones.none { it.label.startsWith("Condonado") })
    }

    @Test
    fun `total recaudado es la ultima linea y va en negritas`() {
        val totals = buildPdfReportModel(MockupFixtures.stateDia(), clock).totals
        val last = totals.last()
        assertEquals("Total recaudado", last.label)
        assertTrue(last.bold)
        assertEquals(formatMoneyMxn(MockupFixtures.stateDia().hero.monto.amount), last.value)
    }

    @Test
    fun `el titulo del pie incluye al cobrador y la fecha generada`() {
        val footer = buildPdfReportModel(MockupFixtures.stateDia(), clock).footer
        assertTrue(footer.leftLabel.contains(MockupFixtures.COBRADOR))
        assertTrue(footer.leftLabel.contains("07/08/2026"))
    }

    @Test
    fun `montos sin condonado formatean cero, nunca vacio`() {
        val state = MockupFixtures.stateDia().copy(
            condonado = MockupFixtures.stateDia().condonado.copy(amount = null),
            condonadoRows = emptyList()
        )

        val summary = buildPdfReportModel(state, clock).summary
        val condonadoCell = summary.cells.single { it.label == "Condonado" }

        assertEquals(formatMoneyMxn(BigDecimal.ZERO), condonadoCell.value)
    }
}
