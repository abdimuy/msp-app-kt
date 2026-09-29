package com.example.msp_app.features.visit.screens

import com.example.msp_app.core.database.dao.sale.EstadoCobranza
import com.example.msp_app.core.database.entities.OverduePaymentsEntity
import com.example.msp_app.core.utils.toCurrency
import com.example.msp_app.data.models.auth.User
import com.example.msp_app.data.models.sale.FrecuenciaPago
import com.example.msp_app.data.models.sale.Sale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [construirTicketDeVisita] de forma aislada — es una función PURA (ver su
 * KDoc en `VisitTicketScreen.kt`), así que se prueba sin Robolectric ni
 * Compose.
 *
 * ## El defecto que la primera prueba guarda
 *
 * El ticket 3 ("SU COMPROMISO FUE DAR ABONOS SEMANALES DE…") imprimía un
 * monto FIJO, `$200.00`, sin importar la parcialidad real de la venta —
 * medido al recuperar esta pantalla el 2026-09-29 (decisión del dueño:
 * cambiar el monto fijo por la parcialidad real). El fixture usa una
 * parcialidad DISTINTA de 200 a propósito: con el literal viejo restaurado
 * ("SEMANALES DE $200.00" pegado en el código) esta prueba se pone roja,
 * porque $175 —la parcialidad del fixture— no aparece en ningún lado.
 *
 * ## El defecto que la segunda prueba guarda
 *
 * La versión original (borrada en `d76d8f69`, recuperada aquí) calculaba el
 * vencimiento con `LocalDate.parse(sale.FECHA, DateTimeFormatter.ofPattern(
 * "dd/MM/yyyy"))`. `Sale.FECHA` viaja en ISO 8601 desde antes de esta
 * recuperación (ver el comentario del campo en `data/models/sale/Sale.kt`),
 * así que ese `parse` revienta con `DateTimeParseException` en cuanto se abre
 * la pantalla. Revertir el cambio a `AppTime` reintroduce el crash.
 */
class VisitTicketScreenTest {

    @Test
    fun `el ticket 3 imprime la parcialidad real, no doscientos fijos`() {
        val venta = venta(parcialidad = 175)
        val ticket = construirTicketDeVisita(
            ticketType = 3,
            sale = venta,
            user = usuario(),
            latePayment = null,
            currentDate = FECHA_IMPRESION
        )

        val montoEsperado = venta.PARCIALIDAD.toCurrency(noDecimals = true)
        assertTrue(
            "el ticket 3 tiene que imprimir la parcialidad real ($montoEsperado): $ticket",
            ticket.contains("SEMANALES DE $montoEsperado")
        )
        assertFalse(
            "el literal fijo viejo no debería sobrevivir con otra parcialidad: $ticket",
            ticket.contains("SEMANALES DE \$200.00")
        )
    }

    /**
     * **Control positivo del anterior.** Con parcialidad $200 de verdad, el
     * texto SÍ coincide con lo que el literal viejo imprimía — prueba que la
     * prueba de arriba mide la parcialidad y no, por accidente, siempre
     * encuentra "no coincide".
     */
    @Test
    fun `con parcialidad de 200 el texto coincide con el que imprimia el literal viejo`() {
        val ticket = construirTicketDeVisita(
            ticketType = 3,
            sale = venta(parcialidad = 200),
            user = usuario(),
            latePayment = null,
            currentDate = FECHA_IMPRESION
        )
        assertTrue(ticket.contains("SEMANALES DE \$200"))
    }

    @Test
    fun `la fecha de vencimiento no truena con FECHA en formato ISO`() {
        val ticket = construirTicketDeVisita(
            ticketType = 2,
            sale = venta(parcialidad = 300, fechaIso = "2025-11-04T00:00:00Z"),
            user = usuario(),
            latePayment = null,
            currentDate = FECHA_IMPRESION
        )
        assertTrue(
            "el ticket 2 tiene que traer una fecha de vencimiento dd/MM/yyyy: $ticket",
            FORMATO_DE_FECHA.containsMatchIn(ticket)
        )
    }

