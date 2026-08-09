package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [SaleProductLocalDataSource] construido por el
 * **constructor de DAOs inyectados** (la forma Hilt) con
 * [com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao] de la
 * DB in-memory de [RoomTestBase]. Prueba además que la forma inyectada es
 * EQUIVALENTE al puente `context` que usan `NewLocalSaleViewModel`/
 * `EditLocalSaleViewModel`/`PendingLocalSalesWorker` (ambos resuelven a la
 * misma DB via [com.example.msp_app.core.database.AppDatabase.getInstance]).
 *
 * `saleProductDao` se resuelve por `AppDatabase.localSaleProduct()` — el
 * método NO lleva el sufijo `Dao` (gotcha documentado en el brief de Task 3).
 */
class SaleProductLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: SaleProductLocalDataSource

    @Before
    fun setUpStore() {
        store = SaleProductLocalDataSource(db.localSaleProduct())
    }

    // ─── insertSaleProduct / getProductsForSale round-trip ────────────────────

    @Test
    fun insertSaleProduct_roundTripsViaGetProductsForSale() = runTest {
        store.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-1",
                articuloId = 100,
                articulo = "Colchon King"
            )
        )

        val products = store.getProductsForSale("sale-1")

        assertEquals(1, products.size)
        assertEquals("Colchon King", products.first().ARTICULO)
    }

    @Test
    fun getProductsForSale_emptyWhenNoProductsForThatSale() = runTest {
        store.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 100)
        )

        assertTrue(store.getProductsForSale("sale-otra").isEmpty())
    }

    @Test
    fun insertSaleProducts_batchInsertsAll() = runTest {
        store.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-1",
                    articuloId = 100,
                    articulo = "Colchon King"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-1",
                    articuloId = 200,
                    articulo = "Base Matrimonial"
                ),
                TestDataFactory.createLocalSaleProductEntity(
                    saleId = "sale-1",
                    articuloId = 300,
                    articulo = "Buro"
                )
            )
        )

        val products = store.getProductsForSale("sale-1")

        assertEquals(
            listOf(100, 200, 300).sorted(),
            products.map { it.ARTICULO_ID }.sorted()
        )
    }

    @Test
    fun deleteProductsForSale_removesOnlyThatSalesProducts() = runTest {
        store.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 100),
                TestDataFactory.createLocalSaleProductEntity(saleId = "sale-2", articuloId = 100)
            )
        )

        store.deleteProductsForSale("sale-1")

        assertTrue(store.getProductsForSale("sale-1").isEmpty())
        assertEquals(1, store.getProductsForSale("sale-2").size)
    }

    @Test
    fun updateServerUuid_updatesOnlyMatchingArticulo() = runTest {
        store.insertSaleProducts(
            listOf(
                TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 100),
                TestDataFactory.createLocalSaleProductEntity(saleId = "sale-1", articuloId = 200)
            )
        )

        store.updateServerUuid(saleId = "sale-1", articuloId = 100, serverUuid = "server-100")

        val products = store.getProductsForSale("sale-1").associateBy { it.ARTICULO_ID }
        assertEquals("server-100", products[100]!!.SERVER_UUID)
        assertNull(products[200]!!.SERVER_UUID)
    }

    @Test
    fun insertSaleProduct_roundTripsComboLink() = runTest {
        // Un producto que viene de un combo local trae COMBO_ID no-nulo — el
        // camino que consume LocalSaleSyncHandler al armar el payload de venta.
        store.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "sale-1",
                articuloId = 100,
                comboId = "combo-1"
            )
        )

        assertEquals("combo-1", store.getProductsForSale("sale-1").first().COMBO_ID)
    }

    // ─── equivalencia inyectado ⇔ puente context ──────────────────────────────

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        store.insertSaleProduct(
            TestDataFactory.createLocalSaleProductEntity(
                saleId = "eq-1",
                articuloId = 100,
                articulo = "Colchon King"
            )
        )

        // Tipo explicito: los dos constructores de un arg (DAO vs Context)
        // hacen ambigua la inferencia de `getApplicationContext<T>()`.
        val contextForm =
            SaleProductLocalDataSource(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(
            "ambos constructores resuelven a la misma DB",
            store.getProductsForSale("eq-1"),
            contextForm.getProductsForSale("eq-1")
        )
    }
}
