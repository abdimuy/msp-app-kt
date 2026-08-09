package com.example.msp_app.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberReducedMotionEnabled

/** `testTag` del dot sólido (siempre presente) de [MspPaymentSyncPill]. */
internal const val PAYMENT_SYNC_PILL_DOT_TAG = "msp_payment_sync_pill_dot"

/**
 * `testTag` del anillo de pulso — SOLO se agrega al árbol cuando
 * [rememberReducedMotionEnabled] es `false`; su ausencia es la señal que usa
 * el test de reduce-motion (no hay forma pública de inspeccionar si un
 * `InfiniteTransition` arrancó, así que se prueba por lo que sí es
 * observable: el nodo del anillo no existe).
 */
internal const val PAYMENT_SYNC_PILL_PULSE_RING_TAG = "msp_payment_sync_pill_pulse_ring"

private val DOT_SIZE = 8.dp
private val PULSE_BOX_SIZE = 16.dp
private val PILL_HORIZONTAL_PADDING = 10.dp
private val PILL_VERTICAL_PADDING = 6.dp
private const val PULSE_DURATION_MS = 2000
private const val PULSE_MAX_SCALE = 2.2f
private const val PULSE_START_ALPHA = 0.55f

/**
 * Pill discreta "N por subir" (1:1 mockup `.syncpill`): dot ámbar
 * ([MspTheme.colors.statusPartial]) con un anillo de pulso ("sonar", 2s loop)
 * detrás + texto corto. Variante compacta de [MspSyncBand] — la copia
 * ("por subir") SÍ va fija aquí (a diferencia de [MspSyncBand], que recibe
 * `message`/`hint` libres): el contrato de este componente es solo
 * [pendingCount], igual que `MASKED_MONEY` es una copia fija del design
 * system.
 *
 * **Reduce-motion (spec §5, obligatorio):** cuando
 * [rememberReducedMotionEnabled] es `true`, el `InfiniteTransition` del pulso
 * NUNCA se crea — no solo se le pone `alpha = 0`, directamente no se
 * compone esa rama — y el dot se pinta sólido y estático. Con la
 * animación activa, el anillo crece de escala `1f` a [PULSE_MAX_SCALE]
 * mientras se desvanece de [PULSE_START_ALPHA] a `0f`, repitiendo cada
 * [PULSE_DURATION_MS] — aproxima el `box-shadow` pulsante del CSS del
 * mockup (`@keyframes pulse`) con un `graphicsLayer` escalado detrás del dot.
 */
@Composable
fun MspPaymentSyncPill(pendingCount: Int, modifier: Modifier = Modifier) {
    val content = MspTheme.colors.statusPartial
    val reduced = rememberReducedMotionEnabled()
    Row(
        modifier = modifier
            .clip(MspTheme.shapes.chip)
            .padding(horizontal = PILL_HORIZONTAL_PADDING, vertical = PILL_VERTICAL_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Box(modifier = Modifier.size(PULSE_BOX_SIZE), contentAlignment = Alignment.Center) {
            if (!reduced) {
                val transition = rememberInfiniteTransition(label = "msp_payment_sync_pill_pulse")
                val progress by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = PULSE_DURATION_MS, easing = LinearEasing)
                    ),
                    label = "msp_payment_sync_pill_pulse_progress"
                )
                val scale = 1f + (PULSE_MAX_SCALE - 1f) * progress
                val alpha = PULSE_START_ALPHA * (1f - progress)
                Box(
                    modifier = Modifier
                        .size(DOT_SIZE)
                        .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
                        .clip(CircleShape)
                        .background(content)
                        .testTag(PAYMENT_SYNC_PILL_PULSE_RING_TAG)
                )
            }
            Box(
                modifier = Modifier
                    .size(DOT_SIZE)
                    .clip(CircleShape)
                    .background(content)
                    .testTag(PAYMENT_SYNC_PILL_DOT_TAG)
            )
        }
        Text(
            text = "$pendingCount por subir",
            style = MspTheme.type.syncLabel,
            color = content
        )
    }
}
