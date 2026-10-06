package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspInsigniaNueva
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Goldens baseline de [MspInsigniaNueva], claro/oscuro × 1.0/1.5/2.0 — SIEMPRE en
 * reposo (sin anillos, punto en escala 1): [MspScreenshotTest] fuerza
 * `ANIMATOR_DURATION_SCALE = 0` en su `@Before`, el mismo mecanismo
 * anti-cuelgue/determinismo que ya documenta [PaymentSyncPillScreenshotTest]. El
 * frame intermedio del anillo (la prueba de que SÍ se ve, no solo que existe)
 * vive aparte en [MspPulseRingFrameScreenshotTest], con el reloj de Compose
 * congelado a propósito.
 */
class MspInsigniaNuevaScreenshotTest : MspScreenshotTest() {

    @Test
    fun `insignia nueva light 1_0`() = captureInsignia(
        "msp_insignia_nueva_light_1_0",
        dark = false,
        fontScale = 1.0f
    )

    @Test
    fun `insignia nueva light 1_5`() = captureInsignia(
        "msp_insignia_nueva_light_1_5",
        dark = false,
        fontScale = 1.5f
    )

    @Test
    fun `insignia nueva light 2_0`() = captureInsignia(
        "msp_insignia_nueva_light_2_0",
        dark = false,
        fontScale = 2.0f
    )

    @Test
    fun `insignia nueva dark 1_0`() = captureInsignia(
        "msp_insignia_nueva_dark_1_0",
        dark = true,
        fontScale = 1.0f
    )

    @Test
    fun `insignia nueva dark 1_5`() = captureInsignia(
        "msp_insignia_nueva_dark_1_5",
        dark = true,
        fontScale = 1.5f
    )

    @Test
    fun `insignia nueva dark 2_0`() = captureInsignia(
        "msp_insignia_nueva_dark_2_0",
        dark = true,
        fontScale = 2.0f
    )

    private fun captureInsignia(name: String, dark: Boolean, fontScale: Float) {
        capture(name = name, dark = dark, fontScale = fontScale) { SampleInsigniaNueva() }
    }
}

@Composable
private fun SampleInsigniaNueva() {
    MspInsigniaNueva(modifier = Modifier.padding(MspTheme.spacing.md))
}
