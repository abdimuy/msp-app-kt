package com.example.msp_app.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.CobranzaSyncStateEntity
import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/** Las tres lecturas que alimentan la tarjeta de envío del menú lateral (spec §4). */
class ConteosDelMenuLateralDaoTest : RobolectricTestBase() {

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun pago(id: String, guardado: Boolean) = PaymentEntity(
        ID = id,
        COBRADOR = "Efraín Domínguez Reyes",
        DOCTO_CC_ACR_ID = 48213,
        DOCTO_CC_ID = if (guardado) 91027 else 0,
        FECHA_HORA_PAGO = "2026-10-06T16:45:00Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 350.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Araceli Jiménez Cortés"
    )

    private fun venta(id: String, enviado: Boolean, permanente: Boolean? = null) = LocalSaleEntity(
        LOCAL_SALE_ID = id,
        NOMBRE_CLIENTE = "Rosa Elena Martínez Vázquez",
        FECHA_VENTA = "2026-10-06T15:30:00Z",
        LATITUD = 19.043415,
        LONGITUD = -98.198234,
        DIRECCION = "Privada de las Rosas 45",
        PARCIALIDAD = 850.0,
        ENGANCHE = 500.0,
        TELEFONO = "2221234567",
        FREC_PAGO = "SEMANAL",
        AVAL_O_RESPONSABLE = null,
        NOTA = null,
        DIA_COBRANZA = "MARTES",
        PRECIO_TOTAL = 6800.0,
        TIEMPO_A_CORTO_PLAZOMESES = 8,
        MONTO_A_CORTO_PLAZO = 6300.0,
        MONTO_DE_CONTADO = 5800.0,
        ENVIADO = enviado,
        LAST_UPLOAD_PERMANENT = permanente
    )

    private fun estado(recurso: String) = CobranzaSyncStateEntity(
        RESOURCE = recurso,
        ZONA_CLIENTE_ID = 21,
        CURSOR = "2026-10-06T16:00:00Z",
        LAST_SYNCED_AT = "2026-10-06T16:00:00Z",
        LAST_ERROR = null
    )

    private fun ventaDeZona(doctoCcAcrId: Int, zonaId: Int, zonaNombre: String) = SaleEntity(
        DOCTO_CC_ACR_ID = doctoCcAcrId,
        DOCTO_CC_ID = doctoCcAcrId,
        FOLIO = "F-$doctoCcAcrId",
        CLIENTE_ID = 30144,
        APLICADO = "N",
        COBRADOR_ID = 7,
        CLIENTE = "Araceli Jiménez Cortés",
        ZONA_CLIENTE_ID = zonaId,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = zonaNombre,
        IMPORTE_PAGO_PROMEDIO = null,
        TOTAL_IMPORTE = 0.0,
        NUM_IMPORTES = 0,
        FECHA = "2026-10-06T15:30:00Z",
        PARCIALIDAD = 1,
        ENGANCHE = 0.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 0.0,
        IMPTE_REST = 0.0,
        SALDO_REST = 0.0,
        FECHA_ULT_PAGO = null,
        CALLE = "",
        CIUDAD = "",
        ESTADO = "",
        TELEFONO = "",
        NOMBRE_COBRADOR = "",
        ESTADO_COBRANZA = "",
        DIA_COBRANZA = "",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 0.0,
        AVAL_O_RESPONSABLE = "",
        FREC_PAGO = null
    )

    @Test
    fun `cuenta solo los pagos sin guardar y se actualiza al insertar`() = runTest {
        val dao = database.paymentDao()
        dao.saveAll(listOf(pago("a", guardado = false), pago("b", guardado = true)))

        dao.observarPendientesDeSubir().test {
            assertEquals(1, awaitItem())
            dao.saveAll(listOf(pago("c", guardado = false)))
            assertEquals(2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `cuenta ventas sin subir y excluye las que el servidor ya resguardo`() = runTest {
        val dao = database.localSaleDao()
        dao.insertSale(venta("v1", enviado = false))
        dao.insertSale(venta("v2", enviado = false, permanente = false))
        dao.insertSale(venta("v3", enviado = false, permanente = true))
        dao.insertSale(venta("v4", enviado = true))

        assertEquals(2, dao.observarSinSubir().first())
    }

    @Test
    fun `observa solo los recursos pedidos`() = runTest {
        val dao = database.cobranzaSyncStateDao()
        dao.upsert(estado("ventas"))
        dao.upsert(estado("pagos"))
        dao.upsert(estado("migration_purge_legacy_pago_ids"))

        val vistos = dao.observarRecursos(listOf("ventas", "pagos")).first()

        assertEquals(setOf("ventas", "pagos"), vistos.map { it.RESOURCE }.toSet())
    }

    @Test
    fun `nombre de zona viene de una venta con ese ZONA_CLIENTE_ID`() = runTest {
        val dao = database.saleDao()
        dao.insertAll(listOf(ventaDeZona(1, zonaId = 25, zonaNombre = "R/25")))

        assertEquals("R/25", dao.observarNombreDeZona(25).first())
    }

    @Test
    fun `nombre de zona es nulo sin ventas de esa zona`() = runTest {
        val dao = database.saleDao()

        assertNull(dao.observarNombreDeZona(25).first())
    }

    @Test
    fun `nombre de zona es nulo si el nombre esta vacio`() = runTest {
        val dao = database.saleDao()
        dao.insertAll(listOf(ventaDeZona(1, zonaId = 25, zonaNombre = "   ")))

        assertNull(dao.observarNombreDeZona(25).first())
    }

    @Test
    fun `ventas de otra zona no cuentan para el nombre`() = runTest {
        val dao = database.saleDao()
        dao.insertAll(listOf(ventaDeZona(1, zonaId = 99, zonaNombre = "R/99")))

        assertNull(dao.observarNombreDeZona(25).first())
    }
}
