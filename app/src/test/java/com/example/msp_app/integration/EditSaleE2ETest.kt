package com.example.msp_app.integration

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.local.entities.LocalSaleComboEntity
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.features.sales.viewmodels.SaleProductsViewModel
import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import com.example.msp_app.utils.PriceParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * E2E tests for the edit sale flow.
 *
 * Tests the full pipeline: DB (existing sale) → SaleProductsViewModel (restore)
 * → user edits → PriceParser → Room DB (save), including combo restore,
 * product swaps, combo add/remove, and atomic combo replacement.
 *
 * Does NOT touch WorkManager, images, or network.
 */
class EditSaleE2ETest : RoomTestBase() {

    private lateinit var saleDataSource: LocalSaleDataSource
    private lateinit var productDataSource: SaleProductLocalDataSource
    private lateinit var comboDataSource: ComboLocalDataSource
    private lateinit var saleProductsVM: SaleProductsViewModel

    private val epsilon = 0.001

    @Before
    fun setUpDataSources() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        saleDataSource = LocalSaleDataSource(context)
        productDataSource = SaleProductLocalDataSource(context)
        comboDataSource = ComboLocalDataSource(context)
        saleProductsVM = SaleProductsViewModel()
    }

    /**
     * Creates an initial sale in DB (simulates what NewLocalSaleViewModel does).
     */
    private suspend fun createInitialSale(
        saleId: String,
        tipoVenta: String = "CREDITO",
        products: List<LocalSaleProductEntity>,
        combos: List<LocalSaleComboEntity> = emptyList(),
        precioTotal: Double = 0.0,
        montoACortoPlazo: Double = 0.0,
        montoDeContado: Double = 0.0
    ) {
        val saleEntity = TestDataFactory.createLocalSaleEntity(
            saleId = saleId,
            tipoVenta = tipoVenta,
            precioTotal = precioTotal,
            montoACortoPlazo = montoACortoPlazo,
            montoDeContado = montoDeContado
        )
        saleDataSource.insertSale(saleEntity)
        productDataSource.insertSaleProducts(products)
        if (combos.isNotEmpty()) {
            comboDataSource.insertCombos(combos)
        }
    }

    /**
     * Simulates what EditSaleScreen's LaunchedEffect does:
     * loads products from DB and restores them into SaleProductsViewModel
     * using addProductToSaleWithCombo + createComboWithId.
     */
    private fun restoreIntoViewModel(
        dbProducts: List<LocalSaleProductEntity>,
        dbCombos: List<LocalSaleComboEntity>,
        productosCamioneta: List<com.example.msp_app.data.models.productInventory.ProductInventory>
    ) {
        dbProducts.forEach { productEntity ->
            val product = productosCamioneta.find { it.ARTICULO_ID == productEntity.ARTICULO_ID }
            if (product != null) {
                saleProductsVM.addProductToSaleWithCombo(
                    product,
                    productEntity.CANTIDAD,
                    productEntity.COMBO_ID
                )
            }
        }
        dbCombos.forEach { combo ->
            saleProductsVM.createComboWithId(
                comboId = combo.COMBO_ID,
                nombreCombo = combo.NOMBRE_COMBO,
                precioLista = combo.PRECIO_LISTA,
                precioCortoPlazo = combo.PRECIO_CORTO_PLAZO,
                precioContado = combo.PRECIO_CONTADO
            )
        }
    }

    /**
     * Simulates what EditLocalSaleViewModel.updateSaleWithImages does:
     * delete old products, insert new ones with COMBO_ID, replace combos atomically.
     */
    private suspend fun persistEdit(saleId: String, tipoVenta: String = "CREDITO") {
        val saleEntity = TestDataFactory.createLocalSaleEntity(
            saleId = saleId,
            tipoVenta = tipoVenta,
            precioTotal = saleProductsVM.getTotalPrecioListaWithCombos(),
            montoACortoPlazo = saleProductsVM.getTotalMontoCortoPlazoWithCombos(),
            montoDeContado = saleProductsVM.getTotalMontoContadoWithCombos(),
            enviado = false
        )
        saleDataSource.updateSale(saleEntity)

        // Delete old products and insert current state
        productDataSource.deleteProductsForSale(saleId)
        val productEntities = saleProductsVM.saleItems.map { saleItem ->
            val parsedPrices = PriceParser.parsePricesFromString(saleItem.product.PRECIOS)
            LocalSaleProductEntity(
                LOCAL_SALE_ID = saleId,
                ARTICULO_ID = saleItem.product.ARTICULO_ID,
                ARTICULO = saleItem.product.ARTICULO,
                CANTIDAD = saleItem.quantity,
                PRECIO_LISTA = parsedPrices.precioLista,
                PRECIO_CORTO_PLAZO = parsedPrices.precioCortoplazo,
                PRECIO_CONTADO = parsedPrices.precioContado,
                COMBO_ID = saleItem.comboId
            )
        }
        productDataSource.insertSaleProducts(productEntities)

        // Atomic combo replacement
        val comboEntities = saleProductsVM.getCombosList().map { combo ->
            LocalSaleComboEntity(
                COMBO_ID = combo.comboId,
                LOCAL_SALE_ID = saleId,
                NOMBRE_COMBO = combo.nombreCombo,
                PRECIO_LISTA = combo.precioLista,
                PRECIO_CORTO_PLAZO = combo.precioCortoPlazo,
                PRECIO_CONTADO = combo.precioContado
            )
        }
        comboDataSource.replaceCombosForSale(saleId, comboEntities)
    }

    // ========================
    // Restore from DB
    // ========================

    @Test
    fun `edit - individual products restore correctly from DB`() = runTest {
        val saleId = "edit-restore-1"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 2,
            precioLista = 1500.0,
            precioCortoplazo = 1200.0,
            precioContado = 1000.0
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            precioLista = 2000.0,
            precioCortoplazo = 1700.0,
            precioContado = 1400.0
        )
        createInitialSale(saleId, products = listOf(p1, p2))

        // Restore
        val dbProducts = productDataSource.getProductsForSale(saleId)
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(dbProducts, emptyList(), camioneta)

        assertEquals(2, saleProductsVM.saleItems.size)
        assertEquals(2, saleProductsVM.saleItems.find { it.product.ARTICULO_ID == 1 }?.quantity)
        assertEquals(1, saleProductsVM.saleItems.find { it.product.ARTICULO_ID == 2 }?.quantity)
    }

    @Test
    fun `edit - combo products restore with correct comboId`() = runTest {
        val saleId = "edit-restore-combo"
        val comboId = "combo-existing-1"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1,
            comboId = comboId
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            comboId = comboId
        )
        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = comboId,
            saleId = saleId,
            nombreCombo = "Combo Recamara",
            precioLista = 5000.0,
            precioCortoplazo = 4500.0,
            precioContado = 3800.0
        )
        createInitialSale(saleId, products = listOf(p1, p2), combos = listOf(combo))

        // Restore
        val dbProducts = productDataSource.getProductsForSale(saleId)
        val dbCombos = comboDataSource.getCombosForSale(saleId)
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(dbProducts, dbCombos, camioneta)

        // Verify products have comboId
        saleProductsVM.saleItems.forEach { item ->
            assertEquals(comboId, item.comboId)
        }

        // Verify combo metadata restored
        val combos = saleProductsVM.getCombosList()
        assertEquals(1, combos.size)
        assertEquals("Combo Recamara", combos[0].nombreCombo)
        assertEquals(5000.0, combos[0].precioLista, epsilon)
        assertEquals(4500.0, combos[0].precioCortoPlazo, epsilon)
        assertEquals(3800.0, combos[0].precioContado, epsilon)
    }

    @Test
    fun `edit - mixed individual and combo products restore correctly`() = runTest {
        val saleId = "edit-restore-mixed"
        val comboId = "combo-mix-1"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1,
            comboId = comboId
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            comboId = comboId
        )
        val p3 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 3,
            articulo = "Almohada",
            cantidad = 2,
            comboId = null
        )
        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = comboId,
            saleId = saleId
        )
        createInitialSale(saleId, products = listOf(p1, p2, p3), combos = listOf(combo))

        val dbProducts = productDataSource.getProductsForSale(saleId)
        val dbCombos = comboDataSource.getCombosForSale(saleId)
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King"),
            TestDataFactory.createProductInventory(id = 3, name = "Almohada")
        )
        restoreIntoViewModel(dbProducts, dbCombos, camioneta)

        assertEquals(3, saleProductsVM.saleItems.size)
        assertEquals(2, saleProductsVM.saleItems.filter { it.comboId != null }.size)
        assertEquals(1, saleProductsVM.saleItems.filter { it.comboId == null }.size)
        assertEquals(1, saleProductsVM.getCombosList().size)
    }

    // ========================
    // Swap products
    // ========================

    @Test
    fun `edit - swap product persists correctly`() = runTest {
        val saleId = "edit-swap-1"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1
        )
        createInitialSale(saleId, products = listOf(p1))

        // Restore
        val dbProducts = productDataSource.getProductsForSale(saleId)
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(dbProducts, emptyList(), camioneta)

        // Swap: remove product 1, add product 2
        saleProductsVM.removeProductFromSale(camioneta[0])
        saleProductsVM.addProductToSale(camioneta[1], 3)

        persistEdit(saleId)

        val products = productDataSource.getProductsForSale(saleId)
        assertEquals(1, products.size)
        assertEquals(2, products[0].ARTICULO_ID)
        assertEquals("Base King", products[0].ARTICULO)
        assertEquals(3, products[0].CANTIDAD)
    }

    @Test
    fun `edit - swap product updates totals`() = runTest {
        val saleId = "edit-swap-totals"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1,
            precioLista = 1500.0,
            precioCortoplazo = 1200.0,
            precioContado = 1000.0
        )
        createInitialSale(
            saleId,
            products = listOf(p1),
            precioTotal = 1500.0,
            montoACortoPlazo = 1200.0,
            montoDeContado = 1000.0
        )

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        val dbProducts = productDataSource.getProductsForSale(saleId)
        restoreIntoViewModel(dbProducts, emptyList(), camioneta)

        // Swap product
        saleProductsVM.removeProductFromSale(camioneta[0])
        saleProductsVM.addProductToSale(camioneta[1], 2)
        saleProductsVM.updateProductPrices(camioneta[1], 2000.0, 1700.0, 1400.0)

        persistEdit(saleId)

        val sale = saleDataSource.getSaleById(saleId)!!
        assertEquals(4000.0, sale.PRECIO_TOTAL, epsilon) // 2000 * 2
        assertEquals(3400.0, sale.MONTO_A_CORTO_PLAZO, epsilon) // 1700 * 2
        assertEquals(2800.0, sale.MONTO_DE_CONTADO, epsilon) // 1400 * 2
    }

    @Test
    fun `edit - add additional product to existing sale`() = runTest {
        val saleId = "edit-add-product"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1
        )
        createInitialSale(saleId, products = listOf(p1))

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        val dbProducts = productDataSource.getProductsForSale(saleId)
        restoreIntoViewModel(dbProducts, emptyList(), camioneta)

        // Add new product
        saleProductsVM.addProductToSale(camioneta[1], 1)

        persistEdit(saleId)

        val products = productDataSource.getProductsForSale(saleId)
        assertEquals(2, products.size)
    }

    // ========================
    // Edit combos
    // ========================

    @Test
    fun `edit - delete combo converts products to individual`() = runTest {
        val saleId = "edit-delete-combo"
        val comboId = "combo-del-1"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            cantidad = 1,
            comboId = comboId
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            comboId = comboId
        )
        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = comboId,
            saleId = saleId,
            precioLista = 5000.0,
            precioCortoplazo = 4500.0,
            precioContado = 3800.0
        )
        createInitialSale(saleId, products = listOf(p1, p2), combos = listOf(combo))

        // Restore
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        // Delete combo
        saleProductsVM.deleteCombo(comboId)

        persistEdit(saleId)

        // Verify combo deleted from DB
        val combos = comboDataSource.getCombosForSale(saleId)
        assertEquals(0, combos.size)

        // Verify products still exist but without comboId
        val products = productDataSource.getProductsForSale(saleId)
        assertEquals(2, products.size)
        products.forEach { assertNull(it.COMBO_ID) }
    }

    @Test
    fun `edit - create new combo during edit persists correctly`() = runTest {
        val saleId = "edit-new-combo"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1
        )
        createInitialSale(saleId, products = listOf(p1, p2))

        // Restore (no combos)
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            emptyList(),
            camioneta
        )

        // Create combo
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        val newComboId = saleProductsVM.createCombo("Combo Nuevo", 4000.0, 3500.0, 3000.0)

        persistEdit(saleId)

        // Verify combo persisted
        val combos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, combos.size)
        assertEquals("Combo Nuevo", combos[0].NOMBRE_COMBO)
        assertEquals(4000.0, combos[0].PRECIO_LISTA, epsilon)

        // Verify products have comboId
        val products = productDataSource.getProductsForSale(saleId)
        assertEquals(2, products.size)
        products.forEach { assertNotNull(it.COMBO_ID) }
    }

    @Test
    fun `edit - replace combo with different products`() = runTest {
        val saleId = "edit-replace-combo"
        val oldComboId = "combo-old"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1,
            comboId = oldComboId
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            comboId = oldComboId
        )
        val p3 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 3,
            articulo = "Almohada",
            cantidad = 1
        )
        val oldCombo = TestDataFactory.createLocalSaleComboEntity(
            comboId = oldComboId,
            saleId = saleId,
            nombreCombo = "Combo Viejo",
            precioLista = 5000.0
        )
        createInitialSale(saleId, products = listOf(p1, p2, p3), combos = listOf(oldCombo))

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King"),
            TestDataFactory.createProductInventory(id = 3, name = "Almohada")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        // Delete old combo, create new one with different products
        saleProductsVM.deleteCombo(oldComboId)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.toggleProductSelection(3)
        saleProductsVM.createCombo("Combo Nuevo", 3000.0, 2500.0, 2000.0)

        persistEdit(saleId)

        val combos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, combos.size)
        assertEquals("Combo Nuevo", combos[0].NOMBRE_COMBO)
        assertEquals(3000.0, combos[0].PRECIO_LISTA, epsilon)

        // Product 1 should be individual now, 2 and 3 in new combo
        val products = productDataSource.getProductsForSale(saleId)
        val individual = products.filter { it.COMBO_ID == null }
        val inCombo = products.filter { it.COMBO_ID != null }
        assertEquals(1, individual.size)
        assertEquals(1, individual[0].ARTICULO_ID)
        assertEquals(2, inCombo.size)
    }

    @Test
    fun `edit - update combo prices persists new values`() = runTest {
        val saleId = "edit-combo-prices"
        val comboId = "combo-price-edit"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            cantidad = 1,
            comboId = comboId
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            comboId = comboId
        )
        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = comboId,
            saleId = saleId,
            precioLista = 5000.0,
            precioCortoplazo = 4500.0,
            precioContado = 3800.0
        )
        createInitialSale(saleId, products = listOf(p1, p2), combos = listOf(combo))

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        // Edit combo prices
        saleProductsVM.updateComboPrices(comboId, 6000.0, 5500.0, 4200.0)

        persistEdit(saleId)

        val dbCombo = comboDataSource.getCombosForSale(saleId)[0]
        assertEquals(6000.0, dbCombo.PRECIO_LISTA, epsilon)
        assertEquals(5500.0, dbCombo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(4200.0, dbCombo.PRECIO_CONTADO, epsilon)
    }

    // ========================
    // Atomic combo replacement
    // ========================

    @Test
    fun `edit - replaceCombosForSale removes old and inserts new atomically`() = runTest {
        val saleId = "edit-atomic-replace"
        val combo1 = TestDataFactory.createLocalSaleComboEntity(
            comboId = "old-combo-1",
            saleId = saleId,
            nombreCombo = "Viejo 1"
        )
        val combo2 = TestDataFactory.createLocalSaleComboEntity(
            comboId = "old-combo-2",
            saleId = saleId,
            nombreCombo = "Viejo 2"
        )
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            comboId = "old-combo-1"
        )
        createInitialSale(saleId, products = listOf(p1), combos = listOf(combo1, combo2))

        // Verify initial state
        assertEquals(2, comboDataSource.getCombosForSale(saleId).size)

        // Replace with single new combo
        val newCombo = TestDataFactory.createLocalSaleComboEntity(
            comboId = "new-combo-1",
            saleId = saleId,
            nombreCombo = "Nuevo 1",
            precioLista = 3000.0,
            precioCortoplazo = 2500.0,
            precioContado = 2000.0
        )
        comboDataSource.replaceCombosForSale(saleId, listOf(newCombo))

        val combos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, combos.size)
        assertEquals("new-combo-1", combos[0].COMBO_ID)
        assertEquals("Nuevo 1", combos[0].NOMBRE_COMBO)
    }

    @Test
    fun `edit - replaceCombosForSale with empty list removes all combos`() = runTest {
        val saleId = "edit-atomic-empty"
        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = "combo-to-remove",
            saleId = saleId
        )
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            comboId = "combo-to-remove"
        )
        createInitialSale(saleId, products = listOf(p1), combos = listOf(combo))

        comboDataSource.replaceCombosForSale(saleId, emptyList())

        val combos = comboDataSource.getCombosForSale(saleId)
        assertEquals(0, combos.size)
    }

    // ========================
    // Price totals after edit
    // ========================

    @Test
    fun `edit - totals recalculate after adding combo`() = runTest {
        val saleId = "edit-totals-combo"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1
        )
        val p3 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 3,
            articulo = "Almohada",
            cantidad = 2
        )
        createInitialSale(saleId, products = listOf(p1, p2, p3))

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King"),
            TestDataFactory.createProductInventory(id = 3, name = "Almohada")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            emptyList(),
            camioneta
        )

        // Edit individual p3 price
        saleProductsVM.updateProductPrices(camioneta[2], 500.0, 400.0, 300.0)

        // Create combo with p1 + p2
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.createCombo("Combo Recamara", 5000.0, 4500.0, 3800.0)

        persistEdit(saleId)

        val sale = saleDataSource.getSaleById(saleId)!!
        // individual p3: 500*2=1000 + combo: 5000 = 6000
        assertEquals(6000.0, sale.PRECIO_TOTAL, epsilon)
        // individual p3: 400*2=800 + combo: 4500 = 5300
        assertEquals(5300.0, sale.MONTO_A_CORTO_PLAZO, epsilon)
        // individual p3: 300*2=600 + combo: 3800 = 4400
        assertEquals(4400.0, sale.MONTO_DE_CONTADO, epsilon)
    }

    @Test
    fun `edit - totals recalculate after deleting combo`() = runTest {
        val saleId = "edit-totals-delete"
        val comboId = "combo-del-totals"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1,
            comboId = comboId
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            comboId = comboId
        )
        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = comboId,
            saleId = saleId,
            precioLista = 5000.0,
            precioCortoplazo = 4500.0,
            precioContado = 3800.0
        )
        createInitialSale(
            saleId,
            products = listOf(p1, p2),
            combos = listOf(combo),
            precioTotal = 5000.0,
            montoACortoPlazo = 4500.0,
            montoDeContado = 3800.0
        )

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        // Delete combo — products become individual
        saleProductsVM.deleteCombo(comboId)

        persistEdit(saleId)

        val sale = saleDataSource.getSaleById(saleId)!!
        // Now both products are individual, prices come from their PRECIOS string
        // Default VALID_PRICES_STRING: lista=1500, corto=1200, contado=1000
        // p1: 1500*1 + p2: 1500*1 = 3000
        assertEquals(3000.0, sale.PRECIO_TOTAL, epsilon)
        assertEquals(2400.0, sale.MONTO_A_CORTO_PLAZO, epsilon) // 1200*2
        assertEquals(2000.0, sale.MONTO_DE_CONTADO, epsilon) // 1000*2
    }

    // ========================
    // CONTADO edit
    // ========================

    @Test
    fun `edit CONTADO - combo prices zeroed for lista and cortoplazo`() = runTest {
        val saleId = "edit-contado-combo"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            cantidad = 1
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1
        )
        createInitialSale(saleId, tipoVenta = "CONTADO", products = listOf(p1, p2))

        saleProductsVM.setTipoVenta("CONTADO")
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            emptyList(),
            camioneta
        )

        // Create combo in CONTADO mode
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.createCombo("Combo Contado", 5000.0, 4500.0, 3800.0)

        persistEdit(saleId, tipoVenta = "CONTADO")

        val dbCombo = comboDataSource.getCombosForSale(saleId)[0]
        assertEquals(0.0, dbCombo.PRECIO_LISTA, epsilon)
        assertEquals(0.0, dbCombo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, dbCombo.PRECIO_CONTADO, epsilon)

        val sale = saleDataSource.getSaleById(saleId)!!
        assertEquals(0.0, sale.PRECIO_TOTAL, epsilon)
        assertEquals(0.0, sale.MONTO_A_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, sale.MONTO_DE_CONTADO, epsilon)
    }

    // ========================
    // Edit marks sale as not sent
    // ========================

    @Test
    fun `edit - saved sale has enviado false`() = runTest {
        val saleId = "edit-enviado"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            cantidad = 1
        )
        createInitialSale(saleId, products = listOf(p1))

        // Mark as sent
        saleDataSource.changeSaleStatus(saleId, true)
        assertEquals(true, saleDataSource.getSaleById(saleId)!!.ENVIADO)

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            emptyList(),
            camioneta
        )

        persistEdit(saleId)

        // After edit, should be not sent
        assertEquals(false, saleDataSource.getSaleById(saleId)!!.ENVIADO)
    }

    // ========================
    // No-op edit (save without changes)
    // ========================

    @Test
    fun `edit - save without changes preserves all data`() = runTest {
        val saleId = "edit-noop"
        val comboId = "combo-noop"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 2,
            precioLista = 1500.0,
            precioCortoplazo = 1200.0,
            precioContado = 1000.0,
            comboId = comboId
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            precioLista = 2000.0,
            precioCortoplazo = 1700.0,
            precioContado = 1400.0,
            comboId = comboId
        )
        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = comboId,
            saleId = saleId,
            nombreCombo = "Combo Original",
            precioLista = 5000.0,
            precioCortoplazo = 4500.0,
            precioContado = 3800.0
        )
        createInitialSale(
            saleId,
            products = listOf(p1, p2),
            combos = listOf(combo),
            precioTotal = 5000.0,
            montoACortoPlazo = 4500.0,
            montoDeContado = 3800.0
        )

        // Restore and immediately save without changes
        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        persistEdit(saleId)

        // Verify products unchanged
        val products = productDataSource.getProductsForSale(saleId)
        assertEquals(2, products.size)
        products.forEach { assertNotNull(it.COMBO_ID) }

        // Verify combo unchanged
        val combos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, combos.size)
        assertEquals("Combo Original", combos[0].NOMBRE_COMBO)
        assertEquals(5000.0, combos[0].PRECIO_LISTA, epsilon)
        assertEquals(4500.0, combos[0].PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, combos[0].PRECIO_CONTADO, epsilon)
    }

    // ========================
    // Multiple combos edit
    // ========================

    @Test
    fun `edit - delete one combo keep another`() = runTest {
        val saleId = "edit-multi-combo"
        val combo1Id = "combo-keep"
        val combo2Id = "combo-delete"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1,
            comboId = combo1Id
        )
        val p2 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 2,
            articulo = "Base King",
            cantidad = 1,
            comboId = combo1Id
        )
        val p3 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 3,
            articulo = "Colchon Queen",
            cantidad = 1,
            comboId = combo2Id
        )
        val p4 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 4,
            articulo = "Base Queen",
            cantidad = 1,
            comboId = combo2Id
        )
        val combo1 = TestDataFactory.createLocalSaleComboEntity(
            comboId = combo1Id,
            saleId = saleId,
            nombreCombo = "Combo King",
            precioLista = 5000.0,
            precioCortoplazo = 4500.0,
            precioContado = 3800.0
        )
        val combo2 = TestDataFactory.createLocalSaleComboEntity(
            comboId = combo2Id,
            saleId = saleId,
            nombreCombo = "Combo Queen",
            precioLista = 4000.0,
            precioCortoplazo = 3500.0,
            precioContado = 3000.0
        )
        createInitialSale(
            saleId,
            products = listOf(p1, p2, p3, p4),
            combos = listOf(combo1, combo2)
        )

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King"),
            TestDataFactory.createProductInventory(id = 2, name = "Base King"),
            TestDataFactory.createProductInventory(id = 3, name = "Colchon Queen"),
            TestDataFactory.createProductInventory(id = 4, name = "Base Queen")
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            comboDataSource.getCombosForSale(saleId),
            camioneta
        )

        // Delete combo2, keep combo1
        saleProductsVM.deleteCombo(combo2Id)

        persistEdit(saleId)

        val combos = comboDataSource.getCombosForSale(saleId)
        assertEquals(1, combos.size)
        assertEquals("Combo King", combos[0].NOMBRE_COMBO)

        val products = productDataSource.getProductsForSale(saleId)
        assertEquals(4, products.size)
        val inCombo = products.filter { it.COMBO_ID != null }
        val individual = products.filter { it.COMBO_ID == null }
        assertEquals(2, inCombo.size)
        assertEquals(2, individual.size)

        // Totals: combo King (5000/4500/3800) + individual p3 + p4 (default prices each)
        val sale = saleDataSource.getSaleById(saleId)!!
        // p3: 1500, p4: 1500 + combo: 5000 = 8000
        assertEquals(8000.0, sale.PRECIO_TOTAL, epsilon)
    }

    // ========================
    // Change quantity during edit
    // ========================

    @Test
    fun `edit - change product quantity updates totals`() = runTest {
        val saleId = "edit-qty-change"
        val p1 = TestDataFactory.createLocalSaleProductEntity(
            saleId = saleId,
            articuloId = 1,
            articulo = "Colchon King",
            cantidad = 1
        )
        createInitialSale(saleId, products = listOf(p1))

        val camioneta = listOf(
            TestDataFactory.createProductInventory(id = 1, name = "Colchon King", stock = 10)
        )
        restoreIntoViewModel(
            productDataSource.getProductsForSale(saleId),
            emptyList(),
            camioneta
        )

        // Change quantity from 1 to 5
        saleProductsVM.updateQuantity(camioneta[0], 5)

        persistEdit(saleId)

        val product = productDataSource.getProductsForSale(saleId)[0]
        assertEquals(5, product.CANTIDAD)

        val sale = saleDataSource.getSaleById(saleId)!!
        // Default prices: lista=1500, corto=1200, contado=1000 * 5
        assertEquals(7500.0, sale.PRECIO_TOTAL, epsilon)
        assertEquals(6000.0, sale.MONTO_A_CORTO_PLAZO, epsilon)
        assertEquals(5000.0, sale.MONTO_DE_CONTADO, epsilon)
    }
}
