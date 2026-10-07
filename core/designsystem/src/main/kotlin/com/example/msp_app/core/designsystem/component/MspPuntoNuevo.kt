package com.example.msp_app.core.designsystem.component

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion

/**
 * `testTag` de la raíz de [MspPuntoNuevo] — SIEMPRE presente, animada o no.
 * Ancla de la prueba "no salta", igual que [MSP_INSIGNIA_NUEVA_TAG].
 */
internal const val MSP_PUNTO_NUEVO_TAG = "msp_punto_nuevo"

/**
 * `testTag` del par de halos — SOLO se agrega cuando [rememberMspReducedMotion]
 * es `false`. Mismo patrón que [MSP_INSIGNIA_NUEVA_RING_TAG].
 */
internal const val MSP_PUNTO_NUEVO_HALO_TAG = "msp_punto_nuevo_halo"

private val PUNTO_SIZE = 10.dp
private const val PUNTO_HALO_START_ALPHA = 0.6f
private const val PUNTO_HALO_MAX_SCALE = 3.4f

/**
 * Punto azul "venta nueva" (1:1 mock "Decidido · v3",
 * `https://claude.ai/artifact/BhdPihYNWezfoZr1ggi2Zk`, decisión del dueño del
 * 2026-10-05): círculo sólido [MspTheme.colors.brand] de 10dp con DOS halos que
 * laten hacia afuera (el segundo con [MSP_PULSE_RING_DELAY_MS] de retraso) — se
 * pinta sobre la venta sin ningún abono en la lista de un cliente, acompañando a
 * [MspInsigniaNueva] en el encabezado de esa misma tarjeta.
 *
 * **Por qué los halos no miden.** Igual que [MspInsigniaNueva]: los halos son
 * `drawCircle` de [Modifier.drawBehind] sobre el `Box` interior de 10dp, nunca un
 * hijo con su propio tamaño — `drawBehind` no participa en medir/layout, y al
 * dibujarse ANTES de [Modifier.clip] en la cadena no queda recortado por él.
 * `rememberMspReducedMotion` en `true` sigue pintando un círculo de 10dp exacto;
 * en `false` se le superponen halos que crecen hasta 3.4× ESE MISMO radio **sin
 * que el `Box` que los contiene crezca ni un dp** — un `Box` más grande "para
 * que quepa el halo" habría sido el error fácil: el halo se dibuja fuera de los
 * límites a propósito, no se le hace espacio.
 *
 * **Decorativo, no lo anuncia TalkBack.** La venta ya la describe
 * [MspInsigniaNueva] con el texto "Nueva" en el encabezado de la tarjeta —
 * anunciar también este punto sería redundante (y "punto azul" no dice nada útil
 * sin contexto). `Modifier.clearAndSetSemantics {}` lo saca del árbol de
 * accesibilidad.
 *
 * **El pulso no recompone**, mismo arreglo que [MspInsigniaNueva]: `halo1`/
 * `halo2` se capturan como `State<Float>` (sin `by`) y su `.value` se lee sólo
 * dentro de la lambda de [Modifier.drawBehind], nunca en el cuerpo de la
 * función — leerlo con `by` suscribiría a `MspPuntoNuevo` entero al cambio de
 * cuadro del `InfiniteTransition`.
 *
 * **Reduce-motion:** mismo contrato que [MspInsigniaNueva] — con
 * [rememberMspReducedMotion] en `true` el `InfiniteTransition` nunca se crea, el
 * punto se pinta sólido y estático.
 */
@Composable
fun MspPuntoNuevo(modifier: Modifier = Modifier) {
    val brand = MspTheme.colors.brand
    val reduced = rememberMspReducedMotion()

    val haloModifier: Modifier = if (reduced) {
        Modifier
    } else {
        val transition = rememberInfiniteTransition(label = "msp_punto_nuevo_pulse")
        // Sin `by`: `State<Float>`, leído dentro de `drawBehind` — ver "El
        // pulso no recompone" en el KDoc de la función.
        val halo1 = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = mspPulseRingSpec(),
            label = "msp_punto_nuevo_halo_1"
        )
        val halo2 = transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = mspPulseRingSpec(MSP_PULSE_RING_DELAY_MS),
            label = "msp_punto_nuevo_halo_2"
        )
        Modifier
            .drawBehind {
                drawPuntoPulseHalo(halo1.value, brand)
                drawPuntoPulseHalo(halo2.value, brand)
            }
            .testTag(MSP_PUNTO_NUEVO_HALO_TAG)
    }

    // `testTag` de raíz en un `Box` envolvente, NUNCA en el mismo `Box` que carga
    // el tag condicional del halo — dos `Modifier.testTag(...)` con valores
    // distintos sobre el MISMO nodo compiten por la misma propiedad de semántica
    // y solo uno sobrevive (medido al revés: ver el comentario equivalente en
    // [MspInsigniaNueva]). El envolvente no tiene tamaño propio, así que el
    // tamaño medido bajo [MSP_PUNTO_NUEVO_TAG] sigue siendo el del círculo de
    // [PUNTO_SIZE].
    Box(modifier = modifier.testTag(MSP_PUNTO_NUEVO_TAG)) {
        Box(
            modifier = Modifier
                .size(PUNTO_SIZE)
                .then(haloModifier)
                .clip(CircleShape)
                .background(brand)
                .clearAndSetSemantics {}
        )
    }
}

/** Un halo del par: círculo centrado que crece de escala 1 a [PUNTO_HALO_MAX_SCALE]. */
private fun DrawScope.drawPuntoPulseHalo(progress: Float, color: Color) {
    val alpha = PUNTO_HALO_START_ALPHA * (1f - progress)
    if (alpha <= 0f) return
    val scale = 1f + (PUNTO_HALO_MAX_SCALE - 1f) * progress
    drawCircle(
        color = color,
        alpha = alpha,
        radius = (size.minDimension / 2f) * scale,
        center = center
    )
}
