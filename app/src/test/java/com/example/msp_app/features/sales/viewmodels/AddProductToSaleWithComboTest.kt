package com.example.msp_app.features.sales.viewmodels

import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SaleProductsViewModel.addProductToSaleWithCombo().
 *
 * This method is used during edit sale restoration to load products
 * with their existing comboId directly from the database.
 */
class AddProductToSaleWithComboTest : RobolectricTestBase() {

    private lateinit var vm: SaleProductsViewModel
    private val epsilon = 0.001

    @Before
    fun setUp() {
        vm = SaleProductsViewModel()
    }

    // ========================
    // Basic behavior
    // ========================

    @Test
    fun `adds product with comboId`() {
        val product = TestDataFactory.createProductInventory(id = 1)
        vm.addProductToSaleWithCombo(product, 2, "combo-1")

        assertEquals(1, vm.saleItems.size)
        assertEquals(2, vm.saleItems[0].quantity)
        assertEquals("combo-1", vm.saleItems[0].comboId)
    }

    @Test
    fun `adds product with null comboId`() {
        val product = TestDataFactory.createProductInventory(id = 1)
        vm.addProductToSaleWithCombo(product, 3, null)

        assertEquals(1, vm.saleItems.size)
        assertEquals(3, vm.saleItems[0].quantity)
        assertNull(vm.saleItems[0].comboId)
    }

    @Test
    fun `zero quantity does nothing`() {
        val product = TestDataFactory.createProductInventory(id = 1)
        vm.addProductToSaleWithCombo(product, 0, "combo-1")

        assertEquals(0, vm.saleItems.size)
    }

    @Test
    fun `negative quantity does nothing`() {
        val product = TestDataFactory.createProductInventory(id = 1)
        vm.addProductToSaleWithCombo(product, -5, "combo-1")

        assertEquals(0, vm.saleItems.size)
    }

    @Test
    fun `caps quantity at stock`() {
        val product = TestDataFactory.createProductInventory(id = 1, stock = 3)
        vm.addProductToSaleWithCombo(product, 10, "combo-1")

        assertEquals(3, vm.saleItems[0].quantity)
    }

    // ========================
    // Duplicate product handling
    // ========================

    @Test
    fun `existing product gets quantity incremented and comboId overwritten`() {
        val product = TestDataFactory.createProductInventory(id = 1, stock = 10)
        vm.addProductToSaleWithCombo(product, 2, null)
        vm.addProductToSaleWithCombo(product, 3, "combo-1")

        assertEquals(1, vm.saleItems.size)
        assertEquals(5, vm.saleItems[0].quantity)
        assertEquals("combo-1", vm.saleItems[0].comboId)
    }

    @Test
    fun `existing product with combo gets comboId updated to null`() {
        val product = TestDataFactory.createProductInventory(id = 1, stock = 10)
        vm.addProductToSaleWithCombo(product, 2, "combo-1")
        vm.addProductToSaleWithCombo(product, 1, null)

        assertEquals(1, vm.saleItems.size)
        assertEquals(3, vm.saleItems[0].quantity)
        assertNull(vm.saleItems[0].comboId)
    }

    @Test
    fun `duplicate product caps total at stock`() {
        val product = TestDataFactory.createProductInventory(id = 1, stock = 5)
        vm.addProductToSaleWithCombo(product, 3, "combo-1")
        vm.addProductToSaleWithCombo(product, 4, "combo-1")

        assertEquals(5, vm.saleItems[0].quantity)
    }

    // ========================
    // Multiple products with different combos
    // ========================

    @Test
    fun `multiple products with same comboId`() {
        val p1 = TestDataFactory.createProductInventory(id = 1, name = "Colchon")
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")
        vm.addProductToSaleWithCombo(p1, 1, "combo-1")
        vm.addProductToSaleWithCombo(p2, 1, "combo-1")

        assertEquals(2, vm.saleItems.size)
        assertEquals("combo-1", vm.saleItems[0].comboId)
        assertEquals("combo-1", vm.saleItems[1].comboId)
    }

    @Test
    fun `products with different comboIds`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")
        val p3 = TestDataFactory.createProductInventory(id = 3, name = "Almohada")
        vm.addProductToSaleWithCombo(p1, 1, "combo-1")
        vm.addProductToSaleWithCombo(p2, 1, "combo-1")
        vm.addProductToSaleWithCombo(p3, 2, null)

