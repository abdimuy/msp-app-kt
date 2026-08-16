package com.example.msp_app.data.local.dao.payment

import com.example.msp_app.core.database.entities.PaymentEntity
import com.example.msp_app.core.testing.RoomTestBase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre [PaymentDao.findCollapsibleUuidTwins]: la red de seguridad idempotente
 * que el reconciliador usa para colapsar el gemelo UUID de un pago cuando su
 * versión numérica ya llegó (PAGO_RECIBIDO_ID persistido) pero mergePagos no
 * lo colapsó de un solo tiro (carrera pull-vs-markDone / histórico).
 *
 * El criterio del colapso es la evidencia del servidor (existe otra fila que
 * nombra ese UUID en `PAGO_RECIBIDO_ID`), NO la bandera local
 * `GUARDADO_EN_MICROSIP`. Lo que protege a una captura que nunca subió es que
 * el servidor no puede nombrar un UUID que no recibió.
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

    /**
     * D4 — la carrera real, en el barrido auto-sanable.
     *
     * El aviso del servidor sale dentro de la misma transacción que escribe el
     * pago, así que la fila numérica con `PAGO_RECIBIDO_ID` puede llegar antes
     * de que `PendingPaymentsWorker.markDone` marque `GUARDADO_EN_MICROSIP`; y
     * si la respuesta HTTP nunca llega, la bandera se queda en 0 para siempre.
     * El criterio viejo (`GUARDADO_EN_MICROSIP = 1`) dejaba el gemelo UUID sin
     * colapsar justo en ese caso, que es el duplicado reportado en campo.
     *
     * Con el criterio nuevo la evidencia es la referencia del servidor, así
     * que el gemelo colapsa aunque la bandera todavía diga 0.
     */
    @Test
    fun pendingUuidTwinReferencedByServerIsCollapsible() = runTest {
        db.paymentDao().saveAll(
            listOf(
                uuidPayment(id = "uuid-pendiente", guardado = false),
                numericPayment(id = "15808630", pagoRecibidoId = "uuid-pendiente")
            )
        )

        val collapsible = db.paymentDao().findCollapsibleUuidTwins()

        assertEquals(
            "el servidor ya nombró el UUID: es prueba de que el pago está en " +
                "Microsip, más fuerte que la bandera local",
            listOf("uuid-pendiente"),
            collapsible
        )

        db.paymentDao().deleteByIDs(collapsible)
        assertNull(db.paymentDao().getPaymentById("uuid-pendiente"))
        assertNotNull(db.paymentDao().getPaymentById("15808630"))
    }

    /**
     * La protección de fondo: una captura pendiente que NUNCA llegó al
     * servidor no está referenciada por ningún `PAGO_RECIBIDO_ID` — el UUID lo
     * genera el teléfono y el servidor no puede nombrar uno que no recibió —
     * así que jamás entra al colapso. Aquí hay filas numéricas del mismo
     * cargo, e incluso una que referencia OTRO UUID: nada la toca.
     */
    @Test
    fun neverUploadedPendingPaymentIsNeverCollapsible() = runTest {
        db.paymentDao().saveAll(
            listOf(
                uuidPayment(id = "uuid-nunca-subido", guardado = false),
                uuidPayment(id = "uuid-otro", guardado = true),
                numericPayment(id = "15808632", pagoRecibidoId = "uuid-otro"),
                numericPayment(id = "15808633", pagoRecibidoId = null)
            )
        )

        val collapsible = db.paymentDao().findCollapsibleUuidTwins()

        assertEquals(
            "sólo el UUID que el servidor nombró es colapsable",
            listOf("uuid-otro"),
            collapsible
        )
        db.paymentDao().deleteByIDs(collapsible)
        assertNotNull(
            "una captura que nunca subió jamás se borra",
            db.paymentDao().getPaymentById("uuid-nunca-subido")
        )
        assertFalse(db.paymentDao().getPaymentById("uuid-nunca-subido")!!.GUARDADO_EN_MICROSIP)
    }

    /**
     * No hay colapso cruzado: el match es por UUID exacto, nunca por
     * contenido, cargo o monto. Dos pagos distintos del mismo cargo y del
     * mismo importe conviven sin borrarse entre sí.
     */
    @Test
    fun collapseDoesNotCrossBetweenDistinctPayments() = runTest {
        db.paymentDao().saveAll(
            listOf(
                uuidPayment(id = "uuid-a", guardado = true),
                uuidPayment(id = "uuid-b", guardado = true),
                numericPayment(id = "15808634", pagoRecibidoId = "uuid-a")
            )
        )

        val collapsible = db.paymentDao().findCollapsibleUuidTwins()

        assertEquals(listOf("uuid-a"), collapsible)
        db.paymentDao().deleteByIDs(collapsible)
        assertNotNull(
            "el gemelo de otro pago no se toca",
            db.paymentDao().getPaymentById("uuid-b")
        )
        assertNotNull(db.paymentDao().getPaymentById("15808634"))
    }

    /**
     * Cerrojo de auto-referencia: una fila que se apunte a sí misma no puede
     * colapsarse — sería borrar la única copia del pago.
     */
    @Test
    fun selfReferencingRowIsNeverCollapsible() = runTest {
        db.paymentDao().saveAll(
            listOf(numericPayment(id = "15808635", pagoRecibidoId = "15808635"))
        )

        val collapsible = db.paymentDao().findCollapsibleUuidTwins()

        assertTrue("una fila que se apunta a sí misma no es colapsable", collapsible.isEmpty())
        assertNotNull(db.paymentDao().getPaymentById("15808635"))
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

    /**
     * [PaymentDao.filterExistingIDs] es la mitad que usa `mergePagos` (los
     * `pago_recibido_id` vienen de la página del servidor, no de una fila
     * local). Mismo contrato: la bandera no filtra, la existencia sí.
     */
    @Test
    fun filterExistingIDsIgnoresTheLocalFlag() = runTest {
        db.paymentDao().saveAll(
            listOf(
                uuidPayment(id = "uuid-pend", guardado = false),
                uuidPayment(id = "uuid-conf", guardado = true)
            )
        )

        val found = db.paymentDao()
            .filterExistingIDs(listOf("uuid-pend", "uuid-conf", "uuid-inexistente"))

        assertEquals(setOf("uuid-pend", "uuid-conf"), found.toSet())
    }
}
