package com.example.msp_app.core.designsystem.screenshot

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.designsystem.component.MspWeeklyBar
import com.example.msp_app.core.designsystem.component.MspWeeklyBarsCard
import com.example.msp_app.core.designsystem.theme.MspTheme
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0) de [MspWeeklyBarsCard] con el ciclo de 5
 * días del mockup semana (task-8-brief.md / `cobranza-A-aireado.html`
 * `DATA.semana.bars`): lun 74%, mar 87%, mié 100%, jue 89%, vie 64% — hoy =
 * viernes (`todayIndex = 4`). La matriz Tier×escala completa llega en Task 10.
 */
class MspWeeklyBarsCardScreenshotTest : MspScreenshotTest() {

    @Test
    fun `weekly bars card light`() {
        capture(name = "msp_weekly_bars_card_light", dark = false) { SampleWeeklyBarsCard() }
    }

    @Test
    fun `weekly bars card dark`() {
        capture(name = "msp_weekly_bars_card_dark", dark = true) { SampleWeeklyBarsCard() }
    }
}

@Composable
private fun SampleWeeklyBarsCard() {
    MspWeeklyBarsCard(
        bars = listOf(
            MspWeeklyBar("lun", 0.74f),
            MspWeeklyBar("mar", 0.87f),
            MspWeeklyBar("mié", 1.0f),
            MspWeeklyBar("jue", 0.89f),
            MspWeeklyBar("vie", 0.64f)
        ),
        todayIndex = TODAY_INDEX,
        modifier = Modifier.padding(MspTheme.spacing.md)
    )
}

private const val TODAY_INDEX = 4
