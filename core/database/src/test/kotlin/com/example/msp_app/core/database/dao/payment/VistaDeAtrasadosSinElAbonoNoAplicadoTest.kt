package com.example.msp_app.core.database.dao.payment

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.AppDatabase
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.database.entities.SaleEntity
import com.example.msp_app.core.testing.RobolectricTestBase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * E-APP-092: `overdue_payments_view` no cuenta el abono -2 ("no se aplicó": el
 * servidor lo rechazó por saldo y nunca llegó a Microsip). Contarlo subía
 * `NUM_IMPORTES`, adelantaba `FECHA_ULT_PAGO` y escondía atraso real de la venta.
 */
class VistaDeAtrasadosSinElAbonoNoAplicadoTest : RobolectricTestBase() {

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

    private fun venta(id: Int) = SaleEntity(
        DOCTO_CC_ACR_ID = id,
        DOCTO_CC_ID = id,
        FOLIO = "F-$id",
        CLIENTE_ID = 30144,
        APLICADO = "N",
        COBRADOR_ID = 7,
        CLIENTE = "Araceli Jiménez Cortés",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Puebla Centro",
        IMPORTE_PAGO_PROMEDIO = null,
        TOTAL_IMPORTE = 0.0,
        NUM_IMPORTES = 0,
        FECHA = "2026-08-04T15:30:00Z",
        PARCIALIDAD = 350,
        ENGANCHE = 500.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 6800.0,
        IMPTE_REST = 0.0,
        SALDO_REST = 5000.0,
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
        FREC_PAGO = "SEMANAL"
    )

    private fun pago(id: String, acr: Int, doctoCcId: Int, fecha: String) = PaymentEntity(
        ID = id,
        COBRADOR = "Efraín Domínguez Reyes",
        DOCTO_CC_ACR_ID = acr,
        DOCTO_CC_ID = doctoCcId,
        FECHA_HORA_PAGO = fecha,
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 350.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 30144,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Araceli Jiménez Cortés"
    )

    @Test
    fun `E-APP-092 la vista no cuenta el abono -2 ni adelanta la fecha del ultimo pago`() =
        runTest {
            database.saleDao().insertAll(listOf(venta(48213)))
            val dao = database.paymentDao()
            dao.savePayment(pago("a-1", 48213, 91027, "2026-08-11T16:00:00Z"))
            dao.savePayment(pago("a-2", 48213, -2, "2026-10-20T16:00:00Z"))

            val fila = dao.getOverduePaymentBySaleId(48213)!!

            assertEquals(1, fila.NUM_IMPORTES)
            assertEquals("2026-08-11T16:00:00Z", fila.FECHA_ULT_PAGO)
        }

    @Test
    fun `E-APP-092 control positivo sin el -2 la vista cuenta los dos abonos reales`() = runTest {
        database.saleDao().insertAll(listOf(venta(48214)))
        val dao = database.paymentDao()
        dao.savePayment(pago("b-1", 48214, 91028, "2026-08-11T16:00:00Z"))
        dao.savePayment(pago("b-2", 48214, 91029, "2026-10-20T16:00:00Z"))

        val fila = dao.getOverduePaymentBySaleId(48214)!!

        assertEquals(2, fila.NUM_IMPORTES)
        assertEquals("2026-10-20T16:00:00Z", fila.FECHA_ULT_PAGO)
    }
}
