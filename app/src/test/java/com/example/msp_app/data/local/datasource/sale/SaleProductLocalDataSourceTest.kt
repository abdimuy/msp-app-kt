package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.dao.localsale.LocalSaleProductDao
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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

    // ─── char-test money-path: propagación de error del DAO ───────────────────

    /**
     * Char-test OLD→NEW. ANTES: `getProductsForSale` envolvía la llamada al DAO
     * en `try { ... } catch { emptyList() }`, así que un fallo del DAO se veía
     * como "venta sin productos" y alimentaba una subida vacía a Microsip.
     * AHORA la excepción se PROPAGA intacta; el guard downstream decide.
     */
    @Test
    fun getProductsForSale_propagatesDaoException_insteadOfSwallowingToEmpty() = runTest {
        val boom = IllegalStateException("db corrupta")
        val throwingStore = SaleProductLocalDataSource(throwingProductDao(boom))

        val thrown = try {
            throwingStore.getProductsForSale("sale-1")
            null
        } catch (e: Exception) {
            e
        }

        assertSame(
            "el fallo del DAO debe propagarse, no colapsar a lista vacía",
            boom,
            thrown
        )
    }

    private fun throwingProductDao(error: Throwable): LocalSaleProductDao =
        object : LocalSaleProductDao {
            override suspend fun insertSaleProduct(saleProduct: LocalSaleProductEntity) = Unit
            override suspend fun insertAllSaleProducts(saleProducts: List<LocalSaleProductEntity>) =
                Unit

            override suspend fun getProductsForSale(saleId: String): List<LocalSaleProductEntity> =
                throw error

            override suspend fun deleteProductsForSale(saleId: String) = Unit
            override suspend fun updateServerUuid(
                saleId: String,
                articuloId: Int,
                serverUuid: String
            ) = Unit
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
