package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import kotlin.math.max

/**
 * **Un renglón que se parte por segmentos enteros, nunca a media palabra.**
 *
 * El renglón de abajo de la fila —cuenta, método, cobrador— era un solo `Text`
 * con `maxLines` y elipsis, y a escala grande el cobrador salía `Maris…`. Es
 * el principio 9 de la rama: *un dato a medias es un dato falso; antes de
 * truncar, apilar*. Aquí cada segmento es su propio texto y se acomoda como un
 * flujo: si cabe en el renglón en curso, va ahí; si no, baja **entero** al
 * siguiente. Sólo un segmento que por sí solo no quepa en todo el ancho se
 * parte —en palabras, no con elipsis—; nunca se recorta.
 *
 * ## Dónde vive el punto medio
 *
 * El separador ` · ` es un nodo aparte y se pinta **sólo entre dos segmentos
 * del mismo renglón**. Cuando un segmento baja, el separador que lo precedía
 * no se coloca: ningún renglón termina con un `·` colgando ni empieza con
 * `· Transferencia`. Por eso ya no hace falta el espacio no separable que
 * tenía el `Text` único: aquella era una defensa para un salto de línea que
 * decidía el texto; ahora el salto lo decide este layout, y el separador sólo
 * existe donde hay algo a sus dos lados.
 *
 * Los separadores no tienen semántica: TalkBack lee los segmentos, no los
 * puntos.
 */
@Composable
internal fun RenglonPorSegmentos(
    segmentos: List<String>,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            segmentos.forEach {
                Text(
                    text = it,
                    style = style,
                    color = color,
                    modifier = Modifier.testTag(SEGMENTO_DEL_RENGLON_TAG)
                )
            }
            repeat(max(segmentos.size - 1, 0)) {
                Text(
                    text = SEPARADOR,
                    style = style,
                    color = color,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
        },
        modifier = modifier
    ) { medibles, restricciones ->
        // Sin tope de alto: cada segmento mide lo que su texto necesita. Con el
        // alto que le queda a la pantalla como tope, una fila pegada al borde
        // de abajo apretaba sus segmentos uno encima de otro.
        val sueltas = Constraints(maxWidth = restricciones.maxWidth)
        val piezas = medibles.take(segmentos.size).map { it.measure(sueltas) }
        val separadores = medibles.drop(segmentos.size).map { it.measure(sueltas) }
        val acomodo = acomodar(piezas, separadores, restricciones.maxWidth)
        // El tamaño que se reporta respeta las restricciones del padre: si
        // excediera el alto disponible, Compose centraría el contenido y los
        // segmentos subirían encima del título. Así, lo que no quepa se
        // desborda hacia ABAJO, como en cualquier Column.
        layout(
            restricciones.constrainWidth(acomodo.ancho),
            restricciones.constrainHeight(acomodo.alto)
        ) {
            acomodo.lugares.forEach { (pieza, x, y) -> pieza.place(x, y) }
        }
    }
}

/** `testTag` de cada segmento del renglón de abajo, para leerlos en orden. */
const val SEGMENTO_DEL_RENGLON_TAG: String = "pagos_contacto_segmento"

/** El texto del separador. Ver "Dónde vive el punto medio" en [RenglonPorSegmentos]. */
private const val SEPARADOR = " · "

private data class Acomodo(val ancho: Int, val alto: Int, val lugares: List<Triple<Placeable, Int, Int>>)

/**
 * El flujo: cada segmento va en el renglón en curso si cabe **junto con su
 * separador**; si no, abre renglón y su separador no se coloca.
 */
private fun acomodar(
    piezas: List<Placeable>,
    separadores: List<Placeable>,
    anchoMaximo: Int
): Acomodo {
    val lugares = mutableListOf<Triple<Placeable, Int, Int>>()
    var x = 0
    var y = 0
    var altoDelRenglon = 0
    var ancho = 0
    piezas.forEachIndexed { i, pieza ->
        val separador = separadores.getOrNull(i - 1)
        val cabeAqui = separador != null && x + separador.width + pieza.width <= anchoMaximo
        if (separador != null && cabeAqui) {
            lugares += Triple(separador, x, y)
            x += separador.width
        } else if (i > 0) {
            y += altoDelRenglon
            x = 0
            altoDelRenglon = 0
        }
        lugares += Triple(pieza, x, y)
        x += pieza.width
        altoDelRenglon = max(altoDelRenglon, pieza.height)
        ancho = max(ancho, x)
    }
    return Acomodo(ancho = ancho, alto = y + altoDelRenglon, lugares = lugares)
}
