package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Alto de `BarraDeDetalle` (56 dp de botón + 2 × 8 dp, `Marco.kt:236-240`) bajo la barra de estado. */
val ALTO_DE_LA_FRANJA: Dp = 72.dp

/** Lo que hay que recorrer a la derecha para que cuente como "abrir el menú". */
val DISTANCIA_PARA_ABRIR: Dp = 48.dp

/**
 * Abre el menú con un deslizamiento a la derecha que NACE en la franja superior
 * (spec §2.2.1). Escucha en el pase `Initial` sin consumir nada hasta decidir:
 * un toque sobre la flecha atrás llega intacto. Un gesto que nace dentro del
 * borde de gestos del sistema ([bordeIzquierdoPx], por defecto
 * `WindowInsets.systemGestures`) es "atrás" del sistema y se ignora.
 */
@Composable
fun ZonaQueAbreElMenu(
    onAbrir: () -> Unit,
    modifier: Modifier = Modifier,
    bordeIzquierdoPx: Int? = null,
    contenido: @Composable () -> Unit
) {
    val densidad = LocalDensity.current
    val borde = bordeIzquierdoPx ?: WindowInsets.systemGestures.getLeft(densidad, LocalLayoutDirection.current)
    val franjaPx = WindowInsets.statusBars.getTop(densidad) + with(densidad) {
        ALTO_DE_LA_FRANJA.toPx()
    }
    val umbralPx = with(densidad) { DISTANCIA_PARA_ABRIR.toPx() }
    val abrir by rememberUpdatedState(onAbrir)

    Box(
        modifier = modifier.pointerInput(borde, franjaPx, umbralPx) {
            awaitEachGesture {
                val inicio =
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (inicio.position.y <= franjaPx && inicio.position.x >= borde) {
                    detectarDeslizamientoALaDerecha(inicio.id, umbralPx)?.let { cambio ->
                        cambio.consume()
                        abrir()
                    }
                }
            }
        }
    ) {
        contenido()
    }
}

/**
 * Sigue el dedo desde [idInicial] hasta que: (a) recorre [umbralPx] más a la
 * derecha que hacia arriba/abajo —devuelve ese cambio, sin consumirlo todavía,
 * para que el llamador decida—; (b) se levanta o se pierde el puntero; o (c) el
 * movimiento vertical gana (`touchSlop`) — en los dos últimos casos, `null`.
 */
private suspend fun AwaitPointerEventScope.detectarDeslizamientoALaDerecha(
    idInicial: PointerId,
    umbralPx: Float
): PointerInputChange? {
    var dx = 0f
    var dy = 0f
    while (true) {
        val evento = awaitPointerEvent(PointerEventPass.Initial)
        val cambio = evento.changes.firstOrNull { it.id == idInicial } ?: return null
        if (!cambio.pressed) return null
        val delta = cambio.positionChange()
        dx += delta.x
        dy += delta.y
        if (abs(dy) > abs(dx) && abs(dy) > viewConfiguration.touchSlop) return null
        if (dx >= umbralPx && dx > 2 * abs(dy)) return cambio
    }
}
