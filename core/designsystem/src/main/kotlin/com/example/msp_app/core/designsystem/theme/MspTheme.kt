package com.example.msp_app.core.designsystem.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * `CompositionLocal` de cada grupo de tokens. Sin valor por defecto sano:
 * leerlos fuera de [MspTheme] arroja un error ruidoso en vez de pintar con
 * basura (patrón kollect §6) — un componente del design system usado sin
 * envolver en [MspTheme] es un bug de integración, no un caso a tolerar.
 * `internal`: el único punto de lectura soportado desde fuera de este
 * archivo es el objeto [MspTheme] de abajo (`MspTheme.colors`, etc.), nunca
 * estos locals directamente.
 */
internal val LocalMspColors = staticCompositionLocalOf<MspColors> {
    error("MspTheme ausente: MspColors leído fuera de MspTheme")
}
internal val LocalMspTypography = staticCompositionLocalOf<MspTypography> {
    error("MspTheme ausente: MspTypography leído fuera de MspTheme")
}
internal val LocalMspSpacing = staticCompositionLocalOf<MspSpacing> {
    error("MspTheme ausente: MspSpacing leído fuera de MspTheme")
}
internal val LocalMspShapes = staticCompositionLocalOf<MspShapes> {
    error("MspTheme ausente: MspShapes leído fuera de MspTheme")
}
internal val LocalMspMotion = staticCompositionLocalOf<MspMotion> {
    error("MspTheme ausente: MspMotion leído fuera de MspTheme")
}

/**
 * Raíz del design system Msp. Provee los 5 grupos de tokens (colores T2,
 * tipografía T3, formas/espaciado/motion T4) vía `CompositionLocal` y
 * envuelve M3 (`MaterialTheme`) para que componentes stock hereden valores
 * sanos — sin dynamic color, sin Material purple (kollect §6).
 *
 * El crossfade de paleta (mecanismo A, fallback de reduce-motion) interpola
 * [MspColors] campo a campo vía [lerpMspColors] mientras `darkTheme` cambia.
 * `animateFloatAsState` settlea instantáneo en la primera composición → un
 * screenshot/`@Preview` estático siempre rinde la paleta destino (`0f`/`1f`
 * exacto), nunca un frame intermedio de por sí — pero todo screenshot test
 * pasa además [animateColors] = `false` explícito (ver
 * `screenshot/MspScreenshotTest.capture`) para blindarse de cambios futuros
 * en esa garantía y renderizar 100% determinista.
 *
 * [animateColors] es el escape hatch que la reveal de tema (Task 9) usará
 * para tomar control manual de la transición en vez de dejar que
 * `MspTheme` la anime sola.
 */
@Composable
fun MspTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    animateColors: Boolean = true,
    content: @Composable () -> Unit
) {
    val light = remember { mspLightColors() }
    val dark = remember { mspDarkColors() }
    val progress by animateFloatAsState(
        targetValue = if (darkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "themeCrossfade"
    )
    val darkResolved = if (animateColors) progress >= 0.5f else darkTheme
    val colors = if (animateColors) {
        remember(progress, light, dark) { lerpMspColors(light, dark, progress) }
    } else {
        remember(darkTheme, light, dark) { if (darkTheme) dark else light }
    }
    val type = remember { mspTypography() }
    val spacing = remember { MspSpacing() }
    val shapes = remember { MspShapes() }
    val motion = remember { MspMotion() }
    CompositionLocalProvider(
        LocalMspColors provides colors,
        LocalMspTypography provides type,
        LocalMspSpacing provides spacing,
        LocalMspShapes provides shapes,
        LocalMspMotion provides motion
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkResolved),
            typography = type.toMaterialTypography(),
            content = content
        )
    }
}

/**
 * Accesores de solo-lectura a los tokens activos — análogo a
 * `MaterialTheme.colorScheme`/`.typography` pero para los tokens propios del
 * design system Msp. Todo componente del módulo (y de la app, una vez
 * migrada) lee de aquí, nunca de `MaterialTheme.*` directamente.
 */
object MspTheme {
    val colors: MspColors
        @Composable
        @ReadOnlyComposable
        get() = LocalMspColors.current

    val type: MspTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalMspTypography.current

    val spacing: MspSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalMspSpacing.current

    val shapes: MspShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalMspShapes.current

    val motion: MspMotion
        @Composable
        @ReadOnlyComposable
        get() = LocalMspMotion.current
}
