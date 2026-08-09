package com.example.msp_app.core.designsystem.theme

import android.provider.Settings
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Tokens de motion del design system Msp. Filosofía del app entera: "sobrio y
 * con propósito — física de springs, sin animación decorativa. El app
 * maneja dinero ajeno; debe sentirse serio". Transcrito 1:1 de `CampoMotion`
 * (kollect-app, ver `.superpowers/research/kollect-app-designsystem.md` §5)
 * — exactamente dos springs, gobernados por convención de KDoc (no por el
 * type system).
 */
@Immutable
class MspMotion internal constructor() {
    /**
     * Spring por defecto para todo: elementos de pantalla asentándose,
     * actualizaciones de progreso, toggles, cambios de lista. Críticamente
     * amortiguado — sin overshoot, sin rebote.
     */
    fun <T> standard(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    /**
     * Spring enfatizado con un rebote sutil. RESERVADO al único beat
     * celebratorio ("pago confirmado", Plan 5) — no usar en otro lugar del
     * design system.
     */
    fun <T> emphasized(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
}

/**
 * Lee el ajuste de accesibilidad "Eliminar/Reducir animaciones" de Android
 * (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`) vía el `contentResolver`
 * del contexto actual. Es el interruptor que TODO componente animado del
 * design system debe consultar (spec §5: toda animación es desactivable →
 * crossfade/instantáneo cuando esto es `true`).
 */
@Composable
fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        scale == 0f
    }
}
