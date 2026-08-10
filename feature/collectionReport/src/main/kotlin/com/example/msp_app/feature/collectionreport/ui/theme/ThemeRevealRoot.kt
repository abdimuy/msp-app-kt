package com.example.msp_app.feature.collectionreport.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
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
 * **Mecanismo (sin reduce-motion) — 1:1 con kollect:** instala un [ThemeRevealController]
 * fresco vía [LocalThemeReveal]; cualquier
 * [com.example.msp_app.core.designsystem.component.MspThemeToggle] dentro de [content] (p. ej.
 * el de `ReportHeader`) reporta su centro en pantalla con `requestRevealFrom` en vez de llamar
 * directo a su `onToggle`. Este root observa `controller.origin`:
 * 1. Graba el `content` vivo en un [androidx.compose.ui.graphics.layer.GraphicsLayer] CADA
 *    frame y lo pinta con `drawLayer` (patrón canónico de captura: un solo `drawContent()` por
 *    pase de dibujo; lo que hay en pantalla ES el layer). Así el layer siempre está poblado.
 * 2. Al pedirse una reveal, [LaunchedEffect] — en este orden EXACTO — arranca el radio en 0,
 *    fija el centro, materializa una copia INMUTABLE del frame viejo con
 *    `contentLayer.toImageBitmap()` **ANTES** de [onToggleTheme] (el flip real), luego voltea
 *    el tema y anima el radio 0 → [revealTargetRadius] con `tween(`[REVEAL_DURATION_MS]`,
 *    `[REVEAL_EASING]`)`. Mientras existe ese snapshot inmutable, [MspTheme] usa
 *    `animateColors = false` para que la PALETA se voltee de golpe (sin crossfade): el único
 *    movimiento es el disco.
 * 3. Un overlay dibuja ese `ImageBitmap` viejo recortado con `clipPath(..., ClipOp.Difference)`:
 *    todo lo que cae FUERA del círculo creciente muestra el frame viejo; el "agujero" que crece
 *    revela el `content` real de abajo, que para entonces ya cambió de tema. Al completar la
 *    animación se limpia el snapshot (`= null`) y `controller.consume()` limpia `origin`.
 *
 * **Fallback reduce-motion (mecanismo A, spec §5):** con [rememberReducedMotionEnabled] activo,
 * este composable ni siquiera instala el controller — `content` cae directo al `onToggle` de
 * `MspThemeToggle` ([LocalThemeReveal] default `null`) y [MspTheme] anima el crossfade de
 * paleta él solo (`animateColors = true`). Cero
 * `Animatable`/`GraphicsLayer`/`toImageBitmap`/`LaunchedEffect` de este composable se componen
 * en esa rama — la garantía anti-cuelgue que el brief exige (los tests que fuerzan
 * `ANIMATOR_DURATION_SCALE = 0`, Roborazzi y compose-test, SIEMPRE toman esta rama; el
 * `GraphicsLayer` respaldado por `RenderNode`/`Picture` no es confiable en Robolectric).
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
    val contentLayer = rememberGraphicsLayer()
    val revealPath = remember { Path() }
    val radius = remember { Animatable(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    // Copia INMUTABLE del frame viejo (pre-flip). Mientras no sea null, la paleta se voltea de
    // golpe (animateColors=false) y este bitmap es lo que se pinta FUERA del disco creciente.
    var oldSnapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var revealCenter by remember { mutableStateOf(Offset.Zero) }
    val origin = controller.origin

    LaunchedEffect(origin) {
        val requestedOrigin = origin ?: return@LaunchedEffect
        radius.snapTo(0f)
        revealCenter = requestedOrigin
        // Materializa el frame viejo ANTES del flip: es lo que el disco "retiene" por fuera.
        oldSnapshot = contentLayer.toImageBitmap()
        onToggleTheme()
        radius.animateTo(
            targetValue = revealTargetRadius(requestedOrigin, boxSize),
            animationSpec = tween(durationMillis = REVEAL_DURATION_MS, easing = REVEAL_EASING)
        )
        oldSnapshot = null
        controller.consume()
    }

    MspTheme(darkTheme = darkTheme, animateColors = oldSnapshot == null) {
        CompositionLocalProvider(LocalThemeReveal provides controller) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
            ) {
                // Capa 1 (contenido vivo): captura canónica — grabar el layer y pintar EL layer
                // (un solo drawContent()), así el layer siempre queda poblado para el snapshot.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            contentLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(contentLayer)
                        }
                ) {
                    content()
                }
                // Capa 2 (overlay del reveal): el frame VIEJO recortado por el disco creciente,
                // dibujado como HERMANO declarado DESPUÉS del contenido → es SIEMPRE la última
                // capa dibujada, por encima del hero y de cualquier otra tarjeta (fix defecto
                // visual: el hero, al recortarse con `Modifier.clip` a su propio graphics layer,
                // componía por encima cuando el overlay se dibujaba dentro del MISMO nodo que el
                // contenido; separarlo a un sibling superior garantiza el z-order). Solo existe
                // mientras hay snapshot (durante la animación), así que fuera del reveal esta
                // capa no se compone.
                val snapshot = oldSnapshot
                if (snapshot != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .drawBehind {
                                revealPath.reset()
                                revealPath.addOval(
                                    Rect(center = revealCenter, radius = radius.value)
                                )
                                clipPath(path = revealPath, clipOp = ClipOp.Difference) {
                                    drawImage(snapshot)
                                }
                            }
                    )
                }
            }
        }
    }
}
