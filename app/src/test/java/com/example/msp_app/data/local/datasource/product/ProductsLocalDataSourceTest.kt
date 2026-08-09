package com.example.msp_app.data.local.datasource.product

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.ProductEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [ProductsLocalDataSource] construido por el
 * **constructor de DAO inyectado** (la forma Hilt) contra la DB in-memory de
 * [RoomTestBase]. Cubre round-trip, filtro por folio, el reemplazo completo
 * de `saveAll` y la equivalencia con el puente `context` que siguen usando
 * `ProductsViewModel`/`PaymentsViewModel`/`SalesViewModel`.
 */
class ProductsLocalDataSourceTest : RoomTestBase() {

    private lateinit var store: ProductsLocalDataSource

    @Before
    fun setUpStore() {
        store = ProductsLocalDataSource(db.productDao())
    }

    private fun product(
        id: Int = 1,
        doctoPvId: Int = 500,
        folio: String = "F-500",
        articulo: String = "Colchon King",
        cantidad: Int = 1,
        precioUnitario: Double = 1500.0,
        precioTotal: Double = 1500.0,
        posicion: Int = 0
    ) = ProductEntity(
        DOCTO_PV_DET_ID = id,
        DOCTO_PV_ID = doctoPvId,
        FOLIO = folio,
        ARTICULO_ID = id,
        ARTICULO = articulo,
        CANTIDAD = cantidad,
        PRECIO_UNITARIO_IMPTO = precioUnitario,
        PRECIO_TOTAL_NETO = precioTotal,
        POSICION = posicion
    )

    @Test
    fun saveAll_and_getProductById_roundTrips() = runTest {
        store.saveAll(listOf(product(id = 1, articulo = "Colchon King")))

        val got = store.getProductById(1)

        assertEquals("Colchon King", got.ARTICULO)
    }

    @Test
    fun getProductsByFolio_filtersByFolio() = runTest {
        store.saveAll(
            listOf(
                product(id = 1, doctoPvId = 500, folio = "F-500"),
                product(id = 2, doctoPvId = 500, folio = "F-500"),
                product(id = 3, doctoPvId = 600, folio = "F-600")
            )
        )

        val result = store.getProductsByFolio("F-500")

        assertEquals(listOf(1, 2), result.map { it.DOCTO_PV_DET_ID }.sorted())
    }

    @Test
    fun saveAll_replacesPreviousContents() = runTest {
        store.saveAll(listOf(product(id = 1, folio = "F-old")))
        store.saveAll(listOf(product(id = 2, folio = "F-new")))

        val result = store.getProductsByFolio("F-old")

        assertTrue("saveAll borra el contenido anterior antes de insertar", result.isEmpty())
        assertEquals("F-new", store.getProductById(2).FOLIO)
    }

    @Test
    fun getProductById_absent_returnsPlatformNullDespiteNonNullType() = runTest {
        // AUDIT (Task 8, no fix): ProductDao.getProductById declara un
        // retorno NO-nulo (`ProductEntity`, no `ProductEntity?`) pero la
        // query no tiene guardia para resultado vacio. Verificado en runtime:
        // Room NO lanza — regresa un null de plataforma que el tipo Kotlin
        // declara imposible. Cualquier caller que confie en el tipo (sin
        // `?.`/`!!`) revienta con NPE en el sitio de uso, lejos del origen
        // real. Documentado aqui, fuera de alcance (el DAO no forma parte de
        // los 5 archivos de este lote) — ver reporte de auditoria.
        val result = store.getProductById(999)

        @Suppress("SENSELESS_COMPARISON")
        assertTrue(
            "ProductDao.getProductById regresa null de plataforma para un id ausente " +
                "pese a declarar tipo no-nulo (bug latente preexistente en ProductDao)",
            result == null
        )
    }

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        store.saveAll(listOf(product(id = 1, articulo = "Sala Reclinable")))

        val contextForm =
            ProductsLocalDataSource(ApplicationProvider.getApplicationContext<Context>())

        assertEquals(
            "ambos constructores resuelven a la misma DB",
            store.getProductById(1).ARTICULO,
            contextForm.getProductById(1).ARTICULO
        )
    }
}
