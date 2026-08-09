package com.example.msp_app.data.local.datasource.productInventoryImage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.ProductInventoryEntity
import com.example.msp_app.core.database.entities.ProductInventoryImageEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [ProductInventoryImageLocalDataSource] construido por
 * el **constructor de DAOs inyectados** (la forma Hilt) contra la DB
 * in-memory de [RoomTestBase]. Cubre las dos DAOs que compone (imagenes +
 * inventario), y la equivalencia con el puente `context` que sigue usando
 * `ProductsInventoryImagesViewModel` (que resuelve `productDao` por su
 * cuenta via `getInstance` — residual documentado para Task 9).
 */
class ProductInventoryImageLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: ProductInventoryImageLocalDataSource

    @Before
    fun setUpStore() {
        store = ProductInventoryImageLocalDataSource(
            db.productInventoryImageDao(),
            db.productInventoryDao()
        )
    }

    private fun product(id: Int = 100, articulo: String = "Colchon King") = ProductInventoryEntity(
        ARTICULO_ID = id,
        ARTICULO = articulo,
        EXISTENCIAS = 10,
        LINEA_ARTICULO_ID = 1,
        LINEA_ARTICULO = "Colchones",
        PRECIOS = null
    )

    private fun image(id: Int = 1, articuloId: Int = 100, ruta: String = "ruta_$id.jpg") =
        ProductInventoryImageEntity(
            IMAGEN_ID = id,
            ARTICULO_ID = articuloId,
            RUTA_LOCAL = ruta
        )

    @Test
    fun insertImage_and_getImagesByProductId_roundTrips() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100)))
        store.insertImage(image(id = 1, articuloId = 100))

        val images = store.getImagesByProductId(100)

        assertEquals(1, images.size)
        assertEquals("ruta_1.jpg", images.first().RUTA_LOCAL)
    }

    @Test
    fun insertAllImages_and_getAllImages() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100)))
        store.insertAllImages(
            listOf(image(id = 1, articuloId = 100), image(id = 2, articuloId = 100))
        )

        assertEquals(listOf(1, 2), store.getAllImages().map { it.IMAGEN_ID }.sorted())
    }

    @Test
    fun getFirstImageByProductId_returnsOneOrNull() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100)))
        assertNull(store.getFirstImageByProductId(100))

        store.insertImage(image(id = 1, articuloId = 100))

        assertEquals(1, store.getFirstImageByProductId(100)!!.IMAGEN_ID)
    }

    @Test
    fun getImageById_roundTrips() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100)))
        store.insertImage(image(id = 1, articuloId = 100))

        assertEquals("ruta_1.jpg", store.getImageById(1)!!.RUTA_LOCAL)
        assertNull(store.getImageById(999))
    }

    @Test
    fun existsByProductId_delegatesToProductInventoryDao() = runTest {
        assertFalse(store.existsByProductId(100))

        db.productInventoryDao().insertAll(listOf(product(id = 100)))

        assertTrue(store.existsByProductId(100))
    }

    @Test
    fun getAllProducts_delegatesToProductInventoryDao() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100), product(id = 200)))

        assertEquals(
            listOf(100, 200),
            store.getAllProducts().map { it.ARTICULO_ID }.sorted()
        )
    }

    @Test
    fun insertSafeImages_isEquivalentToInsertAllImages() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100)))

        store.insertSafeImages(listOf(image(id = 1, articuloId = 100)))

        assertEquals(listOf(1), store.getAllImages().map { it.IMAGEN_ID })
    }

    @Test
    fun deleteImage_removesOnlyThatImage() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100)))
        store.insertAllImages(
            listOf(image(id = 1, articuloId = 100), image(id = 2, articuloId = 100))
        )

        store.deleteImage(image(id = 1, articuloId = 100))

        assertEquals(listOf(2), store.getAllImages().map { it.IMAGEN_ID })
    }

    @Test
    fun deleteAllImages_clearsTable() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100)))
        store.insertAllImages(listOf(image(id = 1, articuloId = 100)))

        store.deleteAllImages()

        assertTrue(store.getAllImages().isEmpty())
    }

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        db.productInventoryDao().insertAll(listOf(product(id = 100)))
        store.insertImage(image(id = 1, articuloId = 100))

        val contextForm = ProductInventoryImageLocalDataSource(
            ApplicationProvider.getApplicationContext<Context>(),
            db.productInventoryDao()
        )

        assertEquals(
            "ambos constructores resuelven a la misma DB",
            store.getAllImages().map { it.IMAGEN_ID },
            contextForm.getAllImages().map { it.IMAGEN_ID }
        )
    }
}
