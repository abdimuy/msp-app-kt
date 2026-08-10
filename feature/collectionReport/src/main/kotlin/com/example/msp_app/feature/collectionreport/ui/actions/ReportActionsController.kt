package com.example.msp_app.feature.collectionreport.ui.actions

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.printing.CollectionReportFormatter
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import java.io.File
import java.io.FileOutputStream

/**
 * Reescritura (AUDIT+REWRITE, DISPATCH-CONVENTIONS) de la generación de PDF / resumen para
 * compartir / ticket de impresión térmica del reporte de cobranza viejo
 * (`:app` `core/utils/PdfGenerator.kt` + `ReportActions.kt`).
 *
 * **AUDIT — bug de dinero encontrado y corregido:** el código viejo formatea montos con
 * `Double.toCurrency(noDecimals = true)` (`core/utils/CurrencyUtils.kt`), que redondea a
 * partir de un `Double` (no exacto — el money-path del piloto es `Money`/`BigDecimal`
 * siempre). Este archivo formatea TODO con [formatMoneyMxn] (`BigDecimal`, peso entero
 * HALF_UP para display — decisión de negocio: MSP no opera con centavos) — mismo contrato
 * que el tablero. Es una corrección consciente, no un cambio accidental de output.
 *
 * **Impresión térmica Bluetooth (P2, ya cableada):** la impresión real ahora vive en
 * `CollectionReportViewModel.printReport` -> `PrinterPort.print` (`:core:printing`), a partir
 * del MISMO contenido de ticket que produce [CollectionReportFormatter]. Este controller ya
 * no arma el ticket a mano: [buildTicketText] delega en [CollectionReportFormatter.toTicketText]
 * para que el PDF, "Compartir ticket" y la impresora impriman EXACTAMENTE el mismo texto
 * (una sola fuente de verdad, dinero en peso entero vía [formatMoneyMxn]).
 *
 * Una sola fachada (no varios objetos por acción) a propósito — mismo criterio que
 * `ReportAggregator` (Task 3): Compartir/PDF/ticket son, semánticamente, una superficie
 * pequeña de funciones puras + I/O ligero de la MISMA responsabilidad ("qué le pasa el
 * cobrador al reporte hacia afuera"); partirla por acción dispersaría helpers compartidos
 * (`reportTitle`/`money`/`center`) sin ganar legibilidad, de ahí el [Suppress].
 */
@Suppress("TooManyFunctions")
internal object ReportActionsController {

    private const val PDF_PAGE_WIDTH = 612
    private const val PDF_PAGE_HEIGHT = 792
    private const val PDF_MARGIN = 40f
    private const val PDF_LINE_SPACING = 15
    private const val PDF_TEXT_SIZE = 10f
    private const val PDF_MIME_TYPE = "application/pdf"
    private const val TEXT_MIME_TYPE = "text/plain"
    private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

    // region — texto para Compartir / ticket de impresión (puro, dinero-seguro) ----------

