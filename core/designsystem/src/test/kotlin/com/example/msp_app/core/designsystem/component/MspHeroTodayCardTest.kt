package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import java.math.BigDecimal
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MspHeroTodayCard]: el monto grande se pinta
 * formateado es-MX (vía [formatMoneyMxn], el mismo formateador de
 * [MspMoneyText]) y la barra de progreso interna expone la fracción dada por
 * semántica de accesibilidad (`ProgressBarRangeInfo`), no solo por pixeles —
 * task-8-brief.md. El golden visual vive en
 * `screenshot/MspHeroTodayCardScreenshotTest`.
 */
class MspHeroTodayCardTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el hero renderiza el monto formateado en amountHero y la barra a la fraccion dada`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspHeroTodayCard(
                    overline = "Cobrado · vie 7 ago",
                    delta = "▲ 12% vs ayer",
                    amount = BigDecimal("18300"),
                    insight = "32 pagos · vas al 91% de tu meta",
                    progress = HERO_PROGRESS,
                    goalLabel = "meta del día",
                    goalAmount = BigDecimal("20000"),
                    cashOnHandLabel = "Efectivo en mano",
                    cashOnHand = BigDecimal("12100"),
                    avgTicketLabel = "Ticket prom.",
                    avgTicket = BigDecimal("572")
                )
            }
        }

        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("18300"))).assertIsDisplayed()
        composeTestRule
            .onNodeWithTag(HERO_PROGRESS_BAR_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(HERO_PROGRESS, 0f..1f)
                )
            )
    }

    @Test
    fun `el hero coerce el progreso fuera de rango antes de exponerlo por semantica`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspHeroTodayCard(
                    overline = "Cobrado · vie 7 ago",
                    delta = "▲ 12% vs ayer",
                    amount = BigDecimal("18300"),
                    insight = "32 pagos · vas al 91% de tu meta",
                    progress = 1.4f,
                    goalLabel = "meta del día",
                    goalAmount = BigDecimal("20000"),
                    cashOnHandLabel = "Efectivo en mano",
                    cashOnHand = BigDecimal("12100"),
                    avgTicketLabel = "Ticket prom.",
                    avgTicket = BigDecimal("572")
                )
            }
        }

        composeTestRule
            .onNodeWithTag(HERO_PROGRESS_BAR_TAG)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(1f, 0f..1f)
                )
            )
    }

    private companion object {
        const val HERO_PROGRESS = 0.91f
    }
}
