package com.example.msp_app.data.api.services.cobranza

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cubre el mapeo de `pago_recibido_id` en [toEntity]: la persistencia de este
 * campo es lo que permite al reconciliador colapsar el gemelo UUID de un
 * pago aunque mergePagos no lo haya hecho de un solo tiro. Ver
 * PaymentDaoCollapseTest y CobranzaReconcilerTest.
 */
class PagoDtoToEntityTest {

    private fun pagoDto(pagoRecibidoId: String?) = PagoDto(
        impte_docto_cc_id = 15808629,
        docto_cc_id = 500,
        docto_cc_acr_id = 100,
        cliente_id = 4821,
        zona_cliente_id = 21,
        folio = "abono",
        concepto_cc_id = 87327,
        fecha = "2026-06-01T09:00:05Z",
        importe = "350.00",
        impuesto = "0.00",
        lat = null,
        lon = null,
        cancelado = false,
        aplicado = true,
        updated_at = "2026-06-01T09:00:06Z",
        cobrador = "Rosa Elena Martinez Vazquez",
        cobrador_id = 7,
        nombre_cliente = "Guadalupe Hernandez Soto",
        forma_cobro_id = 157,
        pago_recibido_id = pagoRecibidoId
    )

    @Test
    fun mapsNonNullPagoRecibidoId() {
        val entity = pagoDto(pagoRecibidoId = "uuid-x").toEntity()

        assertEquals("uuid-x", entity.PAGO_RECIBIDO_ID)
    }

    @Test
    fun mapsNullPagoRecibidoId() {
        val entity = pagoDto(pagoRecibidoId = null).toEntity()

        assertNull(entity.PAGO_RECIBIDO_ID)
    }
}
