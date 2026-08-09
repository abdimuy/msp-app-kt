package com.example.msp_app.data.local.datasource.productInventory

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.ProductInventoryEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [ProductInventoryLocalDataSource] construido por el
 * **constructor de DAO inyectado** (la forma Hilt) contra la DB in-memory de
 * [RoomTestBase]. Cubre round-trip, stock, el reemplazo `REPLACE` de
 * `insertAll`, `deleteAll` y la equivalencia con el puente `context` que
 * siguen usando `ProductDetailsViewModel`/`ProductsInventoryViewModel`.
 */
class ProductInventoryLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: ProductInventoryLocalDataSource

    @Before
    fun setUpStore() {
        store = ProductInventoryLocalDataSource(db.productInventoryDao())
    }

    private fun inventory(
        id: Int = 100,
        articulo: String = "Colchon King",
        existencias: Int = 10,
        lineaId: Int = 1,
        linea: String = "Colchones",
        precios: String? = "Precio de lista:1500.0"
    ) = ProductInventoryEntity(
        ARTICULO_ID = id,
        ARTICULO = articulo,
        EXISTENCIAS = existencias,
        LINEA_ARTICULO_ID = lineaId,
        LINEA_ARTICULO = linea,
        PRECIOS = precios
    )

    @Test
    fun insertAll_and_getAll_roundTrips() = runTest {
        store.insertAll(listOf(inventory(id = 100), inventory(id = 200, articulo = "Sala")))

        val all = store.getAll()

        assertEquals(listOf(100, 200), all.map { it.ARTICULO_ID }.sorted())
    }

    @Test
    fun getProductInventoryById_returnsMatchingRow() = runTest {
        store.insertAll(listOf(inventory(id = 100, articulo = "Colchon King")))

        assertEquals("Colchon King", store.getProductInventoryById(100)?.ARTICULO)
    }

    @Test
    fun getProductInventoryById_absent_returnsNull() = runTest {
        // FIX (Task 4): ProductInventoryDao.getProductInventoryById ahora
        // declara `ProductInventoryEntity?` (mismo patron de bug que
        // ProductDao.getProductById — retorno non-null sin guardia para
        // resultado vacio; Room regresaba un null de plataforma que el tipo
        // declaraba imposible). Con el fix, un id ausente es un `null` de
        // Kotlin legitimo.
        val result = store.getProductInventoryById(999)

        assertNull(
            "ProductInventoryDao.getProductInventoryById debe regresar null para un id ausente",
            result
        )
    }

    @Test
    fun getStockById_returnsExistencias() = runTest {
        store.insertAll(listOf(inventory(id = 100, existencias = 7)))

        assertEquals(7, store.getStockById(100))
    }

    @Test
    fun getStockById_nullWhenAbsent() = runTest {
        assertNull(store.getStockById(999))
    }

    @Test
    fun deleteAll_clearsTable() = runTest {
        store.insertAll(listOf(inventory(id = 100)))

        store.deleteAll()

        assertTrue(store.getAll().isEmpty())
    }

    @Test
    fun insertAll_replaceOnConflict_upsertsExistingId() = runTest {
        store.insertAll(listOf(inventory(id = 100, existencias = 10)))
        store.insertAll(listOf(inventory(id = 100, existencias = 3)))

        assertEquals(1, store.getAll().size)
        assertEquals(3, store.getStockById(100))
    }

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        store.insertAll(listOf(inventory(id = 100, articulo = "Recamara")))

        val contextForm =
            ProductInventoryLocalDataSource(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(
            "ambos constructores resuelven a la misma DB",
            store.getProductInventoryById(100)?.ARTICULO,
            contextForm.getProductInventoryById(100)?.ARTICULO
        )
    }
}