    @Test
    fun `con atrasos, el ticket 2 y el 3 imprimen los pagos vencidos`() {
        val venta = venta(parcialidad = 250)
        val atrasos = OverduePaymentsEntity(
            DOCTO_CC_ID = venta.DOCTO_CC_ID,
            FECHA_ULT_PAGO = "2026-08-01T00:00:00Z",
            NUM_IMPORTES = 6,
            PARCIALIDADES_TRANSCURRIDAS = 3.0,
            NUM_PAGOS_ATRASADOS = 2.0
        )
        listOf(2, 3).forEach { tipo ->
            val ticket = construirTicketDeVisita(
                ticketType = tipo,
                sale = venta,
                user = usuario(),
                latePayment = atrasos,
                currentDate = FECHA_IMPRESION
            )
            assertTrue("ticket $tipo: $ticket", ticket.contains("PAGOS VENCIDOS: 2"))
        }
    }

    @Test
    fun `el ticket 1 no imprime parcialidad ni atrasos`() {
        val ticket = construirTicketDeVisita(
            ticketType = 1,
            sale = venta(parcialidad = 999),
            user = usuario(),
            latePayment = null,
            currentDate = FECHA_IMPRESION
        )
        assertFalse(ticket.contains("SEMANALES DE"))
        assertFalse(ticket.contains("PAGOS VENCIDOS"))
    }

    private fun usuario(): User = User(
        NOMBRE = "Esperanza Villalobos",
        TELEFONO = "3312345678"
    )

    private fun venta(parcialidad: Int, fechaIso: String = "2025-11-04T00:00:00Z"): Sale = Sale(
        DOCTO_CC_ACR_ID = VENTA,
        DOCTO_CC_ID = VENTA,
        FOLIO = "MTY-2026-0188",
        CLIENTE_ID = 9011,
        APLICADO = "N",
        COBRADOR_ID = 41,
        CLIENTE = "María Guadalupe Rentería",
        ZONA_CLIENTE_ID = 7,
        LIMITE_CREDITO = 0.0,
        NOTAS = "",
        ZONA_NOMBRE = "Tlaquepaque",
        IMPORTE_PAGO_PROMEDIO = 441.6,
        TOTAL_IMPORTE = 2650.0,
        NUM_IMPORTES = 6,
        FECHA = fechaIso,
        PARCIALIDAD = parcialidad,
        ENGANCHE = 500.0,
        TIEMPO_A_CORTO_PLAZOMESES = 0,
        MONTO_A_CORTO_PLAZO = 0.0,
        VENDEDOR_1 = "Ricardo Salgado",
        VENDEDOR_2 = "",
        VENDEDOR_3 = "",
        PRECIO_TOTAL = 8400.0,
        IMPTE_REST = 5250.0,
        SALDO_REST = 5250.0,
        FECHA_ULT_PAGO = "2026-08-24T00:00:00Z",
        CALLE = "Av. Río Nilo 442",
        CIUDAD = "Guadalajara",
        ESTADO = "Jalisco",
        TELEFONO = "3312345678",
        NOMBRE_COBRADOR = "Esperanza Villalobos",
        ESTADO_COBRANZA = EstadoCobranza.PENDIENTE,
        DIA_COBRANZA = "VIERNES",
        DIA_TEMPORAL_COBRANZA = "",
        PRECIO_DE_CONTADO = 7200.0,
        AVAL_O_RESPONSABLE = "Rosalba Rentería",
        FREC_PAGO = FrecuenciaPago.SEMANAL
    )

    private companion object {
        const val VENTA = 4477
        const val FECHA_IMPRESION = "01/09/2026 10:00 am"
        val FORMATO_DE_FECHA = Regex("""CREDITO ES EL DIA: \d{2}/\d{2}/\d{4}""")
    }
}
