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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E2E tests for CREDITO sale creation flow.
 *
 * Tests the full pipeline: SaleProductsViewModel (in-memory prices)
 * → PriceParser → LocalSaleProductEntity → Room DB,
 * including credit-specific fields (parcialidad, enganche, frecPago,
 * diaCobranza, avalOResponsable, zona).
 *
 * Does NOT touch WorkManager, images, or network.
 */
class CreditoSaleE2ETest : RoomTestBase() {

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
     * Mirrors NewLocalSaleViewModel.createSaleWithImages:
     * takes SaleItems from SaleProductsViewModel, parses prices, persists to DB.
     * Includes credit-specific fields for CREDITO sales.
     */
    private suspend fun persistSale(
        saleId: String,
        tipoVenta: String = "CREDITO",
        parcialidad: Double = 500.0,
        enganche: Double? = 200.0,
        telefono: String = "5512345678",
        frecPago: String = "SEMANAL",
        diaCobranza: String = "LUNES",
        avalOResponsable: String? = "Maria Lopez",
        zonaClienteId: Int? = 1,
        zonaCliente: String? = "Zona Norte"
    ) {
        val saleEntity = TestDataFactory.createLocalSaleEntity(
            saleId = saleId,
            tipoVenta = tipoVenta,
            precioTotal = saleProductsVM.getTotalPrecioListaWithCombos(),
            montoACortoPlazo = saleProductsVM.getTotalMontoCortoPlazoWithCombos(),
            montoDeContado = saleProductsVM.getTotalMontoContadoWithCombos(),
            parcialidad = parcialidad,
            enganche = enganche,
            telefono = telefono,
            frecPago = frecPago,
            diaCobranza = diaCobranza,
            avalOResponsable = avalOResponsable,
            zonaClienteId = zonaClienteId,
            zonaCliente = zonaCliente
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
    // CREDITO individual products - prices
    // ========================

    @Test
    fun `CREDITO sale - individual products preserve all three prices`() = runTest {
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

        saleProductsVM.updateProductPrices(p1, 1800.0, 1500.0, 1200.0)
        saleProductsVM.updateProductPrices(p2, 2000.0, 1700.0, 1400.0)

        persistSale("e2e-credito-prices")

        // Verify sale header — all totals should be > 0
        val sale = saleDataSource.getSaleById("e2e-credito-prices")!!
        assertEquals("CREDITO", sale.TIPO_VENTA)
        assertTrue("precioTotal should be > 0", sale.PRECIO_TOTAL > 0)
        assertTrue("montoACortoPlazo should be > 0", sale.MONTO_A_CORTO_PLAZO > 0)
        assertTrue("montoDeContado should be > 0", sale.MONTO_DE_CONTADO > 0)

        // Verify product prices are preserved (not zeroed)
        val products = productDataSource.getProductsForSale("e2e-credito-prices")
        assertEquals(2, products.size)
        products.forEach { product ->
            assertTrue(
                "Product ${product.ARTICULO_ID} lista should be > 0",
                product.PRECIO_LISTA > 0
            )
            assertTrue(
                "Product ${product.ARTICULO_ID} cortoplazo should be > 0",
                product.PRECIO_CORTO_PLAZO > 0
            )
            assertTrue(
                "Product ${product.ARTICULO_ID} contado should be > 0",
                product.PRECIO_CONTADO > 0
            )
        }
    }

    @Test
    fun `CREDITO sale - product prices match exact user-entered values`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)
        saleProductsVM.updateProductPrices(product, 1800.0, 1500.0, 1200.0)

        persistSale("e2e-credito-exact")

        val dbProduct = productDataSource.getProductsForSale("e2e-credito-exact")[0]
        assertEquals(1800.0, dbProduct.PRECIO_LISTA, epsilon)
        assertEquals(1500.0, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(1200.0, dbProduct.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale - multiple quantity preserves per-unit prices`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            stock = 10,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 5)
        saleProductsVM.updateProductPrices(product, 1500.0, 1200.0, 900.0)

        persistSale("e2e-credito-qty")

        val dbProduct = productDataSource.getProductsForSale("e2e-credito-qty")[0]
        assertEquals(5, dbProduct.CANTIDAD)
        // Per-unit prices (not multiplied)
        assertEquals(1500.0, dbProduct.PRECIO_LISTA, epsilon)
        assertEquals(1200.0, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(900.0, dbProduct.PRECIO_CONTADO, epsilon)

        // Sale header totals reflect quantity
        val sale = saleDataSource.getSaleById("e2e-credito-qty")!!
        assertEquals(7500.0, sale.PRECIO_TOTAL, epsilon) // 1500 * 5
        assertEquals(6000.0, sale.MONTO_A_CORTO_PLAZO, epsilon) // 1200 * 5
        assertEquals(4500.0, sale.MONTO_DE_CONTADO, epsilon) // 900 * 5
    }

    @Test
    fun `CREDITO sale - header totals match sum of individual product prices times quantity`() =
        runTest {
            val p1 = TestDataFactory.createProductInventory(
                id = 1,
                prices = TestDataFactory.VALID_PRICES_STRING
            )
            val p2 = TestDataFactory.createProductInventory(
                id = 2,
                name = "Base King",
                prices = TestDataFactory.VALID_PRICES_STRING
            )
            val p3 = TestDataFactory.createProductInventory(
                id = 3,
                name = "Almohada",
                prices = TestDataFactory.VALID_PRICES_STRING
            )
            saleProductsVM.addProductToSale(p1, 2) // qty 2
            saleProductsVM.addProductToSale(p2, 1) // qty 1
            saleProductsVM.addProductToSale(p3, 3) // qty 3

            saleProductsVM.updateProductPrices(p1, 1000.0, 800.0, 600.0)
            saleProductsVM.updateProductPrices(p2, 2000.0, 1600.0, 1200.0)
            saleProductsVM.updateProductPrices(p3, 500.0, 400.0, 300.0)

            persistSale("e2e-credito-totals")

            val sale = saleDataSource.getSaleById("e2e-credito-totals")!!
            // p1: 1000*2=2000 + p2: 2000*1=2000 + p3: 500*3=1500 = 5500
            assertEquals(5500.0, sale.PRECIO_TOTAL, epsilon)
            // p1: 800*2=1600 + p2: 1600*1=1600 + p3: 400*3=1200 = 4400
            assertEquals(4400.0, sale.MONTO_A_CORTO_PLAZO, epsilon)
            // p1: 600*2=1200 + p2: 1200*1=1200 + p3: 300*3=900 = 3300
            assertEquals(3300.0, sale.MONTO_DE_CONTADO, epsilon)
        }

    // ========================
    // CREDITO with combos
    // ========================

    @Test
    fun `CREDITO sale - combo preserves all three prices`() = runTest {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.createCombo("Combo Recamara", 5000.0, 4500.0, 3800.0)

        persistSale("e2e-credito-combo")

        val combos = comboDataSource.getCombosForSale("e2e-credito-combo")
        assertEquals(1, combos.size)
        val combo = combos[0]
        assertEquals(5000.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(4500.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, combo.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale - updateComboPrices preserves all three prices`() = runTest {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        val comboId = saleProductsVM.createCombo("Combo", 5000.0, 4500.0, 3800.0)

        // Edit combo prices
        saleProductsVM.updateComboPrices(comboId, 6000.0, 5500.0, 4200.0)

        persistSale("e2e-credito-combo-edit")

        val combo = comboDataSource.getCombosForSale("e2e-credito-combo-edit")[0]
        assertEquals(6000.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(5500.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(4200.0, combo.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale - combo header totals include combo prices`() = runTest {
        val p1 = TestDataFactory.createProductInventory(id = 1)
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.createCombo("Combo Recamara", 5000.0, 4500.0, 3800.0)

        persistSale("e2e-credito-combo-totals")

        val sale = saleDataSource.getSaleById("e2e-credito-combo-totals")!!
        assertEquals(5000.0, sale.PRECIO_TOTAL, epsilon)
        assertEquals(4500.0, sale.MONTO_A_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, sale.MONTO_DE_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale - multiple combos all prices preserved`() = runTest {
        val p1 = TestDataFactory.createProductInventory(id = 1, name = "Colchon King")
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        val p3 = TestDataFactory.createProductInventory(id = 3, name = "Colchon Queen")
        val p4 = TestDataFactory.createProductInventory(id = 4, name = "Base Queen")
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.addProductToSale(p3, 1)
        saleProductsVM.addProductToSale(p4, 1)

        // Combo 1: p1 + p2
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.createCombo("Combo King", 5000.0, 4500.0, 3800.0)

        // Combo 2: p3 + p4
        saleProductsVM.toggleProductSelection(3)
        saleProductsVM.toggleProductSelection(4)
        saleProductsVM.createCombo("Combo Queen", 4000.0, 3500.0, 3000.0)

        persistSale("e2e-credito-multi-combo")

        val combos = comboDataSource.getCombosForSale("e2e-credito-multi-combo")
        assertEquals(2, combos.size)

        val comboKing = combos.find { it.NOMBRE_COMBO == "Combo King" }!!
        assertEquals(5000.0, comboKing.PRECIO_LISTA, epsilon)
        assertEquals(4500.0, comboKing.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, comboKing.PRECIO_CONTADO, epsilon)

        val comboQueen = combos.find { it.NOMBRE_COMBO == "Combo Queen" }!!
        assertEquals(4000.0, comboQueen.PRECIO_LISTA, epsilon)
        assertEquals(3500.0, comboQueen.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3000.0, comboQueen.PRECIO_CONTADO, epsilon)

        // Header totals = sum of both combos
        val sale = saleDataSource.getSaleById("e2e-credito-multi-combo")!!
        assertEquals(9000.0, sale.PRECIO_TOTAL, epsilon) // 5000 + 4000
        assertEquals(8000.0, sale.MONTO_A_CORTO_PLAZO, epsilon) // 4500 + 3500
        assertEquals(6800.0, sale.MONTO_DE_CONTADO, epsilon) // 3800 + 3000
    }

    // ========================
    // CREDITO mixed individual + combo
    // ========================

    @Test
    fun `CREDITO sale - mixed individual and combo products totals correct`() = runTest {
        val p1 = TestDataFactory.createProductInventory(id = 1, name = "Colchon King")
        val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
        val p3 = TestDataFactory.createProductInventory(
            id = 3,
            name = "Almohada",
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(p1, 1)
        saleProductsVM.addProductToSale(p2, 1)
        saleProductsVM.addProductToSale(p3, 2)

        // Create combo with p1 and p2
        saleProductsVM.toggleProductSelection(1)
        saleProductsVM.toggleProductSelection(2)
        saleProductsVM.createCombo("Combo Recamara", 5000.0, 4500.0, 3800.0)

        // Edit individual product price
        saleProductsVM.updateProductPrices(p3, 500.0, 400.0, 300.0)

        persistSale("e2e-credito-mixed")

        val sale = saleDataSource.getSaleById("e2e-credito-mixed")!!
        // individual p3: lista=500*2=1000, corto=400*2=800, contado=300*2=600
        // combo: lista=5000, corto=4500, contado=3800
        assertEquals(6000.0, sale.PRECIO_TOTAL, epsilon) // 1000 + 5000
        assertEquals(5300.0, sale.MONTO_A_CORTO_PLAZO, epsilon) // 800 + 4500
        assertEquals(4400.0, sale.MONTO_DE_CONTADO, epsilon) // 600 + 3800

        // Verify all 3 products persisted
        val products = productDataSource.getProductsForSale("e2e-credito-mixed")
        assertEquals(3, products.size)

        // Individual product (p3) keeps all three prices
        val individualProduct = products.find { it.COMBO_ID == null }!!
        assertEquals(500.0, individualProduct.PRECIO_LISTA, epsilon)
        assertEquals(400.0, individualProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(300.0, individualProduct.PRECIO_CONTADO, epsilon)

        // Combo products exist in product table
        val comboProducts = products.filter { it.COMBO_ID != null }
        assertEquals(2, comboProducts.size)

        // Combo entity preserves prices
        val combo = comboDataSource.getCombosForSale("e2e-credito-mixed")[0]
        assertEquals(5000.0, combo.PRECIO_LISTA, epsilon)
        assertEquals(4500.0, combo.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(3800.0, combo.PRECIO_CONTADO, epsilon)
    }

    // ========================
    // CREDITO credit-specific fields
    // ========================

    @Test
    fun `CREDITO sale - credit-specific fields persist correctly`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)

        persistSale(
            saleId = "e2e-credito-fields",
            parcialidad = 750.0,
            enganche = 500.0,
            telefono = "5598765432",
            frecPago = "QUINCENAL",
            diaCobranza = "MIERCOLES",
            avalOResponsable = "Pedro Garcia",
            zonaClienteId = 3,
            zonaCliente = "Zona Sur"
        )

        val sale = saleDataSource.getSaleById("e2e-credito-fields")!!
        assertEquals("CREDITO", sale.TIPO_VENTA)
        assertEquals(750.0, sale.PARCIALIDAD, epsilon)
        assertEquals(500.0, sale.ENGANCHE!!, epsilon)
        assertEquals("5598765432", sale.TELEFONO)
        assertEquals("QUINCENAL", sale.FREC_PAGO)
        assertEquals("MIERCOLES", sale.DIA_COBRANZA)
        assertEquals("Pedro Garcia", sale.AVAL_O_RESPONSABLE)
        assertEquals(3, sale.ZONA_CLIENTE_ID)
        assertEquals("Zona Sur", sale.ZONA_CLIENTE)
    }

    @Test
    fun `CREDITO sale - zero enganche persists correctly`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)

        persistSale(
            saleId = "e2e-credito-no-enganche",
            enganche = 0.0
        )

        val sale = saleDataSource.getSaleById("e2e-credito-no-enganche")!!
        assertEquals(0.0, sale.ENGANCHE!!, epsilon)
    }

    @Test
    fun `CREDITO sale - null enganche persists correctly`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)

        persistSale(
            saleId = "e2e-credito-null-enganche",
            enganche = null
        )

        val sale = saleDataSource.getSaleById("e2e-credito-null-enganche")!!
        assertEquals(null, sale.ENGANCHE)
    }

    @Test
    fun `CREDITO sale - zona persists with id and name`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)

        persistSale(
            saleId = "e2e-credito-zona",
            zonaClienteId = 5,
            zonaCliente = "Zona Poniente"
        )

        val sale = saleDataSource.getSaleById("e2e-credito-zona")!!
        assertEquals(5, sale.ZONA_CLIENTE_ID)
        assertEquals("Zona Poniente", sale.ZONA_CLIENTE)
    }

    // ========================
    // CREDITO price editing
    // ========================

    @Test
    fun `CREDITO sale - editing product prices updates persisted values`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)

        // Set initial prices
        saleProductsVM.updateProductPrices(product, 1500.0, 1200.0, 1000.0)
        // Edit prices again
        saleProductsVM.updateProductPrices(product, 1800.0, 1400.0, 1100.0)

        persistSale("e2e-credito-edit")

        val dbProduct = productDataSource.getProductsForSale("e2e-credito-edit")[0]
        assertEquals(1800.0, dbProduct.PRECIO_LISTA, epsilon)
        assertEquals(1400.0, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(1100.0, dbProduct.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale - default prices from PRECIOS string when no updateProductPrices`() =
        runTest {
            val product = TestDataFactory.createProductInventory(
                id = 1,
                prices = TestDataFactory.VALID_PRICES_STRING
            )
            saleProductsVM.addProductToSale(product, 1)
            // Do NOT call updateProductPrices — use parsed defaults

            persistSale("e2e-credito-defaults")

            val dbProduct = productDataSource.getProductsForSale("e2e-credito-defaults")[0]
            // VALID_PRICES_STRING = "Precio de lista:1500.0, Precio 4 Meses:1200.0, Precio 1 Meses:1000.0"
            assertEquals(1500.0, dbProduct.PRECIO_LISTA, epsilon)
            assertEquals(1200.0, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
            assertEquals(1000.0, dbProduct.PRECIO_CONTADO, epsilon)
        }

    // ========================
    // CREDITO sale header metadata
    // ========================

    @Test
    fun `CREDITO sale - sale header has correct tipoVenta`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)

        persistSale("e2e-credito-tipo")

        val sale = saleDataSource.getSaleById("e2e-credito-tipo")!!
        assertEquals("CREDITO", sale.TIPO_VENTA)
    }

    @Test
    fun `CREDITO sale - sale header has client and address info`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)

        persistSale("e2e-credito-client")

        val sale = saleDataSource.getSaleById("e2e-credito-client")!!
        assertEquals("Juan Perez", sale.NOMBRE_CLIENTE)
        assertEquals("Calle Principal 123", sale.DIRECCION)
        assertTrue(sale.LATITUD != 0.0)
        assertTrue(sale.LONGITUD != 0.0)
    }

    @Test
    fun `CREDITO sale - new sale starts with enviado false`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)

        persistSale("e2e-credito-enviado")

        val sale = saleDataSource.getSaleById("e2e-credito-enviado")!!
        assertEquals(false, sale.ENVIADO)
    }

    // ========================
    // CREDITO edge cases
    // ========================

    @Test
    fun `CREDITO sale - single product with quantity 1 totals equal per-unit prices`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)
        saleProductsVM.updateProductPrices(product, 2000.0, 1500.0, 1000.0)

        persistSale("e2e-credito-single")

        val sale = saleDataSource.getSaleById("e2e-credito-single")!!
        assertEquals(2000.0, sale.PRECIO_TOTAL, epsilon)
        assertEquals(1500.0, sale.MONTO_A_CORTO_PLAZO, epsilon)
        assertEquals(1000.0, sale.MONTO_DE_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale - product with null PRECIOS string uses zero defaults`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = null
        )
        saleProductsVM.addProductToSale(product, 1)

        persistSale("e2e-credito-null-prices")

        val dbProduct = productDataSource.getProductsForSale("e2e-credito-null-prices")[0]
        assertEquals(0.0, dbProduct.PRECIO_LISTA, epsilon)
        assertEquals(0.0, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(0.0, dbProduct.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale - combo products and individual products are isolatable by COMBO_ID`() =
        runTest {
            val p1 = TestDataFactory.createProductInventory(id = 1, name = "Colchon King")
            val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")
            val p3 = TestDataFactory.createProductInventory(
                id = 3,
                name = "Almohada",
                prices = TestDataFactory.VALID_PRICES_STRING
            )
            saleProductsVM.addProductToSale(p1, 1)
            saleProductsVM.addProductToSale(p2, 1)
            saleProductsVM.addProductToSale(p3, 1)

            saleProductsVM.toggleProductSelection(1)
            saleProductsVM.toggleProductSelection(2)
            val comboId = saleProductsVM.createCombo("Combo Recamara", 5000.0, 4500.0, 3800.0)

            persistSale("e2e-credito-isolation")

            val products = productDataSource.getProductsForSale("e2e-credito-isolation")
            assertEquals(3, products.size)

            // Individual products have null COMBO_ID
            val individualProducts = products.filter { it.COMBO_ID == null }
            assertEquals(1, individualProducts.size)
            assertEquals("Almohada", individualProducts[0].ARTICULO)

            // Combo products have the combo's ID
            val comboProducts = products.filter { it.COMBO_ID != null }
            assertEquals(2, comboProducts.size)
            comboProducts.forEach { product ->
                assertNotNull(product.COMBO_ID)
            }
        }

    @Test
    fun `CREDITO sale - large price values persist correctly`() = runTest {
        val product = TestDataFactory.createProductInventory(
            id = 1,
            prices = TestDataFactory.VALID_PRICES_STRING
        )
        saleProductsVM.addProductToSale(product, 1)
        saleProductsVM.updateProductPrices(product, 99999.99, 88888.88, 77777.77)

        persistSale("e2e-credito-large")

        val dbProduct = productDataSource.getProductsForSale("e2e-credito-large")[0]
        assertEquals(99999.99, dbProduct.PRECIO_LISTA, epsilon)
        assertEquals(88888.88, dbProduct.PRECIO_CORTO_PLAZO, epsilon)
        assertEquals(77777.77, dbProduct.PRECIO_CONTADO, epsilon)
    }

    @Test
    fun `CREDITO sale - price hierarchy lista greater than cortoplazo greater than contado`() =
        runTest {
            val product = TestDataFactory.createProductInventory(
                id = 1,
                prices = TestDataFactory.VALID_PRICES_STRING
            )
            saleProductsVM.addProductToSale(product, 1)
            saleProductsVM.updateProductPrices(product, 3000.0, 2500.0, 2000.0)

            persistSale("e2e-credito-hierarchy")

            val dbProduct = productDataSource.getProductsForSale("e2e-credito-hierarchy")[0]
            assertTrue(
                "lista >= cortoplazo",
                dbProduct.PRECIO_LISTA >= dbProduct.PRECIO_CORTO_PLAZO
            )
            assertTrue(
                "cortoplazo >= contado",
                dbProduct.PRECIO_CORTO_PLAZO >= dbProduct.PRECIO_CONTADO
            )
        }

    // ========================
    // CREDITO vs CONTADO contrast
    // ========================

    @Test
    fun `same products different tipoVenta - CREDITO preserves all, CONTADO zeroes lista and cortoplazo`() =
        runTest {
            // Set up identical products
            val product = TestDataFactory.createProductInventory(
                id = 1,
                prices = TestDataFactory.VALID_PRICES_STRING
            )

            // CREDITO sale
            saleProductsVM.addProductToSale(product, 1)
            saleProductsVM.updateProductPrices(product, 1800.0, 1500.0, 1200.0)
            persistSale("e2e-contrast-credito")

            // Reset VM for CONTADO sale
            saleProductsVM = SaleProductsViewModel()
            saleProductsVM.setTipoVenta("CONTADO")
            saleProductsVM.addProductToSale(product, 1)
            saleProductsVM.updateProductPrices(product, 1800.0, 1500.0, 1200.0)
            persistSale("e2e-contrast-contado", tipoVenta = "CONTADO")

            // CREDITO: all prices preserved
            val creditoProduct = productDataSource.getProductsForSale("e2e-contrast-credito")[0]
            assertEquals(1800.0, creditoProduct.PRECIO_LISTA, epsilon)
            assertEquals(1500.0, creditoProduct.PRECIO_CORTO_PLAZO, epsilon)
            assertEquals(1200.0, creditoProduct.PRECIO_CONTADO, epsilon)

            // CONTADO: lista and cortoplazo zeroed
            val contadoProduct = productDataSource.getProductsForSale("e2e-contrast-contado")[0]
            assertEquals(0.0, contadoProduct.PRECIO_LISTA, epsilon)
            assertEquals(0.0, contadoProduct.PRECIO_CORTO_PLAZO, epsilon)
            assertEquals(1200.0, contadoProduct.PRECIO_CONTADO, epsilon)
        }

    @Test
    fun `same combo different tipoVenta - CREDITO preserves all, CONTADO zeroes lista and cortoplazo`() =
        runTest {
            val p1 = TestDataFactory.createProductInventory(id = 1)
            val p2 = TestDataFactory.createProductInventory(id = 2, name = "Base King")

            // CREDITO combo
            saleProductsVM.addProductToSale(p1, 1)
            saleProductsVM.addProductToSale(p2, 1)
            saleProductsVM.toggleProductSelection(1)
            saleProductsVM.toggleProductSelection(2)
            saleProductsVM.createCombo("Combo", 5000.0, 4500.0, 3800.0)
            persistSale("e2e-combo-credito")

            // Reset VM for CONTADO
            saleProductsVM = SaleProductsViewModel()
            saleProductsVM.setTipoVenta("CONTADO")
            saleProductsVM.addProductToSale(p1, 1)
            saleProductsVM.addProductToSale(p2, 1)
            saleProductsVM.toggleProductSelection(1)
            saleProductsVM.toggleProductSelection(2)
            saleProductsVM.createCombo("Combo", 5000.0, 4500.0, 3800.0)
            persistSale("e2e-combo-contado", tipoVenta = "CONTADO")

            // CREDITO: all combo prices preserved
            val creditoCombo = comboDataSource.getCombosForSale("e2e-combo-credito")[0]
            assertEquals(5000.0, creditoCombo.PRECIO_LISTA, epsilon)
            assertEquals(4500.0, creditoCombo.PRECIO_CORTO_PLAZO, epsilon)
            assertEquals(3800.0, creditoCombo.PRECIO_CONTADO, epsilon)

            // CONTADO: lista and cortoplazo zeroed
            val contadoCombo = comboDataSource.getCombosForSale("e2e-combo-contado")[0]
            assertEquals(0.0, contadoCombo.PRECIO_LISTA, epsilon)
            assertEquals(0.0, contadoCombo.PRECIO_CORTO_PLAZO, epsilon)
            assertEquals(3800.0, contadoCombo.PRECIO_CONTADO, epsilon)
        }
}
