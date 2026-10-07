package com.example.msp_app.feature.pagos.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * El estado de colapso de la cabecera de la lista de clientes: **uno solo**, compartido
 * por los cuatro segmentos, que sobrevive a salir de la pantalla y volver.
 *
 * ## Por qué ya no se deriva del índice de la lista (causa raíz del teclado, 2026-10-07)
 *
 * La versión anterior decidía "grande o compacta" mirando `firstVisibleItemIndex`. Ese
 * índice **también cambia sin que nadie deslice**: buscar apaga el orden de cobranza
 * ([CarteraEnPantalla.proyectar]), la lista se reordena y, con `key = clienteId`, el
 * `LazyListState` se queda anclado al cliente que estaba arriba — que en el orden nuevo
 * vive en el índice 24. Medido (`ElBuscadorNoPierdeElFocoTest`, diagnóstico del
 * 2026-10-07): al teclear "C" el índice saltó `0 → 24`, la cabecera se encogió, un
 * "settle" `24 → 23` la volvió a abrir, y el campo grande —que vivía en un
 * `if (cabeceraGrande)`— salió de la composición y regresó **nuevo**, sin foco y con la
 * selección en `TextRange(0, 0)`. Para el cobrador: el teclado se baja solo.
 *
 * Ahora la cabecera sólo se mueve con **gestos** — lo que llega por `nestedScroll` —:
 * un reordenamiento, un `scrollToItem` o un cambio de segmento no la tocan.
 *
 * ## `exitUntilCollapsed` (decisión del dueño, 2026-10-07)
 *
 * Al subir el dedo la cabecera se encoge primero y la lista se desplaza después. Al
 * bajarlo, la lista regresa primero y la cabecera se abre **sólo en el último tramo**,
 * cuando la lista ya llegó a su primer elemento — un jalón corto hacia abajo a media
 * lista la deja compacta (como el título grande de iOS o `LargeTopAppBar`). Si el
 * dedo se suelta a medio camino, se asienta con un resorte en el extremo más cercano.
 *
 * El mecanismo es el de Material 3 ([TopAppBarDefaults.exitUntilCollapsedScrollBehavior])
 * sin la `TopAppBar`: [ZonaQueSeEncoge] es la que mide la parte que se esconde y le
 * fija [TopAppBarState.heightOffsetLimit].
 *
 * @param arrancaColapsada si no hay estado guardado, la cabecera nace compacta — lo usa
 *   la pantalla cuando la lista ya arranca desplazada (un golden con índice inicial), para
 *   no pintar una cabecera grande sobre una lista que no está en su tope.
 * @param listaVisible la lista del segmento que se ve; sólo se consulta si se puede
 *   desplazar: con una lista que cabe entera en pantalla la cabecera no se encoge (pero
 *   si ya venía encogida de otro segmento, jalar hacia abajo la abre).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberCabeceraQueSeEncoge(
    arrancaColapsada: Boolean,
    reducirMovimiento: Boolean,
    listaVisible: LazyListState
): TopAppBarScrollBehavior {
    val estado = rememberSaveable(saver = TopAppBarState.Saver) {
        TopAppBarState(
            initialHeightOffsetLimit = -Float.MAX_VALUE,
            initialHeightOffset = if (arrancaColapsada) -Float.MAX_VALUE else 0f,
            initialContentOffset = 0f
        )
    }
    val lista by rememberUpdatedState(listaVisible)
    // La misma lambda siempre: Material recuerda el comportamiento con ella como llave,
    // y una nueva en cada recomposición recrearía la conexión de `nestedScroll`.
    // Con la cabecera (algo) encogida siempre se puede: si el segmento nuevo trae una
    // lista corta que no se desplaza, jalar hacia abajo todavía debe poder abrirla.
    val puedeDesplazarse = remember {
        {
            lista.canScrollForward || lista.canScrollBackward ||
                estado.collapsedFraction > 0f
        }
    }
    return TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        state = estado,
        canScroll = puedeDesplazarse,
        snapAnimationSpec = if (reducirMovimiento) {
            snap()
        } else {
            spring(stiffness = Spring.StiffnessMediumLow)
        }
    )
}

/**
 * La parte de la cabecera que se esconde: mide a su contenido completo, reporta esa
 * altura menos lo ya encogido y lo coloca subido esa misma cantidad, recortado. El
 * contenido nunca cambia de rama ni sale de la composición — sólo se desliza bajo la
 * fila de arriba—, así que un campo con foco adentro lo conserva.
 *
 * La altura se lee en la fase de medida (no en composición): cada cuadro del colapso
 * sólo vuelve a medir, no recompone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ZonaQueSeEncoge(
    estado: TopAppBarState,
    modifier: Modifier = Modifier,
    contenido: @Composable () -> Unit
) {
    Layout(content = contenido, modifier = modifier.clipToBounds()) { medibles, restricciones ->
        val pieza = medibles.first().measure(
            restricciones.copy(minHeight = 0, maxHeight = Constraints.Infinity)
        )
        val completa = pieza.height.toFloat()
        if (estado.heightOffsetLimit != -completa) {
            // Si ya estaba toda encogida (o nació así, con -Float.MAX_VALUE), sigue toda
            // encogida con la altura nueva: que aparezca el aviso de "sin ubicación" o
            // cambie la letra no debe asomar un pedazo de la cabecera.
            val estabaColapsada = estado.collapsedFraction >= 1f
            estado.heightOffsetLimit = -completa
            estado.heightOffset = if (estabaColapsada) -completa else estado.heightOffset
        }
        val alto = (completa + estado.heightOffset).roundToInt().coerceIn(0, pieza.height)
        layout(pieza.width, alto) {
            pieza.placeRelative(0, alto - pieza.height)
        }
    }
}
