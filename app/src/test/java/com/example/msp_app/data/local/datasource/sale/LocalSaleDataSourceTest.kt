package com.example.msp_app.data.local.datasource.sale

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Suite exhaustiva de [LocalSaleDataSource] construido por el **constructor
 * de DAOs inyectados** (la forma Hilt) con [com.example.msp_app.core.database.dao.localsale.LocalSaleDao]
 * de la DB in-memory de [RoomTestBase]. Cubre el ciclo de vida completo de
 * una venta local en borrador (alta, imágenes, edición, estado de envío) y
 * prueba que la forma inyectada es EQUIVALENTE al puente `context` que usan
 * `NewLocalSaleViewModel`/`EditLocalSaleViewModel`/`PendingLocalSalesWorker`
 * (ambos resuelven a la misma DB via
 * [com.example.msp_app.core.database.AppDatabase.getInstance]).
 */
class LocalSaleDataSourceTest : RoomTestBase() {

    private lateinit var store: LocalSaleDataSource

    @Before
    fun setUpStore() {
        store = LocalSaleDataSource(db.localSaleDao())
    }

    // ─── insertSale / getSaleById round-trip ──────────────────────────────────

    @Test
    fun insertSale_roundTripsViaGetSaleById() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(
            saleId = "sale-1",
            clientName = "Rosa Elena Martinez"
        )
        store.insertSale(sale)

