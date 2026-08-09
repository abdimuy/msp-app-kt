package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/**
 * Barra de progreso recta — dos `Box` anidados (track full-width + fill
 * `fillMaxWidth(fraction = progress)`), AMBOS clipados a
 * `MspTheme.shapes.chip` (pill completo): si solo se clipa el fill, el track
 * sobresale en las esquinas (1:1 kollect §8.3).
 *
 * Sin animación interna de [progress] — quien la use envuelve el valor en su
 * propio `animateFloatAsState` si quiere una transición suave (p.ej. el
 * `chartGrow`/`heroReveal` del dashboard). Como no hay animación propia, no
 * hay nada que apagar por `rememberReducedMotionEnabled()` aquí — ese
 * chequeo vive en el caller que decida animar.
 *
 * Usos: 9dp en el hero (`heroProgressFill` sobre un pozo translúcido) y 6dp
 * en filas de plan de venta (`brand` sobre `progressTrack`) — [height],
 * [fillColor] y [trackColor] los decide siempre el caller, sin default de
 * marca aquí.
 */
@Composable
fun MspProgressBar(
    progress: Float,
    height: Dp,
    fillColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(MspTheme.shapes.chip)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .clip(MspTheme.shapes.chip)
                .background(fillColor)
        )
    }
}