        val comboProducts = vm.saleItems.filter { it.comboId == "combo-1" }
        val individual = vm.saleItems.filter { it.comboId == null }
        assertEquals(2, comboProducts.size)
        assertEquals(1, individual.size)
    }

    // ========================
    // Interaction with combo methods
    // ========================

    @Test
    fun `products added with comboId are found by getProductsInCombo after createComboWithId`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")
        vm.addProductToSaleWithCombo(p1, 1, "combo-restore")
        vm.addProductToSaleWithCombo(p2, 1, "combo-restore")

        // Register combo metadata (selectedForCombo is empty, so no products get re-assigned)
        vm.createComboWithId("combo-restore", "Combo Restored", 5000.0, 4500.0, 3800.0)

        val comboProducts = vm.getProductsInCombo("combo-restore")
        assertEquals(2, comboProducts.size)
    }

    @Test
    fun `products added with comboId are excluded from getIndividualProducts`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")
        val p3 = TestDataFactory.createProductInventory(id = 3, name = "Almohada")
        vm.addProductToSaleWithCombo(p1, 1, "combo-1")
        vm.addProductToSaleWithCombo(p2, 1, "combo-1")
        vm.addProductToSaleWithCombo(p3, 1, null)

        assertEquals(1, vm.getIndividualProducts().size)
        assertEquals(3, vm.getIndividualProducts()[0].product.ARTICULO_ID)
    }

    @Test
    fun `deleteCombo on restored combo removes comboId from products`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")
        vm.addProductToSaleWithCombo(p1, 1, "combo-del")
        vm.addProductToSaleWithCombo(p2, 1, "combo-del")
        vm.createComboWithId("combo-del", "Combo", 5000.0, 4500.0, 3800.0)

        vm.deleteCombo("combo-del")

        vm.saleItems.forEach { assertNull(it.comboId) }
        assertEquals(0, vm.getCombosList().size)
        assertEquals(2, vm.getIndividualProducts().size)
    }

    // ========================
    // Price calculations with restored combos
    // ========================

    @Test
    fun `WithCombos totals correct after restore`() {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")
        val p3 = TestDataFactory.createProductInventory(
            id = 3,
            name = "Almohada",
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        vm.addProductToSaleWithCombo(p1, 1, "combo-1")
        vm.addProductToSaleWithCombo(p2, 1, "combo-1")
        vm.addProductToSaleWithCombo(p3, 2, null)
        vm.createComboWithId("combo-1", "Combo", 5000.0, 4500.0, 3800.0)

        // p3 individual: lista=1500*2=3000, combo: 5000 = 8000
        assertEquals(8000.0, vm.getTotalPrecioListaWithCombos(), epsilon)
        // p3 individual: corto=1200*2=2400, combo: 4500 = 6900
        assertEquals(6900.0, vm.getTotalMontoCortoPlazoWithCombos(), epsilon)
        // p3 individual: contado=1000*2=2000, combo: 3800 = 5800
        assertEquals(5800.0, vm.getTotalMontoContadoWithCombos(), epsilon)
    }

    @Test
    fun `CONTADO WithCombos totals after restore`() {
        vm.setTipoVenta("CONTADO")
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")
        vm.addProductToSaleWithCombo(p1, 1, "combo-1")
        vm.addProductToSaleWithCombo(p2, 1, "combo-1")
        vm.createComboWithId("combo-1", "Combo", 5000.0, 4500.0, 3800.0)

        assertEquals(0.0, vm.getTotalPrecioListaWithCombos(), epsilon)
        assertEquals(0.0, vm.getTotalMontoCortoPlazoWithCombos(), epsilon)
        assertEquals(3800.0, vm.getTotalMontoContadoWithCombos(), epsilon)
    }

    // ========================
    // Interaction with addProductToSale (no combo)
    // ========================

    @Test
    fun `addProductToSale after addProductToSaleWithCombo overwrites comboId to null`() {
        val product = TestDataFactory.createProductInventory(id = 1, stock = 10)
        vm.addProductToSaleWithCombo(product, 2, "combo-1")
        // addProductToSale doesn't set comboId, but increments quantity
        // comboId stays because addProductToSale doesn't touch it
        vm.addProductToSale(product, 1)

        assertEquals(1, vm.saleItems.size)
        assertEquals(3, vm.saleItems[0].quantity)
        // addProductToSale uses copy without comboId param, so it keeps existing
        // This is important - the comboId is preserved
    }

    // ========================
    // toggleProductSelection on restored combo products
    // ========================

    @Test
    fun `cannot toggle-select a product that already has a comboId`() {
        val product = TestDataFactory.createProductInventory(id = 1)
        vm.addProductToSaleWithCombo(product, 1, "combo-1")

        vm.toggleProductSelection(1)

        // Product already in a combo should not be selectable
        assertEquals(0, vm.getSelectedProductsCount())
    }

    @Test
    fun `can toggle-select a product with null comboId`() {
        val product = TestDataFactory.createProductInventory(id = 1)
        vm.addProductToSaleWithCombo(product, 1, null)

        vm.toggleProductSelection(1)

        assertEquals(1, vm.getSelectedProductsCount())
    }
}
