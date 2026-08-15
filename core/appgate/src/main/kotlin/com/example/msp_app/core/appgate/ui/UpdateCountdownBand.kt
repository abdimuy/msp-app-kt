package com.example.msp_app.core.appgate.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de la banda completa. */
const val UPDATE_COUNTDOWN_BAND_TAG = "msp_update_countdown_band"

/** `testTag` de la acción de la banda ("Instalar" / "Descargar"). */
const val UPDATE_COUNTDOWN_ACTION_TAG = "msp_update_countdown_action"

private val BAND_ICON_SIZE = 20.dp

/**
 * La banda de cuenta regresiva.
 *
 * No se puede descartar, pero no estorba: la app sigue funcionando. Lo único
 * que cambia es el **tono**, y ese cambio es el mensaje — mientras el archivo
 * no está, la banda **pide** (ámbar); cuando ya está, **ofrece** (verde), y
 * entonces instalar cuesta un toque y cero megas.
 *
 * El cambio de tono se anima, y respeta [LocalReduceMotion]: con la
 * preferencia puesta salta al color final sin pasar por ningún intermedio (ver
 * `UpdateCountdownBandTest`, que prueba las dos ramas con aserciones
 * contrarias sobre el mismo estímulo).
 */
@Composable
fun UpdateCountdownBand(
    deadlineLabel: String,
    ready: Boolean,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val container = rememberBandContainerColor(ready)
    val content = if (ready) colors.statusPaid else colors.statusPartial
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.control)
            .background(container)
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm)
            .testTag(UPDATE_COUNTDOWN_BAND_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        Icon(
            imageVector = if (ready) Icons.Filled.Check else Icons.Filled.DateRange,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(BAND_ICON_SIZE)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (ready) "Listo para instalar" else "Actualiza antes del $deadlineLabel",
                style = MspTheme.type.captionStrong,
                color = content
            )
            Text(
                text = if (ready) {
                    "Actualiza antes del $deadlineLabel"
                } else {
                    "Se descarga sola al conectarte a wifi"
                },
                style = MspTheme.type.caption,
                color = content
            )
        }
        Text(
            text = if (ready) "Instalar" else "Descargar",
            style = MspTheme.type.captionStrong,
            color = content,
            modifier = Modifier
                .clickable(onClick = onAction)
                .testTag(UPDATE_COUNTDOWN_ACTION_TAG)
        )
    }
}

/**
 * Color de fondo vigente de la banda.
 *
 * `internal` para que el test pueda leer el valor frame a frame con el reloj
 * congelado: capturar píxeles exigiría un `forceRedraw` que no convive con
 * `autoAdvance = false` (misma trampa medida que documenta `DaySwapTest`).
 */
@Composable
internal fun rememberBandContainerColor(ready: Boolean): Color {
    val colors = MspTheme.colors
    val target = if (ready) colors.statusPaidTint else colors.statusPartialTint
    if (LocalReduceMotion.current) return target
    return animateColorAsState(
        targetValue = target,
        animationSpec = MspTheme.motion.standard(),
        label = "bandTone"
    ).value
}
