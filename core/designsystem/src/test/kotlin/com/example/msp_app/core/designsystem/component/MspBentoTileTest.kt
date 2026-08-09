package com.example.msp_app.core.designsystem.component

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
 * Compose-test (no golden) de [MspBentoTile]: expone dot + label + valor en
 * el árbol de composición (task-8-brief.md). El golden visual vive en
 * `screenshot/MspBentoTileScreenshotTest`.
 */
class MspBentoTileTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el bento tile muestra dot, label y valor`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspBentoTile(
                    dotColor = MspTheme.colors.statusPaid,
                    label = "Efectivo",
                    amount = BigDecimal("12100"),
                    subLine = "22 pagos"
                )
            }
        }

        composeTestRule
            .onNodeWithTag(BENTO_TILE_DOT_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Efectivo").assertIsDisplayed()
        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("12100"))).assertIsDisplayed()
        composeTestRule.onNodeWithText("22 pagos").assertIsDisplayed()
    }
}
