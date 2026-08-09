package com.example.msp_app.feature.collectionreport.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalGraphicsContext
import androidx.compose.ui.unit.IntSize
import com.example.msp_app.core.designsystem.component.LocalThemeReveal
import com.example.msp_app.core.designsystem.component.ThemeRevealController
import com.example.msp_app.core.designsystem.component.maxDistanceToCorner
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberReducedMotionEnabled

/** Duración de la reveal circular — 1:1 kollect §7.2 / task-9-brief.md. */
private const val REVEAL_DURATION_MS = 380

/** Easing de la reveal — task-9-brief.md ("tween(380, FastOutSlowInEasing)"). */
private val REVEAL_EASING = FastOutSlowInEasing

/**
 * Radio objetivo de la reveal para que el círculo cubra toda la caja de [size] desde [origin]
 * — delega en [maxDistanceToCorner] (DS). `internal` y NO `@Composable`: función pura
 * separada de [ThemeRevealRoot] a propósito para poder testearla sin Compose/Robolectric
 * (unit test JVM puro, ver `ThemeRevealRootTest`).
 */
internal fun revealTargetRadius(origin: Offset, size: IntSize): Float =
    maxDistanceToCorner(origin, size.width.toFloat(), size.height.toFloat())

/**
 * Composition root del reveal circular de tema (Telegram-style, kollect §7.2) — el pedazo que
 * Plan 3 dejó fuera de `:core:designsystem` a propósito (necesita el `content` de TODA la
 * pantalla para snapshotearlo, algo que un componente del DS no tiene ni debe tener; ver KDoc
 * de [ThemeRevealController]). Envuelve [content] en [MspTheme] — el caller (Task 10 cablea
 * `CollectionReportScreen` dentro de esto) NO debe volver a envolver en `MspTheme` por su
 * cuenta.
 *
 * **Estado hoisted, no propio:** [darkTheme]/[onToggleTheme] son controlados por el caller
 * (mismo criterio que [com.example.msp_app.core.designsystem.component.MspThemeToggle]) — este
 * root no decide DÓNDE vive la fuente de verdad del tema (esa decisión es del composition root
 * real de la app, Task 10); solo orquesta la animación alrededor de un flip que el caller ya
 * sabe hacer.
 *
 * **Mecanismo (sin reduce-motion):** instala un [ThemeRevealController] fresco vía
 * [LocalThemeReveal] — cualquier [com.example.msp_app.core.designsystem.component.MspThemeToggle]
 * dentro de [content] (p. ej. el de `ReportHeader`) lo detecta y, en vez de llamar directo a su
 * `onToggle`, reporta su centro en pantalla con `requestRevealFrom`. Este root observa
 * `controller.origin`:
 * 1. Mientras no hay reveal activa (`origin == null`), graba cada frame en un [GraphicsLayer] —
 *    la última grabación es siempre "el frame de ANTES del flip" en el instante en que se pide
 *    una reveal (se congela justo ahí, ver el guard de `record` abajo).
 * 2. Al pedirse una reveal, [LaunchedEffect] llama [onToggleTheme] (el flip real — el mismo que
 *    antes recibía `onToggle` directo) y anima un [Animatable] de radio 0 → [revealTargetRadius]
 *    con `tween(`[REVEAL_DURATION_MS]`, `[REVEAL_EASING]`)`. Mientras la reveal está activa, el
 *    `drawWithContent` DEJA de re-grabar el layer (queda congelado con el frame viejo) y en su
 *    lugar lo dibuja como overlay recortado con `clipPath(..., ClipOp.Difference)`: todo lo que
 *    cae FUERA del círculo creciente pinta el snapshot viejo; el "agujero" que crece revela el
 *    contenido real de abajo, que para entonces ya cambió de tema.
 * 3. Al completar la animación, `controller.consume()` limpia `origin` — se reanuda el grabado
 *    continuo y el overlay desaparece (ya no hace falta: el snapshot viejo quedó 100% recortado).
 *
 * **Fallback reduce-motion (mecanismo A, spec §5):** con
 * [rememberReducedMotionEnabled] activo, este composable ni siquiera instala el controller —
 * `content` cae directo al `onToggle` de `MspThemeToggle` ([LocalThemeReveal] default `null`) y
 * [MspTheme] anima el crossfade de paleta él solo (`animateColors = true`). Cero
 * `Animatable`/`GraphicsLayer`/`LaunchedEffect` de este composable se componen en esa rama — la
 * garantía anti-cuelgue que el brief exige (mismo criterio que
 * [com.example.msp_app.feature.collectionreport.ui.components.StaggeredEntrance]/
 * [com.example.msp_app.feature.collectionreport.ui.components.TabTransition]): los tests que
 * fuerzan `ANIMATOR_DURATION_SCALE = 0` (Roborazzi y compose-test) SIEMPRE toman esta rama.
 */
@Composable
fun ThemeRevealRoot(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (rememberReducedMotionEnabled()) {
        MspTheme(darkTheme = darkTheme, animateColors = true) {
            content()
        }
        return
    }

    val controller = remember { ThemeRevealController() }
    val graphicsContext = LocalGraphicsContext.current
    val graphicsLayer = remember(graphicsContext) { graphicsContext.createGraphicsLayer() }
    DisposableEffect(graphicsContext, graphicsLayer) {
        onDispose { graphicsContext.releaseGraphicsLayer(graphicsLayer) }
    }
    val revealPath = remember { Path() }
    val radius = remember { Animatable(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val origin = controller.origin

    LaunchedEffect(origin) {
        val requestedOrigin = origin ?: return@LaunchedEffect
        onToggleTheme()
        radius.snapTo(0f)
        radius.animateTo(
            targetValue = revealTargetRadius(requestedOrigin, boxSize),
            animationSpec = tween(durationMillis = REVEAL_DURATION_MS, easing = REVEAL_EASING)
        )
        controller.consume()
    }

    MspTheme(darkTheme = darkTheme, animateColors = false) {
        CompositionLocalProvider(LocalThemeReveal provides controller) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
                    .drawWithContent {
                        val activeOrigin = controller.origin
                        if (activeOrigin == null) {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                        }
                        drawContent()
                        if (activeOrigin != null) {
                            revealPath.reset()
                            revealPath.addOval(Rect(center = activeOrigin, radius = radius.value))
                            clipPath(path = revealPath, clipOp = ClipOp.Difference) {
                                drawLayer(graphicsLayer)
                            }
                        }
                    }
            ) {
                content()
            }
        }
    }
}
