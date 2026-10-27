package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.database.entities.LocalSaleEntity
import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prioridad del dinero en el barrido (adenda de E-APP-079): el adaptador
 * solo lee los dos conteos del menú lateral. La prueba siembra con los
 * `insert` que ya existen; el adaptador no escribe nada.
 */
class RoomDineroPendienteTest : RoomTestBase() {

    private fun dinero() = RoomDineroPendiente(db.paymentDao(), db.localSaleDao())

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

    @Test
    fun `E-APP-079 sin pagos ni ventas por subir no hay dinero pendiente`() = runTest {
        db.paymentDao().saveAll(listOf(pago("a", guardado = true)))
        db.localSaleDao().insertSale(venta("v1", enviado = true))
        db.localSaleDao().insertSale(venta("v2", enviado = false, permanente = true))
        assertFalse(dinero().hayPendientes())
    }

    @Test
    fun `E-APP-079 un pago sin guardar es dinero pendiente`() = runTest {
        db.paymentDao().saveAll(listOf(pago("a", guardado = false)))
        assertTrue(dinero().hayPendientes())
    }

    @Test
    fun `E-APP-079 una venta local sin subir es dinero pendiente`() = runTest {
        db.localSaleDao().insertSale(venta("v1", enviado = false))
        assertTrue(dinero().hayPendientes())
    }
}
