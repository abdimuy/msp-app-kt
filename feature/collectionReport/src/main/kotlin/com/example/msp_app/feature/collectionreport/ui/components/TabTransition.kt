package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.rememberReducedMotionEnabled
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod

/** Duración del swap Día↔Semana — `.pc.sw-r/.sw-l{animation:... .3s ...}` del mockup. */
private const val TAB_TRANSITION_DURATION_MS = 300

/** Easing exacto del mockup — `cubic-bezier(.2,.7,.2,1)`. */
private val TAB_TRANSITION_EASING = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/**
 * Wrapper de transición Día↔Semana (mockup `.pc.sw-r`/`.pc.sw-l`, `@keyframes swR/swL`):
 * Día→Semana entra deslizando desde la derecha, Semana→Día desde la izquierda, con fade
 * simultáneo — 300ms [TAB_TRANSITION_EASING]. Envuelve `AnimatedContent` con [period] como
 * `targetState`; [content] recibe el periodo objetivo para que el caller arme la sección que
 * corresponda (hoy: [com.example.msp_app.feature.collectionreport.ui.components.HeroSection]).
 *
 * La dirección se deriva del [period] destino (no de un `initialState` explícito): con solo
 * dos estados posibles, "entra desde la derecha" ⟺ el destino es [ReportPeriod.SEMANA] —
 * cierto sin importar desde cuál periodo se venía.
 *
 * **Desactivable** (spec §5): con [rememberReducedMotionEnabled] activo, el swap es
 * instantáneo (`EnterTransition.None`/`ExitTransition.None`, sin slide ni fade) — nunca un
 * crossfade residual que dependa de reloj real.
 */
@Composable
fun TabTransition(
    period: ReportPeriod,
    modifier: Modifier = Modifier,
    content: @Composable (ReportPeriod) -> Unit
) {
    val reduced = rememberReducedMotionEnabled()
    AnimatedContent(
        targetState = period,
        modifier = modifier,
        transitionSpec = {
            if (reduced) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val forward = targetState == ReportPeriod.SEMANA
                val enterSign = if (forward) 1 else -1
                val exitSign = if (forward) -1 else 1
                (
                    slideInHorizontally(
                        animationSpec = tween(TAB_TRANSITION_DURATION_MS, easing = TAB_TRANSITION_EASING)
                    ) { fullWidth -> enterSign * fullWidth } +
                        fadeIn(animationSpec = tween(TAB_TRANSITION_DURATION_MS))
                    ).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(TAB_TRANSITION_DURATION_MS, easing = TAB_TRANSITION_EASING)
                    ) { fullWidth -> exitSign * fullWidth } +
                        fadeOut(animationSpec = tween(TAB_TRANSITION_DURATION_MS))
                )
            }
        },
        label = "collection_report_tab_transition"
    ) { targetPeriod ->
        content(targetPeriod)
    }
}
