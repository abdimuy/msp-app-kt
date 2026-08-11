package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** `testTag` de [MspPrimaryFieldButton] — localiza el CTA en compose-tests. */
internal const val PRIMARY_FIELD_BUTTON_TAG = "msp_primary_field_button"

private val BUTTON_SHADOW_ELEVATION = 8.dp
private const val BUTTON_SHADOW_ALPHA = 0.55f
private val BUTTON_BORDER_WIDTH = 1.dp

/**
 * CTA de campo del design system — altura mínima
 * [MspTheme.spacing.touchTarget] (56dp, "las manos de un cobrador caminando
 * al sol"), shape [MspTheme.shapes.button] (16dp), texto
 * [MspTheme.type.buttonLarge] (1:1 kollect §8.4, `PrimaryFieldButton`).
 *
 * Tres [variant]s ([PrimaryFieldButtonVariant]): [PrimaryFieldButtonVariant.Primary]
 * (fill `brand` + sombra 8dp tintada a `brand` alfa 0.55) y
 * [PrimaryFieldButtonVariant.Danger] (mismo tratamiento de sombra, tintada a
 * `statusOverdue` — reservado a confirmaciones riesgosas, nunca el default)
 * comparten sombra vía `Modifier.shadow` con `ambientColor`/`spotColor`
 * tintados; [PrimaryFieldButtonVariant.Ghost] es outline `brand` sin relleno
 * ni sombra.
 *
 * **[enabled] = `false`:** pinta un fill plano [MspTheme.colors.outline] +
 * texto [MspTheme.colors.onSurfaceMuted] y **apaga la sombra por completo**,
 * sin importar el [variant] — un `Surface`/`Box` clickable de M3 NO aplica
 * ninguna alfa de "deshabilitado" por sí solo (gotcha del brief), así que
 * este estado se pinta a mano en vez de confiar en un modificador automático.
 *
 * **Haptic físico:** cada tap dispara `HapticFeedbackType.LongPress` — "las
 * acciones de dinero deben sentirse físicas" (spec §8.4). No dispara cuando
 * [enabled] es `false` (el `clickable` deshabilitado ni siquiera invoca el
 * lambda).
 *
 * Sin `fillMaxWidth()` propio: el ancho lo decide el [modifier] del caller
 * (mismo criterio que [MspCard]/[MspBentoTile] — este componente no asume
 * layout de fila de acciones).
 *
 * **[maxLines]** (default sin límite, mismo comportamiento de siempre para
 * cualquier caller que no lo toque): permite a un caller angosto (p. ej.
 * [com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar]
 * a tamaño de letra grande) forzar una sola línea con `TextOverflow.Ellipsis`
 * en vez de dejar que el texto envuelva y parta una palabra a la mitad.
 */
@Composable
fun MspPrimaryFieldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PrimaryFieldButtonVariant = PrimaryFieldButtonVariant.Primary,
    enabled: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    val colors = MspTheme.colors
    val haptics = LocalHapticFeedback.current
    val shape = MspTheme.shapes.button
    val fill = if (enabled) variant.fillColor(colors) else colors.outline
    val content = if (enabled) variant.contentColor(colors) else colors.onSurfaceMuted
    val shadowTint = if (enabled) variant.shadowTintColor(colors) else null
    val shadowModifier = if (shadowTint != null) {
        Modifier.shadow(
            elevation = BUTTON_SHADOW_ELEVATION,
            shape = shape,
            ambientColor = shadowTint.copy(alpha = BUTTON_SHADOW_ALPHA),
            spotColor = shadowTint.copy(alpha = BUTTON_SHADOW_ALPHA)
        )
    } else {
        Modifier
    }
    val borderModifier = if (enabled && variant == PrimaryFieldButtonVariant.Ghost) {
        Modifier.border(BUTTON_BORDER_WIDTH, colors.brand, shape)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(shadowModifier)
            .heightIn(min = MspTheme.spacing.touchTarget)
            .clip(shape)
            .background(fill)
            .then(borderModifier)
            .clickable(
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .padding(horizontal = MspTheme.spacing.md)
            .testTag(PRIMARY_FIELD_BUTTON_TAG),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MspTheme.type.buttonLarge,
            color = content,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}
