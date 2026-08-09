package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspSegmentChips
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspSegmentChips] con las opciones
 * Día·Semana del mockup, "Día" activo. La matriz Tier×escala completa
 * (incluida la variante Hora·Nombre) llega en Task 10.
 */
class SegmentChipsScreenshotTest : MspScreenshotTest() {

    @Test
    fun `segment chips light`() {
        capture(name = "msp_segment_chips_light", dark = false) { SampleSegmentChips() }
    }

    @Test
    fun `segment chips dark`() {
        capture(name = "msp_segment_chips_dark", dark = true) { SampleSegmentChips() }
    }
}

@Composable
private fun SampleSegmentChips() {
    MspSegmentChips(
        options = listOf("Día", "Semana"),
        selectedIndex = 0,
        onSelect = {},
        modifier = Modifier.padding(MspTheme.spacing.md)
    )
}
