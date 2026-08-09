package com.example.msp_app.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * Wrapper público de [MspSurface] — la tarjeta base del design system: fill
 * `surface` + hairline 1dp `outline` + forma [MspTheme.shapes.tile] (16dp)
 * por default (1:1 kollect §8.1, `CampoCard`). Variantes con una leve
 * elevación (`MspBentoTile`/`MspCarteraCard`, Task 8) agregan
 * `shadowElevation = 1.dp` encima del hairline, no lo reemplazan.
 */
@Composable
fun MspCard(
    modifier: Modifier = Modifier,
    shape: Shape = MspTheme.shapes.tile,
    color: Color = MspTheme.colors.surface,
    shadowElevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    MspSurface(
        modifier = modifier,
        shape = shape,
        color = color,
        shadowElevation = shadowElevation,
        onClick = onClick,
        content = content
    )
}
