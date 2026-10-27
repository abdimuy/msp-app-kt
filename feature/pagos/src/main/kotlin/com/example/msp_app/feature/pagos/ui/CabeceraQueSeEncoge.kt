package com.example.msp_app.feature.pagos.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * El estado de colapso de la cabecera de la lista de clientes: **uno solo**, compartido
 * por los tres segmentos, que sobrevive a salir de la pantalla y volver.
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
 * ## Contrato con umbral (decisión del dueño, 2026-10-07, como Chrome/Gmail/X)
 *
 * - **Bajando la lista** (dedo hacia arriba) la cabecera se encoge primero, siguiendo
 *   al dedo, y la lista se desplaza después.
 * - **Subiendo a media lista** se ACUMULA el recorrido del dedo; mientras sea menor que
 *   [RECORRIDO_PARA_DESPLEGAR] la cabecera no se mueve. Al pasarlo, se despliega completa
 *   con un resorte. Un fling hacia arriba más rápido que [VELOCIDAD_PARA_DESPLEGAR] la
 *   despliega aunque el dedo no haya recorrido el umbral.
 * - Cualquier desplazamiento hacia abajo reinicia el acumulador.
 * - **En el tope de la lista** siempre queda desplegada: el jalón que sobra después del
 *   primer elemento la abre siguiendo al dedo.
 * - Si se suelta a medio camino, se asienta con resorte en el extremo más cercano.
 *
 * Sólo el recorrido del DEDO (`NestedScrollSource.UserInput`) cuenta para el umbral; el
 * desplazamiento que deja un fling lo decide su velocidad, no su distancia — así cada
 * mecanismo se prueba por separado.
 *
 * Con movimiento reducido, desplegar es instantáneo (sin resorte).
 *
 * @param arrancaColapsada si no hay estado guardado, la cabecera nace compacta — lo usa
 *   la pantalla cuando la lista ya arranca desplazada (un golden con índice inicial).
 * @param listaVisible la lista del segmento que se ve: con una lista que cabe entera en
 *   pantalla la cabecera no se encoge (pero si venía encogida, jalar hacia abajo la abre).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun rememberCabeceraQueSeEncoge(
    arrancaColapsada: Boolean,
    reducirMovimiento: Boolean,
    listaVisible: LazyListState
): CabeceraQueSeEncoge {
    val estado = rememberSaveable(saver = TopAppBarState.Saver) {
        TopAppBarState(
            initialHeightOffsetLimit = -Float.MAX_VALUE,
            initialHeightOffset = if (arrancaColapsada) -Float.MAX_VALUE else 0f,
            initialContentOffset = 0f
        )
    }
    val lista = rememberUpdatedState(listaVisible)
    val reducido = rememberUpdatedState(reducirMovimiento)
    val alcance = rememberCoroutineScope()
    val densidad = LocalDensity.current
    return remember(estado, alcance, densidad) {
        CabeceraQueSeEncoge(
            state = estado,
            lista = { lista.value },
            reducirMovimiento = { reducido.value },
            alcance = alcance,
            recorridoParaDesplegarPx = with(densidad) { RECORRIDO_PARA_DESPLEGAR.toPx() },
            velocidadParaDesplegarPx = with(densidad) { VELOCIDAD_PARA_DESPLEGAR.toPx() }
        )
    }
}

/**
 * El comportamiento de la cabecera: su [state] (de Material, por su `Saver` y su
 * `collapsedFraction`) y la [nestedScrollConnection] que la lista le alimenta. Ver
 * [rememberCabeceraQueSeEncoge] para el contrato.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class CabeceraQueSeEncoge(
    val state: TopAppBarState,
    private val lista: () -> LazyListState,
    private val reducirMovimiento: () -> Boolean,
    private val alcance: CoroutineScope,
    private val recorridoParaDesplegarPx: Float,
    private val velocidadParaDesplegarPx: Float
) {
    /** Lo que el dedo lleva subiendo la lista desde el último movimiento hacia abajo. */
    internal var acumuladoHaciaArribaPx: Float = 0f
        private set

    private var animacion: Job? = null

    private val listaSeDesplaza: Boolean
        get() = lista().let { it.canScrollForward || it.canScrollBackward }

    private val expandida: Boolean get() = state.heightOffset >= 0f

    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f) return Offset.Zero
            // Dedo hacia arriba: la lista baja. Reinicia el acumulador y encoge primero.
            acumuladoHaciaArribaPx = 0f
            if (!listaSeDesplaza) return Offset.Zero
            animacion?.cancel()
            val antes = state.heightOffset
            state.heightOffset = antes + available.y
            return Offset(0f, state.heightOffset - antes)
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (consumed.y > 0f && source == NestedScrollSource.UserInput) {
                acumuladoHaciaArribaPx += consumed.y
                if (acumuladoHaciaArribaPx >= recorridoParaDesplegarPx) desplegar()
            }
            if (available.y > 0f) {
                // La lista ya llegó a su primer elemento: lo que sobra abre la cabecera
                // siguiendo al dedo (o al fling).
                if (animacion?.isActive == true) return Offset.Zero
                val antes = state.heightOffset
                state.heightOffset = antes + available.y
                return Offset(0f, state.heightOffset - antes)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (available.y >= velocidadParaDesplegarPx) desplegar()
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (animacion?.isActive == true) return Velocity.Zero
            val fraccion = state.collapsedFraction
            when {
                // En el tope siempre desplegada.
                !lista().canScrollBackward && !expandida -> desplegar()
                fraccion > 0f && fraccion < 1f ->
                    if (fraccion < MITAD) desplegar() else animarA(state.heightOffsetLimit)
            }
            return Velocity.Zero
        }
    }

    private fun desplegar() {
        acumuladoHaciaArribaPx = 0f
        if (expandida || animacion?.isActive == true) return
        animarA(0f)
    }

    private fun animarA(destino: Float) {
        animacion?.cancel()
        if (reducirMovimiento()) {
            state.heightOffset = destino
            return
        }
        animacion = alcance.launch {
            animate(
                initialValue = state.heightOffset,
                targetValue = destino,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) { valor, _ -> state.heightOffset = valor }
        }
    }
}

/**
 * Cuánto tiene que subir el dedo la lista, a media lista, para que la cabecera se
 * despliegue. Un jalón corto (releer el cliente de arriba) no la abre; un recorrido
 * real, sí. Punto de partida pedido por el dueño (2026-10-07); ajustable.
 */
internal val RECORRIDO_PARA_DESPLEGAR: Dp = 120.dp

/**
 * Velocidad de un fling hacia arriba (por segundo) que despliega la cabecera aunque el
 * dedo no haya recorrido [RECORRIDO_PARA_DESPLEGAR]: quien avienta la lista hacia
 * arriba quiere volver, no releer. Ajustable.
 */
internal val VELOCIDAD_PARA_DESPLEGAR: Dp = 1_500.dp

/** Soltada a medio camino, por debajo de esta fracción se abre y por encima se cierra. */
private const val MITAD = 0.5f

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
