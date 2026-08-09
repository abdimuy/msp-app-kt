package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberReducedMotionEnabled
import com.example.msp_app.feature.collectionreport.domain.Timeline
import com.example.msp_app.feature.collectionreport.domain.TimelineBucket
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Alto máximo del área de barras (mockup `Math.max(valor*0.4, 6)px` con `valor` normalizado
 * `0..100`): `100 * 0.4 = 40dp` — la altura de cada barra es `SPARK_MAX_HEIGHT * fraction`,
 * equivalente al cálculo del mockup una vez que `fraction` (`0..1`) reemplaza a `valor/100`.
 */
private val SPARK_MAX_HEIGHT = 40.dp
private val SPARK_MIN_HEIGHT = 6.dp
private val SPARK_BAR_SHAPE = RoundedCornerShape(3.dp)
private val SPARK_BAR_GAP = 4.dp
private val SPARK_LABEL_GAP = 5.dp
private const val SPARK_IDLE_ALPHA = 0.22f
private const val SPARK_LABEL_ALPHA = 0.7f
private const val SPARK_GROW_DURATION_MS = 500
private const val FRACTION_SCALE = 4

/**
 * Sparkline embebida del hero (mockup `.spark`/`.spark .b`/`.spark i`) — el DS deja el
 * contenedor/estilo en [com.example.msp_app.core.designsystem.component.MspHeroTodayCard]
 * (slot `sparkline`), esta barra concreta la arma el piloto (Plan 5). Cada barra escala su
 * alto proporcional a [TimelineBucket.total] contra el máximo de [Timeline.buckets]
 * ([SPARK_MAX_HEIGHT] a fracción 1, [SPARK_MIN_HEIGHT] como piso para que una barra en cero
 * siga siendo visible); la barra en [Timeline.highlightIndex] pinta
 * `colors.heroProgressFill`, el resto `colors.onBrand` a [SPARK_IDLE_ALPHA] (1:1
 * `rgba(255,255,255,.22)`/`var(--heroFill)` del mockup).
 *
 * En [ReportPeriod.SEMANA] cada barra es `clickable` → [onBarClick] con su índice (abre el
 * sheet de detalle del día, `SheetKind.DIA_CICLO`); en [ReportPeriod.DIA] las barras no son
 * accionables (no hay sheet de detalle por hora).
 *
 * **Crecimiento animado, gateado por reduce-motion** (spec §5): cada barra anima su alto
 * 0→objetivo en [SPARK_GROW_DURATION_MS] vía `Animatable` + `tween` (frame-clock de Compose,
 * nunca `kotlinx.coroutines.delay()` — mismo criterio anti-cuelgue que
 * [com.example.msp_app.feature.collectionreport.ui.components.StaggeredEntrance]); con
 * [rememberReducedMotionEnabled] activo, la barra se pinta directo a su alto final
 * (`snapTo`), sin animar.
 */
@Composable
fun Sparkline(
    timeline: Timeline,
    period: ReportPeriod,
    modifier: Modifier = Modifier,
    onBarClick: ((Int) -> Unit)? = null
) {
    val maxAmount = timeline.buckets.maxOfOrNull { it.total.amount } ?: BigDecimal.ZERO
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SPARK_BAR_GAP),
        verticalAlignment = Alignment.Bottom
    ) {
        timeline.buckets.forEachIndexed { index, bucket ->
            val fraction = barFraction(bucket, maxAmount)
            val targetHeight = maxOf(SPARK_MAX_HEIGHT * fraction, SPARK_MIN_HEIGHT)
            val highlighted = index == timeline.highlightIndex
            val clickHandler = onBarClick?.takeIf { period == ReportPeriod.SEMANA }
                ?.let { handler -> { handler(index) } }
            SparkBar(
                label = bucket.label,
                targetHeight = targetHeight,
                highlighted = highlighted,
                onClick = clickHandler,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun barFraction(bucket: TimelineBucket, maxAmount: BigDecimal): Float {
    if (maxAmount.signum() <= 0) return 0f
    return bucket.total.amount
        .divide(maxAmount, FRACTION_SCALE, RoundingMode.HALF_UP)
        .toFloat()
        .coerceIn(0f, 1f)
}

@Composable
private fun SparkBar(
    label: String,
    targetHeight: Dp,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = MspTheme.colors
    val reduced = rememberReducedMotionEnabled()
    val heightAnim = remember { Animatable(if (reduced) targetHeight.value else 0f) }
    LaunchedEffect(targetHeight, reduced) {
        if (reduced) {
            heightAnim.snapTo(targetHeight.value)
        } else {
            heightAnim.animateTo(targetHeight.value, tween(SPARK_GROW_DURATION_MS))
        }
    }
    Column(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SPARK_LABEL_GAP)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightAnim.value.dp)
                .clip(SPARK_BAR_SHAPE)
                .background(
                    if (highlighted) {
                        colors.heroProgressFill
                    } else {
                        colors.onBrand.copy(
                            alpha = SPARK_IDLE_ALPHA
                        )
                    }
                )
        )
        Text(
            text = label,
            style = MspTheme.type.ringCaption,
            color = colors.onBrand.copy(alpha = SPARK_LABEL_ALPHA)
        )
    }
}
