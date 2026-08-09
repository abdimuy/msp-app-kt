package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.component.OnBrandAlpha
import com.example.msp_app.core.designsystem.component.brandGradientBackground
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspProgressBar] en sus dos usos del
 * brief: hero al 91% (9dp, `heroProgressFill` sobre un pozo translúcido
 * `OnBrandAlpha.WELL` encima del gradiente de marca) y fila de plan al 40%
 * (6dp, `brand` sobre `progressTrack`). La matriz Tier×escala completa
 * llega en Task 10.
 */
class MspProgressBarScreenshotTest : MspScreenshotTest() {

    @Test
    fun `progress bar light`() {
        capture(name = "msp_progress_bar_light", dark = false) { SampleProgressBars() }
    }

    @Test
    fun `progress bar dark`() {
        capture(name = "msp_progress_bar_dark", dark = true) { SampleProgressBars() }
    }
}

@Composable
private fun SampleProgressBars() {
    Column(
        modifier = Modifier
            .padding(MspTheme.spacing.md)
            .width(240.dp),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.lg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .brandGradientBackground(MspTheme.colors, MspTheme.shapes.heroCard)
                .padding(MspTheme.spacing.md)
        ) {
            MspProgressBar(
                progress = HERO_PROGRESS,
                height = HERO_HEIGHT,
                fillColor = MspTheme.colors.heroProgressFill,
                trackColor = MspTheme.colors.onBrand.copy(alpha = OnBrandAlpha.WELL)
            )
        }
        MspProgressBar(
            progress = ROW_PROGRESS,
            height = ROW_HEIGHT,
            fillColor = MspTheme.colors.brand,
            trackColor = MspTheme.colors.progressTrack
        )
    }
}

private val HERO_HEIGHT = 9.dp
private val ROW_HEIGHT = 6.dp
private const val HERO_PROGRESS = 0.91f
private const val ROW_PROGRESS = 0.40f
