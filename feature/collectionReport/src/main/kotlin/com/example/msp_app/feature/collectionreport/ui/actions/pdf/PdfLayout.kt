package com.example.msp_app.feature.collectionreport.ui.actions.pdf

/**
 * Constantes de layout del PDF de cobranza (Task de rediseño: tabla densa multipágina que
 * reemplaza el volcado línea-a-línea del ticket térmico de 58mm). US-Letter (612×792pt),
 * márgenes de 40pt — mismo tamaño que `PdfGenerator` (`:app`, `generateDailyReportPdf`), la
 * referencia visual de este rediseño.
 *
 * Puras (`Float`, sin `android.graphics`) a propósito: [PdfPaginator]/[buildPdfBlocks] las
 * consumen para decidir dónde corta cada página SIN necesitar un `Canvas`/`Paint` real, así la
 * paginación completa queda cubierta por unit tests JVM puros (ver KDoc de
 * [com.example.msp_app.feature.collectionreport.ui.actions.ReportActionsController.generatePdf]
 * sobre la limitación conocida de Robolectric con `PdfDocument`). [PdfCanvasRenderer] es el
 * ÚNICO archivo de este paquete que dibuja de verdad (`android.graphics.Canvas`), y usa estas
 * MISMAS constantes para que la altura reservada al paginar coincida exacto con lo que se
 * dibuja — una sola fuente de verdad de layout.
 */
internal object PdfLayout {
    const val PAGE_WIDTH = 612f
    const val PAGE_HEIGHT = 792f
    const val MARGIN = 40f
    const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

    /** Hairline + una línea de pie + aire — reservado en TODAS las páginas (spec: pie SIEMPRE). */
    const val FOOTER_RESERVED = 30f

    /** Alto disponible para el flujo de bloques en una página (de [MARGIN] a antes del pie). */
    const val MAX_CONTENT_HEIGHT = PAGE_HEIGHT - MARGIN * 2 - FOOTER_RESERVED

    // Bloques fijos (solo página 1, ver [buildPdfBlocks]).
    const val HEADER_HEIGHT = 96f
    const val SUMMARY_HEIGHT = 48f

    // Flujo de secciones.
    const val SECTION_TITLE_HEIGHT = 18f
    const val TABLE_HEADER_HEIGHT = 16f
    const val PAYMENT_ROW_HEIGHT = 14f
    const val CONDONACION_ROW_HEIGHT = 14f
    const val RECAP_RULE_HEIGHT = 10f
    const val RECAP_LINE_HEIGHT = 14f
    const val VISIT_LINE_HEIGHT = 12f
    const val VISIT_ROW_GAP = 6f

    const val SPACER_SMALL = 6f
    const val SPACER_MEDIUM = 14f

    // Texto (usado tanto por el renderer para `Paint.textSize` como para medir el wrap de notas).
    const val TITLE_TEXT_SIZE = 20f
    const val EYEBROW_TEXT_SIZE = 8f
    const val SUBTITLE_TEXT_SIZE = 10f
    const val META_TEXT_SIZE = 9f
    const val SECTION_TITLE_TEXT_SIZE = 9f
    const val TABLE_HEADER_TEXT_SIZE = 8f
    const val ROW_TEXT_SIZE = 9f
    const val SUMMARY_LABEL_TEXT_SIZE = 8f
    const val SUMMARY_VALUE_TEXT_SIZE = 13f
    const val RECAP_TEXT_SIZE = 9.5f
    const val VISIT_NOTE_TEXT_SIZE = 9f
    const val FOOTER_TEXT_SIZE = 8f

    /** Sangría de la nota de visita (bajo la línea fecha/cliente) y ancho máximo para envolver. */
    const val VISIT_NOTE_INDENT = 14f
    const val VISIT_NOTE_MAX_WIDTH = CONTENT_WIDTH - VISIT_NOTE_INDENT

    // Columnas de la tabla de pagos (fracción de [CONTENT_WIDTH] desde [MARGIN]).
    const val COL_FECHA_WIDTH = 70f
    const val COL_VALUE_WIDTH = 80f
    const val COL_METODO_WIDTH = 110f
}
