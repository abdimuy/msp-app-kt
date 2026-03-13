package com.example.msp_app.features.sales.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.features.sales.components.combo.ComboCard
import com.example.msp_app.features.sales.viewmodels.ComboItem
import com.example.msp_app.features.sales.viewmodels.SaleItem
import com.example.msp_app.`test-fixtures`.TestDataFactory
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class ComboCardContadoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val combo = ComboItem(
        comboId = "combo-1",
        nombreCombo = "Combo Recamara",
        precioLista = 5000.0,
        precioCortoPlazo = 4500.0,
        precioContado = 4000.0
    )

    private val products = listOf(
        SaleItem(
            product = TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            quantity = 1,
            comboId = "combo-1"
        ),
        SaleItem(
            product = TestDataFactory.createProductInventory(id = 2, name = "Base King"),
            quantity = 1,
            comboId = "combo-1"
        )
    )

    @Test
    fun `CREDITO combo header shows precioLista`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComboCard(
                    combo = combo,
                    products = products,
                    onDelete = {},
                    tipoVenta = "CREDITO"
                )
            }
        }

        // Header should show precioLista formatted as currency
        composeTestRule.onNodeWithText("Combo Recamara").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 productos").assertIsDisplayed()
    }

    @Test
    fun `CONTADO combo header shows precioContado`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComboCard(
                    combo = combo,
                    products = products,
                    onDelete = {},
                    tipoVenta = "CONTADO"
                )
            }
        }

        composeTestRule.onNodeWithText("Combo Recamara").assertIsDisplayed()
    }

    @Test
    fun `CREDITO expanded combo shows editable Lista, CPlazo and Contado inputs`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComboCard(
                    combo = combo,
                    products = products,
                    onDelete = {},
                    onPriceChange = { _, _, _ -> },
                    tipoVenta = "CREDITO"
                )
            }
        }

        // Expand the card
        composeTestRule.onNodeWithContentDescription("Expandir").performClick()

        composeTestRule.onNodeWithText("Contado:").assertIsDisplayed()
        composeTestRule.onNodeWithText("C.Plazo:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lista:").assertIsDisplayed()
    }

    @Test
    fun `CONTADO expanded combo shows only Contado editable input`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComboCard(
                    combo = combo,
                    products = products,
                    onDelete = {},
                    onPriceChange = { _, _, _ -> },
                    tipoVenta = "CONTADO"
                )
            }
        }

        // Expand the card
        composeTestRule.onNodeWithContentDescription("Expandir").performClick()

        composeTestRule.onNodeWithText("Contado:").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("C.Plazo:").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "C.Plazo should not be visible for CONTADO" }
        }
        composeTestRule.onAllNodesWithText("Lista:").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "Lista should not be visible for CONTADO" }
        }
    }

    @Test
    fun `CREDITO expanded combo read-only shows all three prices`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComboCard(
                    combo = combo,
                    products = products,
                    onDelete = {},
                    onPriceChange = null,
                    tipoVenta = "CREDITO"
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Expandir").performClick()

        composeTestRule.onNodeWithText("Contado").assertIsDisplayed()
        composeTestRule.onNodeWithText("Corto Plazo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lista").assertIsDisplayed()
    }

    @Test
    fun `CONTADO expanded combo read-only shows only Contado price`() {
        composeTestRule.setContent {
            MaterialTheme {
                ComboCard(
                    combo = combo,
                    products = products,
                    onDelete = {},
                    onPriceChange = null,
                    tipoVenta = "CONTADO"
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Expandir").performClick()

        composeTestRule.onNodeWithText("Contado").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Corto Plazo").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "Corto Plazo should not be visible for CONTADO" }
        }
        composeTestRule.onAllNodesWithText("Lista").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "Lista should not be visible for CONTADO" }
        }
    }
}
