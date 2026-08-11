package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MetaCard]/[MetaCardTier2] — "Meta de la semana": las etiquetas
 * exactas "Porcentaje cobro"/"Porcentaje cuentas", el valor de cada anillo, el check ✓ de la
 * meta de cobro (60%, [com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje.META_COBRO_PCT])
 * y el subtítulo "N de M clientes" de cobertura. El golden visual (Tier 1 lado a lado / Tier 2
 * apilado, light+dark) vive en `screenshot/MetaCardScreenshotTest`.
 */
class MetaCardTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `MetaCard pinta titulo, etiquetas exactas y ambos valores`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MetaCard(
                    porcentajeCobro = 91f,
                    porcentajeCuentas = 78f,
                    clientesPagaron = 39,
                    clientesTotal = 50
                )
            }
        }

        composeTestRule.onNodeWithText("Meta de la semana").assertExists()
        composeTestRule.onNodeWithText("Porcentaje cobro").assertExists()
        composeTestRule.onNodeWithText("Porcentaje cuentas").assertExists()
        composeTestRule.onNodeWithText("91%").assertExists()
        composeTestRule.onNodeWithText("78%").assertExists()
        composeTestRule.onNodeWithText("39 de 50 clientes").assertExists()
    }

    @Test
    fun `MetaCard marca la meta de cobro alcanzada con check cuando llega al 60 por ciento`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MetaCard(
                    porcentajeCobro = 60f,
                    porcentajeCuentas = 0f,
                    clientesPagaron = 0,
                    clientesTotal = 0
                )
            }
        }

        composeTestRule.onNodeWithText("meta 60% ✓").assertExists()
    }

    @Test
    fun `MetaCard no marca check cuando el cobro esta debajo de la meta`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MetaCard(
                    porcentajeCobro = 59f,
                    porcentajeCuentas = 0f,
                    clientesPagaron = 0,
                    clientesTotal = 0
                )
            }
        }

        composeTestRule.onNodeWithText("meta 60%").assertExists()
        composeTestRule.onNodeWithText("meta 60% ✓").assertDoesNotExist()
    }

    @Test
    fun `MetaCard permite un cobro ponderado que excede 100 por ciento`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MetaCard(
                    porcentajeCobro = 143f,
                    porcentajeCuentas = 100f,
                    clientesPagaron = 5,
                    clientesTotal = 5
                )
            }
        }

        composeTestRule.onNodeWithText("143%").assertExists()
        composeTestRule.onNodeWithText("meta 60% ✓").assertExists()
    }

    @Test
    fun `MetaCardTier2 pinta las mismas etiquetas y valores apilados`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MetaCardTier2(
                    porcentajeCobro = 91f,
                    porcentajeCuentas = 78f,
                    clientesPagaron = 39,
                    clientesTotal = 50
                )
            }
        }

        composeTestRule.onNodeWithText("Meta de la semana").assertExists()
        composeTestRule.onNodeWithText("Porcentaje cobro").assertExists()
        composeTestRule.onNodeWithText("Porcentaje cuentas").assertExists()
        composeTestRule.onNodeWithText("91%").assertExists()
        composeTestRule.onNodeWithText("78%").assertExists()
        composeTestRule.onNodeWithText("39 de 50 clientes").assertExists()
        composeTestRule.onNodeWithText("meta 60% ✓").assertExists()
    }
}
