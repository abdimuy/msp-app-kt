package com.example.msp_app.features.payments.newpayment

import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.FrecuenciaPago
import com.example.msp_app.data.models.sale.Sale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Characterization test for [PaymentFactory.fromSale] — freezes the
 * attribution mapping (Q6) extracted verbatim from
 * `NewPaymentDialog.handleSavePayment()`. If this test starts failing after
 * an edit to [PaymentFactory], the edit is changing money-attribution
 * behavior and needs an explicit product decision, not a quiet tweak.
 */
class PaymentFactoryTest {

    // The sale's own COBRADOR_ID (12) deliberately differs from the current
    // user's COBRADOR_ID (77) below — this is the subtle part of the
    // contract: attribution follows whoever is registering the payment
    // (currentUser), not the cobrador nominally assigned to the sale.
    private val sale = Sale(
        DOCTO_CC_ACR_ID = 55231,
        DOCTO_CC_ID = 90112,
        FOLIO = "CV-55231",
        CLIENTE_ID = 8842,
        APLICADO = "S",
        COBRADOR_ID = 12,
        CLIENTE = "Maria Guadalupe Hernandez Cortes",
        ZONA_CLIENTE_ID = 21,
        LIMITE_CREDITO = 15000.0,
        NOTAS = "",
        ZONA_NOMBRE = "R/21",
        IMPORTE_PAGO_PROMEDIO = 300.0,
        TOTAL_IMPORTE = 4800.0,
        NUM_IMPORTES = 16,
        FECHA = "2026-02-15T00:00:00Z",
        PARCIALIDAD = 300,
        ENGANCHE = 500.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "Roberto Sanchez Luna",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 5300.0,
        IMPTE_REST = 3200.0,
        SALDO_REST = 3200.0,
        FECHA_ULT_PAGO = "2026-07-10T14:00:00Z",
        CALLE = "AV INDEPENDENCIA 245",
        CIUDAD = "TEHUACAN",
        ESTADO = "PUEBLA",
        TELEFONO = "2381234567",
        NOMBRE_COBRADOR = "Fernando Ramirez Ortiz",
        ESTADO_COBRANZA = EstadoCobranza.PENDIENTE,
        DIA_COBRANZA = "VIERNES",
        DIA_TEMPORAL_COBRANZA = "VIERNES",
        PRECIO_DE_CONTADO = 4800.0,
        AVAL_O_RESPONSABLE = "Jose Luis Hernandez Cortes",
        FREC_PAGO = FrecuenciaPago.SEMANAL
    )

    private val currentUser = User(
        ID = "uid-cobrador-77",
        NOMBRE = "Alejandro Martinez Reyes",
        EMAIL = "alejandro.martinez@muebleriamsp.mx",
        COBRADOR_ID = 77,
        TELEFONO = "2387654321",
        ZONA_CLIENTE_ID = 21
    )

    @Test
    fun `builds payment with exact attribution mapping`() {
        val payment = PaymentFactory.fromSale(
            sale = sale,
            currentUser = currentUser,
            importe = 300.0,
            formaCobroId = 1,
            id = "ticket-abc-123",
            fecha = "2026-07-28T16:45:00Z"
        )

        assertEquals(sale.NOMBRE_COBRADOR, payment.COBRADOR)
        assertEquals(currentUser.COBRADOR_ID, payment.COBRADOR_ID)
        // The subtle part: attribution must come from currentUser, never
        // from the sale's own (possibly stale/different) cobrador.
        assertNotEquals(sale.COBRADOR_ID, payment.COBRADOR_ID)
        assertEquals(sale.ZONA_CLIENTE_ID, payment.ZONA_CLIENTE_ID)
        assertEquals(sale.DOCTO_CC_ACR_ID, payment.DOCTO_CC_ACR_ID)
        assertEquals(0, payment.DOCTO_CC_ID)
        assertEquals(sale.CLIENTE_ID, payment.CLIENTE_ID)
        assertEquals(sale.CLIENTE, payment.NOMBRE_CLIENTE)
        assertEquals(300.0, payment.IMPORTE, 0.0)
        assertEquals(1, payment.FORMA_COBRO_ID)
        assertEquals("ticket-abc-123", payment.ID)
        assertEquals("2026-07-28T16:45:00Z", payment.FECHA_HORA_PAGO)
        assertEquals(0.0, payment.LAT)
        assertEquals(0.0, payment.LNG)
        assertEquals(false, payment.GUARDADO_EN_MICROSIP)
    }
}
