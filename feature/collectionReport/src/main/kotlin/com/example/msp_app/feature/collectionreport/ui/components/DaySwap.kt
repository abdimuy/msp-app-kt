package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.msp_app.feature.collectionreport.ui.theme.REPORT_STANDARD_DURATION_MS
import com.example.msp_app.feature.collectionreport.ui.theme.ReportStandardEasing
import com.example.msp_app.feature.collectionreport.ui.theme.rememberReportReducedMotion
import java.time.LocalDate

/**
 * Opacidad de arranque del contenido al cambiar de día. No es 0: un blanqueo total se lee como
 * "se fue la pantalla"; una atenuación parcial se lee como "esto se acaba de actualizar", que es
 * exactamente lo que pasó. Sobrio, sin rebote — el criterio de movimiento de la casa.
 */
private const val DAY_SWAP_START_ALPHA = 0.4f

/**
 * Transición del contenido del tablero al cambiar el día mostrado ([day]): el bloque se
 * atenúa a [DAY_SWAP_START_ALPHA] y sube a opacidad plena en [REPORT_STANDARD_DURATION_MS] con
 * [ReportStandardEasing] — los MISMOS tokens del swap Día↔Semana y del colapsable de pagos, sin
 * inventar una curva nueva.
 *
 * **Solo transiciona lo que cambia.** Envuelve el bloque de cifras/detalle, no la pantalla: el
 * encabezado, el selector de periodo, la subfila y la propia tira de días quedan FUERA y no se
 * mueven — tocar un chip no debe sentirse como volver a entrar a la pantalla. Por lo mismo no
 * re-dispara la entrada escalonada ([StaggeredEntrance]), que sigue corriendo una sola vez.
 *
 * **Nada en la PRIMERA composición.** El estado inicial se siembra con [day], así que al montar
 * no hay cambio que animar y el bloque nace opaco; solo un cambio POSTERIOR dispara la subida.
 * Sin esta guarda, el `LaunchedEffect` correría al entrar y la atenuación se apilaría sobre la
 * entrada escalonada — el mismo defecto que documenta `toggle-jank-diagnosis.md` (fix 4).
 *
 * **Reduce-motion y "sin día" son ESTRUCTURALES:** ninguna de las dos ramas compone el
 * `Animatable`/`LaunchedEffect` ni el `graphicsLayer` — se pinta [content] tal cual, como hace
 * [StaggeredEntrance]. `day == null` (Semana, o un cobrador sin ciclo) no tiene día que
 * intercambiar, así que tampoco monta nada: el árbol queda idéntico al de siempre.
 */
@Composable
fun DaySwap(day: LocalDate?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!daySwapAnimates(day)) {
        content()
        return
    }
    val alpha = rememberDaySwapAlpha(day)
    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) { content() }
}

/**
 * Opacidad actual del bloque, [OPAQUE] mientras no haya nada que transicionar.
 *
 * Existe aparte de [DaySwap] para que la transición se pueda AFIRMAR en un test sin capturar
 * píxeles: `graphicsLayer` no deja rastro en el árbol de semántica, y capturar la pantalla
 * exige un `forceRedraw` que no convive con el reloj congelado (medido: cuelga 2 s y truena).
 * Con este valor a la vista, un test puede leer la opacidad frame a frame y distinguir de verdad
 * "instantáneo" de "animado" — no una aserción que pasaría de las dos formas.
 *
 * Con reduce-motion (o sin día) devuelve [OPAQUE] SIN componer el `Animatable` ni el
 * `LaunchedEffect`: el corto-circuito es estructural, no una animación de 0 ms.
 */
@Composable
internal fun rememberDaySwapAlpha(day: LocalDate?): Float {
    if (!daySwapAnimates(day)) return OPAQUE
    val alpha = remember { Animatable(OPAQUE) }
    var shownDay by remember { mutableStateOf(day) }
    LaunchedEffect(day) {
        if (day != shownDay) {
            shownDay = day
            alpha.snapTo(DAY_SWAP_START_ALPHA)
            alpha.animateTo(
                targetValue = OPAQUE,
                animationSpec = tween(REPORT_STANDARD_DURATION_MS, easing = ReportStandardEasing)
            )
        }
    }
    return alpha.value
}

/** Opacidad plena — el estado en reposo y el destino de toda transición de día. */
private const val OPAQUE = 1f

/**
 * ¿Hay algo que transicionar? Predicado compartido por [DaySwap] (que decide si envuelve
 * siquiera) y [rememberDaySwapAlpha] (que decide si anima), para que las dos decisiones no
 * puedan divergir.
 */
@Composable
private fun daySwapAnimates(day: LocalDate?): Boolean =
    day != null && !rememberReportReducedMotion()
