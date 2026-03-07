package com.example.msp_app.data.local.dao

import com.example.msp_app.`test-fixtures`.RoomTestBase
import com.example.msp_app.`test-fixtures`.TestDataFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LocalSaleDaoTest : RoomTestBase() {

    private val dao get() = db.localSaleDao()

    @Test
    fun `insert and getSaleById roundtrip`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-1")
        dao.insertSale(sale)
        val retrieved = dao.getSaleById("sale-1")
        assertNotNull(retrieved)
        assertEquals("sale-1", retrieved!!.LOCAL_SALE_ID)
        assertEquals("Juan Perez", retrieved.NOMBRE_CLIENTE)
    }

    @Test
    fun `getSaleById returns null for nonexistent`() = runTest {
        val result = dao.getSaleById("nonexistent")
        assertNull(result)
    }

    @Test
    fun `REPLACE on duplicate ID overwrites`() = runTest {
        val sale1 = TestDataFactory.createLocalSaleEntity(saleId = "sale-1", clientName = "Juan")
        val sale2 = TestDataFactory.createLocalSaleEntity(saleId = "sale-1", clientName = "Pedro")
        dao.insertSale(sale1)
        dao.insertSale(sale2)
        val retrieved = dao.getSaleById("sale-1")
        assertEquals("Pedro", retrieved!!.NOMBRE_CLIENTE)
    }

    @Test
    fun `updateSaleStatus changes enviado flag`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-1", enviado = false)
        dao.insertSale(sale)
        dao.updateSaleStatus("sale-1", true)
        val retrieved = dao.getSaleById("sale-1")
        assertTrue(retrieved!!.ENVIADO)
    }

    @Test
    fun `getSalesByStatus filters correctly`() = runTest {
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1", enviado = false))
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-2", enviado = true))
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-3", enviado = false))
        val pending = dao.getSalesByStatus(false)
        assertEquals(2, pending.size)
        val sent = dao.getSalesByStatus(true)
        assertEquals(1, sent.size)
    }

    @Test
    fun `image insert and get roundtrip`() = runTest {
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        val image = TestDataFactory.createLocalSaleImageEntity(saleId = "sale-1", imageId = "img-1")
        dao.insertSaleImage(image)
        val images = dao.getImagesForSale("sale-1")
        assertEquals(1, images.size)
        assertEquals("img-1", images[0].LOCAL_SALE_IMAGE_ID)
    }

    @Test
    fun `deleteImagesForSale removes images`() = runTest {
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        dao.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(saleId = "sale-1", imageId = "img-1")
        )
        dao.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(saleId = "sale-1", imageId = "img-2")
        )
        dao.deleteImagesForSale("sale-1")
        val images = dao.getImagesForSale("sale-1")
        assertTrue(images.isEmpty())
    }

    @Test
    fun `FK cascade deletes images when sale deleted via REPLACE`() = runTest {
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        dao.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(saleId = "sale-1", imageId = "img-1")
        )
        // REPLACE triggers delete+insert, which cascades
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1", clientName = "New"))
        val images = dao.getImagesForSale("sale-1")
        assertTrue(images.isEmpty())
    }

    @Test
    fun `updateSaleFields preserves images (no cascade)`() = runTest {
        val sale = TestDataFactory.createLocalSaleEntity(saleId = "sale-1")
        dao.insertSale(sale)
        dao.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(saleId = "sale-1", imageId = "img-1")
        )

        dao.updateSaleFields(
            localSaleId = "sale-1",
            nombreCliente = "Updated Name",
            fechaVenta = sale.FECHA_VENTA,
            latitud = sale.LATITUD,
            longitud = sale.LONGITUD,
            direccion = sale.DIRECCION,
            parcialidad = sale.PARCIALIDAD,
            enganche = sale.ENGANCHE,
            telefono = sale.TELEFONO,
            frecPago = sale.FREC_PAGO,
            avalOResponsable = sale.AVAL_O_RESPONSABLE,
            nota = sale.NOTA,
            diaCobranza = sale.DIA_COBRANZA,
            precioTotal = sale.PRECIO_TOTAL,
            tiempoACortoPlazoMeses = sale.TIEMPO_A_CORTO_PLAZOMESES,
            montoACortoPlazo = sale.MONTO_A_CORTO_PLAZO,
            montoDeContado = sale.MONTO_DE_CONTADO,
            enviado = sale.ENVIADO,
            numero = sale.NUMERO,
            colonia = sale.COLONIA,
            poblacion = sale.POBLACION,
            ciudad = sale.CIUDAD,
            tipoVenta = sale.TIPO_VENTA,
            zonaClienteId = sale.ZONA_CLIENTE_ID,
            zonaCliente = sale.ZONA_CLIENTE,
            clienteId = sale.CLIENTE_ID
        )

        val images = dao.getImagesForSale("sale-1")
        assertEquals(1, images.size)
        val updated = dao.getSaleById("sale-1")
        assertEquals("Updated Name", updated!!.NOMBRE_CLIENTE)
    }

    @Test
    fun `deleteImageById removes specific image`() = runTest {
        dao.insertSale(TestDataFactory.createLocalSaleEntity(saleId = "sale-1"))
        dao.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(saleId = "sale-1", imageId = "img-1")
        )
        dao.insertSaleImage(
            TestDataFactory.createLocalSaleImageEntity(saleId = "sale-1", imageId = "img-2")
        )
        dao.deleteImageById("img-1")
        val images = dao.getImagesForSale("sale-1")
        assertEquals(1, images.size)
        assertEquals("img-2", images[0].LOCAL_SALE_IMAGE_ID)
    }
}
