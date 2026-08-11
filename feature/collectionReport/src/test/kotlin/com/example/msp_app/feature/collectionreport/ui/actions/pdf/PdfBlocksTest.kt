package com.example.msp_app.feature.collectionreport.ui.actions.pdf

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.ui.ForgivenessRowUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi
import com.example.msp_app.feature.collectionreport.ui.VisitRowUi
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de [buildPdfBlocks] — convierte [PdfReportModel] (dinero-seguro, ya armado por
 * [buildPdfReportModel]) en el flujo de [PdfBlock] que [paginatePdfBlocks] reparte en páginas.
 * Se construye [PdfReportModel] a mano (sin pasar por [CollectionReportUiState]) para poner el
 * foco en las decisiones de ESTE archivo: qué secciones se omiten, el orden de las filas, el
 * zebra alternado, y el wrap de la nota de visita.
 */
class PdfBlocksTest {

    private val header = PdfHeaderModel(
        eyebrow = "MUEBLERÍA MSP · COBRANZA",
        title = "Reporte de cobranza",
        subtitle = "Día · viernes 7 ago 2026",
        cobradorLine = "Cobrador: Gabriel Roque",
        generatedLine = "Generado: 07/08/2026 18:00"
    )

    private val summary = PdfSummaryModel(
        cells = listOf(
            PdfSummaryCell("Total pagos", "2", PdfEmphasis.INK),
            PdfSummaryCell("Recaudado", "$2,050", PdfEmphasis.INK),
            PdfSummaryCell("Visitas", "0", PdfEmphasis.INK),
            PdfSummaryCell("Condonado", "$0", PdfEmphasis.VIOLET)
        )
    )

    private fun payment(
        id: String,
        cliente: String,
        method: PaymentMethod,
        monto: String,
        hora: String
    ) = PaymentRowUi(
        id = id,
        cliente = cliente,
        ventaLabel = "Venta $id",
        paidAt = AppTime.parseWireFormat("2026-08-07T$hora:00"),
        amount = Money.of(BigDecimal(monto)),
        method = method,
        synced = true
    )

    private fun model(
        payments: List<PaymentRowUi> = emptyList(),
        condonaciones: List<ForgivenessRowUi> = emptyList(),
        visits: List<VisitRowUi> = emptyList()
    ) = PdfReportModel(
        header = header,
        summary = summary,
        payments = payments,
        condonaciones = condonaciones,
        totals = listOf(PdfTotalLine("Total recaudado", "$2,050", PdfEmphasis.INK, bold = true)),
        visits = visits,
        footer = PdfFooterModel("Generado por Gabriel Roque · 07/08/2026 18:00")
    )

    private val noWrapMeasure: (String) -> Float = { 0f } // nunca excede maxWidth -> nunca envuelve

    @Test
    fun `sin pagos omite la seccion de pagos entera, sin encabezado suelto`() {
        val blocks = buildPdfBlocks(model(), noWrapMeasure)

        assertFalse(blocks.any { it is PdfBlock.SectionTitle && it.text == "DETALLE DE PAGOS" })
        assertFalse(blocks.any { it is PdfBlock.PaymentTableHeader })
    }

    @Test
    fun `con pagos arma titulo, encabezado de tabla y una fila por pago, en el orden del modelo`() {
        val payments = listOf(
            payment("p1", "María López Hernández", PaymentMethod.EFECTIVO, "1200", "09:12"),
            payment("p2", "Juan Pérez Ramírez", PaymentMethod.TRANSFERENCIA, "850", "09:40")
        )

        val blocks = buildPdfBlocks(model(payments = payments), noWrapMeasure)

        val sectionIndex = blocks.indexOfFirst {
            it is PdfBlock.SectionTitle && it.text == "DETALLE DE PAGOS"
        }
        val headerIndex = blocks.indexOfFirst { it is PdfBlock.PaymentTableHeader }
        val rowIds = blocks.filterIsInstance<PdfBlock.PaymentRow>().map { it.data.id }

        assertTrue(sectionIndex in 0 until headerIndex)
        assertEquals(listOf("p1", "p2"), rowIds)
    }

