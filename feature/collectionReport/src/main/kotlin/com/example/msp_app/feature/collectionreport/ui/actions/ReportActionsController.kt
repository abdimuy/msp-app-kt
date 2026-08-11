package com.example.msp_app.feature.collectionreport.ui.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.printing.CollectionReportFormatter
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.actions.pdf.PdfCanvasRenderer
import com.example.msp_app.feature.collectionreport.ui.actions.pdf.PdfLayout
import com.example.msp_app.feature.collectionreport.ui.actions.pdf.buildPdfBlocks
import com.example.msp_app.feature.collectionreport.ui.actions.pdf.buildPdfReportModel
import com.example.msp_app.feature.collectionreport.ui.actions.pdf.paginatePdfBlocks
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
     * desglose por pago (TODOS los pagos, en ambos periodos) + desglose de visitas + totales +
     * condonaciones + visitas, con dinero SIEMPRE en peso entero vía [formatMoneyMxn]. Es el
     * MISMO contenido/layout que imprime la impresora térmica (P2) y que escribe el PDF — una
     * sola fuente de verdad.
     */
    fun buildTicketText(state: CollectionReportUiState, clock: AppClock = AppClock.System): String =
        CollectionReportFormatter.toTicketText(state, clock)

    // endregion

    // region — PDF (PdfDocument real, contenido dinero-seguro) --------------------------

    /**
     * Genera el PDF del reporte del rango actual en `context.cacheDir` (mismo directorio que
     * el `<cache-path path="."/>` que `:app` ya declara para su `FileProvider`, ver
     * `AndroidManifest.xml`) y devuelve el archivo.
     *
     * **Rediseño (dispatch "tabla densa multipágina"):** ya NO reusa [buildTicketText] línea
     * por línea (el ticket térmico de 58mm queda para "Compartir ticket"/impresión); el PDF
     * ahora es su propio documento tabular, construido en tres pasos puros
     * (`internal.pdf`, testeables en JVM sin `Canvas`) + un dibujo:
     * 1. [buildPdfReportModel] mapea [state] (dinero-seguro, mismo `Money`/`formatMoneyMxn`
     *    que el tablero/ticket) — pagos YA ordenados por `state.sort`.
     * 2. [buildPdfBlocks] lo convierte en el flujo de bloques de layout (encabezado, banda de
     *    resumen, tabla de pagos, condonaciones, totales, visitas con nota envuelta).
     * 3. [paginatePdfBlocks] reparte ESE flujo en páginas de a lo más
     *    [PdfLayout.MAX_CONTENT_HEIGHT] puntos, repitiendo el encabezado de columnas de la
     *    tabla de pagos si el corte cae a mitad de la lista — ningún pago ni visita se pierde
     *    sin importar cuántos haya (~200 pagos / ~100 visitas, el caso de uso del dispatch).
     * 4. Con la paginación YA resuelta se conoce `totalPages` ANTES de dibujar, así que
     *    [PdfCanvasRenderer] pinta "Página X de N" correcto desde la primera página (segunda
     *    pasada: medir con alturas puras, luego dibujar — no hace falta un pase de "stampeo"
     *    posterior).
     */
    fun generatePdf(
        context: Context,
        state: CollectionReportUiState,
        fileName: String,
        clock: AppClock = AppClock.System
    ): File {
        val document = PdfDocument()
        val notePaint = Paint().apply {
            typeface = Typeface.DEFAULT
            textSize = PdfLayout.VISIT_NOTE_TEXT_SIZE
        }
        val model = buildPdfReportModel(state, clock)
        val blocks = buildPdfBlocks(model, notePaint::measureText)
        val pages = paginatePdfBlocks(
            blocks,
            PdfLayout.MAX_CONTENT_HEIGHT
        ).ifEmpty { listOf(emptyList()) }
        val totalPages = pages.size
        pages.forEachIndexed { index, pageBlocks ->
            val pageInfo = PdfDocument.PageInfo
                .Builder(PdfLayout.PAGE_WIDTH.toInt(), PdfLayout.PAGE_HEIGHT.toInt(), index + 1)
                .create()
            val page = document.startPage(pageInfo)
            PdfCanvasRenderer.drawPage(page.canvas, pageBlocks, index + 1, totalPages, model.footer)
            document.finishPage(page)
        }
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

    /**
     * `Intent.ACTION_VIEW` del PDF ya generado — para el botón "PDF" (abrir en un visor
     * externo), a diferencia de [buildPdfShareIntent] (botón "Compartir", `ACTION_SEND`). Mismo
     * [pdfUri]/permiso de lectura que Compartir — el visor recibe el MISMO archivo. Delega la
     * forma del intent en el overload puro de abajo.
     */
    fun buildPdfViewIntent(context: Context, file: File): Intent =
        buildPdfViewIntent(pdfUri(context, file))

    /**
     * Núcleo puro de [buildPdfViewIntent]: arma el `Intent.ACTION_VIEW` a partir de una [uri]
     * YA resuelta. Separado del `Context`/`FileProvider` real (que sí resuelve [uri] arriba)
     * para poder probar la FORMA del intent (acción/tipo/flag de lectura) sin depender de
     * `FileProvider.getUriForFile` — este módulo, igual que [generatePdf] (ver su KDoc), no
     * puede ejercitar un `FileProvider` real en unit test: el manifest de test de Robolectric
     * (`Config.NONE` en `RobolectricTestBase`) no declara ningún `<provider>`, así que resolver
     * una Uri real revienta con `IllegalArgumentException` fuera de una Activity/Application
     * de verdad — no un bug de este código.
     */
    internal fun buildPdfViewIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, PDF_MIME_TYPE)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Lanza [intent] con [context] y, si no hay ninguna app capaz de manejarlo (p. ej.
     * `ACTION_VIEW` de un PDF en un dispositivo sin visor instalado), cae a un chooser en vez
     * de tronar con `ActivityNotFoundException`; si tampoco el chooser encuentra destino,
     * no-op silencioso (mejor que un crash del botón "PDF").
     */
    fun startActivitySafely(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent.createChooser(intent, null))
            } catch (_: ActivityNotFoundException) {
                // Ningún visor/chooser disponible — no hay nada más que hacer desde aquí.
            }
        }
    }

    // endregion

    /** Fuente única del título (la comparte [CollectionReportFormatter] con el ticket/impresión). */
    private fun reportTitle(period: ReportPeriod): String =
        CollectionReportFormatter.reportTitle(period)

    private fun money(amount: Money): String = formatMoneyMxn(amount.amount)
}
