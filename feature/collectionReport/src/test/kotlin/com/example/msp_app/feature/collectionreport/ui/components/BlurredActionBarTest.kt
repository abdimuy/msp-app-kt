package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [BlurredActionBar]: pinta las tres acciones del mockup
 * (`.actions` Compartir/Imprimir/PDF) y cada tap informa su callback correcto — el golden
 * visual vive en `screenshot/BlurredActionBarScreenshotTest`.
 */
class BlurredActionBarTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `pinta los tres botones de accion del mockup`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                BlurredActionBar(onCompartirClick = {}, onImprimirClick = {}, onPdfClick = {})
            }
        }

        composeTestRule.onNodeWithText("Compartir").assertExists()
        composeTestRule.onNodeWithText("Imprimir").assertExists()
        composeTestRule.onNodeWithText("PDF").assertExists()
    }

    @Test
    fun `tocar Compartir informa onCompartirClick`() {
        var tapped = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                BlurredActionBar(
                    onCompartirClick = { tapped = true },
                    onImprimirClick = {},
                    onPdfClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Compartir").performClick()

        assertTrue(tapped)
    }

    @Test
    fun `tocar Imprimir informa onImprimirClick`() {
        var tapped = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                BlurredActionBar(
                    onCompartirClick = {},
                    onImprimirClick = { tapped = true },
                    onPdfClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Imprimir").performClick()

        assertTrue(tapped)
    }

    @Test
    fun `tocar PDF informa onPdfClick`() {
        var tapped = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                BlurredActionBar(
                    onCompartirClick = {},
                    onImprimirClick = {},
                    onPdfClick = { tapped = true }
                )
            }
        }

        composeTestRule.onNodeWithText("PDF").performClick()

        assertTrue(tapped)
    }
}
