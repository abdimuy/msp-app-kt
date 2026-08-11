package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** Prefijo de `testTag` de cada segmento — `"${SEGMENT_CHIP_TAG_PREFIX}0"`, `…1`, etc. */
internal const val SEGMENT_CHIP_TAG_PREFIX = "msp_segment_chip_"

private val TRACK_PADDING = 4.dp
private val SEGMENT_VERTICAL_PADDING = 10.dp

// Padding horizontal de cada segmento en modo compacto ([equalWidth] = false):
// da ancho al chip a partir de su texto (mockup `.seg span{padding:5px 12px}`).
// En modo full-width ([equalWidth] = true) el `weight(1f)` ya reparte el ancho,
// así que este padding no aplica.
private val SEGMENT_HORIZONTAL_PADDING = 12.dp
private val SEGMENT_SHADOW_ELEVATION = 1.dp

/**
 * Selector segmentado (Día·Semana, Hora·Nombre — 1:1 mockup `.period`/`.seg`):
 * un track pill ([MspTheme.colors.progressTrack] — el mismo token de "riel
 * gris" que usa [MspProgressBar], reutilizado aquí para el fondo del
 * contenedor pill; no hay un token `segmentTrack` dedicado) con N botones
 * pill que se reparten el ancho por igual.
 *
 * El segmento activo ([selectedIndex]) pinta fondo [MspTheme.colors.surface]
 * + texto [MspTheme.colors.brand] + una sombra sutil de 1dp
 * (`box-shadow:0 1px 4px rgba(0,0,0,.12)` del mockup, aproximada con
 * `Modifier.shadow`); los inactivos son fondo transparente + texto
 * [MspTheme.colors.onSurfaceMuted]. Sin animación en el swap (el CSS del
 * mockup solo anima `color .2s`, imperceptible) — el chip de estado
 * ([MspStatusChip]) tampoco anima su swap, mismo criterio.
 *
 * Este componente es **stateless**: [selectedIndex] lo sostiene el caller,
 * [onSelect] solo informa qué índice se tocó — el mismo patrón que
 * [MspPrivacyEyeToggle]/`MspThemeToggle`.
 */
@Composable
fun MspSegmentChips(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    equalWidth: Boolean = true
) {
    Row(
        modifier = modifier
            .clip(MspTheme.shapes.chip)
            .background(MspTheme.colors.progressTrack)
            .padding(TRACK_PADDING)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            // equalWidth=true (mockup `.period`): los segmentos reparten el ancho por
            // igual (`weight(1f)`) y el track llena el contenedor. equalWidth=false
            // (mockup `.seg`): cada segmento se dimensiona a su texto + padding
            // horizontal, y el track queda compacto — para convivir con un label al
            // lado sin encimarse (ver DetailHeader).
            val widthModifier = if (equalWidth) Modifier.weight(1f) else Modifier
            val horizontalPadding = if (equalWidth) 0.dp else SEGMENT_HORIZONTAL_PADDING
            Box(
                modifier = widthModifier
                    .then(
                        if (selected) {
                            Modifier.shadow(
                                elevation = SEGMENT_SHADOW_ELEVATION,
                                shape = MspTheme.shapes.chip,
                                clip = false
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(MspTheme.shapes.chip)
                    .background(if (selected) MspTheme.colors.surface else Color.Transparent)
                    .clickable(onClick = { onSelect(index) })
                    .padding(horizontal = horizontalPadding, vertical = SEGMENT_VERTICAL_PADDING)
                    .testTag("$SEGMENT_CHIP_TAG_PREFIX$index"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MspTheme.type.segmentLabel,
                    color = if (selected) MspTheme.colors.brand else MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}
