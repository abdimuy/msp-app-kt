package com.example.msp_app.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion

/**
 * **Lo que una elección revela, entrando y saliendo — nunca de golpe.**
 *
 * El caso que lo origina: en "¿Qué pasó en la puerta?" (`RegistrarVisitaScreen`),
 * elegir un desenlace hacía aparecer de golpe la captura que le corresponde
 * —la promesa, la hora de la cita, de cuáles cuentas— y el dueño lo describió
 * como "muy agresivo". Cambiar de una elección a otra se sentía igual de
 * brusco: el contenido viejo desaparecía y el nuevo aparecía en el mismo
 * instante, un parpadeo en vez de un reemplazo.
 *
 * [MspRevealedContent] envuelve [AnimatedContent] con el criterio único del
 * design system en vez de que cada pantalla escriba su propio
 * `transitionSpec`: crossfade con [MspMotion.standard] —el mismo spring que
 * ya gobierna "cambios de lista" en toda la app (ver su KDoc)— y el tamaño del
 * contenedor se anima solo, con el `SizeTransform` por default de
 * [AnimatedContent]. Es spring, no `tween`: la app entera se rige por dos
 * springs y no por duraciones sueltas, así que "corta" aquí significa
 * "críticamente amortiguado y con rigidez media", igual que cualquier otro
 * asentamiento de la app — no un número de milisegundos inventado para esta
 * pantalla.
 *
 * ## Sólo lo que se revela, nunca el selector
 *
 * [targetState] es lo que decide QUÉ se revela — un desenlace elegido, un
 * filtro, cualquier llave que identifique "cuál es el contenido de abajo" —,
 * no el control que lo elige. El selector (los renglones de opción, un
 * segmento, lo que sea) se queda fuera de este composable y se pinta como
 * siempre: la regla del dueño fue "anima lo que se revela, no el selector", y
 * la forma de cumplirla es que este composable nunca reciba el selector como
 * parte de [content].
 *
 * ## Movimiento reducido: el contenido está, no se anima
 *
 * Con [rememberMspReducedMotion] activo la transición es instantánea
 * (`EnterTransition.None` / `ExitTransition.None`, igual que
 * [MspThemeRevealHost] y `HojaDeLaUbicacion`): el contenido nuevo está
 * completo desde el primer frame. Nunca hay un estado a medias ni una
 * pantalla en blanco — eso es justo lo que
 * `ElContenidoRevelaConMovimientoReducidoTest` mide, porque una animación no
 * se puede demostrar con un golden (una captura congela un solo frame), pero
 * la PRESENCIA del contenido sin ella sí es una aserción que puede fallar.
 */
@Composable
fun <T> MspRevealedContent(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable (T) -> Unit
) {
    val sinMovimiento = rememberMspReducedMotion()
    val spec: FiniteAnimationSpec<Float> = MspTheme.motion.standard()
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentAlignment = contentAlignment,
        transitionSpec = {
            if (sinMovimiento) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(spec) togetherWith fadeOut(spec)
            }
        },
        label = "MspRevealedContent"
    ) { estado -> content(estado) }
}
