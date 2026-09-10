package com.example.msp_app.core.designsystem.component

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

/** Duración de la reveal circular — 1:1 kollect §7.2 / task-9-brief.md. */
private const val REVEAL_DURATION_MS = 380

/** Easing de la reveal — task-9-brief.md ("tween(380, FastOutSlowInEasing)"). */
private val REVEAL_EASING = FastOutSlowInEasing

/**
 * Radio objetivo para que el círculo cubra toda la caja de [size] desde [origin]. Pura y
 * separada del composable a propósito, para poder probarla sin Compose/Robolectric.
 */
fun revealTargetRadius(origin: Offset, size: IntSize): Float =
    maxDistanceToCorner(origin, size.width.toFloat(), size.height.toFloat())

/**
 * **El host de la reveal circular de tema — el mecanismo, una sola vez, para toda la app.**
 *
 * ## Por qué existe este archivo (Ruling BP)
 *
 * El mecanismo vivía **entero dentro de `:feature:collectionReport`**
 * (`ui/theme/ThemeRevealRoot.kt`), y el KDoc de [ThemeRevealController] declaraba que el root
 * "necesita el `content` de TODA la app, algo que este módulo no tiene ni debe tener". La
 * consecuencia práctica no era teórica: **el mismo `MspThemeToggle` se comportaba distinto en
 * dos pantallas de nuestra propia app** — reveal circular en el reporte de cobranza, crossfade
 * en cualquier otra— porque solo el reporte instalaba un host. Kollect lo instala en su raíz
 * (`MainActivity` → `ThemeRevealRoot`), así que sus toggles animan todos igual.
 *
 * No tener la animación sería aceptable; que **el mismo control se sienta distinto según la
 * pantalla** no lo es, y es la clase de incoherencia que ninguna captura muestra.
 *
 * **No se resolvió montándolo en la raíz de `:app`** (Ruling BJ): el host dibuja —envuelve el
 * contenido en un `Box` que graba un `GraphicsLayer`— así que ponerlo alrededor de
 * `AppNavigation` metería a la app legada entera a un pipeline de dibujo nuevo, sin un solo
 * golden que lo cubra. Se resolvió al revés: el **mecanismo** se centraliza acá y cada pantalla
 * Msp lo instala sobre sí misma, igual que ya hace con `MspTheme`.
 *
 * ## La ranura [tema], y por qué el host no conoce el tema
 *
 * El host no recibe `darkTheme` ni llama a
 * [com.example.msp_app.core.designsystem.theme.MspTheme]: recibe [tema], una ranura que el
 * caller cierra con **su** envoltorio de tema y a la que el host solo le dice **cuándo animar
 * la paleta**. Eso es lo que permite que el reporte siga usando su `ReportMspTheme` (rampa
 * tipográfica comprimida + `fontScale` neutralizado) y que la lista de clientes use un
 * `MspTheme` pelado, **compartiendo el mismo mecanismo de reveal**. Sin la ranura habría que
 * elegir entre duplicar 80 líneas o imponerle a un feature la tipografía del otro.
 *
 * ## Mecanismo (sin reduce-motion) — 1:1 kollect §7.2
 *
 * Instala un [ThemeRevealController] fresco vía [LocalThemeReveal]; cualquier [MspThemeToggle]
 * dentro de [content] reporta su centro en pantalla con `requestRevealFrom` en vez de llamar
 * directo a su `onToggle`. Este host observa `controller.origin`:
 * 1. Graba el `content` vivo en un [androidx.compose.ui.graphics.layer.GraphicsLayer] CADA
 *    frame y lo pinta con `drawLayer` (captura canónica: un solo `drawContent()` por pase de
 *    dibujo; lo que hay en pantalla ES el layer). Así el layer siempre está poblado.
 * 2. Al pedirse una reveal, [LaunchedEffect] — en este orden EXACTO — arranca el radio en 0,
 *    fija el centro, materializa una copia INMUTABLE del frame viejo con
 *    `contentLayer.toImageBitmap()` **ANTES** de [onToggleTheme] (el flip real), luego voltea
 *    el tema y anima el radio 0 → [revealTargetRadius] con `tween(`[REVEAL_DURATION_MS]`,
 *    `[REVEAL_EASING]`)`. Mientras existe ese snapshot, [tema] recibe `animateColors = false`
 *    para que la PALETA se voltee de golpe: el único movimiento es el disco.
 * 3. Un overlay dibuja ese `ImageBitmap` viejo recortado con `clipPath(..., ClipOp.Difference)`:
 *    lo que cae FUERA del círculo creciente muestra el frame viejo, y el agujero que crece
 *    revela el `content` real, que ya cambió de tema. Al completar se limpia el snapshot y
 *    `controller.consume()` limpia `origin`.
 *
 * ## Fallback reduce-motion (mecanismo A, spec §5)
 *
 * Con [reducedMotion] este composable **ni instala el controller**: `content` cae directo al
 * `onToggle` de [MspThemeToggle] ([LocalThemeReveal] default `null`) y el crossfade de paleta lo
 * anima el propio tema (`animateColors = true`). Cero `Animatable`/`GraphicsLayer`/
 * `toImageBitmap`/`LaunchedEffect` se componen en esa rama — la garantía anti-cuelgue que el
 * brief exige (todo test que fuerza `ANIMATOR_DURATION_SCALE = 0`, Roborazzi y compose-test,
 * SIEMPRE toma esta rama; el `GraphicsLayer` respaldado por `RenderNode`/`Picture` no es
 * confiable en Robolectric).
 *
 * La rama de reduce-motion **no envuelve nada en un `Box` y no aplica [modifier]** — es un
 * `return` temprano idéntico al que tenía `ThemeRevealRoot` antes de esta extracción. Esa
 * asimetría se conserva a propósito: es la rama que fotografían los 86 goldens `.png` de
 * `feature/collectionReport` (carpeta `src/test/screenshots`, verificados byte a byte contra
 * `--rerun-tasks`), y agregarle un nodo de layout los movería todos.
 *
 * [reducedMotion] llega como parámetro y no se lee acá porque **la combinación de señales es
 * del caller**: el reporte usa `rememberReportReducedMotion()` (accesibilidad del SO **o** la
 * preferencia propia de la app), y ese criterio deliberadamente no se centralizó (ver su KDoc).
 */
@Composable
fun MspThemeRevealHost(
    onToggleTheme: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    tema: @Composable (animateColors: Boolean, contenido: @Composable () -> Unit) -> Unit,
    content: @Composable () -> Unit
) {
    if (reducedMotion) {
        tema(true) { content() }
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

    tema(oldSnapshot == null) {
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
                // declarado DESPUÉS del contenido → es SIEMPRE la última capa dibujada, por
                // encima de cualquier tarjeta que se recorte a su propio graphics layer (fix de
                // z-order medido en el reporte). Solo existe mientras hay snapshot.
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