    @Test
    fun `las filas de pago alternan zebra por indice`() {
        val payments = (1..4).map {
            payment("p$it", "Cliente $it", PaymentMethod.EFECTIVO, "100", "09:0$it")
        }

        val zebras = buildPdfBlocks(model(payments = payments), noWrapMeasure)
            .filterIsInstance<PdfBlock.PaymentRow>()
            .map { it.zebra }

        assertEquals(listOf(true, false, true, false), zebras)
    }

    @Test
    fun `metodo efectivo se pinta verde y transferencia slate`() {
        val payments = listOf(
            payment("p1", "Cliente A", PaymentMethod.EFECTIVO, "100", "09:00"),
            payment("p2", "Cliente B", PaymentMethod.TRANSFERENCIA, "100", "09:00")
        )

        val rows = buildPdfBlocks(
            model(payments = payments),
            noWrapMeasure
        ).filterIsInstance<PdfBlock.PaymentRow>()

        assertEquals(PdfEmphasis.GREEN, rows[0].data.metodoEmphasis)
        assertEquals(PdfEmphasis.SLATE, rows[1].data.metodoEmphasis)
    }

    @Test
    fun `sin condonaciones omite la seccion entera`() {
        val blocks = buildPdfBlocks(model(), noWrapMeasure)
        assertFalse(blocks.any { it is PdfBlock.SectionTitle && it.text == "CONDONACIONES" })
    }

    @Test
    fun `con condonaciones arma una fila violeta por cada una`() {
        val rows = listOf(
            ForgivenessRowUi("Ana Ruiz", "", Money.of(BigDecimal("600"))),
            ForgivenessRowUi("Luis Gómez", "", Money.of(BigDecimal("500")))
        )

        val blocks = buildPdfBlocks(model(condonaciones = rows), noWrapMeasure)

        assertTrue(blocks.any { it is PdfBlock.SectionTitle && it.text == "CONDONACIONES" })
        val clientes = blocks.filterIsInstance<PdfBlock.CondonacionRow>().map { it.data.cliente }
        assertEquals(listOf("Ana Ruiz", "Luis Gómez"), clientes)
    }

    @Test
    fun `totales siempre se dibujan, incluso sin pagos ni condonaciones`() {
        val blocks = buildPdfBlocks(model(), noWrapMeasure)
        assertTrue(blocks.contains(PdfBlock.TotalsRule))
        assertTrue(blocks.any { it is PdfBlock.TotalLine && it.line.label == "Total recaudado" })
    }

    @Test
    fun `sin visitas omite la seccion entera`() {
        val blocks = buildPdfBlocks(model(), noWrapMeasure)
        assertFalse(blocks.any { it is PdfBlock.SectionTitle && it.text == "VISITAS" })
    }

    @Test
    fun `la nota de una visita se envuelve con el medidor inyectado, texto completo sin truncar`() {
        val nota = "Cliente pidió que regrese la próxima semana porque anda de viaje de trabajo"
        val visit = VisitRowUi(
            cliente = "Diego Mora",
            nota = nota,
            tipo = "Pidió que regrese otro día",
            visitedAt = AppTime.parseWireFormat("2026-08-07T17:05:00")
        )
        // Medidor determinista: 10pt por carácter -> fuerza el wrap dado VISIT_NOTE_MAX_WIDTH.
        val measure: (String) -> Float = { it.length * 10f }

        val block = buildPdfBlocks(model(visits = listOf(visit)), measure)
            .filterIsInstance<PdfBlock.VisitRow>()
            .single()

        assertTrue(
            "la nota larga debe envolverse en más de una línea",
            block.data.noteLines.size > 1
        )
        assertEquals(nota, block.data.noteLines.joinToString(" "))
        assertEquals("Pidió que regrese otro día", block.data.tipo)
    }

    @Test
    fun `una nota en blanco no produce lineas fantasma`() {
        val visit = VisitRowUi(
            cliente = "Carlos Vega",
            nota = "",
            tipo = "No se encontraba",
            visitedAt = AppTime.parseWireFormat("2026-08-07T14:15:00")
        )

        val block = buildPdfBlocks(model(visits = listOf(visit)), noWrapMeasure)
            .filterIsInstance<PdfBlock.VisitRow>()
            .single()

        assertEquals(emptyList<String>(), block.data.noteLines)
    }
}
