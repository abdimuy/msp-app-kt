package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import kotlin.math.roundToInt

private val RING_DIAMETER = 74.dp
private val RING_STROKE_WIDTH = 7.dp
private const val FULL_SWEEP_DEG = 360f
private const val START_ANGLE_DEG = -90f
private const val PERCENT_SCALE = 100

/**
 * Anillo de progreso circular — 74dp de diámetro, trazo 7dp
 * `StrokeCap.Round`, dibujado con dos `drawArc` (track completo + barrido
 * `360 * progress` desde las 12 en punto, `-90°`) y el porcentaje centrado
 * con `MspTheme.type.ringValue` (1:1 kollect §8.3). El trazo se inscribe con
 * un margen de medio grosor para que el `StrokeCap.Round` no se recorte
 * contra el borde del `Canvas`.
 *
 * Sin animación interna de [progress], igual que [MspProgressBar] — el
 * caller decide si anima el valor.
 */
@Composable
fun MspProgressRing(
    progress: Float,
    fillColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
    valueColor: Color = MspTheme.colors.onSurface
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(modifier = modifier.size(RING_DIAMETER), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(RING_DIAMETER)) {
            val strokePx = RING_STROKE_WIDTH.toPx()
            val diameter = size.minDimension - strokePx
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
            drawArc(
                color = trackColor,
                startAngle = START_ANGLE_DEG,
                sweepAngle = FULL_SWEEP_DEG,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = fillColor,
                startAngle = START_ANGLE_DEG,
                sweepAngle = FULL_SWEEP_DEG * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = stroke
            )
        }
        Text(
            text = "${(fraction * PERCENT_SCALE).roundToInt()}%",
            style = MspTheme.type.ringValue,
            color = valueColor
        )
    }
}
