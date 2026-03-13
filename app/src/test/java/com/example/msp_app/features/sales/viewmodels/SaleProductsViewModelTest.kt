package com.example.msp_app.features.sales.viewmodels

import com.example.msp_app.`test-fixtures`.RobolectricTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import com.example.msp_app.utils.PriceParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaleProductsViewModelTest : RobolectricTestBase() {

    private lateinit var viewModel: SaleProductsViewModel
    private val epsilon = 0.001

    @Before
    fun setUp() {
        viewModel = SaleProductsViewModel()
    }

    // --- Products ---

    @Test
    fun `addProductToSale adds product`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 2)
        assertEquals(1, viewModel.saleItems.size)
        assertEquals(2, viewModel.saleItems[0].quantity)
    }

    @Test
    fun `addProductToSale existing product increments quantity`() {
        val product = TestDataFactory.createProductInventory(stock = 10)
        viewModel.addProductToSale(product, 2)
        viewModel.addProductToSale(product, 3)
        assertEquals(1, viewModel.saleItems.size)
        assertEquals(5, viewModel.saleItems[0].quantity)
    }

    @Test
    fun `addProductToSale caps at stock`() {
        val product = TestDataFactory.createProductInventory(stock = 5)
        viewModel.addProductToSale(product, 10)
        assertEquals(5, viewModel.saleItems[0].quantity)
    }

    @Test
    fun `addProductToSale zero quantity does nothing`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 0)
        assertTrue(viewModel.saleItems.isEmpty())
    }

    @Test
    fun `addProductToSale negative quantity does nothing`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, -1)
        assertTrue(viewModel.saleItems.isEmpty())
    }

    @Test
    fun `removeProductFromSale removes product`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 2)
        viewModel.removeProductFromSale(product)
        assertTrue(viewModel.saleItems.isEmpty())
    }

    @Test
    fun `updateQuantity updates existing product`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 2)
        viewModel.updateQuantity(product, 5)
        assertEquals(5, viewModel.saleItems[0].quantity)
    }

    @Test
    fun `updateQuantity to zero removes product`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 2)
        viewModel.updateQuantity(product, 0)
        assertTrue(viewModel.saleItems.isEmpty())
    }

    // --- Getters ---

    @Test
    fun `getQuantityForProduct returns quantity`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 3)
        assertEquals(3, viewModel.getQuantityForProduct(product))
    }

    @Test
    fun `getQuantityForProduct returns 0 for unknown product`() {
        val product = TestDataFactory.createProductInventory()
        assertEquals(0, viewModel.getQuantityForProduct(product))
    }

    @Test
    fun `getTotalItems sums quantities`() {
        viewModel.addProductToSale(TestDataFactory.createProductInventory(id = 1), 2)
        viewModel.addProductToSale(TestDataFactory.createProductInventory(id = 2), 3)
        assertEquals(5, viewModel.getTotalItems())
    }

    @Test
    fun `hasItems returns true when items exist`() {
        viewModel.addProductToSale(TestDataFactory.createProductInventory(), 1)
        assertTrue(viewModel.hasItems())
    }

    @Test
    fun `hasItems returns false when empty`() {
        assertFalse(viewModel.hasItems())
    }

    // --- Prices ---

    @Test
    fun `getTotalPrecioLista calculates correctly`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)
        assertEquals(3000.0, viewModel.getTotalPrecioLista(), epsilon)
    }

    @Test
    fun `getTotalMontoCortoplazo calculates correctly`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)
        assertEquals(2400.0, viewModel.getTotalMontoCortoplazo(), epsilon)
    }

    @Test
    fun `getTotalMontoContado calculates correctly`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)
        assertEquals(2000.0, viewModel.getTotalMontoContado(), epsilon)
    }

    @Test
    fun `updateProductPrices updates parsed prices`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 1)
        viewModel.updateProductPrices(product, 2000.0, 1800.0, 1500.0)
        val updated = viewModel.saleItems[0].product.PRECIOS
        val parsed = PriceParser.parsePricesFromString(updated)
        assertEquals(2000.0, parsed.precioLista, epsilon)
        assertEquals(1800.0, parsed.precioCortoplazo, epsilon)
        assertEquals(1500.0, parsed.precioContado, epsilon)
    }

    // --- Combos ---

    @Test
    fun `toggleProductSelection selects and deselects`() {
        val product = TestDataFactory.createProductInventory(id = 1)
        viewModel.addProductToSale(product, 1)
        viewModel.toggleProductSelection(1)
        assertTrue(viewModel.isProductSelected(1))
        viewModel.toggleProductSelection(1)
        assertFalse(viewModel.isProductSelected(1))
    }

    @Test
    fun `toggleProductSelection blocked during isCreatingCombo`() {
        val product = TestDataFactory.createProductInventory(id = 1)
        viewModel.addProductToSale(product, 1)
        viewModel.toggleProductSelection(1)
        viewModel.setCreatingCombo(true)
        viewModel.toggleProductSelection(1) // should be blocked
        assertTrue(viewModel.isProductSelected(1))
    }

    @Test
    fun `canCreateCombo requires 2 or more selected`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        assertFalse(viewModel.canCreateCombo())
        viewModel.toggleProductSelection(2)
        assertTrue(viewModel.canCreateCombo())
    }

    @Test
    fun `createCombo assigns IDs and clears selection`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        val comboId = viewModel.createCombo("Combo Test", 3000.0, 2500.0, 2000.0)

        assertNotNull(comboId)
        assertTrue(viewModel.selectedForCombo.isEmpty())
        assertEquals(comboId, viewModel.saleItems[0].comboId)
        assertEquals(comboId, viewModel.saleItems[1].comboId)
        assertEquals("Combo Test", viewModel.combos[comboId]?.nombreCombo)
    }

    @Test
    fun `clearSelection clears all`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        viewModel.addProductToSale(p1, 1)
        viewModel.toggleProductSelection(1)
        viewModel.clearSelection()
        assertTrue(viewModel.selectedForCombo.isEmpty())
    }

    @Test
    fun `deleteCombo removes combo and unlinks products`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        val comboId = viewModel.createCombo("Combo", 3000.0, 2500.0, 2000.0)

        viewModel.deleteCombo(comboId)
        assertNull(viewModel.combos[comboId])
        assertNull(viewModel.saleItems[0].comboId)
        assertNull(viewModel.saleItems[1].comboId)
    }

    @Test
    fun `getProductsInCombo returns correct products`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        val p3 = TestDataFactory.createProductInventory(id = 3)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.addProductToSale(p3, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        val comboId = viewModel.createCombo("Combo", 3000.0, 2500.0, 2000.0)

        val comboProducts = viewModel.getProductsInCombo(comboId)
        assertEquals(2, comboProducts.size)
    }

    @Test
    fun `getIndividualProducts returns non-combo products`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        val p3 = TestDataFactory.createProductInventory(id = 3)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.addProductToSale(p3, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 3000.0, 2500.0, 2000.0)

        val individual = viewModel.getIndividualProducts()
        assertEquals(1, individual.size)
        assertEquals(3, individual[0].product.ARTICULO_ID)
    }

    // --- Totals with combos ---

    @Test
    fun `getTotalPrecioListaWithCombos mixes individual and combo`() {
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p3 = TestDataFactory.createProductInventory(
            id = 3,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.addProductToSale(p3, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 2800.0, 2300.0, 1900.0)

        // p3 individual: 1500.0, combo: 2800.0
        assertEquals(4300.0, viewModel.getTotalPrecioListaWithCombos(), epsilon)
    }

    @Test
    fun `getTotalMontoCortoPlazoWithCombos mixes individual and combo`() {
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p3 = TestDataFactory.createProductInventory(
            id = 3,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.addProductToSale(p3, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 2800.0, 2300.0, 1900.0)

        // p3 individual: 1200.0, combo: 2300.0
        assertEquals(3500.0, viewModel.getTotalMontoCortoPlazoWithCombos(), epsilon)
    }

    @Test
    fun `getTotalMontoContadoWithCombos mixes individual and combo`() {
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p3 = TestDataFactory.createProductInventory(
            id = 3,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.addProductToSale(p3, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 2800.0, 2300.0, 1900.0)

        // p3 individual: 1000.0, combo: 1900.0
        assertEquals(2900.0, viewModel.getTotalMontoContadoWithCombos(), epsilon)
    }

    @Test
    fun `updateComboPrices updates combo`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        val comboId = viewModel.createCombo("Combo", 3000.0, 2500.0, 2000.0)

        viewModel.updateComboPrices(comboId, 3500.0, 3000.0, 2500.0)
        val combo = viewModel.combos[comboId]!!
        assertEquals(3500.0, combo.precioLista, epsilon)
        assertEquals(3000.0, combo.precioCortoPlazo, epsilon)
        assertEquals(2500.0, combo.precioContado, epsilon)
    }

    // --- Clear ---

    @Test
    fun `clearSale clears everything`() {
        viewModel.addProductToSale(TestDataFactory.createProductInventory(id = 1), 1)
        viewModel.addProductToSale(TestDataFactory.createProductInventory(id = 2), 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 1000.0, 900.0, 800.0)

        viewModel.clearSale()
        assertTrue(viewModel.saleItems.isEmpty())
        assertTrue(viewModel.combos.isEmpty())
        assertTrue(viewModel.selectedForCombo.isEmpty())
    }

    @Test
    fun `clearAll clears everything`() {
        viewModel.addProductToSale(TestDataFactory.createProductInventory(), 1)
        viewModel.clearAll()
        assertTrue(viewModel.saleItems.isEmpty())
        assertTrue(viewModel.combos.isEmpty())
        assertTrue(viewModel.selectedForCombo.isEmpty())
    }

    // --- CONTADO price zeroing ---

    @Test
    fun `getTotalPrecioLista returns 0 for CONTADO`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)
        viewModel.setTipoVenta("CONTADO")
        assertEquals(0.0, viewModel.getTotalPrecioLista(), epsilon)
    }

    @Test
    fun `getTotalPrecioLista returns value for CREDITO`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)
        assertEquals(3000.0, viewModel.getTotalPrecioLista(), epsilon)
    }

    @Test
    fun `getTotalMontoCortoplazo returns 0 for CONTADO`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)
        viewModel.setTipoVenta("CONTADO")
        assertEquals(0.0, viewModel.getTotalMontoCortoplazo(), epsilon)
    }

    @Test
    fun `getTotalMontoCortoplazo returns value for CREDITO`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)
        assertEquals(2400.0, viewModel.getTotalMontoCortoplazo(), epsilon)
    }

    @Test
    fun `getTotalMontoContado returns value regardless of tipoVenta`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)
        viewModel.setTipoVenta("CONTADO")
        assertEquals(2000.0, viewModel.getTotalMontoContado(), epsilon)
    }

    @Test
    fun `getTotalPrecioListaWithCombos returns 0 for CONTADO`() {
        viewModel.setTipoVenta("CONTADO")
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 2800.0, 2300.0, 1900.0)

        assertEquals(0.0, viewModel.getTotalPrecioListaWithCombos(), epsilon)
    }

    @Test
    fun `getTotalMontoCortoPlazoWithCombos returns 0 for CONTADO`() {
        viewModel.setTipoVenta("CONTADO")
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 2800.0, 2300.0, 1900.0)

        assertEquals(0.0, viewModel.getTotalMontoCortoPlazoWithCombos(), epsilon)
    }

    @Test
    fun `getTotalMontoContadoWithCombos returns value for CONTADO`() {
        viewModel.setTipoVenta("CONTADO")
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p3 = TestDataFactory.createProductInventory(
            id = 3,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.addProductToSale(p3, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 2800.0, 2300.0, 1900.0)

        // p3 individual: 1000.0, combo contado: 1900.0
        assertEquals(2900.0, viewModel.getTotalMontoContadoWithCombos(), epsilon)
    }

    @Test
    fun `updateProductPrices forces lista and cortoplazo to 0 for CONTADO`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 1)
        viewModel.setTipoVenta("CONTADO")
        viewModel.updateProductPrices(product, 2000.0, 1800.0, 1500.0)
        val updated = viewModel.saleItems[0].product.PRECIOS
        val parsed = PriceParser.parsePricesFromString(updated)
        assertEquals(0.0, parsed.precioLista, epsilon)
        assertEquals(0.0, parsed.precioCortoplazo, epsilon)
        assertEquals(1500.0, parsed.precioContado, epsilon)
    }

    @Test
    fun `createCombo forces lista and cortoplazo to 0 for CONTADO`() {
        viewModel.setTipoVenta("CONTADO")
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        val comboId = viewModel.createCombo("Combo", 3000.0, 2500.0, 2000.0)
        val combo = viewModel.combos[comboId]!!
        assertEquals(0.0, combo.precioLista, epsilon)
        assertEquals(0.0, combo.precioCortoPlazo, epsilon)
        assertEquals(2000.0, combo.precioContado, epsilon)
    }

    @Test
    fun `setTipoVenta updates tipoVenta`() {
        assertEquals("CREDITO", viewModel.tipoVenta)
        viewModel.setTipoVenta("CONTADO")
        assertEquals("CONTADO", viewModel.tipoVenta)
    }

    @Test
    fun `default tipoVenta is CREDITO`() {
        assertEquals("CREDITO", viewModel.tipoVenta)
    }

    @Test
    fun `updateComboPrices forces lista and cortoplazo to 0 for CONTADO`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        val comboId = viewModel.createCombo("Combo", 3000.0, 2500.0, 2000.0)

        viewModel.setTipoVenta("CONTADO")
        viewModel.updateComboPrices(comboId, 3500.0, 3000.0, 2500.0)
        val combo = viewModel.combos[comboId]!!
        assertEquals(0.0, combo.precioLista, epsilon)
        assertEquals(0.0, combo.precioCortoPlazo, epsilon)
        assertEquals(2500.0, combo.precioContado, epsilon)
    }

    @Test
    fun `updateComboPrices preserves all prices for CREDITO`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2)
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        val comboId = viewModel.createCombo("Combo", 3000.0, 2500.0, 2000.0)

        viewModel.updateComboPrices(comboId, 3500.0, 3000.0, 2500.0)
        val combo = viewModel.combos[comboId]!!
        assertEquals(3500.0, combo.precioLista, epsilon)
        assertEquals(3000.0, combo.precioCortoPlazo, epsilon)
        assertEquals(2500.0, combo.precioContado, epsilon)
    }

    @Test
    fun `updateProductPrices preserves all prices for CREDITO`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 1)
        viewModel.updateProductPrices(product, 2000.0, 1800.0, 1500.0)
        val parsed = PriceParser.parsePricesFromString(viewModel.saleItems[0].product.PRECIOS)
        assertEquals(2000.0, parsed.precioLista, epsilon)
        assertEquals(1800.0, parsed.precioCortoplazo, epsilon)
        assertEquals(1500.0, parsed.precioContado, epsilon)
    }

    @Test
    fun `switching from CONTADO to CREDITO restores normal price behavior`() {
        val product = TestDataFactory.createProductInventory(
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(product, 2)

        viewModel.setTipoVenta("CONTADO")
        assertEquals(0.0, viewModel.getTotalPrecioLista(), epsilon)
        assertEquals(0.0, viewModel.getTotalMontoCortoplazo(), epsilon)

        viewModel.setTipoVenta("CREDITO")
        assertEquals(3000.0, viewModel.getTotalPrecioLista(), epsilon)
        assertEquals(2400.0, viewModel.getTotalMontoCortoplazo(), epsilon)
        assertEquals(2000.0, viewModel.getTotalMontoContado(), epsilon)
    }

    @Test
    fun `switching CONTADO to CREDITO restores combo total behavior`() {
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p3 = TestDataFactory.createProductInventory(
            id = 3,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 1)
        viewModel.addProductToSale(p2, 1)
        viewModel.addProductToSale(p3, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)
        viewModel.createCombo("Combo", 2800.0, 2300.0, 1900.0)

        viewModel.setTipoVenta("CONTADO")
        assertEquals(0.0, viewModel.getTotalPrecioListaWithCombos(), epsilon)

        viewModel.setTipoVenta("CREDITO")
        // p3 individual: 1500.0, combo: 2800.0
        assertEquals(4300.0, viewModel.getTotalPrecioListaWithCombos(), epsilon)
    }

    @Test
    fun `CONTADO updateProductPrices then switch to CREDITO prices stay zeroed in product`() {
        val product = TestDataFactory.createProductInventory()
        viewModel.addProductToSale(product, 1)
        viewModel.setTipoVenta("CONTADO")
        viewModel.updateProductPrices(product, 2000.0, 1800.0, 1500.0)

        // Prices were stored as 0 for lista/cortoplazo
        val parsed = PriceParser.parsePricesFromString(viewModel.saleItems[0].product.PRECIOS)
        assertEquals(0.0, parsed.precioLista, epsilon)
        assertEquals(0.0, parsed.precioCortoplazo, epsilon)

        // Switching back doesn't magically restore stored prices - they're already 0 in the product
        viewModel.setTipoVenta("CREDITO")
        val parsedAfter = PriceParser.parsePricesFromString(viewModel.saleItems[0].product.PRECIOS)
        assertEquals(0.0, parsedAfter.precioLista, epsilon)
        assertEquals(1500.0, parsedAfter.precioContado, epsilon)
    }

    @Test
    fun `getSelectedItemsSuggestedPrices calculates correctly`() {
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        viewModel.addProductToSale(p1, 2)
        viewModel.addProductToSale(p2, 1)
        viewModel.toggleProductSelection(1)
        viewModel.toggleProductSelection(2)

        val (lista, cortoPlazo, contado) = viewModel.getSelectedItemsSuggestedPrices()
        // p1: 1500*2=3000, p2: 1500*1=1500 → 4500
        assertEquals(4500.0, lista, epsilon)
        // p1: 1200*2=2400, p2: 1200*1=1200 → 3600
        assertEquals(3600.0, cortoPlazo, epsilon)
        // p1: 1000*2=2000, p2: 1000*1=1000 → 3000
        assertEquals(3000.0, contado, epsilon)
    }
}
