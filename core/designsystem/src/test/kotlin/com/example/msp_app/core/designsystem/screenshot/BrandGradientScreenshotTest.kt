package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.brandGradientBackground
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de un `Box` con
 * [brandGradientBackground] — gradiente de marca 150° plano, sin glow
 * (task-6-brief.md). La matriz Tier×escala completa llega en Task 10.
 */
class BrandGradientScreenshotTest : MspScreenshotTest() {

    @Test
    fun `gradient light`() {
        capture(name = "brand_gradient_light", dark = false) { SampleGradientBox() }
    }

    @Test
    fun `gradient dark`() {
        capture(name = "brand_gradient_dark", dark = true) { SampleGradientBox() }
    }
}

@Composable
private fun SampleGradientBox() {
    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 140.dp)
            .brandGradientBackground(MspTheme.colors, MspTheme.shapes.heroCard)
    )
}
