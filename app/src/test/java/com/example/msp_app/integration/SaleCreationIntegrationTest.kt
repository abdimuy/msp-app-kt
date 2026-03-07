package com.example.msp_app.integration

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.data.local.datasource.sale.ComboLocalDataSource
import com.example.msp_app.data.local.datasource.sale.LocalSaleDataSource
import com.example.msp_app.data.local.datasource.sale.SaleProductLocalDataSource
import com.example.msp_app.data.local.entities.LocalSaleProductEntity
import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import com.example.msp_app.utils.PriceParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaleCreationIntegrationTest : RoomTestBase() {

    private lateinit var saleDataSource: LocalSaleDataSource
    private lateinit var productDataSource: SaleProductLocalDataSource
    private lateinit var comboDataSource: ComboLocalDataSource

    @Before
    fun setUpDataSources() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        saleDataSource = LocalSaleDataSource(context)
        productDataSource = SaleProductLocalDataSource(context)
        comboDataSource = ComboLocalDataSource(context)
    }

    // ========================
    // CREDITO sale flows
    // ========================

    @Test
    fun `full CREDITO sale with products and combos`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-1", tipoVenta = "CREDITO")
        saleDataSource.insertSale(sale)

        val products = listOf(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-1",
                articuloId = 100,
                comboId = "combo-1"
            ),
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-1",
                articuloId = 101,
                comboId = "combo-1"
            ),
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 102)
        )
        productDataSource.insertSaleProducts(products)

        val combo = TestDataFactory.createLocalSaleComboEntity(
            comboId = "combo-1",
            saleId = "sale-1"
        )
        comboDataSource.insertCombo(combo)

        val retrievedSale = saleDataSource.getSaleById("sale-1")
        assertNotNull(retrievedSale)
        assertEquals("CREDITO", retrievedSale!!.TIPO_VENTA)

        val retrievedProducts = productDataSource.getProductsForSale("sale-1")
        assertEquals(3, retrievedProducts.size)

        val retrievedCombos = comboDataSource.getCombosForSale("sale-1")
        assertEquals(1, retrievedCombos.size)
    }

    @Test
    fun `CREDITO sale persists all credit-specific fields`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(
            saleId = "cred-fields",
            tipoVenta = "CREDITO",
            parcialidad = 750.0,
            enganche = 500.0,
            frecPago = "SEMANAL",
            diaCobranza = "LUNES",
            telefono = "5512345678",
            avalOResponsable = "Maria Lopez",
            zonaClienteId = 3,
            zonaCliente = "Zona Sur"
        )
        saleDataSource.insertSale(sale)

        val retrieved = saleDataSource.getSaleById("cred-fields")!!
        assertEquals(750.0, retrieved.PARCIALIDAD, 0.001)
        assertEquals(500.0, retrieved.ENGANCHE!!, 0.001)
        assertEquals("SEMANAL", retrieved.FREC_PAGO)
        assertEquals("LUNES", retrieved.DIA_COBRANZA)
        assertEquals("5512345678", retrieved.TELEFONO)
        assertEquals("Maria Lopez", retrieved.AVAL_O_RESPONSABLE)
        assertEquals(3, retrieved.ZONA_CLIENTE_ID)
        assertEquals("Zona Sur", retrieved.ZONA_CLIENTE)
    }

    @Test
    fun `CREDITO sale with zone info required`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(
            saleId = "cred-zone",
            tipoVenta = "CREDITO",
            zonaClienteId = 5,
            zonaCliente = "Zona Centro"
        )
        saleDataSource.insertSale(sale)

        val retrieved = saleDataSource.getSaleById("cred-zone")!!
        assertEquals(5, retrieved.ZONA_CLIENTE_ID)
        assertEquals("Zona Centro", retrieved.ZONA_CLIENTE)
    }

    // ========================
    // CONTADO sale flows
    // ========================

    @Test
    fun `full CONTADO sale has zeroed credit fields`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(
            saleId = "sale-contado",
            tipoVenta = "CONTADO",
            parcialidad = 0.0,
            enganche = 0.0,
            frecPago = "",
            diaCobranza = "",
            avalOResponsable = null
        )
        saleDataSource.insertSale(sale)

        val retrieved = saleDataSource.getSaleById("sale-contado")!!
        assertEquals("CONTADO", retrieved.TIPO_VENTA)
        assertEquals(0.0, retrieved.PARCIALIDAD, 0.001)
        assertEquals(0.0, retrieved.ENGANCHE!!, 0.001)
        assertEquals("", retrieved.FREC_PAGO)
        assertEquals("", retrieved.DIA_COBRANZA)
        assertNull(retrieved.AVAL_O_RESPONSABLE)
    }

    @Test
    fun `CONTADO sale with products only (no combos)`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(
            saleId = "contado-prod",
            tipoVenta = "CONTADO"
        )
        saleDataSource.insertSale(sale)

        productDataSource.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "contado-prod",
                    articuloId = 200,
                    precioContado = 800.0
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "contado-prod",
                    articuloId = 201,
                    precioContado = 1200.0
                )
            )
        )

        val products = productDataSource.getProductsForSale("contado-prod")
        assertEquals(2, products.size)
        val totalContado = products.sumOf { it.PRECIO_CONTADO }
        assertEquals(2000.0, totalContado, 0.001)

        val combos = comboDataSource.getCombosForSale("contado-prod")
        assertTrue(combos.isEmpty())
    }

    // ========================
    // Product management
    // ========================

    @Test
    fun `multiple products with different prices`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-prices")
        saleDataSource.insertSale(sale)

        val products = listOf(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-prices",
                articuloId = 100,
                precioLista = 1500.0,
                precioCortoplazo = 1200.0,
                precioContado = 1000.0
            ),
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-prices",
                articuloId = 101,
                precioLista = 2000.0,
                precioCortoplazo = 1800.0,
                precioContado = 1500.0
            ),
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-prices",
                articuloId = 102,
                precioLista = 800.0,
                precioCortoplazo = 700.0,
                precioContado = 600.0
            )
        )
        productDataSource.insertSaleProducts(products)

        val retrieved = productDataSource.getProductsForSale("sale-prices")
        assertEquals(3, retrieved.size)
        assertEquals(4300.0, retrieved.sumOf { it.PRECIO_LISTA }, 0.001)
        assertEquals(3700.0, retrieved.sumOf { it.PRECIO_CORTO_PLAZO }, 0.001)
        assertEquals(3100.0, retrieved.sumOf { it.PRECIO_CONTADO }, 0.001)
    }

    @Test
    fun `products with quantities persist correctly`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-qty")
        saleDataSource.insertSale(sale)

        productDataSource.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-qty",
                    articuloId = 100,
                    cantidad = 3
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-qty",
                    articuloId = 101,
                    cantidad = 1
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-qty",
                    articuloId = 102,
                    cantidad = 5
                )
            )
        )

        val products = productDataSource.getProductsForSale("sale-qty")
        assertEquals(9, products.sumOf { it.CANTIDAD })
    }

    @Test
    fun `PriceParser parsed prices match stored product prices`() = runTest {
        val priceString = TestDataFactory.VALID_PRICES_STRING
        val parsed = PriceParser.parsePricesFromString(priceString)

        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-parse")
        saleDataSource.insertSale(sale)

        productDataSource.insertSaleProduct(
            LocalSaleProductEntity(
                LOCAL_SALE_ID = "sale-parse",
                ARTICULO_ID = 100,
                ARTICULO = "Colchon",
                CANTIDAD = 2,
                PRECIO_LISTA = parsed.precioLista,
                PRECIO_CORTO_PLAZO = parsed.precioCortoplazo,
                PRECIO_CONTADO = parsed.precioContado
            )
        )

        val product = productDataSource.getProductsForSale("sale-parse")[0]
        assertEquals(1500.0, product.PRECIO_LISTA, 0.001)
        assertEquals(1200.0, product.PRECIO_CORTO_PLAZO, 0.001)
        assertEquals(1000.0, product.PRECIO_CONTADO, 0.001)
    }

    @Test
    fun `same product cannot be duplicated in same sale (REPLACE)`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-dup")
        saleDataSource.insertSale(sale)

        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-dup",
                articuloId = 100,
                cantidad = 2
            )
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-dup",
                articuloId = 100,
                cantidad = 5
            )
        )

        val products = productDataSource.getProductsForSale("sale-dup")
        assertEquals(1, products.size)
        assertEquals(5, products[0].CANTIDAD)
    }

    @Test
    fun `same product in different sales are independent`() = runTest {
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-a"))
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-b"))

        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-a",
                articuloId = 100,
                cantidad = 2
            )
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-b",
                articuloId = 100,
                cantidad = 7
            )
        )

        assertEquals(2, productDataSource.getProductsForSale("sale-a")[0].CANTIDAD)
        assertEquals(7, productDataSource.getProductsForSale("sale-b")[0].CANTIDAD)
    }

    @Test
    fun `delete products for one sale does not affect other sales`() = runTest {
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-del-a"))
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-del-b"))

        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-del-a", articuloId = 100)
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-del-b", articuloId = 100)
        )

        productDataSource.deleteProductsForSale("sale-del-a")

        assertTrue(productDataSource.getProductsForSale("sale-del-a").isEmpty())
        assertEquals(1, productDataSource.getProductsForSale("sale-del-b").size)
    }

    // ========================
    // Combo management
    // ========================

    @Test
    fun `combo products linked via COMBO_ID`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-combo-link")
        saleDataSource.insertSale(sale)

        productDataSource.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-combo-link",
                    articuloId = 100,
                    comboId = "combo-1"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-combo-link",
                    articuloId = 101,
                    comboId = "combo-1"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-combo-link",
                    articuloId = 102
                )
            )
        )

        val products = productDataSource.getProductsForSale("sale-combo-link")
        val comboProducts = products.filter { it.COMBO_ID == "combo-1" }
        val individualProducts = products.filter { it.COMBO_ID == null }
        assertEquals(2, comboProducts.size)
        assertEquals(1, individualProducts.size)
    }

    @Test
    fun `multiple combos in one sale`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-multi-combo")
        saleDataSource.insertSale(sale)

        comboDataSource.insertCombos(
            listOf(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "combo-a",
                    saleId = "sale-multi-combo",
                    nombreCombo = "Combo Recamara",
                    precioLista = 8000.0
                ),
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "combo-b",
                    saleId = "sale-multi-combo",
                    nombreCombo = "Combo Sala",
                    precioLista = 6000.0
                )
            )
        )

        productDataSource.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-multi-combo",
                    articuloId = 100,
                    comboId = "combo-a"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-multi-combo",
                    articuloId = 101,
                    comboId = "combo-a"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-multi-combo",
                    articuloId = 102,
                    comboId = "combo-b"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-multi-combo",
                    articuloId = 103,
                    comboId = "combo-b"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-multi-combo",
                    articuloId = 104
                )
            )
        )

        val combos = comboDataSource.getCombosForSale("sale-multi-combo")
        assertEquals(2, combos.size)

        val products = productDataSource.getProductsForSale("sale-multi-combo")
        assertEquals(2, products.count { it.COMBO_ID == "combo-a" })
        assertEquals(2, products.count { it.COMBO_ID == "combo-b" })
        assertEquals(1, products.count { it.COMBO_ID == null })
    }

    @Test
    fun `combo prices are independent from product prices`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-combo-price")
        saleDataSource.insertSale(sale)

        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(
                comboId = "combo-price",
                saleId = "sale-combo-price",
                precioLista = 5000.0,
                precioCortoplazo = 4500.0,
                precioContado = 4000.0
            )
        )

        productDataSource.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-combo-price",
                    articuloId = 100,
                    precioLista = 3000.0,
                    comboId = "combo-price"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-combo-price",
                    articuloId = 101,
                    precioLista = 3000.0,
                    comboId = "combo-price"
                )
            )
        )

        val combo = comboDataSource.getCombosForSale("sale-combo-price")[0]
        val productTotal = productDataSource.getProductsForSale("sale-combo-price").sumOf {
            it.PRECIO_LISTA
        }

        // Combo price (5000) is less than sum of products (6000) - discount applied
        assertEquals(5000.0, combo.PRECIO_LISTA, 0.001)
        assertEquals(6000.0, productTotal, 0.001)
        assertTrue(combo.PRECIO_LISTA < productTotal)
    }

    @Test
    fun `delete combos does not delete products`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-del-combo")
        saleDataSource.insertSale(sale)

        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "c1", saleId = "sale-del-combo")
        )

        productDataSource.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-del-combo",
                    articuloId = 100,
                    comboId = "c1"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-del-combo",
                    articuloId = 101,
                    comboId = "c1"
                )
            )
        )

        comboDataSource.deleteCombosForSale("sale-del-combo")

        assertTrue(comboDataSource.getCombosForSale("sale-del-combo").isEmpty())
        // Products still exist, just their COMBO_ID still references the deleted combo
        assertEquals(2, productDataSource.getProductsForSale("sale-del-combo").size)
    }

    // ========================
    // Image management
    // ========================

    @Test
    fun `sale with images via FK`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-img")
        val images = listOf(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-img"),
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-img")
        )
        saleDataSource.insertSaleWithImages(sale, images)

        val retrievedImages = saleDataSource.getImagesForSale("sale-img")
        assertEquals(2, retrievedImages.size)
    }

    @Test
    fun `add images incrementally to existing sale`() = runTest {
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-inc-img"))
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-inc-img")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-inc-img")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-3", saleId = "sale-inc-img")
        )

        assertEquals(3, saleDataSource.getImagesForSale("sale-inc-img").size)
    }

    @Test
    fun `delete specific image keeps others`() = runTest {
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-del-img"))
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-del-img")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-del-img")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-3", saleId = "sale-del-img")
        )

        saleDataSource.deleteImageById("img-2")

        val remaining = saleDataSource.getImagesForSale("sale-del-img")
        assertEquals(2, remaining.size)
        assertTrue(remaining.none { it.LOCAL_SALE_IMAGE_ID == "img-2" })
    }

    @Test
    fun `delete multiple images by IDs`() = runTest {
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-batch-del"))
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-batch-del")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-batch-del")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-3", saleId = "sale-batch-del")
        )

        saleDataSource.deleteImagesByIds(listOf("img-1", "img-3"))

        val remaining = saleDataSource.getImagesForSale("sale-batch-del")
        assertEquals(1, remaining.size)
        assertEquals("img-2", remaining[0].LOCAL_SALE_IMAGE_ID)
    }

    @Test
    fun `images from different sales are isolated`() = runTest {
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-img-a"))
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-img-b"))

        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-a1", saleId = "sale-img-a")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-a2", saleId = "sale-img-a")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-b1", saleId = "sale-img-b")
        )

        assertEquals(2, saleDataSource.getImagesForSale("sale-img-a").size)
        assertEquals(1, saleDataSource.getImagesForSale("sale-img-b").size)
    }

    // ========================
    // Cascade and FK behavior
    // ========================

    @Test
    fun `cascade delete removes images and combos on REPLACE`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-cascade")
        saleDataSource.insertSale(sale)
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-cascade")
        )
        comboDataSource.insertCombo(
            TestDataFactory.createLocalSaleComboEntity(comboId = "combo-1", saleId = "sale-cascade")
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-cascade", articuloId = 100)
        )

        // REPLACE triggers cascade on FK children (images, combos)
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-cascade", clientName = "New")
        )

        assertTrue(saleDataSource.getImagesForSale("sale-cascade").isEmpty())
        assertTrue(comboDataSource.getCombosForSale("sale-cascade").isEmpty())
        // Products don't have FK cascade, they use composite key - check behavior
    }

    @Test
    fun `updateSale preserves images (no cascade)`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-update")
        saleDataSource.insertSale(sale)
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-update")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-update")
        )

        // updateSale uses UPDATE (not REPLACE), so no cascade
        val updatedSale = TestDataFactory.createLocalSaleEntity(
            saleId = "sale-update",
            clientName = "Updated Client"
        )
        saleDataSource.updateSale(updatedSale)

        val updated = saleDataSource.getSaleById("sale-update")!!
        assertEquals("Updated Client", updated.NOMBRE_CLIENTE)

        val images = saleDataSource.getImagesForSale("sale-update")
        assertEquals(2, images.size)
    }

    // ========================
    // Sale status management
    // ========================

    @Test
    fun `sale starts as not sent`() = runTest {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-status", enviado = false)
        )
        val sale = saleDataSource.getSaleById("sale-status")!!
        assertFalse(sale.ENVIADO)
    }

    @Test
    fun `mark sale as sent`() = runTest {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-sent", enviado = false)
        )
        saleDataSource.changeSaleStatus("sale-sent", true)

        val sale = saleDataSource.getSaleById("sale-sent")!!
        assertTrue(sale.ENVIADO)
    }

    @Test
    fun `mark sent sale back to pending`() = runTest {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-revert", enviado = true)
        )
        saleDataSource.changeSaleStatus("sale-revert", false)

        val sale = saleDataSource.getSaleById("sale-revert")!!
        assertFalse(sale.ENVIADO)
    }

    @Test
    fun `getPendingSales returns only unsent sales`() = runTest {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "pending-1", enviado = false)
        )
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "pending-2", enviado = false)
        )
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sent-1", enviado = true)
        )

        val pending = saleDataSource.getPendingSales()
        assertEquals(2, pending.size)
        assertTrue(pending.all { !it.ENVIADO })
    }

    @Test
    fun `status change does not affect products or images`() = runTest {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-status-safe", enviado = false)
        )
        productDataSource.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-status-safe",
                articuloId = 100
            )
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(
                imageId = "img-1",
                saleId = "sale-status-safe"
            )
        )

        saleDataSource.changeSaleStatus("sale-status-safe", true)

        assertEquals(1, productDataSource.getProductsForSale("sale-status-safe").size)
        assertEquals(1, saleDataSource.getImagesForSale("sale-status-safe").size)
    }

    // ========================
    // Address and optional fields
    // ========================

    @Test
    fun `sale with all address fields`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(
            saleId = "sale-addr",
            direccion = "Av. Reforma",
            numero = "456",
            colonia = "Juarez",
            poblacion = "Cuauhtemoc",
            ciudad = "CDMX"
        )
        saleDataSource.insertSale(sale)

        val retrieved = saleDataSource.getSaleById("sale-addr")!!
        assertEquals("Av. Reforma", retrieved.DIRECCION)
        assertEquals("456", retrieved.NUMERO)
        assertEquals("Juarez", retrieved.COLONIA)
        assertEquals("Cuauhtemoc", retrieved.POBLACION)
        assertEquals("CDMX", retrieved.CIUDAD)
    }

    @Test
    fun `sale with null optional fields`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(
            saleId = "sale-null",
            numero = null,
            colonia = null,
            poblacion = null,
            ciudad = null,
            enganche = null,
            avalOResponsable = null,
            nota = null,
            clienteId = null
        )
        saleDataSource.insertSale(sale)

        val retrieved = saleDataSource.getSaleById("sale-null")!!
        assertNull(retrieved.NUMERO)
        assertNull(retrieved.COLONIA)
        assertNull(retrieved.POBLACION)
        assertNull(retrieved.CIUDAD)
        assertNull(retrieved.ENGANCHE)
        assertNull(retrieved.AVAL_O_RESPONSABLE)
        assertNull(retrieved.NOTA)
        assertNull(retrieved.CLIENTE_ID)
    }

    @Test
    fun `sale with cliente ID for existing client`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-client", clienteId = 42)
        saleDataSource.insertSale(sale)

        val retrieved = saleDataSource.getSaleById("sale-client")!!
        assertEquals(42, retrieved.CLIENTE_ID)
    }

    // ========================
    // Location data
    // ========================

    @Test
    fun `sale preserves GPS coordinates`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(
            saleId = "sale-gps",
            latitude = 19.432608,
            longitude = -99.133209
        )
        saleDataSource.insertSale(sale)

        val retrieved = saleDataSource.getSaleById("sale-gps")!!
        assertEquals(19.432608, retrieved.LATITUD, 0.000001)
        assertEquals(-99.133209, retrieved.LONGITUD, 0.000001)
    }

    // ========================
    // Edit sale flow (update without losing data)
    // ========================

    @Test
    fun `edit sale updates fields without losing images`() = runTest {
        // Create original sale with images
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(
                saleId = "sale-edit",
                clientName = "Juan Original",
                telefono = "5500000000"
            )
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-edit")
        )
        saleDataSource.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-edit")
        )

        // Update via updateSale (not insertSale which would cascade delete)
        saleDataSource.updateSale(
            TestDataFactory.createLocalSaleEntity(
                saleId = "sale-edit",
                clientName = "Juan Editado",
                telefono = "5511111111"
            )
        )

        val sale = saleDataSource.getSaleById("sale-edit")!!
        assertEquals("Juan Editado", sale.NOMBRE_CLIENTE)
        assertEquals("5511111111", sale.TELEFONO)

        // Images preserved
        assertEquals(2, saleDataSource.getImagesForSale("sale-edit").size)
    }

    @Test
    fun `edit sale can replace products`() = runTest {
        saleDataSource.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-edit-prod"))
        productDataSource.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-edit-prod",
                    articuloId = 100
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-edit-prod",
                    articuloId = 101
                )
            )
        )

        // Delete old products and insert new ones (like edit flow does)
        productDataSource.deleteProductsForSale("sale-edit-prod")
        productDataSource.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-edit-prod",
                    articuloId = 200
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-edit-prod",
                    articuloId = 201
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-edit-prod",
                    articuloId = 202
                )
            )
        )

        val products = productDataSource.getProductsForSale("sale-edit-prod")
        assertEquals(3, products.size)
        assertTrue(products.none { it.ARTICULO_ID == 100 })
        assertTrue(products.any { it.ARTICULO_ID == 200 })
    }

    @Test
    fun `edit sale marks as not sent`() = runTest {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-edit-status", enviado = true)
        )
        saleDataSource.updateSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-edit-status", enviado = false)
        )

        val sale = saleDataSource.getSaleById("sale-edit-status")!!
        assertFalse(sale.ENVIADO)
    }

    // ========================
    // Multiple sales / isolation
    // ========================

    @Test
    fun `multiple concurrent sales are isolated`() = runTest {
        // Create 3 sales with their own products and combos
        for (i in 1..3) {
            saleDataSource.insertSale(
                TestDataFactory.createLocalSaleEntity(saleId = "multi-$i", clientName = "Client $i")
            )
            productDataSource.insertSaleProducts(
                listOf(
                    TestDataFactory.createLocalSaleProductEntity(
                        saleId = "multi-$i",
                        articuloId = i * 100
                    ),
                    TestDataFactory.createLocalSaleProductEntity(
                        saleId = "multi-$i",
                        articuloId = i * 100 + 1
                    )
                )
            )
            comboDataSource.insertCombo(
                TestDataFactory.createLocalSaleComboEntity(
                    comboId = "combo-$i",
                    saleId = "multi-$i"
                )
            )
        }

        // Verify each sale has its own data
        for (i in 1..3) {
            val sale = saleDataSource.getSaleById("multi-$i")!!
            assertEquals("Client $i", sale.NOMBRE_CLIENTE)
            assertEquals(2, productDataSource.getProductsForSale("multi-$i").size)
            assertEquals(1, comboDataSource.getCombosForSale("multi-$i").size)
        }
    }

    @Test
    fun `duplicate sale ID uses REPLACE`() = runTest {
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-dup", clientName = "Juan")
        )
        saleDataSource.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "sale-dup", clientName = "Pedro")
        )
        val retrieved = saleDataSource.getSaleById("sale-dup")
        assertEquals("Pedro", retrieved!!.NOMBRE_CLIENTE)
    }

    @Test
    fun `nonexistent sale returns null`() = runTest {
        assertNull(saleDataSource.getSaleById("does-not-exist"))
    }

    @Test
    fun `empty products list for nonexistent sale`() = runTest {
        assertTrue(productDataSource.getProductsForSale("does-not-exist").isEmpty())
    }

    @Test
    fun `empty combos list for nonexistent sale`() = runTest {
        assertTrue(comboDataSource.getCombosForSale("does-not-exist").isEmpty())
    }

    @Test
    fun `empty images list for nonexistent sale`() = runTest {
        assertTrue(saleDataSource.getImagesForSale("does-not-exist").isEmpty())
    }
}
