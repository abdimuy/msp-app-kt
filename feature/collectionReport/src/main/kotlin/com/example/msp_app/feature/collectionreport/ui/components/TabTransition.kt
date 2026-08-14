package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.theme.REPORT_STANDARD_DURATION_MS
import com.example.msp_app.feature.collectionreport.ui.theme.ReportStandardEasing
import com.example.msp_app.feature.collectionreport.ui.theme.rememberReportReducedMotion

/**
 * Wrapper de transición Día↔Semana (mockup `.pc.sw-r`/`.pc.sw-l`, `@keyframes swR/swL`):
 * Día→Semana entra deslizando desde la derecha, Semana→Día desde la izquierda, con fade
 * simultáneo — [REPORT_STANDARD_DURATION_MS] con [ReportStandardEasing] (tokens compartidos del
 * módulo, `ui/theme/ReportMotion.kt`). Envuelve `AnimatedContent` con [period] como
 * `targetState`; [content] recibe el periodo objetivo para que el caller arme la sección que
 * corresponda (hoy: [com.example.msp_app.feature.collectionreport.ui.components.HeroSection]).
 *
 * La dirección se deriva del [period] destino (no de un `initialState` explícito): con solo
 * dos estados posibles, "entra desde la derecha" ⟺ el destino es [ReportPeriod.SEMANA] —
 * cierto sin importar desde cuál periodo se venía.
 *
 * **Desactivable** (spec §5): con [rememberReportReducedMotion] activo (accesibilidad del SO
 * O "Deshabilitar animaciones" de Configuración), el swap es instantáneo
 * (`EnterTransition.None`/`ExitTransition.None`, sin slide ni fade) — nunca un crossfade
 * residual que dependa de reloj real.
 */
@Composable
fun TabTransition(
    period: ReportPeriod,
    modifier: Modifier = Modifier,
    content: @Composable (ReportPeriod) -> Unit
) {
    val reduced = rememberReportReducedMotion()
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
                        animationSpec = tween(REPORT_STANDARD_DURATION_MS, easing = ReportStandardEasing)
                    ) { fullWidth -> enterSign * fullWidth } +
                        fadeIn(animationSpec = tween(REPORT_STANDARD_DURATION_MS))
                    ).togetherWith(
                    slideOutHorizontally(
                        animationSpec = tween(REPORT_STANDARD_DURATION_MS, easing = ReportStandardEasing)
                    ) { fullWidth -> exitSign * fullWidth } +
                        fadeOut(animationSpec = tween(REPORT_STANDARD_DURATION_MS))
                )
            }
        },
        label = "collection_report_tab_transition"
    ) { targetPeriod ->
        content(targetPeriod)
    }
}
