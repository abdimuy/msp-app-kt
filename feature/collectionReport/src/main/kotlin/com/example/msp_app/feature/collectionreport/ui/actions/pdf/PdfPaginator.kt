package com.example.msp_app.feature.collectionreport.ui.actions.pdf

/**
 * Reparte el flujo de [PdfBlock] de [buildPdfBlocks] en páginas de a lo más [maxContentHeight]
 * puntos cada una — el corazón de la paginación manual del PDF (spec del dispatch: "cuando el
 * cursor cruzaría el margen inferior, termina la página, abre una nueva, y — dentro de la tabla
 * de pagos — repite el encabezado de columna antes de seguir").
 *
 * **Ningún bloque se pierde ni se reordena:** este helper solo DECIDE los cortes de página; la
 * única inserción que hace es un [PdfBlock.PaymentTableHeader] sintético al inicio de una
 * página cuando el corte cae A MITAD de una racha de [PdfBlock.PaymentRow] — exactamente el
 * caso "repite el encabezado" del spec. El resto de los bloques viaja en el MISMO orden que
 * [buildPdfBlocks] produjo, así `pages.flatten()` menos los encabezados sintéticos es
 * literalmente [blocks] — invariante que cubren los tests de este archivo.
 *
 * **Página vacía como caso base:** [blocks] vacío -> lista de páginas vacía (el caller decide
 * si eso significa "una página en blanco", mismo criterio que el `paginate` de líneas que este
 * helper reemplaza).
 */
internal fun paginatePdfBlocks(
    blocks: List<PdfBlock>,
    maxContentHeight: Float
): List<List<PdfBlock>> {
    if (blocks.isEmpty()) return emptyList()
    val pages = mutableListOf<MutableList<PdfBlock>>()
    var current = mutableListOf<PdfBlock>()
    var used = 0f
    var inPaymentsRun = false

    fun flushPage() {
        pages.add(current)
        current = mutableListOf()
        used = 0f
    }

    for (block in blocks) {
        inPaymentsRun = when (block) {
            is PdfBlock.PaymentRow -> true
            PdfBlock.PaymentTableHeader -> inPaymentsRun
            else -> false
        }

        if (current.isNotEmpty() && used + block.height > maxContentHeight) {
            flushPage()
            if (inPaymentsRun) {
                current.add(PdfBlock.PaymentTableHeader)
                used += PdfBlock.PaymentTableHeader.height
            }
        }

        current.add(block)
        used += block.height
    }
    pages.add(current)
    return pages
}
