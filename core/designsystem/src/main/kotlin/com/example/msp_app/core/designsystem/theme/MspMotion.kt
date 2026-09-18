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

/**
 * **El interruptor de movimiento reducido EFECTIVO de una pantalla Msp** —
 * reducido si CUALQUIERA de las dos señales lo pide:
 *
 * 1. [rememberReducedMotionEnabled] — la señal de **accesibilidad del sistema
 *    operativo** (`Settings.Global.ANIMATOR_DURATION_SCALE == 0f`). Es la que el
 *    cobrador enciende en Ajustes de Android, fuera de esta app.
 * 2. [LocalReduceMotion] — la **preferencia propia de la app**, la casilla
 *    "Deshabilitar animaciones" de Configuración, provista por la raíz de
 *    composición que cablea la pantalla.
 *
 * Son señales distintas y ninguna implica a la otra: el KDoc de
 * [LocalReduceMotion] lo dice explícitamente. Una animación que solo consulta la
 * primera ignora la casilla que el propio cobrador marcó dentro de la app; una
 * que solo consulta la segunda ignora el ajuste de accesibilidad del teléfono.
 *
 * ## Por qué AHORA sí vive en `:core:designsystem`
 *
 * Vivió deliberadamente fuera hasta ahora. El KDoc de [LocalReduceMotion]
 * declaraba que combinar ambas señales "es trabajo de la raíz de composición
 * cuando cablee la pantalla — no de este módulo", y `ListaDeClientesScreen`
 * cargaba la combinación en una línea con el comentario "cuando aparezca el
 * tercer caller, esto sí gana su lugar en `:core:designsystem`".
 *
 * Aparecieron **ocho**: las ocho pantallas Msp nuevas instalan
 * [com.example.msp_app.core.designsystem.component.MspThemeRevealHost] y todas
 * le tienen que pasar el MISMO criterio. Ocho copias de un `||` es la forma
 * exacta en que dos de ellas terminan divergiendo —una consultando una señal y
 * otra las dos— y esa divergencia no la ve ningún golden: el defecto solo
 * aparece en el teléfono de quien tiene el ajuste puesto.
 *
 * ## Lo que esta función NO reemplaza
 *
 * `feature.collectionreport.ui.theme.rememberReportReducedMotion()` **no se
 * unifica con ésta y no se toca.** Su KDoc explica que es el criterio propio del
 * reporte de cobranza y que no se centralizó a propósito, "porque no toda
 * pantalla migrada quiere necesariamente el mismo criterio de combinación". Que
 * hoy los dos cuerpos coincidan no es razón para atarlos: el día que el reporte
 * cambie el suyo, esta función no tiene por qué moverse con él.
 */
@Composable
fun rememberMspReducedMotion(): Boolean =
    rememberReducedMotionEnabled() || LocalReduceMotion.current
