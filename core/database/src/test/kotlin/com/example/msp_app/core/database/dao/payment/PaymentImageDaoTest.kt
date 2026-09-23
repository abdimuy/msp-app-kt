package com.example.msp_app.core.database.dao.payment

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.PaymentImageEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Las consultas de `pago_imagenes` contra el Room real (Robolectric, DB en
 * memoria) — el SQL, no la intención.
 *
 * Lo que se prueba aquí y no río arriba: que el filtro sea por la columna que
 * dice su nombre. `getByPagoId` filtra por **`PAGO_ID`** y `marcarSubida` por
 * **`ID`**; son dos columnas con dos significados (el pago y la imagen) y este
 * plan ya cazó siete defectos de la familia "un id donde iba el otro". Un test
 * con una sola fila no distinguiría un filtro del otro, así que todos los de
 * abajo siembran **dos pagos** y **dos imágenes**.
 */
class PaymentImageDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase
    private lateinit var dao: PaymentImageDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.paymentImageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `getByPagoId trae solo las del pago pedido, en orden`() = runTest {
        dao.insertAll(
            listOf(
                imagen("IMG-B", pagoId = "PAGO-1", orden = 1),
                imagen("IMG-A", pagoId = "PAGO-1", orden = 0),
                imagen("IMG-C", pagoId = "PAGO-2", orden = 0)
            )
        )

        assertEquals(listOf("IMG-A", "IMG-B"), dao.getByPagoId("PAGO-1").map { it.ID })
        assertEquals(listOf("IMG-C"), dao.getByPagoId("PAGO-2").map { it.ID })
    }

    /** `SUBIDA_EN IS NULL` es la definición de "pendiente". */
    @Test
    fun `getPendientesDe deja fuera las ya subidas`() = runTest {
        dao.insertAll(
            listOf(
                imagen("IMG-A", orden = 0, subidaEn = "2026-09-01T10:00:00Z"),
                imagen("IMG-B", orden = 1)
            )
        )

        assertEquals(listOf("IMG-B"), dao.getPendientesDe("PAGO-1").map { it.ID })
    }

    /**
     * **`marcarSubida` filtra por el id de la IMAGEN.** Con las dos del mismo
     * pago, un filtro por `PAGO_ID` marcaría las dos — y declararía entregada
     * una foto que el servidor nunca recibió.
     */
    @Test
    fun `marcarSubida toca una sola fila, la de esa imagen`() = runTest {
        dao.insertAll(listOf(imagen("IMG-A", orden = 0), imagen("IMG-B", orden = 1)))

        dao.marcarSubida("IMG-A", "2026-09-04T18:00:00Z")

        val filas = dao.getByPagoId("PAGO-1").associateBy { it.ID }
        assertEquals("2026-09-04T18:00:00Z", filas.getValue("IMG-A").SUBIDA_EN)
        assertNull(filas.getValue("IMG-B").SUBIDA_EN)
    }

    /** La PK es el UUID del teléfono: reinsertar la misma captura no la duplica. */
    @Test
    fun `insertar dos veces la misma imagen no la duplica`() = runTest {
        dao.insertAll(listOf(imagen("IMG-A", orden = 0)))
        dao.insertAll(listOf(imagen("IMG-A", orden = 0)))

        assertEquals(1, dao.getByPagoId("PAGO-1").size)
    }

    @Test
    fun `rutasVivas devuelve las rutas de todas las filas`() = runTest {
        dao.insertAll(listOf(imagen("IMG-A", orden = 0), imagen("IMG-B", pagoId = "PAGO-2")))

        assertEquals(setOf("/files/IMG-A.jpg", "/files/IMG-B.jpg"), dao.rutasVivas().toSet())
    }

    // --- El barrido: las DOS condiciones, cada una con su control ------------

    /**
     * Huérfana **y** vieja: su pago ya no está en `Payment` y se creó antes del
     * corte. Es la única que se barre.
     */
    @Test
    fun `huerfanasAnterioresA trae la vieja cuyo pago ya no existe`() = runTest {
        sembrarPago("PAGO-VIVO")
        dao.insertAll(
            listOf(
                imagen("IMG-HUERFANA", pagoId = "PAGO-IDO", creadaEn = "2026-08-01T10:00:00Z"),
                imagen("IMG-CON-PAGO", pagoId = "PAGO-VIVO", creadaEn = "2026-08-01T10:00:00Z"),
                imagen("IMG-RECIENTE", pagoId = "PAGO-IDO", creadaEn = "2026-09-04T10:00:00Z")
            )
        )

        val barridas = dao.huerfanasAnterioresA("2026-08-28T18:00:00Z")

        assertEquals(listOf("IMG-HUERFANA"), barridas.map { it.ID })
    }

    /**
     * **El caso que el KDoc de `PaymentImageEntity` obliga a proteger.** El pago
     * capturado se borra y se reinserta con otra llave en cada merge del sync:
     * durante ese instante sus comprobantes quedan sin padre. Barrer sin mirar
     * la antigüedad se los llevaría — comprobantes de dinero ya cobrado.
     */
    @Test
    fun `una huerfana reciente NO se barre`() = runTest {
        dao.insertAll(
            listOf(imagen("IMG-RECIEN", pagoId = "PAGO-IDO", creadaEn = "2026-09-04T10:00:00Z"))
        )

        assertTrue(dao.huerfanasAnterioresA("2026-08-28T18:00:00Z").isEmpty())
    }

    @Test
    fun `eliminar borra solo la fila de esa imagen`() = runTest {
        dao.insertAll(listOf(imagen("IMG-A", orden = 0), imagen("IMG-B", orden = 1)))

        dao.eliminar("IMG-A")

        assertEquals(listOf("IMG-B"), dao.getByPagoId("PAGO-1").map { it.ID })
    }

    private suspend fun sembrarPago(id: String) {
        database.paymentDao().savePayment(
            PaymentEntity(
                ID = id,
                COBRADOR = "Mendoza Torres, Ana",
                DOCTO_CC_ACR_ID = 5000,
                DOCTO_CC_ID = 6000,
                FECHA_HORA_PAGO = "2026-09-01T09:30:00Z",
                GUARDADO_EN_MICROSIP = false,
                IMPORTE = 220.0,
                LAT = 0.0,
                LNG = 0.0,
                CLIENTE_ID = 11486,
                COBRADOR_ID = 200,
                FORMA_COBRO_ID = 157,
                ZONA_CLIENTE_ID = 21552,
                NOMBRE_CLIENTE = "López García, Minerva"
            )
        )
    }

    private fun imagen(
        id: String,
        pagoId: String = "PAGO-1",
        orden: Int = 0,
        creadaEn: String = "2026-09-04T17:00:00Z",
        subidaEn: String? = null
    ) = PaymentImageEntity(
        ID = id,
        PAGO_ID = pagoId,
        URI = "/files/$id.jpg",
        MIME = "image/jpeg",
        ORDEN = orden,
        CREADA_EN = creadaEn,
        SUBIDA_EN = subidaEn
    )
}
