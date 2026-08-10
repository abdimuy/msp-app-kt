package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.PrintPhase
import com.example.msp_app.feature.collectionreport.ui.PrintSheetUi

private const val LABEL_TITLE_PRINTING = "Imprimiendo"
private const val LABEL_TITLE_SELECTING = "Elige una impresora"
private const val LABEL_TITLE_SUCCESS = "Reporte impreso"
private const val LABEL_TITLE_ERROR = "No se imprimió"
private const val LABEL_CHANGE_PRINTER = "Cambiar impresora"
private const val LABEL_PRINT_AGAIN = "Imprimir de nuevo"
private const val LABEL_RETRY = "Reintentar"
private const val LABEL_PRINTING_BODY = "Enviando el ticket a la impresora"
private const val LABEL_NO_PRINTERS = "Empareja una impresora en los ajustes de Bluetooth"
private const val LABEL_NO_TARGET = "Ninguna impresora seleccionada"

/**
 * Bottom sheet de impresión (P2), manejado por `state.printSheet`: no renderiza nada si no hay
 * flujo de impresión abierto. Mismo patrón que [ReportSheets] (`ModalBottomSheet` M3 dirigido
 * por estado, `skipPartiallyExpanded = true`), otra responsabilidad.
 *
 * **"Cambiar impresora" SIEMPRE disponible** (requisito clave del usuario, ausente en
 * kollect): en [PrintPhase.PRINTING]/[PrintPhase.SUCCESS]/[PrintPhase.ERROR] hay un botón
 * explícito que abre el picker Bluetooth; en [PrintPhase.SELECTING] el picker mismo es la lista.
 * El default sigue siendo la última impresora usada (auto) — esto solo hace que cambiarla esté
 * a un toque, en cualquier momento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintSheet(
    state: CollectionReportUiState,
    onDismiss: () -> Unit,
    onPrint: () -> Unit,
    onSelectPrinter: (PrinterDevice) -> Unit,
    onChangePrinter: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val printSheet = state.printSheet ?: return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MspTheme.colors.surface,
        contentColor = MspTheme.colors.onSurface
    ) {
        PrintSheetBody(
            printSheet = printSheet,
            onPrint = onPrint,
            onSelectPrinter = onSelectPrinter,
            onChangePrinter = onChangePrinter
        )
    }
}

/**
 * Cuerpo del bottom sheet de impresión SIN el `ModalBottomSheet` que lo envuelve — extraído
 * aparte para los goldens Roborazzi (mismo motivo que [SheetBody]: `captureRoboImage` solo
 * toma la ventana raíz, no el `Popup` donde M3 monta el `ModalBottomSheet`, así que capturar
 * el sheet completo saldría en blanco).
 */
@Composable
internal fun PrintSheetBody(
    printSheet: PrintSheetUi,
    onPrint: () -> Unit,
    onSelectPrinter: (PrinterDevice) -> Unit,
    onChangePrinter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.lg)
            .padding(bottom = MspTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Text(
            text = titleFor(printSheet.phase),
            style = MspTheme.type.detailTitle,
            color = MspTheme.colors.onSurface
        )
        when (printSheet.phase) {
            PrintPhase.PRINTING -> PrintingContent(printSheet, onChangePrinter)
            PrintPhase.SELECTING -> SelectingContent(printSheet, onSelectPrinter)
            PrintPhase.SUCCESS -> SuccessContent(printSheet, onPrint, onChangePrinter)
            PrintPhase.ERROR -> ErrorContent(printSheet, onPrint, onChangePrinter)
        }
    }
}

@Composable
private fun PrintingContent(printSheet: PrintSheetUi, onChangePrinter: () -> Unit) {
    Text(
        text = printSheet.target?.let { "$LABEL_PRINTING_BODY: ${it.name}" } ?: LABEL_PRINTING_BODY,
        style = MspTheme.type.subtitle,
        color = MspTheme.colors.onSurfaceMuted
    )
    ChangePrinterButton(onChangePrinter)
}

@Composable
private fun SelectingContent(printSheet: PrintSheetUi, onSelectPrinter: (PrinterDevice) -> Unit) {
    if (printSheet.printers.isEmpty()) {
        Text(
            text = LABEL_NO_PRINTERS,
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted
        )
    } else {
        printSheet.printers.forEach { device ->
            PrinterRow(
                device = device,
                selected = device.address == printSheet.target?.address,
                onClick = { onSelectPrinter(device) }
            )
        }
    }
}

@Composable
private fun SuccessContent(
    printSheet: PrintSheetUi,
    onPrint: () -> Unit,
    onChangePrinter: () -> Unit
) {
    Text(
        text = printSheet.target?.name ?: LABEL_NO_TARGET,
        style = MspTheme.type.subtitle,
        color = MspTheme.colors.onSurfaceMuted
    )
    MspPrimaryFieldButton(
        text = LABEL_PRINT_AGAIN,
        variant = PrimaryFieldButtonVariant.Primary,
        onClick = onPrint,
        modifier = Modifier.fillMaxWidth()
    )
    ChangePrinterButton(onChangePrinter)
}

@Composable
private fun ErrorContent(
    printSheet: PrintSheetUi,
    onPrint: () -> Unit,
    onChangePrinter: () -> Unit
) {
    Text(
        text = printSheet.message ?: "no se pudo imprimir el reporte",
        style = MspTheme.type.body,
        color = MspTheme.colors.danger
    )
    MspPrimaryFieldButton(
        text = LABEL_RETRY,
        variant = PrimaryFieldButtonVariant.Primary,
        onClick = onPrint,
        modifier = Modifier.fillMaxWidth()
    )
    ChangePrinterButton(onChangePrinter)
}

@Composable
private fun ChangePrinterButton(onChangePrinter: () -> Unit) {
    MspPrimaryFieldButton(
        text = LABEL_CHANGE_PRINTER,
        variant = PrimaryFieldButtonVariant.Ghost,
        onClick = onChangePrinter,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrinterRow(
    device: PrinterDevice,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MspTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Column(modifier = Modifier) {
            Text(
                text = device.name,
                style = MspTheme.type.name,
                color = if (selected) MspTheme.colors.brand else MspTheme.colors.onSurface
            )
            Text(
                text = device.address,
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
    }
}

private fun titleFor(phase: PrintPhase): String = when (phase) {
    PrintPhase.PRINTING -> LABEL_TITLE_PRINTING
    PrintPhase.SELECTING -> LABEL_TITLE_SELECTING
    PrintPhase.SUCCESS -> LABEL_TITLE_SUCCESS
    PrintPhase.ERROR -> LABEL_TITLE_ERROR
}
