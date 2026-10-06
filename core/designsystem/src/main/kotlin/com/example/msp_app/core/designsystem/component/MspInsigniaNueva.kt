package com.example.msp_app.core.designsystem.component

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion

/**
 * `testTag` de la raíz de [MspInsigniaNueva] — SIEMPRE presente, animada o no. Es
 * el ancla de la prueba "no salta": el tamaño medido bajo este tag tiene que ser
 * idéntico con el pulso corriendo y con movimiento reducido.
 */
internal const val MSP_INSIGNIA_NUEVA_TAG = "msp_insignia_nueva"

/**
 * `testTag` del par de anillos — SOLO se agrega cuando [rememberMspReducedMotion]
 * es `false`; su ausencia es la señal (mismo patrón que
 * [PAYMENT_SYNC_PILL_PULSE_RING_TAG] en [MspPaymentSyncPill]) de que el gate de
 * reduce-motion apagó la animación de verdad, no solo visualmente.
 */
internal const val MSP_INSIGNIA_NUEVA_RING_TAG = "msp_insignia_nueva_ring"

/** `testTag` del punto blanco interior — decorativo, siempre presente. */
internal const val MSP_INSIGNIA_NUEVA_DOT_TAG = "msp_insignia_nueva_dot"

private val INSIGNIA_DOT_SIZE = 7.dp
private val INSIGNIA_GAP = 6.dp
private val INSIGNIA_PADDING = PaddingValues(start = 10.dp, top = 5.dp, end = 12.dp, bottom = 5.dp)
private val INSIGNIA_TEXT_SIZE = 13.sp
private val INSIGNIA_RING_MAX_WIDTH = 9.dp
private const val INSIGNIA_RING_START_ALPHA = 0.75f

/**
 * Insignia sólida "Nueva" (1:1 mock "Decidido · v3",
 * `https://claude.ai/artifact/BhdPihYNWezfoZr1ggi2Zk`, decisión del dueño del
 * 2026-10-05): fondo [MspTheme.colors.brand] / texto [MspTheme.colors.onBrand],
 * un punto blanco interior que respira y DOS anillos que laten alrededor de la
 * píldora completa (el segundo con [MSP_PULSE_RING_DELAY_MS] de retraso) — se usa
 * en el encabezado de la tarjeta de cliente cuando tiene una venta sin ningún
 * abono, junto con [MspPuntoNuevo] sobre esa venta en la lista.
 *
 * **Por qué el anillo no mide.** El anillo es un `drawRoundRect` trazado por
 * [Modifier.drawBehind] sobre la fila interior que forma la píldora — `drawBehind`
 * es un modificador de dibujo puro, nunca participa en medir/layout, así que esa
 * fila mide exactamente lo mismo tenga o no el anillo activo. Y como el anillo se
 * dibuja ANTES de [Modifier.clip] en la cadena, su pintura ocurre sobre el lienzo
 * sin que ese `clip` (que solo afecta lo que se dibuja DESPUÉS de él: el fondo
 * sólido) lo recorte — crece hacia afuera de la píldora, nunca hacia dentro, y
 * nadie fuerza un tamaño de `Box` más grande "por si acaso" para contenerlo. Esto
 * se mide, no se infiere: [com.example.msp_app.core.designsystem.component.MspInsigniaNuevaTest]
 * compara `getBoundsInRoot()` con el pulso corriendo contra movimiento reducido.
 *
 * **Reduce-motion (obligatorio, mismo contrato que [MspPaymentSyncPill]):** con
 * [rememberMspReducedMotion] en `true` el `InfiniteTransition` NUNCA se crea — ni
 * los anillos ni la respiración del punto se componen — y se pinta el estado de
 * reposo (punto en escala 1, sin anillos). Se usa [rememberMspReducedMotion] y no
 * el más antiguo `rememberReducedMotionEnabled` porque este es un componente
 * NUEVO: su KDoc documenta que ya combina la señal de accesibilidad del sistema
 * con la casilla propia de la app, y es el criterio que toda pantalla Msp nueva
 * debe compartir — no hay razón para que este componente consulte solo una señal
 * cuando la pantalla que lo hospeda puede estar respetando las dos.
 */
