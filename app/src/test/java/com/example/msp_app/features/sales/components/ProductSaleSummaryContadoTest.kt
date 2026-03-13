package com.example.msp_app.features.sales.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.features.sales.components.productselector.ProductSaleSummary
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.`test-fixtures`.TestDataFactory
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class ProductSaleSummaryContadoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var viewModel: SaleProductsViewModel

    @Before
    fun setUp() {
        viewModel = SaleProductsViewModel()
    }

    @Test
    fun `CREDITO shows all three price inputs for individual product`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 1)

        composeTestRule.setContent {
            MaterialTheme {
                ProductSaleSummary(
                    saleProductsViewModel = viewModel,
                    productosCamioneta = emptyList(),
                    onOpenProductSheet = {},
                    tipoVenta = "CREDITO"
                )
            }
        }

        composeTestRule.onNodeWithText("Contado:").assertIsDisplayed()
        composeTestRule.onNodeWithText("C.Plazo:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lista:").assertIsDisplayed()
    }

    @Test
    fun `CONTADO shows only Contado price input for individual product`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 1)

        composeTestRule.setContent {
            MaterialTheme {
                ProductSaleSummary(
                    saleProductsViewModel = viewModel,
                    productosCamioneta = emptyList(),
                    onOpenProductSheet = {},
                    tipoVenta = "CONTADO"
                )
            }
        }

        composeTestRule.onNodeWithText("Contado:").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("C.Plazo:").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "C.Plazo input should not be visible for CONTADO" }
        }
        composeTestRule.onAllNodesWithText("Lista:").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "Lista input should not be visible for CONTADO" }
        }
    }

    @Test
    fun `CONTADO shows only Contado price input with multiple products`() {
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            name = "Base King",
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)

        composeTestRule.setContent {
            MaterialTheme {
                ProductSaleSummary(
                    saleProductsViewModel = viewModel,
                    productosCamioneta = emptyList(),
                    onOpenProductSheet = {},
                    tipoVenta = "CONTADO"
                )
            }
        }

        // Should show 2 Contado labels (one per product)
        composeTestRule.onAllNodesWithText("Contado:").fetchSemanticsNodes().let {
            assert(it.size == 2) { "Expected 2 Contado inputs, got ${it.size}" }
        }
        // No C.Plazo or Lista
        composeTestRule.onAllNodesWithText("C.Plazo:").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "C.Plazo should not be visible for CONTADO" }
        }
        composeTestRule.onAllNodesWithText("Lista:").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "Lista should not be visible for CONTADO" }
        }
    }

    @Test
    fun `CREDITO shows all three price inputs with multiple products`() {
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            name = "Base King",
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)

        composeTestRule.setContent {
            MaterialTheme {
                ProductSaleSummary(
                    saleProductsViewModel = viewModel,
                    productosCamioneta = emptyList(),
                    onOpenProductSheet = {},
                    tipoVenta = "CREDITO"
                )
            }
        }

        // 2 products × 3 price labels each
        composeTestRule.onAllNodesWithText("Contado:").fetchSemanticsNodes().let {
            assert(it.size == 2) { "Expected 2 Contado inputs, got ${it.size}" }
        }
        composeTestRule.onAllNodesWithText("C.Plazo:").fetchSemanticsNodes().let {
            assert(it.size == 2) { "Expected 2 C.Plazo inputs, got ${it.size}" }
        }
        composeTestRule.onAllNodesWithText("Lista:").fetchSemanticsNodes().let {
            assert(it.size == 2) { "Expected 2 Lista inputs, got ${it.size}" }
        }
    }

    @Test
    fun `empty sale shows no product section`() {
        composeTestRule.setContent {
            MaterialTheme {
                ProductSaleSummary(
                    saleProductsViewModel = viewModel,
                    productosCamioneta = emptyList(),
                    onOpenProductSheet = {},
                    tipoVenta = "CONTADO"
                )
            }
        }

        composeTestRule.onNodeWithText("No hay productos agregados").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Contado:").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "No price inputs when no products" }
        }
    }
}
