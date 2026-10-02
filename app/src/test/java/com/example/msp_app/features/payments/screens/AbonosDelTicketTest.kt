package com.example.msp_app.features.payments.screens

import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.data.models.payment.Payment
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * E-APP-052: el ticket no lista como abono uno que el servidor no tiene (-1).
 * Decisión del dueño del 2026-10-02: ocultarlo. Control: el aplicado y el
 * pendiente de subir siguen en la lista, en su orden.
 */
class AbonosDelTicketTest {

    @Test
    fun `quita los no aplicados y conserva aplicados y pendientes en orden`() {
        val pagos = listOf(
            pago("aplicado", documento = 15_866_668),
            pago("no-aplicado", documento = DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR),
            pago("pendiente", documento = 0)
        )

        assertEquals(listOf("aplicado", "pendiente"), abonosDelTicket(pagos).map { it.ID })
    }

    private fun pago(id: String, documento: Int) = Payment(
        ID = id,
        COBRADOR = "Efrain Dominguez Reyes",
        DOCTO_CC_ACR_ID = 14_186_779,
        DOCTO_CC_ID = documento,
        FECHA_HORA_PAGO = "2026-08-11T22:18:31Z",
        GUARDADO_EN_MICROSIP = true,
        IMPORTE = 250.0,
        LAT = null,
        LNG = null,
        CLIENTE_ID = 5021,
        COBRADOR_ID = 12,
        FORMA_COBRO_ID = 157,
        ZONA_CLIENTE_ID = 25,
        NOMBRE_CLIENTE = "Rocio Aguilar Medina"
    )
}
