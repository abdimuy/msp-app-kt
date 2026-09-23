package com.example.msp_app.core.database.dao.product

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.ProductEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * `getProductsByFolios` contra el Room real (Robolectric, DB en memoria) — el
 * `IN (:folios)` de verdad, no la intención.
 *
 * Existe por la ronda de arreglo 1 de la Task 2: `RoomProductosAdapter.productosDeVarios`
 * agrupa el resultado de esta consulta por `FOLIO` en memoria, así que si el
 * `IN` trajera de más (otro folio) o de menos (uno de los pedidos), el defecto
 * quedaría escondido detrás del `groupBy` y ningún test de dominio lo vería —
 * los de `application/` usan [com.example.msp_app.feature.pagos.data.fake.FakeProductosPort],
 * que no ejecuta SQL.
 */
class ProductDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase
    private lateinit var dao: ProductDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.productDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Dos folios pedidos traen SUS renglones, ni los de un tercer folio ajeno. */
    @Test
    fun `getProductsByFolios trae los renglones de los folios pedidos y ningun otro`() = runTest {
        dao.saveAll(
            listOf(
                producto(1, "Y00001786", "RECAMARA CANTARO KING SIZE CHOCOLATE", posicion = 0),
                producto(2, "Y00001786", "BASE DE CAMA MATRIMONIAL", posicion = 1),
                producto(3, "Y00002103", "BOCINA PROFESIONAL 8'' AUDIOBAHN", posicion = 0),
                // Un tercer folio que NO se pide — si el IN trajera de más, esta
                // fila aparecería en el resultado.
                producto(4, "Y00009999", "TELEVISION 55 PULGADAS", posicion = 0)
            )
        )

        val filas = dao.getProductsByFolios(listOf("Y00001786", "Y00002103"))

        assertEquals(
            setOf("Y00001786", "Y00002103"),
            filas.map { it.FOLIO }.toSet()
        )
        assertEquals(3, filas.size)
        assertTrue(
            "un folio no pedido no debe colarse",
            filas.none { it.FOLIO == "Y00009999" }
        )
    }

    /** Un folio de la lista sin renglones sincronizados simplemente no aporta filas. */
    @Test
    fun `un folio sin renglones sincronizados no aporta filas, pero los demas si`() = runTest {
        dao.saveAll(
            listOf(producto(1, "Y00001786", "RECAMARA CANTARO KING SIZE CHOCOLATE", posicion = 0))
        )

        val filas = dao.getProductsByFolios(listOf("Y00001786", "Y00002103"))

        assertEquals(listOf("Y00001786"), filas.map { it.FOLIO })
    }

    /** Una lista vacía no revienta el `IN` — contesta vacío. */
    @Test
    fun `una lista de folios vacia contesta vacio`() = runTest {
        dao.saveAll(
            listOf(producto(1, "Y00001786", "RECAMARA CANTARO KING SIZE CHOCOLATE", posicion = 0))
        )

        assertTrue(dao.getProductsByFolios(emptyList()).isEmpty())
    }

    private fun producto(id: Int, folio: String, articulo: String, posicion: Int) = ProductEntity(
        DOCTO_PV_DET_ID = id,
        DOCTO_PV_ID = id,
        FOLIO = folio,
        ARTICULO_ID = id,
        ARTICULO = articulo,
        CANTIDAD = 1,
        PRECIO_UNITARIO_IMPTO = 100.0,
        PRECIO_TOTAL_NETO = 100.0,
        POSICION = posicion
    )
}