        val got = store.getSaleById("sale-1")!!
        assertEquals("Rosa Elena Martinez", got.NOMBRE_CLIENTE)
    }

    @Test
    fun getSaleById_nullWhenAbsent() = runTest {
        assertNull(store.getSaleById("no-existe"))
    }

    // ─── getAllSales: ventana de 7 dias ────────────────────────────────────────

    @Test
    fun getAllSales_excludesSalesOlderThan7Days() = runTest {
        val now = Instant.now()
        val recent = TestDataFactory.createLocalSaleEntity(
            saleId = "reciente",
            fechaVenta = now.minusSeconds(60).toString()
        )
        val old = TestDataFactory.createLocalSaleEntity(
            saleId = "vieja",
            fechaVenta = now.minusSeconds(10 * 24 * 3600).toString()
        )
        store.insertSale(recent)
        store.insertSale(old)

        val all = store.getAllSales()

        assertEquals(
            "solo la venta dentro de la ventana de 7 dias aparece",
            listOf("reciente"),
            all.map { it.LOCAL_SALE_ID }
        )
    }

    @Test
    fun getAllSales_orderedByFechaVentaDesc() = runTest {
        val now = Instant.now()
        store.insertSale(
            TestDataFactory.createLocalSaleEntity(
                saleId = "a",
                fechaVenta = now.minusSeconds(3600).toString()
            )
        )
        store.insertSale(
            TestDataFactory.createLocalSaleEntity(
                saleId = "b",
                fechaVenta = now.minusSeconds(60).toString()
            )
        )

        assertEquals(listOf("b", "a"), store.getAllSales().map { it.LOCAL_SALE_ID })
    }

    // ─── imagenes ───────────────────────────────────────────────────────────

    @Test
    fun insertSaleImage_roundTripsViaGetImagesForSale() = runTest {
        store.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-1")
        )

        val images = store.getImagesForSale("sale-1")

        assertEquals(1, images.size)
        assertEquals("img-1", images.first().LOCAL_SALE_IMAGE_ID)
    }

    @Test
    fun deleteImagesForSale_removesAllImagesOfThatSale() = runTest {
        store.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-1")
        )
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-1")
        )

        store.deleteImagesForSale("sale-1")

        assertTrue(store.getImagesForSale("sale-1").isEmpty())
    }

    @Test
    fun insertSaleWithImages_insertsSaleAndAllImagesTogether() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-1")
        val images = listOf(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-1"),
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-1")
        )

        store.insertSaleWithImages(sale, images)

        assertEquals("sale-1", store.getSaleById("sale-1")!!.LOCAL_SALE_ID)
        assertEquals(
            listOf("img-1", "img-2").sorted(),
            store.getImagesForSale("sale-1").map { it.LOCAL_SALE_IMAGE_ID }.sorted()
        )
    }

    // ─── Task 3 [MONEY]: atomicidad de insertSaleWithImages (no partial-write) ──

    /**
     * Caracterización del bug PRE-EXISTENTE. La secuencia SIN transacción
     * (`insertSale` seguido de un loop de `insertSaleImage`) deja una venta a
     * medias cuando una imagen viola la FK a mitad del loop. Se reproduce con
     * llamadas crudas al DAO — la venta y la primera imagen SÍ quedan escritas.
     */
    @Test
    fun oldNonTransactionalSequence_leavesPartialWrite() = runTest {
        val dao = db.localSaleDao()
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "venta-vieja")
        val valida = TestDataFactory.createLocalSaleImageEntity(
            imageId = "img-valida",
            saleId = "venta-vieja"
        )
        // FK apunta a una venta inexistente: el 2do insert revienta a mitad.
        val huerfana = TestDataFactory.createLocalSaleImageEntity(
            imageId = "img-huerfana",
            saleId = "venta-fantasma"
        )

        try {
            dao.insertSale(sale)
            listOf(valida, huerfana).forEach { dao.insertSaleImage(it) }
        } catch (_: SQLiteException) {
            // esperado: la imagen huérfana viola la FK
        }

        // Sin transacción, la venta y la 1ra imagen quedaron escritas: partial-write.
        assertEquals("venta-vieja", dao.getSaleById("venta-vieja")?.LOCAL_SALE_ID)
        assertEquals(
            listOf("img-valida"),
            dao.getImagesForSale("venta-vieja").map { it.LOCAL_SALE_IMAGE_ID }
        )
    }

    /**
     * Versión NUEVA (transaccional): el mismo fallo a mitad de la secuencia
     * revierte TODO — ni la venta ni la imagen válida previa quedan
     * persistidas. Prueba que `insertSaleWithImages` es atómico.
     */
    @Test
    fun insertSaleWithImages_rollsBackEverythingWhenAnImageViolatesFk() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "venta-atomica")
        val images = listOf(
            TestDataFactory.createLocalSaleImageEntity(
                imageId = "img-valida",
                saleId = "venta-atomica"
            ),
            // FK apunta a una venta inexistente: revienta el 2do insert.
            TestDataFactory.createLocalSaleImageEntity(
                imageId = "img-huerfana",
                saleId = "venta-fantasma"
            )
        )

        val threw = try {
            store.insertSaleWithImages(sale, images)
            false
        } catch (_: SQLiteException) {
            true
        }

        assertTrue("el insert compuesto debe fallar por la FK inválida", threw)
        assertNull(
            "la venta no debe quedar a medias tras el rollback",
            store.getSaleById("venta-atomica")
        )
        assertTrue(
            "ninguna imagen debe persistir tras el rollback",
            store.getImagesForSale("venta-atomica").isEmpty()
        )
    }

    @Test
    fun deleteImageById_removesOnlyThatImage() = runTest {
        store.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-1")
        )
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-1")
        )

        store.deleteImageById("img-1")

        assertEquals(
            listOf("img-2"),
            store.getImagesForSale("sale-1").map { it.LOCAL_SALE_IMAGE_ID }
        )
    }

    @Test
    fun deleteImagesByIds_removesGivenIds() = runTest {
        store.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-1")
        )
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-2", saleId = "sale-1")
        )
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-3", saleId = "sale-1")
        )

        store.deleteImagesByIds(listOf("img-1", "img-3"))

        assertEquals(
            listOf("img-2"),
            store.getImagesForSale("sale-1").map { it.LOCAL_SALE_IMAGE_ID }
        )
    }

    @Test
    fun deleteImagesByIds_emptyListIsNoOp() = runTest {
        store.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-1")
        )

        // Guard explicito en el datasource: una lista vacia NO debe generar un
        // `IN ()` invalido ni borrar nada.
        store.deleteImagesByIds(emptyList())

        assertEquals(
            listOf("img-1"),
            store.getImagesForSale("sale-1").map { it.LOCAL_SALE_IMAGE_ID }
        )
    }

    @Test
    fun updateImageServerUuid_persistsUuid() = runTest {
        store.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-1")
        )

        store.updateImageServerUuid("img-1", "server-uuid-123")

        assertEquals(
            "server-uuid-123",
            store.getImagesForSale("sale-1").first().SERVER_UUID
        )
    }

    // ─── estado de envio ───────────────────────────────────────────────────

    @Test
    fun changeSaleStatus_and_getPendingSales() = runTest {
        store.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "pend-1"))
        store.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sent-1"))
        store.changeSaleStatus("sent-1", enviado = true)

        val pending = store.getPendingSales()

        assertEquals(listOf("pend-1"), pending.map { it.LOCAL_SALE_ID })
    }

    // ─── updateSale: UPDATE real, preserva imagenes (no CASCADE) ─────────────

    @Test
    fun updateSale_changesFieldsAndPreservesImages() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-1", clientName = "Original")
        store.insertSale(sale)
        store.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(imageId = "img-1", saleId = "sale-1")
        )

        val updated = TestDataFactory.createLocalSaleEntity(
            saleId = "sale-1",
            clientName = "Actualizado"
        )
        store.updateSale(updated)

        assertEquals("Actualizado", store.getSaleById("sale-1")!!.NOMBRE_CLIENTE)
        assertEquals(
            "updateSale usa UPDATE real, no REPLACE: no dispara el CASCADE que borraria imagenes",
            listOf("img-1"),
            store.getImagesForSale("sale-1").map { it.LOCAL_SALE_IMAGE_ID }
        )
    }

    // ─── equivalencia inyectado ⇔ puente context ──────────────────────────────

    @Test
    fun injectedFormEquivalentToContextForm() = runTest {
        store.insertSale(
            TestDataFactory.createLocalSaleEntity(saleId = "eq-1", clientName = "Fernando Ramirez")
        )

        // Tipo explicito: los dos constructores de un arg (DAO vs Context)
        // hacen ambigua la inferencia de `getApplicationContext<T>()`.
        val contextForm = LocalSaleDataSource(ApplicationProvider.getApplicationContext<Context>())

        // LocalSaleEntity es `class` (no `data class`): sin equals/hashCode
        // generados, assertEquals compararia por referencia. Comparamos el
        // campo que probamos que viajo.
        assertEquals(
            "ambos constructores resuelven a la misma DB",
            store.getSaleById("eq-1")!!.NOMBRE_CLIENTE,
            contextForm.getSaleById("eq-1")!!.NOMBRE_CLIENTE
        )
    }
}