@Composable
fun MspInsigniaNueva(texto: String = "Nueva", modifier: Modifier = Modifier) {
    val brand = MspTheme.colors.brand
    val onBrand = MspTheme.colors.onBrand
    val reduced = rememberMspReducedMotion()

    val dotScale: Float
    val ringModifier: Modifier
    if (reduced) {
        dotScale = 1f
        ringModifier = Modifier
    } else {
        val transition = rememberInfiniteTransition(label = "msp_insignia_nueva_pulse")
        val ring1 by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = mspPulseRingSpec(),
            label = "msp_insignia_nueva_ring_1"
        )
        val ring2 by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = mspPulseRingSpec(MSP_PULSE_RING_DELAY_MS),
            label = "msp_insignia_nueva_ring_2"
        )
        val breathe by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = mspPulseBreatheSpec(),
            label = "msp_insignia_nueva_breathe"
        )
        dotScale = breathe
        ringModifier = Modifier
            .drawBehind {
                drawInsigniaPulseRing(ring1, brand)
                drawInsigniaPulseRing(ring2, brand)
            }
            .testTag(MSP_INSIGNIA_NUEVA_RING_TAG)
    }

    // `testTag` de raíz en un `Box` envolvente, NUNCA en la misma fila que carga el
    // tag condicional del anillo: dos `Modifier.testTag(...)` con valores distintos
    // sobre el MISMO nodo compiten por la misma propiedad de semántica y solo uno
    // sobrevive — medido al revés (los dos primeros intentos de esta tarea fallaron
    // así, `MspInsigniaNuevaTest`/`MspPuntoNuevoTest` lo dejaron rojo). El `Box` sin
    // tamaño propio envuelve exactamente al contenido, así que el tamaño medido bajo
    // [MSP_INSIGNIA_NUEVA_TAG] sigue siendo el de la píldora completa.
    Box(modifier = modifier.testTag(MSP_INSIGNIA_NUEVA_TAG)) {
        Row(
            modifier = Modifier
                .then(ringModifier)
                .clip(MspTheme.shapes.chip)
                .background(brand)
                .padding(INSIGNIA_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(INSIGNIA_GAP)
        ) {
            Box(
                modifier = Modifier
                    .size(INSIGNIA_DOT_SIZE)
                    .graphicsLayer(scaleX = dotScale, scaleY = dotScale)
                    .clip(CircleShape)
                    .background(onBrand)
                    .testTag(MSP_INSIGNIA_NUEVA_DOT_TAG)
                    .clearAndSetSemantics {}
            )
            Text(
                text = texto,
                style = MspTheme.type.chipLabel.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = INSIGNIA_TEXT_SIZE
                ),
                color = onBrand
            )
        }
    }
}

/**
 * Un anillo del par: `drawRoundRect` centrado en el mismo radio que la píldora
 * ([MspTheme.shapes.chip], completamente redondeada) pero inflado hacia afuera
 * por la mitad del ancho del trazo — así el trazo queda centrado en el borde de
 * la píldora (igual que el `outline`/`outline-offset:0` del CSS) y la mitad que
 * cae hacia dentro queda tapada por el fondo sólido pintado después.
 */
private fun DrawScope.drawInsigniaPulseRing(progress: Float, color: Color) {
    val width = INSIGNIA_RING_MAX_WIDTH.toPx() * progress
    if (width <= 0f) return
    val alpha = INSIGNIA_RING_START_ALPHA * (1f - progress)
    if (alpha <= 0f) return
    val inset = width / 2f
    drawRoundRect(
        color = color,
        alpha = alpha,
        topLeft = Offset(-inset, -inset),
        size = Size(size.width + width, size.height + width),
        cornerRadius = CornerRadius(size.height / 2f + inset),
        style = Stroke(width = width)
    )
}
