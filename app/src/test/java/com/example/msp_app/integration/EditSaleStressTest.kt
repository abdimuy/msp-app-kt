package com.example.msp_app.integration

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.local.entities.LocalSaleComboEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.data.models.productInventory.ProductInventory
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import com.example.msp_app.utils.PriceParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stress and adversarial tests for edit sale flow.
 *
 * Tries to break the edit pipeline with edge cases:
 * orphaned combos, missing warehouse products, rapid edits,
 * combo ID collisions, empty states, and boundary conditions.
 */
class EditSaleStressTest : RoomTestBase() {

    private lateinit var saleDataSource: LocalSaleDataSource
    private lateinit var productDataSource: SaleProductLocalDataSource
    private lateinit var comboDataSource: ComboLocalDataSource
    private lateinit var vm: SaleProductsViewModel

    private val epsilon = 0.001

    @Before
    fun setUpDataSources() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        saleDataSource = LocalSaleDataSource(context)
        productDataSource = SaleProductLocalDataSource(context)
        comboDataSource = ComboLocalDataSource(context)
        vm = SaleProductsViewModel()
    }

    private suspend fun createSale(
        saleId: String,
        products: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity> = emptyList(),
        tipoVenta: String = "CREDITO"
    ) {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = saleId, tipoVenta = tipoVenta)
        )
        productDataSource.insertSaleProducts(products)
        if (combos.isNotEmpty()) comboDataSource.insertCombos(combos)
    }

    private fun restore(
        dbProducts: List<LocalSaleProductEntity>,
        dbCombos: List<LocalSaleComboEntity>,
        camioneta: List<ProductInventory>
    ) {
        dbProducts.forEach { pe ->
            val p = camioneta.find { it.ARTICULO_ID == pe.ARTICULO_ID }
            if (p != null) vm.addProductToSaleWithCombo(p, pe.CANTIDAD, pe.COMBO_ID)
        }
        dbCombos.forEach { c ->
            vm.createComboWithId(
                c.COMBO_ID,
                c.NOMBRE_COMBO,
                c.PRECIO_LISTA,
                c.PRECIO_CORTO_PLAZO,
                c.PRECIO_CONTADO
            )
        }
    }

    private suspend fun persistEdit(saleId: String, tipoVenta: String = "CREDITO") {
        saleDataSource.updateSale(
            TestDataFactory.createLocalSaleEntity(
                saleId = saleId,
                tipoVenta = tipoVenta,
                precioTotal = vm.getTotalPrecioListaWithCombos(),
                montoACortoPlazo = vm.getTotalMontoCortoPlazoWithCombos(),
                montoDeContado = vm.getTotalMontoContadoWithCombos(),
                enviado = false
            )
        )
        productDataSource.deleteProductsForSale(saleId)
        val entities = vm.saleItems.map { si ->
            val pp = PriceParser.parsePricesFromString(si.product.PRECIOS)
            LocalSaleProductEntity(
                LOCAL_SALE_ID = saleId,
                ARTICULO_ID = si.product.ARTICULO_ID,
                ARTICULO = si.product.ARTICULO,
                CANTIDAD = si.quantity,
                PRECIO_LISTA = pp.precioLista,
                PRECIO_CORTO_PLAZO = pp.precioCortoplazo,
                PRECIO_CONTADO = pp.precioContado,
                COMBO_ID = si.comboId
            )
        }
        productDataSource.insertSaleProducts(entities)
        val comboEntities = vm.getCombosList().map { c ->
            LocalSaleComboEntity(
                COMBO_ID = c.comboId,
                LOCAL_SALE_ID = saleId,
                NOMBRE_COMBO = c.nombreCombo,
                PRECIO_LISTA = c.precioLista,
                PRECIO_CORTO_PLAZO = c.precioCortoPlazo,
                PRECIO_CONTADO = c.precioContado
            )
        }
        comboDataSource.replaceCombosForSale(saleId, comboEntities)
    }

    // ========================
    // Product not in warehouse
    // ========================

    @Test
    fun `restore skips products not found in warehouse`() = runTest {
        val saleId = "stress-missing-product"
        createSale(
            saleId,
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    articulo = "Colchon King",
                    cantidad = 2
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 999,
                    articulo = "Producto Eliminado",
                    cantidad = 1
                )
            )
        )

        // Warehouse only has product 1
        val camioneta = listOf(TestDataFactory.createProductInventory(id = 1))
        restore(productDataSource.getProductsForSale(saleId), emptyList(), camioneta)

        // Only product 1 was restored
        assertEquals(1, vm.saleItems.size)
        assertEquals(1, vm.saleItems[0].product.ARTICULO_ID)
    }

    @Test
    fun `combo product missing from warehouse - combo partially restored`() = runTest {
        val saleId = "stress-combo-partial"
        val comboId = "combo-partial"
        createSale(
            saleId,
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    cantidad = 1,
                    comboId = comboId
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 999,
                    articulo = "Gone",
                    cantidad = 1,
                    comboId = comboId
                )
            ),
            combos = listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = comboId,
                    saleId = saleId
                )
            )
        )

        val camioneta = listOf(TestDataFactory.createProductInventory(id = 1))
        restore(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        // Only 1 product restored, but combo metadata exists
        assertEquals(1, vm.saleItems.size)
        assertEquals(comboId, vm.saleItems[0].comboId)
        assertEquals(1, vm.getCombosList().size)
    }

    // ========================
    // Orphaned combos
    // ========================

    @Test
    fun `combo in DB but no products reference it - combo is registered but empty`() = runTest {
        val saleId = "stress-orphan-combo"
        createSale(
            saleId,
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    cantidad = 1
                    // no comboId
                )
            ),
            combos = listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "orphan",
                    saleId = saleId
                )
            )
        )

        val camioneta = listOf(TestDataFactory.createProductInventory(id = 1))
        restore(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        // Combo metadata registered but no products linked
        assertEquals(1, vm.getCombosList().size)
        assertEquals(0, vm.getProductsInCombo("orphan").size)

        // After save, orphan combo persists (user should see and can delete it)
        persistEdit(saleId)
        val dbCombos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, dbCombos.size)
    }

    // ========================
    // Double save
    // ========================

    @Test
    fun `saving edit twice produces same result`() = runTest {
        val saleId = "stress-double-save"
        createSale(
            saleId,
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    cantidad = 1
                )
            )
        )

        val camioneta = listOf(TestDataFactory.createProductInventory(id = 1))
        restore(productDataSource.getProductsForSale(saleId), emptyList(), camioneta)

        vm.addProductToSale(TestDataFactory.createProductInventory(id = 2, name = "Base"), 1)

        persistEdit(saleId)
        val firstProducts = productDataSource.getProductsForSale(saleId)

        persistEdit(saleId)
        val secondProducts = productDataSource.getProductsForSale(saleId)

        assertEquals(firstProducts.size, secondProducts.size)
        assertEquals(
            firstProducts.map { it.ARTICULO_ID }.sorted(),
            secondProducts.map { it.ARTICULO_ID }.sorted()
        )
    }

    // ========================
    // All products removed
    // ========================

    @Test
    fun `removing all products results in empty product list`() = runTest {
        val saleId = "stress-remove-all"
        createSale(
            saleId,
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    cantidad = 1
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 2,
                    articulo = "Base",
                    cantidad = 1
                )
            )
        )

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1),
            TestDataFactory.createProductInventory(id = 2, name = "Base")
        )
        restore(productDataSource.getProductsForSale(saleId), emptyList(), camioneta)

        vm.removeProductFromSale(camioneta[0])
        vm.removeProductFromSale(camioneta[1])

        persistEdit(saleId)

        val products = productDataSource.getProductsForSale(saleId)
        assertEquals(0, products.size)
        val sale = saleDataSource.getSaleById(saleId)!!
        assertEquals(0.0, sale.PRECIO_TOTAL, epsilon)
    }

    // ========================
    // Combo then remove all combo products
    // ========================

    @Test
    fun `removing all products from a combo leaves orphan combo in VM`() = runTest {
        val comboId = "combo-orphan-test"
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")

        vm.addProductToSaleWithCombo(p1, 1, comboId)
        vm.addProductToSaleWithCombo(p2, 1, comboId)
        vm.createComboWithId(comboId, "Combo", 5000.0, 4500.0, 3800.0)

        // Remove both products
        vm.removeProductFromSale(p1)
        vm.removeProductFromSale(p2)

        // Combo still exists in VM but has no products
        assertEquals(1, vm.getCombosList().size)
        assertEquals(0, vm.getProductsInCombo(comboId).size)
        assertEquals(0, vm.saleItems.size)

        // Totals only include combo price (no individual products)
        assertEquals(5000.0, vm.getTotalPrecioListaWithCombos(), epsilon)
    }

    // ========================
    // Rapid combo create/delete cycles
    // ========================

    @Test
    fun `create and delete combo multiple times`() = runTest {
        val saleId = "stress-rapid-combo"
        createSale(
            saleId,
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    cantidad = 1
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 2,
                    articulo = "Base",
                    cantidad = 1
                )
            )
        )

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1),
            TestDataFactory.createProductInventory(id = 2, name = "Base")
        )
        restore(productDataSource.getProductsForSale(saleId), emptyList(), camioneta)

        // Create combo
        vm.toggleProductSelection(1)
        vm.toggleProductSelection(2)
        val id1 = vm.createCombo("Round 1", 5000.0, 4500.0, 3800.0)

        // Delete it
        vm.deleteCombo(id1)

        // Create again
        vm.toggleProductSelection(1)
        vm.toggleProductSelection(2)
        val id2 = vm.createCombo("Round 2", 4000.0, 3500.0, 3000.0)

        // Delete again
        vm.deleteCombo(id2)

        // Create final
        vm.toggleProductSelection(1)
        vm.toggleProductSelection(2)
        vm.createCombo("Final", 3000.0, 2500.0, 2000.0)

        persistEdit(saleId)

        val combos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, combos.size)
        assertEquals("Final", combos[0].NOMBRE_COMBO)
        assertEquals(3000.0, combos[0].PRECIO_LISTA, epsilon)

        val products = productDataSource.getProductsForSale(saleId)
        products.forEach { assertNotNull(it.COMBO_ID) }
    }

    // ========================
    // Switch tipoVenta during edit
    // ========================

    @Test
    fun `switch CREDITO to CONTADO during edit zeroes prices`() = runTest {
        val saleId = "stress-switch-tipo"
        val comboId = "combo-switch"
        createSale(
            saleId,
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    cantidad = 1,
                    comboId = comboId,
                    precioLista = 1500.0,
                    precioCortoplazo = 1200.0,
                    precioContado = 1000.0
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 2,
                    articulo = "Base",
                    cantidad = 1,
                    comboId = comboId,
                    precioLista = 1500.0,
                    precioCortoplazo = 1200.0,
                    precioContado = 1000.0
                )
            ),
            combos = listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = comboId,
                    saleId = saleId,
                    precioLista = 5000.0,
                    precioCortoplazo = 4500.0,
                    precioContado = 3800.0
                )
            )
        )

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1),
            TestDataFactory.createProductInventory(id = 2, name = "Base")
        )
        restore(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        // Switch to CONTADO
        vm.setTipoVenta("CONTADO")

        // Update combo prices (lista/corto should be zeroed)
        vm.updateComboPrices(comboId, 6000.0, 5500.0, 4200.0)

        persistEdit(saleId, tipoVenta = "CONTADO")

        val sale = saleDataSource.getSaleById(saleId)!!
        assertEquals("CONTADO", sale.TIPO_VENTA)
        assertEquals(0.0, sale.PRECIO_TOTAL, epsilon)
        assertEquals(0.0, sale.MONTO_A_CORTO_PLAZO, epsilon)
        assertTrue(sale.MONTO_DE_CONTADO > 0)

        val combo = comboDataSource.getCombosForSale(saleId)[0]
        assertEquals(0.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(0.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(4200.0, combo.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `switch CONTADO to CREDITO during edit preserves prices`() = runTest {
        val saleId = "stress-switch-credito"
        createSale(
            saleId,
            tipoVenta = "CONTADO",
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    cantidad = 1,
                    precioLista = 0.0,
                    precioCortoplazo = 0.0,
                    precioContado = 1000.0
                )
            )
        )

        vm.setTipoVenta("CONTADO")
        val camioneta = listOf(TestDataFactory.createProductInventory(id = 1))
        restore(productDataSource.getProductsForSale(saleId), emptyList(), camioneta)

        // Switch to CREDITO
        vm.setTipoVenta("CREDITO")

        // Create combo with all prices
        vm.addProductToSale(TestDataFactory.createProductInventory(id = 2, name = "Base"), 1)
        vm.toggleProductSelection(1)
        vm.toggleProductSelection(2)
        vm.createCombo("Combo Credito", 5000.0, 4500.0, 3800.0)

        persistEdit(saleId, tipoVenta = "CREDITO")

        val combo = comboDataSource.getCombosForSale(saleId)[0]
        assertEquals(5000.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(4500.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, combo.PRECIO_CONTADO, epsilon)
    }

    // ========================
    // Large number of products/combos
    // ========================

    @Test
    fun `edit with 20 products and 5 combos`() = runTest {
        val saleId = "stress-large"
        val products = (1..20).map { i ->
            TestDataFactory.createLocalSaleProductEntity(
                saleId = saleId,
                articuloId = i,
                articulo = "Product $i",
                cantidad = 1,
                comboId = if (i <= 10) "combo-${(i - 1) / 2 + 1}" else null
            )
        }
        val combos = (1..5).map { i ->
            TestDataFactory.createLocalSaleComboEntity(
                comboId = "combo-$i",
                saleId = saleId,
                nombreCombo = "Combo $i",
                precioLista = 1000.0 * i,
                precioCortoplazo = 800.0 * i,
                precioContado = 600.0 * i
            )
        }
        createSale(saleId, products = products, combos = combos)

        val camioneta = (1..20).map { i ->
            TestDataFactory.createProductInventory(id = i, name = "Product $i")
        }
        restore(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        assertEquals(20, vm.saleItems.size)
        assertEquals(5, vm.getCombosList().size)
        assertEquals(10, vm.getIndividualProducts().size)

        // Delete 2 combos, keep 3
        vm.deleteCombo("combo-1")
        vm.deleteCombo("combo-2")

        persistEdit(saleId)

        val dbCombos = comboDataSource.getCombosForSale(saleId)
        assertEquals(3, dbCombos.size)

        val dbProducts = productDataSource.getProductsForSale(saleId)
        assertEquals(20, dbProducts.size)
        // Products from deleted combos should be individual
        val orphanedProducts = dbProducts.filter { it.COMBO_ID == null }
        assertEquals(14, orphanedProducts.size) // 10 original individual + 4 from 2 deleted combos
    }

    // ========================
    // Edit product that's in a combo
    // ========================

    @Test
    fun `changing quantity of product in combo preserves comboId`() {
        val p1 = TestDataFactory.createProductInventory(id = 1, stock = 10)
        vm.addProductToSaleWithCombo(p1, 1, "combo-1")
        vm.createComboWithId("combo-1", "Combo", 5000.0, 4500.0, 3800.0)

        vm.updateQuantity(p1, 5)

        assertEquals(5, vm.saleItems[0].quantity)
        assertEquals("combo-1", vm.saleItems[0].comboId)
    }

    // ========================
    // Delete combo then re-create with same products
    // ========================

    @Test
    fun `delete combo then re-create with same products gets new comboId`() = runTest {
        val saleId = "stress-recreate"
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")

        vm.addProductToSaleWithCombo(p1, 1, "old-combo")
        vm.addProductToSaleWithCombo(p2, 1, "old-combo")
        vm.createComboWithId("old-combo", "Viejo", 5000.0, 4500.0, 3800.0)

        // Delete
        vm.deleteCombo("old-combo")
        vm.saleItems.forEach { assertNull(it.comboId) }

        // Re-create
        vm.toggleProductSelection(1)
        vm.toggleProductSelection(2)
        val newId = vm.createCombo("Nuevo", 4000.0, 3500.0, 3000.0)

        // New combo has different ID
        assertTrue(newId != "old-combo")
        assertEquals("Nuevo", vm.getCombosList()[0].nombreCombo)
        vm.saleItems.forEach { assertEquals(newId, it.comboId) }

        // Persist and verify
        createSale(saleId, products = emptyList())
        persistEdit(saleId)

        val dbCombos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, dbCombos.size)
        assertEquals(newId, dbCombos[0].COMBO_ID)
    }

    // ========================
    // Product stock reduced since creation
    // ========================

    @Test
    fun `restore caps quantity to current stock`() {
        val p1 = TestDataFactory.createProductInventory(id = 1, stock = 2) // was 5 when sold
        vm.addProductToSaleWithCombo(p1, 5, null)

        // Capped to current stock of 2
        assertEquals(2, vm.saleItems[0].quantity)
    }

    // ========================
    // Multiple edits chain
    // ========================

    @Test
    fun `edit chain - create, save, edit, save, edit, save`() = runTest {
        val saleId = "stress-chain"
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon"),
            TestDataFactory.createProductInventory(id = 2, name = "Base"),
            TestDataFactory.createProductInventory(id = 3, name = "Almohada")
        )

        // Initial creation
        createSale(
            saleId,
            products = listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = saleId,
                    articuloId = 1,
                    cantidad = 1
                )
            )
        )

        // Edit 1: add product, create combo
        vm = SaleProductsViewModel()
        restore(productDataSource.getProductsForSale(saleId), emptyList(), camioneta)
        vm.addProductToSale(camioneta[1], 1)
        vm.toggleProductSelection(1)
        vm.toggleProductSelection(2)
        vm.createCombo("Combo v1", 3000.0, 2500.0, 2000.0)
        persistEdit(saleId)

        assertEquals(1, comboDataSource.getCombosForSale(saleId).size)
        assertEquals(2, productDataSource.getProductsForSale(saleId).size)

        // Edit 2: delete combo, add new individual product
        vm = SaleProductsViewModel()
        val dbCombos2 = comboDataSource.getCombosForSale(saleId)
        restore(productDataSource.getProductsForSale(saleId), dbCombos2, camioneta)
        vm.deleteCombo(dbCombos2[0].COMBO_ID)
        vm.addProductToSale(camioneta[2], 3)
        persistEdit(saleId)

        assertEquals(0, comboDataSource.getCombosForSale(saleId).size)
        assertEquals(3, productDataSource.getProductsForSale(saleId).size)

        // Edit 3: create new combo with different products
        vm = SaleProductsViewModel()
        restore(productDataSource.getProductsForSale(saleId), emptyList(), camioneta)
        vm.toggleProductSelection(2)
        vm.toggleProductSelection(3)
        vm.createCombo("Combo v2", 2000.0, 1500.0, 1000.0)
        persistEdit(saleId)

        val finalCombos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, finalCombos.size)
        assertEquals("Combo v2", finalCombos[0].NOMBRE_COMBO)

        val finalProducts = productDataSource.getProductsForSale(saleId)
        assertEquals(3, finalProducts.size)
        val inCombo = finalProducts.filter { it.COMBO_ID != null }
        assertEquals(2, inCombo.size)
    }

    // ========================
    // Zero-price products
    // ========================

    @Test
    fun `edit with null PRECIOS string persists zero prices`() = runTest {
        val saleId = "stress-null-prices"
        val product = TestDataFactory.createProductInventory(id = 1, prices = null)
        vm.addProductToSaleWithCombo(product, 1, null)

        createSale(saleId, products = emptyList())
        persistEdit(saleId)

        val dbProduct = productDataSource.getProductsForSale(saleId)[0]
        assertEquals(0.0, dbProduct.PRECIO_LISTA, epsilon)
        assertEquals(0.0, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(0.0, dbProduct.PRECIO_CONTADO, epsilon)
    }

    // ========================
    // ComboId that looks like another combo
    // ========================

    @Test
    fun `combo IDs with similar prefixes are properly isolated`() = runTest {
        val saleId = "stress-id-collision"
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base")
        val p3 = TestDataFactory.createProductInventory(id = 3, name = "Almohada")
        val p4 = TestDataFactory.createProductInventory(id = 4, name = "Sabana")

        vm.addProductToSaleWithCombo(p1, 1, "combo-1")
        vm.addProductToSaleWithCombo(p2, 1, "combo-1")
        vm.addProductToSaleWithCombo(p3, 1, "combo-10")
        vm.addProductToSaleWithCombo(p4, 1, "combo-10")
        vm.createComboWithId("combo-1", "Combo One", 3000.0, 2500.0, 2000.0)
        vm.createComboWithId("combo-10", "Combo Ten", 4000.0, 3500.0, 3000.0)

        assertEquals(2, vm.getProductsInCombo("combo-1").size)
        assertEquals(2, vm.getProductsInCombo("combo-10").size)

        // Delete combo-1, combo-10 should be unaffected
        vm.deleteCombo("combo-1")

        assertEquals(0, vm.getProductsInCombo("combo-1").size)
        assertEquals(2, vm.getProductsInCombo("combo-10").size)
        assertEquals(1, vm.getCombosList().size)
    }
}
