package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** `testTag` del nombre de la tarjeta de cliente (la caja que recibe el arrastre). */
const val NOMBRE_EN_LA_TARJETA_TAG: String = "pagos_nombre_en_la_tarjeta"

/** `testTag` del texto que se mueve al arrastrar (su posición dice cuánto se movió). */
const val TEXTO_DEL_NOMBRE_TAG: String = "pagos_texto_del_nombre"

/**
 * Tras soltar el nombre, cuánto se queda donde lo dejó el dedo antes de regresar y
 * reanudar la marquesina. Decisión del dueño (2026-10-07); ajustable.
 */
const val PAUSA_ANTES_DE_VOLVER_MS: Long = 2_000L

/**
 * **Un renglón que, si no cabe, corre solo — y que el dedo puede arrastrar.**
 *
 * - Si el texto cabe: un `Text` normal, sin gestos (no le roba nada a nadie).
 * - Si no cabe: marquesina (`basicMarquee`), como siempre. Al tocar y arrastrar de
 *   lado, la marquesina se pausa y el texto sigue al dedo, acotado entre su inicio y su
 *   fin. Al soltar, tras [PAUSA_ANTES_DE_VOLVER_MS], regresa con un resorte y la
 *   marquesina vuelve (decisión del dueño, 2026-10-07).
 *
 * **El conflicto con el deslizado de segmentos.** La lista cambia de segmento al
 * deslizar de lado (`detectHorizontalDragGestures` en el contenedor). El arrastre de
 * este nombre se detecta en el hijo, que en la pasada `Main` ve el gesto ANTES que el
 * contenedor y lo consume: deslizar sobre un nombre desbordado no cambia de pestaña;
 * fuera del nombre, o sobre uno que cabe, todo sigue igual. Un toque simple no se
 * consume (la tarjeta se sigue abriendo) y un arrastre vertical no pasa el umbral
 * horizontal (la lista se sigue desplazando).
 *
 * Con movimiento reducido no hay marquesina ni arrastre: el nombre se corta con "…",
 * como antes — para quien pidió que nada se moviera.
 *
 * Al empezar a arrastrar, el texto arranca desde su posición inicial y no desde donde
 * iba la marquesina (`basicMarquee` no expone su desplazamiento).
 */
@Composable
fun NombreArrastrable(
    nombre: String,
    estilo: TextStyle,
    color: Color,
    reducirMovimiento: Boolean,
    modifier: Modifier = Modifier
) {
    if (reducirMovimiento) {
        Text(
            text = nombre,
            style = estilo,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.testTag(NOMBRE_EN_LA_TARJETA_TAG)
        )
        return
    }
    val medidor = rememberTextMeasurer()
    BoxWithConstraints(modifier.testTag(NOMBRE_EN_LA_TARJETA_TAG)) {
        val anchoDelTexto = remember(nombre, estilo) {
            medidor.measure(
                text = nombre,
                style = estilo,
                maxLines = 1,
                softWrap = false
            ).size.width
        }
        val desborde = (anchoDelTexto - constraints.maxWidth).coerceAtLeast(0).toFloat()
        if (desborde <= 0f) {
            Text(
                text = nombre,
                style = estilo,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.testTag(TEXTO_DEL_NOMBRE_TAG)
            )
            return@BoxWithConstraints
        }
        val desplazamiento = remember(nombre) { Animatable(0f) }
        var enManos by remember(nombre) { mutableStateOf(false) }
        var regreso by remember { mutableStateOf<Job?>(null) }
        val alcance = rememberCoroutineScope()
        Box(
            modifier = Modifier
                .clipToBounds()
                .pointerInput(nombre, desborde) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            regreso?.cancel()
                            enManos = true
                        },
                        onDragEnd = {
                            regreso = alcance.launch {
                                delay(PAUSA_ANTES_DE_VOLVER_MS)
                                desplazamiento.animateTo(
                                    0f,
                                    spring(stiffness = Spring.StiffnessLow)
                                )
                                enManos = false
                            }
                        },
                        onDragCancel = {
                            alcance.launch { desplazamiento.snapTo(0f) }
                            enManos = false
                        }
                    ) { cambio, arrastre ->
                        cambio.consume()
                        alcance.launch {
                            // Dedo a la izquierda → se ve el final del nombre.
                            desplazamiento.snapTo(
                                (desplazamiento.value - arrastre).coerceIn(0f, desborde)
                            )
                        }
                    }
                }
        ) {
            if (enManos) {
                Text(
                    text = nombre,
                    style = estilo,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .wrapContentWidth(align = Alignment.Start, unbounded = true)
                        .graphicsLayer { translationX = -desplazamiento.value }
                        .testTag(TEXTO_DEL_NOMBRE_TAG)
                )
            } else {
                Text(
                    text = nombre,
                    style = estilo,
                    color = color,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .basicMarquee()
                        .testTag(TEXTO_DEL_NOMBRE_TAG)
                )
            }
        }
    }
}
