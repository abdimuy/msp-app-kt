package com.example.msp_app.core.appgate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

private val OUTLINE_WIDTH = 1.5.dp

/**
 * Botón de acción de la pantalla de bloqueo.
 *
 * No usa `MspPrimaryFieldButton` por una razón concreta: el mockup pide un
 * botón **verde** para "Instalar" (la salida buena, ya resuelta) y ese
 * componente solo tiene `Primary`/`Ghost`/`Danger` — verde no existe ahí, y
 * agregar un variant al design system para una sola pantalla obligaría a
 * regrabar sus goldens de Roborazzi. Se conservan sus tokens (misma forma,
 * misma altura mínima de área táctil, mismo padding).
 *
 * [fillColor] `Color.Transparent` produce la variante delineada
 * ("Reintentar" del estado sin conexión).
 */
@Composable
internal fun GateActionButton(
    text: String,
    onClick: () -> Unit,
    fillColor: Color,
    contentColor: Color,
    tag: String,
    enabled: Boolean = true
) {
    val shape = MspTheme.shapes.button
    val outlined = fillColor == Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MspTheme.spacing.touchTarget)
            .clip(shape)
            .background(fillColor)
            .then(
                if (outlined) Modifier.border(OUTLINE_WIDTH, contentColor, shape) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MspTheme.type.buttonLarge,
            color = contentColor,
            textAlign = TextAlign.Center
        )
    }
}
