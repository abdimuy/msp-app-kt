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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(MspTheme.shapes.chip)
            .background(MspTheme.colors.progressTrack)
            .padding(TRACK_PADDING)
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
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
                    .padding(vertical = SEGMENT_VERTICAL_PADDING)
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
