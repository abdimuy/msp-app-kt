package com.example.msp_app.core.designsystem.component

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/**
 * Bridge origen→dueño del flip de tema (1:1 `CampoThemeRevealController`,
 * kollect §7.2). Desacopla "qué botón se tocó" (quien reporta el [origin] en
 * coordenadas de pantalla) de "quién ejecuta el flip circular" (el
 * composition root de la app — `ThemeRevealRoot`, **Plan 5**, NO este
 * módulo).
 *
 * **Frontera Task 9 / Plan 5:** este archivo (+ [LocalThemeReveal] +
 * [maxDistanceToCorner] + `MspThemeToggle` en `ThemeToggle.kt`) es TODO lo
 * que el design system provee. El `ThemeRevealRoot` que de verdad ejecuta la
 * reveal — grabar el frame viejo en un `GraphicsLayer` cada composición,
 * animar un `Animatable(0f)` de radio con `tween(380,
 * FastOutSlowInEasing)`, y recortar el snapshot viejo con `clipPath(...,
 * ClipOp.Difference)` — necesita el `content` de TODA la app (el árbol
 * completo a snapshotear), algo que este módulo no tiene ni debe tener. Ese
 * root vive en el piloto de composición de Plan 5, no aquí.
 */
@Stable
class ThemeRevealController {
    /** Punto (coordenadas root) desde el que crece el círculo, o `null` cuando no hay una reveal en curso. */
    var origin: Offset? by mutableStateOf(null)
        private set

    /** El caller (`MspThemeToggle`) pide una reveal desde su propio centro en pantalla. */
    fun requestRevealFrom(origin: Offset) {
        this.origin = origin
    }

    /** El dueño de la reveal (`ThemeRevealRoot`, Plan 5) limpia [origin] al terminar la animación. */
    fun consume() {
        origin = null
    }
}

/**
 * Distancia más larga desde [origin] a cualquiera de las 4 esquinas de una
 * caja `width`×`height` — el radio que necesita el círculo de la reveal para
 * cubrir toda la pantalla sin dejar una esquina sin pintar. 1:1 kollect §7.2.
 */
fun maxDistanceToCorner(origin: Offset, width: Float, height: Float): Float {
    val dx = maxOf(origin.x, width - origin.x)
    val dy = maxOf(origin.y, height - origin.y)
    return hypot(dx, dy)
}

/**
 * `CompositionLocal` del bridge de reveal, default `null` (sin host
 * instalado). `MspThemeToggle` lo consulta: si hay un controller Y su propio
 * centro en pantalla es válido, pide la reveal circular; si no, cae al
 * `onToggle()` directo (crossfade fallback de
 * [com.example.msp_app.core.designsystem.theme.MspTheme]).
 */
val LocalThemeReveal = staticCompositionLocalOf<ThemeRevealController?> { null }
