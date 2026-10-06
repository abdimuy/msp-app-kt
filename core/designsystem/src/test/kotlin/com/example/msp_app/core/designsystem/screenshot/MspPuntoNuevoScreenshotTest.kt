package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspPuntoNuevo
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Goldens baseline de [MspPuntoNuevo], claro/oscuro × 1.0/1.5/2.0 — SIEMPRE en
 * reposo (sin halos), mismo motivo que [MspInsigniaNuevaScreenshotTest]. El
 * punto no tiene texto que reflowee con `fontScale`, así que las tres escalas
 * son principalmente un control de que el círculo de 10dp no se deforma — el
 * frame intermedio del halo vive en [MspPulseRingFrameScreenshotTest].
 */
class MspPuntoNuevoScreenshotTest : MspScreenshotTest() {

    @Test
    fun `punto nuevo light 1_0`() = capturePunto(
        "msp_punto_nuevo_light_1_0",
        dark = false,
        fontScale = 1.0f
    )

    @Test
    fun `punto nuevo light 1_5`() = capturePunto(
        "msp_punto_nuevo_light_1_5",
        dark = false,
        fontScale = 1.5f
    )

    @Test
    fun `punto nuevo light 2_0`() = capturePunto(
        "msp_punto_nuevo_light_2_0",
        dark = false,
        fontScale = 2.0f
    )

    @Test
    fun `punto nuevo dark 1_0`() = capturePunto(
        "msp_punto_nuevo_dark_1_0",
        dark = true,
        fontScale = 1.0f
    )

    @Test
    fun `punto nuevo dark 1_5`() = capturePunto(
        "msp_punto_nuevo_dark_1_5",
        dark = true,
        fontScale = 1.5f
    )

    @Test
    fun `punto nuevo dark 2_0`() = capturePunto(
        "msp_punto_nuevo_dark_2_0",
        dark = true,
        fontScale = 2.0f
    )

    private fun capturePunto(name: String, dark: Boolean, fontScale: Float) {
        capture(name = name, dark = dark, fontScale = fontScale) { SamplePuntoNuevo() }
    }
}

@Composable
private fun SamplePuntoNuevo() {
    MspPuntoNuevo(modifier = Modifier.padding(MspTheme.spacing.md))
}
