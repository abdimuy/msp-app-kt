package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspPaymentSyncPill
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspPaymentSyncPill]: "3 por subir"
 * (1:1 mockup `.syncpill`). Render estático y determinista gracias al
 * `ANIMATOR_DURATION_SCALE = 0` que [MspScreenshotTest] fuerza para TODA
 * captura (ver su `@Before`): con reduce-motion activo,
 * [com.example.msp_app.core.designsystem.theme.rememberReducedMotionEnabled]
 * devuelve `true` y el composable NUNCA crea el `InfiniteTransition` del
 * anillo de pulso — el golden queda con el dot sólido, sin anillo, "sin
 * pulse/sombra animada; determinista" (spec §5).
 *
 * Por qué es obligatorio y no una optimización: `captureRoboImage` NO pasa por
 * el idling de `ComposeTestRule` (que sabe excluir un `InfiniteTransition` de
 * `waitForIdle()`), sino por el drenado directo del `Looper` shadow de
 * Robolectric, que reintenta indefinidamente mientras el anillo siga
 * reencolando frame callbacks — en la práctica cuelga `recordRoborazziDebug`
 * (reproducido: la corrida se atoraba tras grabar el primer golden). La
 * animación real (con reduce-motion apagado) se cubre en
 * `component/PaymentSyncPillTest`. La matriz Tier×escala completa llega en
 * Task 10.
 */
class PaymentSyncPillScreenshotTest : MspScreenshotTest() {

    @Test
    fun `payment sync pill light`() {
        capture(name = "msp_payment_sync_pill_light", dark = false) { SamplePaymentSyncPill() }
    }

    @Test
    fun `payment sync pill dark`() {
        capture(name = "msp_payment_sync_pill_dark", dark = true) { SamplePaymentSyncPill() }
    }
}

@Composable
private fun SamplePaymentSyncPill() {
    MspPaymentSyncPill(pendingCount = 3, modifier = Modifier.padding(MspTheme.spacing.md))
}
