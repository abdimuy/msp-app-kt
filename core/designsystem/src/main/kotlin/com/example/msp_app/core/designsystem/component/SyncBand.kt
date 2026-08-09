package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` del dot de estado de [MspSyncBand] — mismo rol que [STATUS_CHIP_ICON_TAG] pero para el dot suelto. */
internal const val SYNC_BAND_DOT_TAG = "msp_sync_band_dot"

private val DOT_SIZE = 8.dp
private val BAND_HORIZONTAL_PADDING = 13.dp
private val BAND_VERTICAL_PADDING = 9.dp

/**
 * Strip de sincronización full-width, no bloqueante (1:1 kollect §8.5,
 * `.sync`): dot de 8dp + [message] (p. ej. "3 pagos por subir") + [hint] de
 * tranquilidad al final (p. ej. "se sube solo al recuperar señal"), todo
 * teñido por [state] (ámbar `Pending` / verde `Ok`). Ninguna copia va
 * hardcodeada aquí — el caller decide el texto exacto, el DS solo aporta
 * forma + color.
 *
 * "No bloqueante": es una fila informativa dentro del flujo normal de
 * pantalla, no un diálogo/snackbar que interrumpa — el caller la coloca donde
 * quiera (típicamente bajo el header, como en el mockup), este componente no
 * asume overlay ni posición fija.
 *
 * Sin animación de pulso en esta banda (esa vive en `MspPaymentSyncPill`,
 * la variante compacta) — el dot aquí es estático.
 */
@Composable
fun MspSyncBand(
    state: SyncBandState,
    message: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val content = state.contentColor(colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.control)
            .background(state.tintColor(colors))
            .padding(horizontal = BAND_HORIZONTAL_PADDING, vertical = BAND_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(DOT_SIZE)
                .clip(CircleShape)
                .background(content)
                .testTag(SYNC_BAND_DOT_TAG)
        )
        Text(
            text = message,
            style = MspTheme.type.syncLabel,
            color = content,
            modifier = Modifier
                .padding(start = MspTheme.spacing.sm)
                .weight(1f, fill = false)
        )
        Text(
            text = hint,
            style = MspTheme.type.caption,
            color = content,
            modifier = Modifier.padding(start = MspTheme.spacing.sm)
        )
    }
}
