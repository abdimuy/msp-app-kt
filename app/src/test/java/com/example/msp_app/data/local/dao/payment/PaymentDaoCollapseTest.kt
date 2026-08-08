package com.example.msp_app.data.local.dao.payment

import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.`test-fixtures`.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre [PaymentDao.findCollapsibleUuidTwins]: la red de seguridad idempotente
 * que el reconciliador usa para colapsar el gemelo UUID de un pago cuando su
 * versión numérica ya llegó (PAGO_RECIBIDO_ID persistido) pero mergePagos no
 * lo colapsó de un solo tiro (carrera pull-vs-markDone / histórico).
 */
class PaymentDaoCollapseTest : RoomTestBase() {

    private fun uuidPayment(id: String, guardado: Boolean, doctoCcId: Int = 100) = PaymentEntity(
        ID = id,
        COBRADOR = "Rosa Elena Martinez Vazquez",
        DOCTO_CC_ACR_ID = doctoCcId,
        DOCTO_CC_ID = doctoCcId + 1,
        FECHA_HORA_PAGO = "2026-06-01T09:00:00Z",
        GUARDADO_EN_MICROSIP = guardado,
        IMPORTE = 350.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 4821,
        COBRADOR_ID = 7,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 21,
        NOMBRE_CLIENTE = "Guadalupe Hernandez Soto"
    )

    private fun numericPayment(id: String, pagoRecibidoId: String?, doctoCcId: Int = 100) =
        PaymentEntity(
            ID = id,
            COBRADOR = "Rosa Elena Martinez Vazquez",
            DOCTO_CC_ACR_ID = doctoCcId,
            DOCTO_CC_ID = doctoCcId + 1,
            FECHA_HORA_PAGO = "2026-06-01T09:00:05Z",
            GUARDADO_EN_MICROSIP = true,
            IMPORTE = 350.0,
            LAT = null,
            LNG = null,
            CLIENTE_ID = 4821,
            COBRADOR_ID = 7,
            FORMA_COBRO_ID = 157,
            ZONA_CLIENTE_ID = 21,
            NOMBRE_CLIENTE = "Guadalupe Hernandez Soto",
            PAGO_RECIBIDO_ID = pagoRecibidoId
        )

    @Test
    fun uploadedUuidTwinWithMatchingNumericIsCollapsible() = runTest {
        db.paymentDao().saveAll(
            listOf(
                uuidPayment(id = "uuid-x", guardado = true),
                numericPayment(id = "15808629", pagoRecibidoId = "uuid-x")
            )
        )

        val collapsible = db.paymentDao().findCollapsibleUuidTwins()
        assertEquals(listOf("uuid-x"), collapsible)

        db.paymentDao().deleteByIDs(collapsible)

        assertNull(db.paymentDao().getPaymentById("uuid-x"))
        assertNotNull(db.paymentDao().getPaymentById("15808629"))
    }

    @Test
    fun pendingUuidTwinIsNeverCollapsible() = runTest {
        db.paymentDao().saveAll(
            listOf(
                uuidPayment(id = "uuid-pendiente", guardado = false),
                numericPayment(id = "15808630", pagoRecibidoId = "uuid-pendiente")
            )
        )

        val collapsible = db.paymentDao().findCollapsibleUuidTwins()

        assertTrue("un UUID aun no subido nunca debe colapsarse", collapsible.isEmpty())
        assertNotNull(db.paymentDao().getPaymentById("uuid-pendiente"))
    }

    @Test
    fun numericPaymentWithoutPagoRecibidoIdYieldsEmptyList() = runTest {
        db.paymentDao().saveAll(
            listOf(
                uuidPayment(id = "uuid-y", guardado = true),
                numericPayment(id = "15808631", pagoRecibidoId = null)
            )
        )

        val collapsible = db.paymentDao().findCollapsibleUuidTwins()

        assertTrue(collapsible.isEmpty())
    }
}
