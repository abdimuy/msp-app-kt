package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspCard] con contenido de muestra —
 * fill `surface` + hairline 1dp `outline` + `shapes.tile` (task-6-brief.md).
 * La matriz Tier×escala completa llega en Task 10.
 */
class MspCardScreenshotTest : MspScreenshotTest() {

    @Test
    fun `card light`() {
        capture(name = "msp_card_light", dark = false) { SampleCard() }
    }

    @Test
    fun `card dark`() {
        capture(name = "msp_card_dark", dark = true) { SampleCard() }
    }
}

@Composable
private fun SampleCard() {
    MspCard(modifier = Modifier.padding(MspTheme.spacing.md)) {
        Text(
            text = "Venta #4821 — Sofá 3 plazas",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface,
            modifier = Modifier.padding(MspTheme.spacing.md)
        )
    }
}
