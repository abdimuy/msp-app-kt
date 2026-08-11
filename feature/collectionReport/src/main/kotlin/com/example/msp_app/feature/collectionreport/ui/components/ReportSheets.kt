package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState

private val SHEET_SHAPE = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val SHEET_LEADING_SIZE = 28.dp
private val SHEET_LEADING_ICON_SIZE = 22.dp

// Avatar de iniciales / tile de método del sheet (mockup `.srow .sa`: 34px) — más chico que el
// de la lista principal (`.prow .ava`/tile, 40dp), 1:1 con el resumen por día
// ([DetailList.DAY_AVATAR_SIZE]).
private val SHEET_AVATAR_SIZE = 34.dp
private val SHEET_DIVIDER_HEIGHT = 1.dp
private val SHEET_TRAILING_LINE_SPACING = 3.dp
private const val EMPTY_SHEET_MESSAGE = "Sin datos aún"

/**
 * `ModalBottomSheet` (M3) manejado por `state.sheet` (Task 5): no renderiza nada si no hay
 * sheet abierto. `skipPartiallyExpanded = true` — solo dos anclas (oculto/expandido), sin un
 * estado a medias que complique capturar un golden determinista (gotcha del brief). El
 * cuerpo (título/subtítulo/filas) se deriva de `SheetKind` + el resto del estado vía
 * `deriveSheetContent` (`ReportSheetContent.kt`).
 *
 * `shape` 24dp solo arriba (mockup `.sheet{border-radius:24px 24px 0 0}`) — no es
 * `shapes.sectionCard` (18dp) del design system, es el radio propio del mockup para ESTE
 * contenedor específico, mismo criterio que `SCROLL_BOTTOM_CONTENT_PADDING` en
 * `CollectionReportScreen`. El handle (mockup `.handle`) lo pinta el `dragHandle` default de
 * `ModalBottomSheet`, no se reinventa.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSheets(
    state: CollectionReportUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val sheet = state.sheet ?: return
    val content = deriveSheetContent(sheet, state)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        sheetState = sheetState,
        shape = SHEET_SHAPE,
        containerColor = MspTheme.colors.surface,
        contentColor = MspTheme.colors.onSurface
    ) {
        SheetBody(content = content, masked = state.masked)
    }
}

/**
 * Cuerpo del sheet (título + subtítulo + filas) SIN el `ModalBottomSheet` que lo envuelve —
 * extraído aparte para que los goldens Roborazzi (gotcha del brief: "ModalBottomSheet en
 * Roborazzi puede ser complicado") capturen el contenido REAL sin depender de que
 * `captureRoboImage` (que solo toma el `Canvas` de la ventana raíz) alcance la ventana
 * `Popup` separada donde M3 monta el `ModalBottomSheet` — verificado empíricamente: capturar
 * `ReportSheets` completo produce un golden en blanco (el Popup no está en esa ventana),
 * mientras que las queries de semántica de `ComposeTestRule` (`onNodeWithText`, ver
 * `ReportSheetsTest`) SÍ cruzan ventanas y encuentran el contenido sin problema — por eso el
 * comportamiento (abrir/cerrar/masked) se prueba contra [ReportSheets] real y el golden
 * visual contra este `SheetBody` aislado (la cromática del propio `ModalBottomSheet` —
 * scrim, handle, animación de entrada — es responsabilidad de Material3, no de este piloto).
 */
@Composable
internal fun SheetBody(content: SheetContentUi, masked: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Scroll del cuerpo (fix de dispositivo): un día del ciclo con muchos pagos
            // desbordaba el alto máximo del `ModalBottomSheet` y las últimas filas quedaban
            // recortadas, inalcanzables. Con `verticalScroll` el contenido se desplaza dentro
            // del sheet, así TODOS los pagos del día son alcanzables. El padding va DESPUÉS del
            // scroll para que quede dentro del área desplazable.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.lg)
            .padding(bottom = MspTheme.spacing.lg)
    ) {
        Text(
            text = content.title,
            style = MspTheme.type.detailTitle,
            color = MspTheme.colors.onSurface
        )
        Text(
            text = content.subtitle,
            style = MspTheme.type.subtitle,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.padding(top = MspTheme.spacing.xs, bottom = MspTheme.spacing.sm)
        )
        if (content.rows.isEmpty()) {
            EmptySheetRows()
        } else {
            content.rows.forEachIndexed { index, row ->
                SheetRow(row = row, masked = masked)
                if (index != content.rows.lastIndex) SheetRowDivider()
            }
        }
    }
}

@Composable
private fun SheetRow(row: SheetRowUi, masked: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        SheetRowLeading(row)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, style = MspTheme.type.name, color = MspTheme.colors.onSurface)
            row.subtitle?.let {
                Text(
                    text = it,
                    style = MspTheme.type.subtitle,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            // Tercera línea (Task 1, solo filas de pago): saldo restante de la venta, mismo
            // componente que `DetailList.PaymentRow` reusa — una sola fuente de verdad visual.
            row.saldo?.let { SaldoLine(saldo = it, masked = masked) }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(SHEET_TRAILING_LINE_SPACING)
        ) {
            when {
                row.amount != null -> MspMoneyText(
                    amount = row.amount.amount,
                    masked = masked,
                    style = MspTheme.type.amountRow,
                    color = MspTheme.colors.onSurface
                )

                row.text != null -> Text(
                    text = row.text,
                    style = MspTheme.type.captionStrong,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            // Chip "Por subir" (Task 1): solo en filas de pago ([row.method] no nulo) que aún
            // no sincronizan — mismo chip que `DetailList.PaymentRow`.
            if (row.method != null && !row.synced) PendingUploadChip()
        }
    }
}

/**
 * Leading de una fila de sheet — tres formas mutuamente excluyentes (ver KDoc de
 * [SheetRowUi]): tile de método (pago), avatar de iniciales (condonación) o glifo Lucide
 * suelto (hero, sin fondo tintado — reemplaza los emojis 📊/⚡/🕘/🎯). Filas sin ninguno
 * (Visitas, o los renglones textuales de "Detalle de pago") no dibujan nada en esta columna.
 */
@Composable
private fun SheetRowLeading(row: SheetRowUi, modifier: Modifier = Modifier) {
    when {
        row.method != null -> MethodTile(
            method = row.method,
            modifier = modifier,
            size = SHEET_AVATAR_SIZE
        )

        row.avatar && row.leading != null ->
            MspInitialsAvatar(initials = row.leading, modifier = modifier, size = SHEET_AVATAR_SIZE)

        row.leadingIcon != null -> Box(
            modifier = modifier.size(SHEET_LEADING_SIZE),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = row.leadingIcon,
                contentDescription = null,
                tint = MspTheme.colors.brand,
                modifier = Modifier.size(SHEET_LEADING_ICON_SIZE)
            )
        }

        row.leading != null -> Box(
            modifier = modifier.size(SHEET_LEADING_SIZE),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = row.leading,
                style = MspTheme.type.captionStrong,
                color = MspTheme.colors.brand
            )
        }
    }
}

@Composable
private fun SheetRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SHEET_DIVIDER_HEIGHT)
            .background(MspTheme.colors.outline)
    )
}

@Composable
private fun EmptySheetRows(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = EMPTY_SHEET_MESSAGE,
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}
