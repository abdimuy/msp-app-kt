package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspProgressRing
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspProgressRing] al 91% —
 * `brand` sobre `progressTrack`, porcentaje centrado con
 * `MspTheme.type.ringValue` (task-6-brief.md). La matriz Tier×escala
 * completa llega en Task 10.
 */
class MspProgressRingScreenshotTest : MspScreenshotTest() {

    @Test
    fun `progress ring light`() {
        capture(name = "msp_progress_ring_light", dark = false) { SampleProgressRing() }
    }

    @Test
    fun `progress ring dark`() {
        capture(name = "msp_progress_ring_dark", dark = true) { SampleProgressRing() }
    }
}

@Composable
private fun SampleProgressRing() {
    MspProgressRing(
        progress = RING_PROGRESS,
        fillColor = MspTheme.colors.brand,
        trackColor = MspTheme.colors.progressTrack,
        modifier = Modifier.padding(MspTheme.spacing.md)
    )
}

private const val RING_PROGRESS = 0.91f
