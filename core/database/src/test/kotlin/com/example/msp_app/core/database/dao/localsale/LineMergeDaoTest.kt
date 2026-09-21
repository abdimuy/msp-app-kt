package com.example.msp_app.core.database.dao.localsale

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.LocalSaleComboEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.LocalSaleProductEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private const val SALE_ID = "sale-merge-001"

/**
 * Cubre el merge de líneas que CONSERVA `SERVER_UUID` (plan "Corregir una
 * venta antes de que suba", sección "El merge de líneas"):
 * `LocalSaleProductDao.mergeProductsForSale` y
 * `LocalSaleComboDao.mergeCombosForSale`, la alternativa a "borrar todo y
 * reinsertar" (`deleteProductsForSale`+`insertAllSaleProducts`,
 * `replaceCombosForSale`) que el mismo patrón le costó SEIS defectos al lado
 * Go del proyecto (ver CLAUDE.md del repo hermano, sección de cobranza).
 *
 * Invariante: corregir una venta no reacuña la identidad de las líneas que
 * no cambiaron. Una línea que sobrevive (misma clave primaria) conserva el
 * `SERVER_UUID` que el subidor ya le había acuñado en un intento previo; una
 * línea nueva entra con `SERVER_UUID = NULL` para que el subidor se lo
 * acuñe; una línea quitada se borra.
 */
class LineMergeDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        runTest {
            database.localSaleDao().insertSale(
                LocalSaleEntity(
                    LOCAL_SALE_ID = SALE_ID,
                    NOMBRE_CLIENTE = "Rosa Elena Martinez Vazquez",
                    FECHA_VENTA = "2026-09-18T15:30:00Z",
                    LATITUD = 19.043415,
                    LONGITUD = -98.198234,
                    DIRECCION = "Privada de las Rosas 45",
                    PARCIALIDAD = 850.0,
                    ENGANCHE = 500.0,
                    TELEFONO = "2221234567",
                    FREC_PAGO = "SEMANAL",
                    AVAL_O_RESPONSABLE = "Juan Martinez Vazquez",
                    NOTA = null,
                    DIA_COBRANZA = "MARTES",
                    PRECIO_TOTAL = 6800.0,
                    TIEMPO_A_CORTO_PLAZOMESES = 8,
                    MONTO_A_CORTO_PLAZO = 6300.0,
                    MONTO_DE_CONTADO = 5800.0,
                    ENVIADO = false
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun product(
        articuloId: Int,
        articulo: String,
        cantidad: Int,
        serverUuid: String? = null
    ) = LocalSaleProductEntity(
        LOCAL_SALE_ID = SALE_ID,
        ARTICULO_ID = articuloId,
        ARTICULO = articulo,
        CANTIDAD = cantidad,
        PRECIO_LISTA = 1500.0,
        PRECIO_CORTO_PLAZO = 1200.0,
        PRECIO_CONTADO = 1000.0,
        SERVER_UUID = serverUuid
    )

    private fun combo(comboId: String, nombre: String, serverUuid: String? = null) =
        LocalSaleComboEntity(
            COMBO_ID = comboId,
            LOCAL_SALE_ID = SALE_ID,
            NOMBRE_COMBO = nombre,
            PRECIO_LISTA = 5000.0,
            PRECIO_CORTO_PLAZO = 4500.0,
            PRECIO_CONTADO = 4000.0,
            SERVER_UUID = serverUuid
        )

    // ─── Productos ──────────────────────────────────────────────────────

    @Test
    fun `linea que sobrevive conserva server uuid`() = runTest {
        // Estado previo: el subidor ya intentó subir la venta y acuñó
        // SERVER_UUID en dos de los tres productos.
        database.localSaleProduct().insertAllSaleProducts(
            listOf(
                product(1, "Colchon King", cantidad = 1, serverUuid = "uuid-colchon-king"),
                product(2, "Base Queen", cantidad = 2, serverUuid = "uuid-base-queen"),
                product(3, "Almohada", cantidad = 4, serverUuid = "uuid-almohada")
            )
        )

        // Corrección: el articulo 1 cambia de cantidad (sobrevive), el 2 se
        // quita, el 3 se queda igual (sobrevive), y entra un articulo nuevo.
        database.localSaleProduct().mergeProductsForSale(
            SALE_ID,
            listOf(
                product(1, "Colchon King", cantidad = 3),
                product(3, "Almohada", cantidad = 4),
                product(4, "Cabecera", cantidad = 1)
            )
        )

        val result = database.localSaleProduct().getProductsForSale(
            SALE_ID
        ).associateBy { it.ARTICULO_ID }

        assertEquals(setOf(1, 3, 4), result.keys)

        val survivorWithNewQuantity = result.getValue(1)
        assertEquals(3, survivorWithNewQuantity.CANTIDAD)
        assertEquals(
            "el articulo 1 sobrevive con cantidad distinta: conserva su SERVER_UUID",
            "uuid-colchon-king",
            survivorWithNewQuantity.SERVER_UUID
        )

        val unchangedSurvivor = result.getValue(3)
        assertEquals(
            "el articulo 3 no cambio: tambien conserva su SERVER_UUID",
            "uuid-almohada",
            unchangedSurvivor.SERVER_UUID
        )

        val newProduct = result.getValue(4)
        assertNull(
            "el articulo nuevo entra con SERVER_UUID nulo, el subidor se lo acuña",
            newProduct.SERVER_UUID
        )

        assertTrue(
            "el articulo 2 (quitado en la correccion) ya no debe existir",
            result[2] == null
        )
    }

    @Test
    fun `merge de productos con lista vacia borra todos los productos de la venta`() = runTest {
        database.localSaleProduct().insertAllSaleProducts(
            listOf(product(1, "Colchon King", cantidad = 1, serverUuid = "uuid-colchon-king"))
        )

        database.localSaleProduct().mergeProductsForSale(SALE_ID, emptyList())

        assertTrue(database.localSaleProduct().getProductsForSale(SALE_ID).isEmpty())
    }

    @Test
    fun `merge de productos no toca los productos de otra venta`() = runTest {
        database.localSaleDao().insertSale(
            LocalSaleEntity(
                LOCAL_SALE_ID = "otra-venta",
                NOMBRE_CLIENTE = "Guadalupe Ramirez Torres",
                FECHA_VENTA = "2026-09-18T16:00:00Z",
                LATITUD = 19.0,
                LONGITUD = -98.0,
                DIRECCION = "Otra direccion",
                PARCIALIDAD = 500.0,
                ENGANCHE = 200.0,
                TELEFONO = "2221111111",
                FREC_PAGO = "SEMANAL",
                AVAL_O_RESPONSABLE = null,
                NOTA = null,
                DIA_COBRANZA = "LUNES",
                PRECIO_TOTAL = 3000.0,
                TIEMPO_A_CORTO_PLAZOMESES = 4,
                MONTO_A_CORTO_PLAZO = 2800.0,
                MONTO_DE_CONTADO = 2600.0,
                ENVIADO = false
            )
        )
        database.localSaleProduct().insertAllSaleProducts(
            listOf(
                LocalSaleProductEntity(
                    LOCAL_SALE_ID = "otra-venta",
                    ARTICULO_ID = 99,
                    ARTICULO = "Producto ajeno",
                    CANTIDAD = 1,
                    PRECIO_LISTA = 100.0,
                    PRECIO_CORTO_PLAZO = 90.0,
                    PRECIO_CONTADO = 80.0,
                    SERVER_UUID = "uuid-ajeno"
                )
            )
        )

        database.localSaleProduct().mergeProductsForSale(SALE_ID, emptyList())

        val ajeno = database.localSaleProduct().getProductsForSale("otra-venta")
        assertEquals(1, ajeno.size)
        assertEquals("uuid-ajeno", ajeno.first().SERVER_UUID)
    }

    // ─── Combos ─────────────────────────────────────────────────────────

    @Test
    fun `combo que sobrevive conserva server uuid, mismo caso que productos`() = runTest {
        database.localSaleComboDao().insertAllCombos(
            listOf(
                combo("combo-recamara", "Combo Recamara", serverUuid = "uuid-combo-recamara"),
                combo("combo-sala", "Combo Sala", serverUuid = "uuid-combo-sala"),
                combo("combo-comedor", "Combo Comedor", serverUuid = "uuid-combo-comedor")
            )
        )

        // combo-recamara sobrevive (cambia nombre/precio), combo-comedor sobrevive
        // sin cambios, combo-cocina es nuevo, y combo-sala se quita.
        database.localSaleComboDao().mergeCombosForSale(
            SALE_ID,
            listOf(
                combo("combo-recamara", "Combo Recamara Grande"),
                combo("combo-comedor", "Combo Comedor"),
                combo("combo-cocina", "Combo Cocina")
            )
        )

        val result = database.localSaleComboDao().getCombosForSale(
            SALE_ID
        ).associateBy { it.COMBO_ID }

        assertEquals(setOf("combo-recamara", "combo-comedor", "combo-cocina"), result.keys)

        assertEquals(
            "combo-recamara sobrevive con nombre distinto: conserva su SERVER_UUID",
            "uuid-combo-recamara",
            result.getValue("combo-recamara").SERVER_UUID
        )
        assertEquals("Combo Recamara Grande", result.getValue("combo-recamara").NOMBRE_COMBO)

        assertEquals(
            "combo-comedor no cambio: tambien conserva su SERVER_UUID",
            "uuid-combo-comedor",
            result.getValue("combo-comedor").SERVER_UUID
        )

        assertNull(
            "combo-cocina es nuevo: entra con SERVER_UUID nulo",
            result.getValue("combo-cocina").SERVER_UUID
        )

        assertTrue("combo-sala (quitado) ya no debe existir", result["combo-sala"] == null)
    }

    @Test
    fun `merge de combos con lista vacia borra todos los combos de la venta`() = runTest {
        database.localSaleComboDao().insertAllCombos(
            listOf(combo("combo-recamara", "Combo Recamara", serverUuid = "uuid-combo-recamara"))
        )

        database.localSaleComboDao().mergeCombosForSale(SALE_ID, emptyList())

        assertTrue(database.localSaleComboDao().getCombosForSale(SALE_ID).isEmpty())
    }

    // ─── Corregir dos veces seguidas (productos y combos, no solo la venta) ─

    @Test
    fun `corregir dos veces seguidas no deja lineas huerfanas y conserva identidad`() = runTest {
        database.localSaleProduct().insertAllSaleProducts(
            listOf(product(1, "Colchon King", cantidad = 1, serverUuid = "uuid-colchon-king"))
        )

        // Primera correccion: se agrega un producto nuevo.
        database.localSaleProduct().mergeProductsForSale(
            SALE_ID,
            listOf(
                product(1, "Colchon King", cantidad = 1),
                product(2, "Base Queen", cantidad = 1)
            )
        )
        // El subidor acuña el SERVER_UUID del producto nuevo entre correcciones.
        database.localSaleProduct().updateServerUuid(SALE_ID, 2, "uuid-base-queen")

        // Segunda correccion: se quita el producto 1, sobrevive el 2.
        database.localSaleProduct().mergeProductsForSale(
            SALE_ID,
            listOf(product(2, "Base Queen", cantidad = 2))
        )

        val result = database.localSaleProduct().getProductsForSale(SALE_ID)
        assertEquals(1, result.size)
        val survivor = result.first()
        assertEquals(2, survivor.ARTICULO_ID)
        assertEquals(2, survivor.CANTIDAD)
        assertEquals(
            "el producto acuñado entre correcciones no debe perder su identidad en la segunda",
            "uuid-base-queen",
            survivor.SERVER_UUID
        )
    }
}
