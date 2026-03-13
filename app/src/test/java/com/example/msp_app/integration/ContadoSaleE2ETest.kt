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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E2E tests for CONTADO sale creation flow.
 *
 * Tests the full pipeline: SaleProductsViewModel (in-memory prices)
 * → PriceParser → LocalSaleProductEntity → Room DB,
 * without touching WorkManager, images, or network.
 */
class ContadoSaleE2ETest : RoomTestBase() {

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
     * Simulates what NewLocalSaleViewModel.createSaleWithImages does:
     * takes SaleItems from SaleProductsViewModel, parses prices, persists to DB.
     */
    private suspend fun persistSale(saleId: String, tipoVenta: String = "CONTADO") {
        val saleEntity = TestDataFactory.createLocalSaleEntity(
            saleId = saleId,
            tipoVenta = tipoVenta,
            precioTotal = saleProductsVM.getTotalPrecioListaWithCombos(),
            montoACortoPlazo = saleProductsVM.getTotalMontoCortoPlazoWithCombos(),
            montoDeContado = saleProductsVM.getTotalMontoContadoWithCombos()
        )
        saleDataSource.insertSale(saleEntity)

        // Mirror createSaleWithImages product mapping (line 317-331)
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

        // Mirror combo mapping from NewSaleScreen (line 234-248)
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
        if (comboEntities.isNotEmpty()) {
            comboDataSource.insertCombos(comboEntities)
        }
    }

    // ========================
    // CONTADO individual products
    // ========================

