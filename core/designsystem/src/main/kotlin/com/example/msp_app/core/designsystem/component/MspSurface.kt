package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * Sustrato compartido de toda tarjeta/superficie "casi-plana" del design
 * system: fill `surface` + hairline 1dp `outline` + una forma token.
 * Envuelve M3 `Surface`, agrega el `BorderStroke` automáticamente y elige el
 * overload clickable/no-clickable según [onClick] (1:1 kollect §8.1,
 * `CampoSurface`).
 *
 * `internal`: el punto de entrada público del design system es [MspCard];
 * nada fuera de este módulo instancia `MspSurface` directamente (Task 8 sí
 * la reutiliza, desde dentro del módulo, para variantes con elevación como
 * `MspBentoTile`/`MspCarteraCard`).
 *
 * El hairline va SIEMPRE — no es opcional ni condicionado a [shadowElevation]:
 * define las tarjetas casi-planas del sistema (spec §2.3, sombra hairline +
 * 1dp).
 */
@Composable
internal fun MspSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MspTheme.shapes.tile,
    color: Color = MspTheme.colors.surface,
    shadowElevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val border = BorderStroke(1.dp, MspTheme.colors.outline)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            shadowElevation = shadowElevation,
            content = content
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = color,
            border = border,
            shadowElevation = shadowElevation,
            content = content
        )
    }
}
