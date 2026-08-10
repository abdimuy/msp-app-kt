package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.feature.collectionreport.ui.PrintPhase
import com.example.msp_app.feature.collectionreport.ui.PrintSheetUi
import com.example.msp_app.feature.collectionreport.ui.components.PrintSheetBody
import org.junit.Test

private val SHEET_PREVIEW_SHAPE = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

/**
 * Golden baseline (light+dark @1.0, Tier 1) del bottom sheet de impresión (P2), INCLUYENDO la
 * afordancia "Cambiar impresora" siempre visible (requisito clave del usuario). Se captura
 * [PrintSheetBody] (título + cuerpo por fase) envuelto en una tarjeta con el radio 24dp del
 * mockup, NO el `ModalBottomSheet` M3 real — mismo gotcha que `ReportSheetsScreenshotTest`
 * (`captureRoboImage` solo toma la ventana raíz; el `ModalBottomSheet` vive en un `Popup`).
 * El comportamiento real vive en `ui/components/PrintSheetTest`.
 */
class PrintSheetScreenshotTest : CollectionReportScreenshotTest() {

    private val printerA = PrinterDevice(address = "00:11:22:33:44:55", name = "Impresora A")
    private val printerB = PrinterDevice(address = "AA:BB:CC:DD:EE:FF", name = "Impresora B")

    @Test
    fun `print sheet picker light`() {
        capture(name = "collection_report_print_selecting_light", dark = false) {
            Sheet(PrintSheetUi(PrintPhase.SELECTING, printers = listOf(printerA, printerB)))
        }
    }

    @Test
    fun `print sheet picker dark`() {
        capture(name = "collection_report_print_selecting_dark", dark = true) {
            Sheet(PrintSheetUi(PrintPhase.SELECTING, printers = listOf(printerA, printerB)))
        }
    }

    @Test
    fun `print sheet printing light`() {
        capture(name = "collection_report_print_printing_light", dark = false) {
            Sheet(PrintSheetUi(PrintPhase.PRINTING, target = printerA))
        }
    }

    @Test
    fun `print sheet printing dark`() {
        capture(name = "collection_report_print_printing_dark", dark = true) {
            Sheet(PrintSheetUi(PrintPhase.PRINTING, target = printerA))
        }
    }

    @Test
    fun `print sheet success light`() {
        capture(name = "collection_report_print_success_light", dark = false) {
            Sheet(PrintSheetUi(PrintPhase.SUCCESS, target = printerA, printers = listOf(printerA)))
        }
    }

    @Test
    fun `print sheet success dark`() {
        capture(name = "collection_report_print_success_dark", dark = true) {
            Sheet(PrintSheetUi(PrintPhase.SUCCESS, target = printerA, printers = listOf(printerA)))
        }
    }

    @Test
    fun `print sheet error light`() {
        capture(name = "collection_report_print_error_light", dark = false) {
            Sheet(errorSheet())
        }
    }

    @Test
    fun `print sheet error dark`() {
        capture(name = "collection_report_print_error_dark", dark = true) {
            Sheet(errorSheet())
        }
    }

    private fun errorSheet() = PrintSheetUi(
        phase = PrintPhase.ERROR,
        target = printerA,
        printers = listOf(printerA),
        message = "no se pudo conectar con la impresora"
    )

    @Composable
    private fun Sheet(printSheet: PrintSheetUi) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SHEET_PREVIEW_SHAPE)
                .background(MspTheme.colors.surface)
                .padding(top = MspTheme.spacing.sm)
        ) {
            PrintSheetBody(
                printSheet = printSheet,
                onPrint = {},
                onSelectPrinter = {},
                onChangePrinter = {}
            )
        }
    }
}