    @Test
    fun `CONTADO sale - individual products persist with zeroed lista and cortoplazo`() = runTest {
        saleProductsVM.setTipoVenta("CONTADO")
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        val p2 = TestDataFactory.createProductInventory(
            id = 2,
            name = "Base King",
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(p1, 2)
        saleProductsVM.addProductToSale(p2, 1)

        // User edits prices while in CONTADO mode
        saleProductsVM.updateProductPrices(p1, 1800.0, 1500.0, 1200.0)
        saleProductsVM.updateProductPrices(p2, 2000.0, 1700.0, 1400.0)

        persistSale("e2e-contado-1")

        // Verify sale header
        val sale = saleDataSource.getSaleById("e2e-contado-1")!!
        assertEquals("CONTADO", sale.TIPO_VENTA)
        assertEquals(0.0, sale.PRECIO_TOTAL, epsilon) // lista total = 0
        assertEquals(0.0, sale.MONTO_A_CORTO_PLAZO, epsilon) // cortoplazo total = 0
        assertTrue(sale.MONTO_DE_CONTADO > 0) // contado total > 0

        // Verify product prices
        val products = productDataSource.getProductsForSale("e2e-contado-1")
        assertEquals(2, products.size)
        products.forEach { product ->
            assertEquals(
                "Product ${product.ARTICULO_ID} lista should be 0",
                0.0,
                product.PRECIO_LISTA,
                epsilon
            )
            assertEquals(
                "Product ${product.ARTICULO_ID} cortoplazo should be 0",
                0.0,
                product.PRECIO_CORTO_PLAZO,
                epsilon
            )
            assertTrue(
                "Product ${product.ARTICULO_ID} contado should be > 0",
                product.PRECIO_CONTADO > 0
            )
        }
    }

    @Test
    fun `CONTADO sale - product contado prices match what user entered`() = runTest {
        saleProductsVM.setTipoVenta("CONTADO")
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)
        saleProductsVM.updateProductPrices(product, 9999.0, 8888.0, 750.0)

        persistSale("e2e-contado-price")

        val dbProduct = productDataSource.getProductsForSale("e2e-contado-price")[0]
        assertEquals(750.0, dbProduct.PRECIO_CONTADO, epsilon)
        assertEquals(0.0, dbProduct.PRECIO_LISTA, epsilon)
        assertEquals(0.0, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
    }

    // ========================
    // CONTADO with combos
    // ========================

    @Test
    fun `CONTADO sale - combo prices persist with zeroed lista and cortoplazo`() = runTest {
        saleProductsVM.setTipoVenta("CONTADO")
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)

        val comboId = saleProductsVM.createCombo("Combo Recamara", 5000.0, 4500.0, 3800.0)

        persistSale("e2e-contado-combo")

        // Verify combo in DB
        val combos = comboDataSource.getCombosForSale("e2e-contado-combo")
        assertEquals(1, combos.size)
        val combo = combos[0]
        assertEquals(0.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(0.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, combo.PRECIO_CONTADO, epsilon)

        // Verify sale header totals
        val sale = saleDataSource.getSaleById("e2e-contado-combo")!!
        assertEquals(0.0, sale.PRECIO_TOTAL, epsilon)
        assertEquals(0.0, sale.MONTO_A_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, sale.MONTO_DE_CONTADO, epsilon)
    }

    @Test
    fun `CONTADO sale - mixed individual and combo products`() = runTest {
        saleProductsVM.setTipoVenta("CONTADO")

        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        val p3 = TestDataFactory.createProductInventory(id = 3, name = "Almohada")
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.addProductToSale(p3, 2)

        // Create combo with p1 and p2
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.createCombo("Combo Recamara", 5000.0, 4500.0, 3800.0)

        // Edit individual product price
        saleProductsVM.updateProductPrices(p3, 500.0, 400.0, 300.0)

        persistSale("e2e-contado-mixed")

        val sale = saleDataSource.getSaleById("e2e-contado-mixed")!!
        assertEquals(0.0, sale.PRECIO_TOTAL, epsilon)
        assertEquals(0.0, sale.MONTO_A_CORTO_PLAZO, epsilon)

        // p3 individual contado (300*2=600 from getTotalMontoContadoWithCombos?
        // Actually, updateProductPrices zeroes lista/corto, keeps contado at 300
        // getTotalMontoContadoWithCombos = individual(p3: 300*2=600) + combo(3800) = 4400
        // Wait - p3 quantity is 2 but PriceParser works per-unit, and the product entity
        // stores per-unit price. The total is calculated differently...
        // Actually look at the persistSale: it calls getTotalMontoContadoWithCombos()
        // which sums individual contado (parsed price * quantity) + combo contado prices
        // For p3: PriceParser would parse the updated PRECIOS string where contado=300
        // times quantity 2 = 600, plus combo 3800 = 4400
        assertEquals(4400.0, sale.MONTO_DE_CONTADO, epsilon)

        // Verify individual product (p3) has zeroed lista/cortoplazo
        val products = productDataSource.getProductsForSale("e2e-contado-mixed")
        assertEquals(3, products.size)
        val individualProduct = products.find { it.COMBO_ID == null }!!
        assertEquals(0.0, individualProduct.PRECIO_LISTA, epsilon)
        assertEquals(0.0, individualProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(300.0, individualProduct.PRECIO_CONTADO, epsilon)

        // Combo products retain their original prices in the product rows
        // (the combo entity holds the billing price, not individual product rows)
        val comboProducts = products.filter { it.COMBO_ID != null }
        assertEquals(2, comboProducts.size)

        // Verify combo
        val combo = comboDataSource.getCombosForSale("e2e-contado-mixed")[0]
        assertEquals(0.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(0.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, combo.PRECIO_CONTADO, epsilon)
    }

    // ========================
    // CREDITO contrast
    // ========================

    @Test
    fun `CREDITO sale - all three prices are preserved`() = runTest {
        // Default is CREDITO
        val p1 = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(p1, 2)
        saleProductsVM.updateProductPrices(p1, 1800.0, 1500.0, 1200.0)

        persistSale("e2e-credito", tipoVenta = "CREDITO")

        val sale = saleDataSource.getSaleById("e2e-credito")!!
        assertTrue(sale.PRECIO_TOTAL > 0)
        assertTrue(sale.MONTO_A_CORTO_PLAZO > 0)
        assertTrue(sale.MONTO_DE_CONTADO > 0)

        val product = productDataSource.getProductsForSale("e2e-credito")[0]
        assertEquals(1800.0, product.PRECIO_LISTA, epsilon)
        assertEquals(1500.0, product.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(1200.0, product.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale with combo preserves all prices`() = runTest {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.createCombo("Combo Recamara", 5000.0, 4500.0, 3800.0)

        persistSale("e2e-credito-combo", tipoVenta = "CREDITO")

        val combo = comboDataSource.getCombosForSale("e2e-credito-combo")[0]
        assertEquals(5000.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(4500.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, combo.PRECIO_CONTADO, epsilon)
    }

    // ========================
    // Edge cases
    // ========================

    @Test
    fun `CONTADO sale - updateComboPrices also zeroes lista and cortoplazo`() = runTest {
        saleProductsVM.setTipoVenta("CONTADO")
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        val comboId = saleProductsVM.createCombo("Combo", 5000.0, 4500.0, 3800.0)

        // User edits combo price while in CONTADO
        saleProductsVM.updateComboPrices(comboId, 6000.0, 5500.0, 4200.0)

        persistSale("e2e-contado-edit-combo")

        val combo = comboDataSource.getCombosForSale("e2e-contado-edit-combo")[0]
        assertEquals(0.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(0.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(4200.0, combo.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `CONTADO sale - multiple quantity product persists per-unit price correctly`() = runTest {
        saleProductsVM.setTipoVenta("CONTADO")
        val product = TestDataFactory.createProductInventory(
            id = 1,
            stock = 10,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 5)
        saleProductsVM.updateProductPrices(product, 1500.0, 1200.0, 900.0)

        persistSale("e2e-contado-qty")

        val dbProduct = productDataSource.getProductsForSale("e2e-contado-qty")[0]
        assertEquals(5, dbProduct.CANTIDAD)
        // Per-unit prices (not multiplied by quantity)
        assertEquals(0.0, dbProduct.PRECIO_LISTA, epsilon)
        assertEquals(0.0, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(900.0, dbProduct.PRECIO_CONTADO, epsilon)

        // Sale header total should reflect quantity
        val sale = saleDataSource.getSaleById("e2e-contado-qty")!!
        assertEquals(4500.0, sale.MONTO_DE_CONTADO, epsilon) // 900 * 5
    }
}
