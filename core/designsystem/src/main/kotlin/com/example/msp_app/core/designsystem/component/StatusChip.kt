package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * `testTag` del ícono del chip — localiza el ícono en el compose-test que
 * prueba "nunca solo color" (el estado siempre expone ícono + texto, no solo
 * el matiz de fondo).
 */
internal const val STATUS_CHIP_ICON_TAG = "msp_status_chip_icon"

/**
 * Pill de estado de cobranza: fondo tint + color de contenido + **ícono +
 * texto**, todo derivado de [status] contra [MspTheme.colors]/[MspIcons].
 *
 * **Regla dura (accesibilidad, spec §2.1 / §5):** el estado se codifica con
 * **color + ícono + texto** juntos, NUNCA solo color — un usuario con
 * daltonismo distingue el estado por la forma del ícono y por el texto, no por
 * el matiz. Por eso no existe una variante "solo dot" de este chip para estado
 * semántico (el dot suelto de método de cobro efectivo/transferencia es otra
 * cosa: un `Box` de color en la fila, no un `MspStatusChip`).
 *
 * El ícono va `contentDescription = null` (decorativo para lectores de
 * pantalla): [text] ya comunica el estado a quien no ve la pantalla, así que
 * anunciar ambos sería redundante; la redundancia ícono/color es para el canal
 * visual (daltonismo), no para el auditivo.
 */
@Composable
fun MspStatusChip(status: ChipStatus, text: String, modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    val content = status.contentColor(colors)
    Row(
        modifier = modifier
            .clip(MspTheme.shapes.chip)
            .background(status.tintColor(colors))
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Icon(
            imageVector = status.icon(),
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(14.dp)
                .testTag(STATUS_CHIP_ICON_TAG)
        )
        Text(
            text = text,
            style = MspTheme.type.chipLabel,
            color = content
        )
    }
}
