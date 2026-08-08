package com.example.msp_app.features.visit.newvisit

import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.FrecuenciaPago
import com.example.msp_app.data.models.sale.Sale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Characterization test for [VisitFactory.fromSale] — freezes the
 * attribution mapping extracted verbatim from `NewVisitDialog.handleSaveVisit()`.
 * If this test starts failing after an edit to [VisitFactory], the edit is
 * changing attribution behavior and needs an explicit product decision, not
 * a quiet tweak. Mirrors
 * [com.example.msp_app.features.payments.newpayment.PaymentFactoryTest].
 */
class VisitFactoryTest {

    // The sale's own COBRADOR_ID (14) deliberately differs from the current
    // user's COBRADOR_ID (203) below — the subtle part of the contract:
    // attribution follows whoever is physically registering the visita
    // (currentUser), not the cobrador nominally assigned to the sale.
    private val sale = Sale(
        DOCTO_CC_ACR_ID = 61204,
        DOCTO_CC_ID = 71305,
        FOLIO = "CV-61204",
        CLIENTE_ID = 9931,
        APLICADO = "S",
        COBRADOR_ID = 14,
        CLIENTE = "Rosa Isela Dominguez Bautista",
        ZONA_CLIENTE_ID = 34,
        LIMITE_CREDITO = 12000.0,
        NOTAS = "",
        ZONA_NOMBRE = "R/34",
        IMPORTE_PAGO_PROMEDIO = 280.0,
        TOTAL_IMPORTE = 4200.0,
        NUM_IMPORTES = 15,
        FECHA = "2026-03-10T00:00:00Z",
        PARCIALIDAD = 280,
        ENGANCHE = 400.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "Ignacio Perez Cabrera",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 4600.0,
        IMPTE_REST = 2800.0,
        SALDO_REST = 2800.0,
        FECHA_ULT_PAGO = "2026-07-15T13:00:00Z",
        CALLE = "CALLE HIDALGO 118",
        CIUDAD = "TEHUACAN",
        ESTADO = "PUEBLA",
        TELEFONO = "2381122334",
        NOMBRE_COBRADOR = "Fernando Ramirez Ortiz",
        ESTADO_COBRANZA = EstadoCobranza.NO_PAGADO,
        DIA_COBRANZA = "MARTES",
        DIA_TEMPORAL_COBRANZA = "MARTES",
        PRECIO_DE_CONTADO = 4200.0,
        AVAL_O_RESPONSABLE = "Martin Dominguez Bautista",
        FREC_PAGO = FrecuenciaPago.SEMANAL
    )

    private val currentUser = User(
        ID = "uid-cobrador-203",
        NOMBRE = "Guadalupe Torres Salinas",
        EMAIL = "guadalupe.torres@muebleriamsp.mx",
        COBRADOR_ID = 203,
        TELEFONO = "2385566778",
        ZONA_CLIENTE_ID = 34
    )

    @Test
    fun `builds visit with exact attribution mapping`() {
        val visit = VisitFactory.fromSale(
            sale = sale,
            currentUser = currentUser,
            tipoVisita = "SIN_PAGO",
            formaCobroId = 0,
            nota = "Cliente no se encontraba, se le dejo recado con la vecina",
            id = "visita-abc-456",
            fecha = "2026-07-28T17:20:00Z"
        )

        assertEquals(sale.NOMBRE_COBRADOR, visit.COBRADOR)
        assertEquals(currentUser.COBRADOR_ID, visit.COBRADOR_ID)
        // The subtle part: attribution must come from currentUser, never
        // from the sale's own (possibly stale/different) cobrador.
        assertNotEquals(sale.COBRADOR_ID, visit.COBRADOR_ID)
        assertEquals(sale.ZONA_CLIENTE_ID, visit.ZONA_CLIENTE_ID)
        assertEquals(sale.DOCTO_CC_ACR_ID, visit.IMPTE_DOCTO_CC_ID)
        assertEquals(sale.CLIENTE_ID, visit.CLIENTE_ID)
        assertEquals(0, visit.FORMA_COBRO_ID)
        assertEquals(0.0, visit.LAT, 0.0)
        assertEquals(0.0, visit.LNG, 0.0)
        assertEquals(0, visit.GUARDADO_EN_MICROSIP)
        assertEquals(
            "Cliente no se encontraba, se le dejo recado con la vecina",
            visit.NOTA
        )
        assertEquals("SIN_PAGO", visit.TIPO_VISITA)
        assertEquals("2026-07-28T17:20:00Z", visit.FECHA)
        assertEquals("visita-abc-456", visit.ID)
    }

    @Test
    fun `null currentUser falls back to COBRADOR_ID 0 (preserved, not endorsed, behavior)`() {
        // The dialog's original guard rejects a resolved COBRADOR_ID == 0, but
        // does not reject a null user outright — VisitFactory preserves that
        // as-is rather than introducing new validation as part of this
        // refactor. This test documents the fact, it does not endorse it.
        val visit = VisitFactory.fromSale(
            sale = sale,
            currentUser = null,
            tipoVisita = "CON_PAGO",
            formaCobroId = 0,
            nota = "",
            id = "visita-sin-usuario-789",
            fecha = "2026-07-28T17:25:00Z"
        )

        assertEquals(0, visit.COBRADOR_ID)
        // The rest of the attribution mapping is unaffected by a null user.
        assertEquals(sale.NOMBRE_COBRADOR, visit.COBRADOR)
        assertEquals(sale.CLIENTE_ID, visit.CLIENTE_ID)
    }
}
