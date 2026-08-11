package com.example.msp_app.feature.collectionreport.ui.actions.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cobertura de [paginatePdfBlocks] — el corazón testeable de la paginación del PDF (rediseño
 * "tabla densa multipágina", ver KDoc de
 * [com.example.msp_app.feature.collectionreport.ui.actions.ReportActionsController.generatePdf]):
 * ningún bloque se pierde ni se reordena, el corte respeta el tope por página, y la tabla de
 * pagos repite su encabezado de columnas cuando el corte cae a mitad de la lista.
 */
class PdfPaginatorTest {

    private fun paymentBlock(id: String, index: Int = 0): PdfBlock.PaymentRow = PdfBlock.PaymentRow(
        PdfPaymentRowData(
            id = id,
            fecha = "10/08 09:00",
            cliente = "Cliente $id",
            metodo = "Efectivo",
            metodoEmphasis = PdfEmphasis.GREEN,
            importe = "$100"
        ),
        zebra = index % 2 == 0
    )

    private fun visitBlock(id: String): PdfBlock.VisitRow = PdfBlock.VisitRow(
        PdfVisitRowData(
            fecha = "10/08 09:00",
            cliente = "Visita $id",
            tipo = "No se encontraba",
            noteLines = listOf("Nota de $id")
        )
    )

    @Test
    fun `una lista vacia produce cero paginas`() {
        assertEquals(
            emptyList<List<PdfBlock>>(),
            paginatePdfBlocks(emptyList(), maxContentHeight = 100f)
        )
    }

    @Test
    fun `bloques que caben enteros en el tope producen una sola pagina`() {
        val blocks = listOf(PdfBlock.Spacer(10f), PdfBlock.Spacer(10f), PdfBlock.Spacer(10f))

        val pages = paginatePdfBlocks(blocks, maxContentHeight = 100f)

        assertEquals(listOf(blocks), pages)
    }

    @Test
    fun `corta exactamente cuando el acumulado excede el tope, sin perder ningun bloque`() {
        val blocks = (1..10).map { PdfBlock.Spacer(10f) }

        val pages = paginatePdfBlocks(blocks, maxContentHeight = 25f)

        assertEquals(listOf(2, 2, 2, 2, 2), pages.map { it.size })
        assertEquals(blocks, pages.flatten())
    }

    @Test
    fun `un bloque mas alto que el tope no produce una pagina vacia infinita`() {
        val blocks = listOf(PdfBlock.Spacer(500f), PdfBlock.Spacer(10f))

        val pages = paginatePdfBlocks(blocks, maxContentHeight = 100f)

        // El bloque gigante SIEMPRE se coloca (nunca se descarta) aunque exceda el tope —
        // una página vacía no tiene sentido.
        assertEquals(blocks[0], pages[0].single())
        assertEquals(blocks[1], pages[1].single())
    }

    @Test
    fun `paginar cientos de pagos y visitas no pierde ninguno y conserva el orden`() {
        val paymentIds = (1..200).map { "p$it" }
        val visitIds = (1..100).map { "v$it" }
        val blocks = buildList {
            add(PdfBlock.SectionTitle("DETALLE DE PAGOS"))
            add(PdfBlock.PaymentTableHeader)
            paymentIds.forEachIndexed { index, id -> add(paymentBlock(id, index)) }
            add(PdfBlock.Spacer(PdfLayout.SPACER_MEDIUM))
            add(PdfBlock.SectionTitle("VISITAS"))
            visitIds.forEach { id -> add(visitBlock(id)) }
        }

        val pages = paginatePdfBlocks(blocks, PdfLayout.MAX_CONTENT_HEIGHT)

        assertTrue("un reporte de este tamaño debe abarcar varias páginas", pages.size > 1)

        val flattened = pages.flatten()
        val actualPaymentIds = flattened.filterIsInstance<PdfBlock.PaymentRow>().map { it.data.id }
        val actualVisitIds = flattened.filterIsInstance<PdfBlock.VisitRow>().map { it.data.cliente }

        assertEquals("ningún pago se pierde y el orden se conserva", paymentIds, actualPaymentIds)
        assertEquals(
            "ninguna visita se pierde y el orden se conserva",
            visitIds.map { "Visita $it" },
            actualVisitIds
        )
    }

    @Test
    fun `ninguna pagina arranca con una fila de pago sin su encabezado de columnas`() {
        val blocks = buildList {
            add(PdfBlock.PaymentTableHeader)
            (1..200).forEachIndexed { index, i -> add(paymentBlock("p$i", index)) }
        }

        val pages = paginatePdfBlocks(blocks, PdfLayout.MAX_CONTENT_HEIGHT)

        assertTrue(pages.size > 1)
        pages.forEach { page ->
            if (page.isNotEmpty() && page.first() is PdfBlock.PaymentRow) {
                error("una página arrancó con PaymentRow sin PaymentTableHeader antes: $page")
            }
        }
        // El encabezado se repite al menos una vez además del natural de la primera página.
        val headerCount = pages.flatten().count { it is PdfBlock.PaymentTableHeader }
        assertTrue("el encabezado debe repetirse en las páginas de continuación", headerCount > 1)
    }
}
