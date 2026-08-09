package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.rememberReducedMotionEnabled

/** Duración de la entrada escalonada — `.an{animation:rise .5s forwards}` del mockup. */
private const val ENTRANCE_DURATION_MS = 500

/** Delay base (primer elemento) — `.an:nth-child(1){animation-delay:.03s}`. */
private const val ENTRANCE_BASE_DELAY_MS = 30

/** Paso de delay entre elementos consecutivos — `.09s - .03s = .06s`. */
private const val ENTRANCE_STEP_MS = 60

/** Desplazamiento vertical inicial — `.an{transform:translateY(9px)}`. */
private val ENTRANCE_RISE = 9.dp

/**
 * Entrada fade+rise escalonada por [index] (kollect/mockup `.an`, `@keyframes rise`):
 * opacidad 0→1 + traslación vertical [ENTRANCE_RISE]→0 en [ENTRANCE_DURATION_MS] (≤500ms,
 * spec del brief), con el delay creciendo `ENTRANCE_BASE_DELAY_MS + index * ENTRANCE_STEP_MS`
 * por elemento — mismo escalonado que el CSS `.an:nth-child(n)`.
 *
 * Implementado con `tween(delayMillis = ...)` — el delay vive DENTRO del `AnimationSpec`
 * (frame-clock de Compose), nunca como un `kotlinx.coroutines.delay()` de pared: así el
 * harness de compose-test (y Roborazzi) puede sincronizar/asentar la animación pumpeando
 * frames en vez de esperar reloj real — mismo criterio anti-cuelgue que
 * [com.example.msp_app.feature.collectionreport.ui.components.Sparkline] y
 * [com.example.msp_app.feature.collectionreport.ui.components.TabTransition].
 *
 * **Desactivable** (spec §5): con [rememberReducedMotionEnabled] activo, [content] se
 * pinta directo a opacidad plena sin animar — ninguna rama de `Animatable`/`LaunchedEffect`
 * se compone, mismo patrón que `MspPaymentSyncPill` (`:core:designsystem`).
 */
@Composable
fun StaggeredEntrance(index: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (rememberReducedMotionEnabled()) {
        Box(modifier = modifier) { content() }
        return
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(index) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = ENTRANCE_DURATION_MS,
                delayMillis = ENTRANCE_BASE_DELAY_MS + index * ENTRANCE_STEP_MS,
                easing = LinearOutSlowInEasing
            )
        )
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * ENTRANCE_RISE.toPx()
        }
    ) {
        content()
    }
}
