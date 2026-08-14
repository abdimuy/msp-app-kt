package com.example.msp_app.feature.collectionreport.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.rememberReducedMotionEnabled

/**
 * Duración estándar de las transiciones de estado del reporte (swap Día↔Semana, expandir/
 * colapsar la lista de pagos) — `.3s` del mockup (`.pc.sw-r/.sw-l{animation:... .3s ...}`).
 *
 * Vive aquí y no en cada componente porque ya la comparten dos transiciones distintas
 * ([com.example.msp_app.feature.collectionreport.ui.components.TabTransition] y el colapsable
 * de [com.example.msp_app.feature.collectionreport.ui.components.DetailList]): duplicar el
 * número las dejaría divergir en la primera afinación y el tablero se sentiría desparejo.
 */
internal const val REPORT_STANDARD_DURATION_MS = 300

/**
 * Easing estándar del reporte — `cubic-bezier(.2,.7,.2,1)` EXACTO del mockup: arranca rápido y
 * frena largo, sin overshoot. Es la única curva del módulo para transiciones de estado (misma
 * filosofía que `MspMotion`: "sobrio y con propósito, sin rebote" — el app maneja dinero ajeno).
 * No inventar curvas nuevas por componente; si una transición necesita otra sensación, primero
 * hay que justificar por qué esta no sirve.
 */
internal val ReportStandardEasing: Easing = CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f)

/**
 * Interruptor de reduce-motion EFECTIVO del reporte de cobranza: reducido si CUALQUIERA de
 * las dos señales lo pide —
 * [rememberReducedMotionEnabled] (ajuste de accesibilidad del SISTEMA operativo,
 * `ANIMATOR_DURATION_SCALE == 0`) O [LocalReduceMotion] (preferencia PROPIA de la app, elegida
 * en Configuración, spec `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`
 * §"Deshabilitar animaciones") — igual que documenta el KDoc de [LocalReduceMotion]: "combinar
 * ambas señales... es trabajo de la raíz de composición cuando cablee la pantalla". Este
 * composable ES esa combinación para `:feature:collectionReport` (cada pantalla migrada la
 * resuelve por su cuenta; no se centraliza en `:core:designsystem` porque no toda pantalla
 * migrada quiere necesariamente el mismo criterio de combinación).
 *
 * Todas las animaciones del reporte que antes solo consultaban [rememberReducedMotionEnabled]
 * directo ([com.example.msp_app.feature.collectionreport.ui.theme.ThemeRevealRoot],
 * [com.example.msp_app.feature.collectionreport.ui.components.StaggeredEntrance],
 * [com.example.msp_app.feature.collectionreport.ui.components.TabTransition],
 * [com.example.msp_app.feature.collectionreport.ui.components.Sparkline]) ahora consultan esta
 * función en su lugar — así "Deshabilitar animaciones" en Configuración degrada TODO el
 * reporte a instantáneo/estático, no solo lo que ya cubría la accesibilidad del sistema.
 */
@Composable
fun rememberReportReducedMotion(): Boolean =
    rememberReducedMotionEnabled() || LocalReduceMotion.current
