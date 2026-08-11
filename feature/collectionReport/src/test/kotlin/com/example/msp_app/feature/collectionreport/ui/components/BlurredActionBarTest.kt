package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.ui.tier2.ReportTier
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [BlurredActionBar]: pinta las tres acciones del mockup
 * (`.actions` Compartir/Imprimir/PDF) y cada tap informa su callback correcto — el golden
 * visual vive en `screenshot/BlurredActionBarScreenshotTest`. Los tests de
 * [ReportTier.TIER_2] cubren el fix del bug "Grande/Muy grande rompe el reporte" (ver KDoc de
 * [BlurredActionBar]): los tres botones deben apilarse en columna, no envolver en una fila
 * apretada.
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

    @Test
    fun `en Tier 2 los tres botones se apilan en columna, no en fila`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                BlurredActionBar(
                    onCompartirClick = {},
                    onImprimirClick = {},
                    onPdfClick = {},
                    tier = ReportTier.TIER_2
                )
            }
        }

        val compartirBounds = composeTestRule.onNodeWithText("Compartir").getUnclippedBoundsInRoot()
        val imprimirBounds = composeTestRule.onNodeWithText("Imprimir").getUnclippedBoundsInRoot()
        val pdfBounds = composeTestRule.onNodeWithText("PDF").getUnclippedBoundsInRoot()

        // Apilados: cada botón empieza por debajo de donde termina el anterior. En la fila de
        // Tier 1 los tres comparten el mismo `top` — esta aserción falla ahí a propósito.
        assertTrue(imprimirBounds.top >= compartirBounds.bottom)
        assertTrue(pdfBounds.top >= imprimirBounds.bottom)
        // Ancho completo: las tres columnas terminan en el mismo `right` (mismo padre `Column`
        // con `fillMaxWidth()` cada botón), a diferencia de la fila con pesos desiguales.
        assertTrue(compartirBounds.right == imprimirBounds.right)
        assertTrue(imprimirBounds.right == pdfBounds.right)
    }

    @Test
    fun `en Tier 1 (default) los tres botones comparten la misma fila`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                BlurredActionBar(onCompartirClick = {}, onImprimirClick = {}, onPdfClick = {})
            }
        }

        val compartirBounds = composeTestRule.onNodeWithText("Compartir").getUnclippedBoundsInRoot()
        val pdfBounds = composeTestRule.onNodeWithText("PDF").getUnclippedBoundsInRoot()

        assertTrue(compartirBounds.top == pdfBounds.top)
    }

    @Test
    fun `en Tier 2 cada tap informa su callback`() {
        var compartirTapped = false
        var imprimirTapped = false
        var pdfTapped = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                BlurredActionBar(
                    onCompartirClick = { compartirTapped = true },
                    onImprimirClick = { imprimirTapped = true },
                    onPdfClick = { pdfTapped = true },
                    tier = ReportTier.TIER_2
                )
            }
        }

        composeTestRule.onNodeWithText("Compartir").performClick()
        composeTestRule.onNodeWithText("Imprimir").performClick()
        composeTestRule.onNodeWithText("PDF").performClick()

        assertTrue(compartirTapped)
        assertTrue(imprimirTapped)
        assertTrue(pdfTapped)
    }
}
