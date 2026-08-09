package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

private val TILE_SHADOW_ELEVATION = 1.dp
private val BAR_AREA_HEIGHT = 48.dp
private val BAR_MIN_HEIGHT = 4.dp
private val BAR_SHAPE = RoundedCornerShape(3.dp)

/**
 * Tarjeta autónoma de barras por día del ciclo (kollect §9, "WeeklyBarsCard"
 * del catálogo — task-8-brief.md: "el componente autónomo reutilizable",
 * distinto del spark embebido dentro de [MspHeroTodayCard]). [MspCard]
 * (hairline + `shadowElevation = 1.dp`) conteniendo una fila de barras: alto
 * proporcional a [MspWeeklyBar.fraction] dentro de un área fija
 * ([BAR_AREA_HEIGHT]) para que las etiquetas queden siempre alineadas sin
 * importar la altura de cada barra; la de [todayIndex] resalta en
 * `colors.brand` (1:1 `.wkbars i.on{background:var(--brand)}` kollect), las
 * demás en `colors.chartTrack`. Etiqueta por barra en `type.trendLabel`
 * (tabular).
 *
 * **Sin animación interna gated en tiempo/random** (task-8-brief.md): igual
 * que [MspProgressBar]/[MspProgressRing], este componente no anima
 * [MspWeeklyBar.fraction] por sí mismo — el caller que quiera el efecto
 * "grow" envuelve el valor en su propio `animateFloatAsState` antes de
 * construir la lista. Como no hay animación propia aquí, no hay nada que
 * apagar por `rememberReducedMotionEnabled()` — ese chequeo, si aplica, vive
 * en el caller que decida animar.
 */
@Composable
fun MspWeeklyBarsCard(bars: List<MspWeeklyBar>, todayIndex: Int, modifier: Modifier = Modifier) {
    MspCard(modifier = modifier, shadowElevation = TILE_SHADOW_ELEVATION) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MspTheme.spacing.md),
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            bars.forEachIndexed { index, bar ->
                WeeklyBarColumn(
                    bar = bar,
                    highlighted = index == todayIndex,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WeeklyBarColumn(
    bar: MspWeeklyBar,
    highlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BAR_AREA_HEIGHT),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight(bar.fraction))
                    .clip(BAR_SHAPE)
                    .background(if (highlighted) colors.brand else colors.chartTrack)
            )
        }
        Text(
            text = bar.label,
            style = MspTheme.type.trendLabel,
            color = colors.onSurfaceMuted,
            modifier = Modifier.padding(top = MspTheme.spacing.xs)
        )
    }
}

private fun barHeight(fraction: Float): Dp = maxOf(
    BAR_MIN_HEIGHT,
    BAR_AREA_HEIGHT * fraction.coerceIn(0f, 1f)
)