    /**
     * Resumen corto para `Intent.ACTION_SEND` (Compartir, mockup `.actions .ghost` share):
     * periodo + rango + total + duo + condonado/visitas. Cifras SIEMPRE en claro (compartir
     * un resumen enmascarado no tiene sentido — el usuario decide a quién se lo manda).
     */
    fun buildShareText(state: CollectionReportUiState): String = buildString {
        appendLine(reportTitle(state.period))
        appendLine("Cobrador: ${state.cobrador}")
        appendLine("Rango: ${state.rangeLabel}")
        appendLine("Total cobrado: ${money(state.hero.monto)}")
        appendLine("Efectivo: ${money(state.efectivo.amount)} (${state.efectivo.count} pagos)")
        appendLine(
            "Transferencia: ${money(
                state.transferencia.amount
            )} (${state.transferencia.count} pagos)"
        )
        state.condonado.amount?.let { appendLine("Condonado: ${money(it)}") }
        state.visitas.count?.let { appendLine("Visitas: $it") }
    }.trim()

    /** `Intent.ACTION_SEND` de texto plano con [buildShareText] — real, listo para lanzar. */
    fun buildShareIntent(state: CollectionReportUiState): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = TEXT_MIME_TYPE
            putExtra(Intent.EXTRA_SUBJECT, reportTitle(state.period))
            putExtra(Intent.EXTRA_TEXT, buildShareText(state))
        }

    /**
     * `Intent.ACTION_SEND` del [buildTicketText] como texto plano — puente temporal de
     * "Imprimir" mientras la conexión Bluetooth real queda PARKED FOR USER (ver KDoc de
     * archivo): comparte el mismo ticket dinero-seguro que una impresora térmica recibiría,
     * así el botón hace algo real y útil (copiar/enviar a una app de impresión que el
     * cobrador ya tenga) en vez de un no-op silencioso.
     */
    fun buildTicketShareIntent(
        state: CollectionReportUiState,
        clock: AppClock = AppClock.System
    ): Intent = Intent(Intent.ACTION_SEND).apply {
        type = TEXT_MIME_TYPE
        putExtra(Intent.EXTRA_SUBJECT, "Ticket de cobranza")
        putExtra(Intent.EXTRA_TEXT, buildTicketText(state, clock))
    }

    /**
     * Ticket completo, delegado a [CollectionReportFormatter.toTicketText]: encabezado +
     * desglose por pago (Día, si el estado los conserva) + totales + condonaciones + visitas,
     * con dinero SIEMPRE en peso entero vía [formatMoneyMxn]. Es el MISMO contenido/layout que
     * imprime la impresora térmica (P2) y que escribe el PDF — una sola fuente de verdad.
     */
    fun buildTicketText(state: CollectionReportUiState, clock: AppClock = AppClock.System): String =
        CollectionReportFormatter.toTicketText(state, clock)

    // endregion

    // region — PDF (PdfDocument real, contenido dinero-seguro) --------------------------

    /**
     * Genera el PDF del reporte del rango actual en `context.cacheDir` (mismo directorio que
     * el `<cache-path path="."/>` que `:app` ya declara para su `FileProvider`, ver
     * `AndroidManifest.xml`) y devuelve el archivo. Contenido = [buildTicketText] línea por
     * línea (reusa el mismo texto dinero-seguro que el ticket — una sola fuente de verdad
     * para "qué dice el PDF/impresión", en vez de un layout de tabla independiente como el
     * `PdfGenerator` viejo, que podía divergir).
     */
    fun generatePdf(
        context: Context,
        state: CollectionReportUiState,
        fileName: String,
        clock: AppClock = AppClock.System
    ): File {
        val document = PdfDocument()
        val paint = android.graphics.Paint().apply {
            textSize = PDF_TEXT_SIZE
            typeface = Typeface.MONOSPACE
        }
        val pageInfo = PdfDocument.PageInfo.Builder(PDF_PAGE_WIDTH, PDF_PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        var yPos = PDF_MARGIN
        buildTicketText(state, clock).lineSequence().forEach { line ->
            if (yPos > PDF_PAGE_HEIGHT - PDF_MARGIN) return@forEach
            page.canvas.drawText(line, PDF_MARGIN, yPos, paint)
            yPos += PDF_LINE_SPACING
        }
        document.finishPage(page)
        val file = File(context.cacheDir, fileName)
        document.writeTo(FileOutputStream(file))
        document.close()
        return file
    }

    /** Nombre de archivo determinista (mismo criterio que `ReportActions.kt` viejo). */
    fun pdfFileName(state: CollectionReportUiState): String {
        val suffix = state.period.name.lowercase()
        return "reporte_cobranza_$suffix.pdf"
    }

    /** Uri compartible del PDF vía el `FileProvider` que `:app` ya declara. */
    fun pdfUri(context: Context, file: File) =
        FileProvider.getUriForFile(context, context.packageName + FILE_PROVIDER_SUFFIX, file)

    /** `Intent.ACTION_SEND` del PDF ya generado — real, listo para lanzar. */
    fun buildPdfShareIntent(context: Context, state: CollectionReportUiState, file: File): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = PDF_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, pdfUri(context, file))
            putExtra(Intent.EXTRA_SUBJECT, reportTitle(state.period))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    // endregion

    /** Fuente única del título (la comparte [CollectionReportFormatter] con el ticket/impresión). */
    private fun reportTitle(period: ReportPeriod): String =
        CollectionReportFormatter.reportTitle(period)

    private fun money(amount: Money): String = formatMoneyMxn(amount.amount)
}
