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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
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

// Avatar de iniciales del sheet (mockup `.srow .sa`: 34px) — más chico que el de la lista
// principal (`.prow .ava`, 38dp), 1:1 con el resumen por día ([DetailList.DAY_AVATAR_SIZE]).
private val SHEET_AVATAR_SIZE = 34.dp
private val SHEET_DIVIDER_HEIGHT = 1.dp
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
        row.leading?.let { leading ->
            if (row.avatar) {
                // Fila de cliente (pago/condonación/día del ciclo): avatar tintado (mockup
                // `.srow .sa`), reusa el mismo componente del design system que la lista `.prow`.
                MspInitialsAvatar(initials = leading, size = SHEET_AVATAR_SIZE)
            } else {
                // Glifo/emoji suelto de las filas del hero (📊/⚡/🕘/🎯) — sin fondo tintado.
                Box(
                    modifier = Modifier.size(SHEET_LEADING_SIZE),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = leading,
                        style = MspTheme.type.captionStrong,
                        color = MspTheme.colors.brand
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, style = MspTheme.type.name, color = MspTheme.colors.onSurface)
            row.subtitle?.let {
                Text(
                    text = it,
                    style = MspTheme.type.subtitle,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
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
