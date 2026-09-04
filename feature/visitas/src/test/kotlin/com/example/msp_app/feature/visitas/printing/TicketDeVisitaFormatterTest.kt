package com.example.msp_app.feature.visitas.printing

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.printing.adapters.foldToPrintableAscii
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.application.TicketRenderer
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.feature.visitas.domain.model.DesenlaceImpreso
import com.example.msp_app.feature.visitas.ui.TicketDeVisitaFixtures
import java.math.BigDecimal
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El contenido del papel que se deja en la puerta.
 *
 * Lo que estas pruebas fijan, además del ancho y el dinero:
 *  - **el mensaje corresponde al desenlace registrado**. El ticket viejo dejaba
 *    elegir la carta a mano, sin relación con lo capturado; nada impedía dejar
 *    la carta de cobranza dura donde el cliente acababa de prometer pagar.
 *  - **el compromiso se imprime desde sus campos**, no desde la nota.
 */
class TicketDeVisitaFormatterTest {

    private val ancho = PrinterProfile.PROFILE_58MM.charsPerLine

    private fun texto(
        ticket: com.example.msp_app.feature.visitas.domain.model.TicketDeVisita =
            TicketDeVisitaFixtures.ticket(),
        permiso: PrintPermission = PrintPermission.PrimeraImpresion
    ): String = TicketDeVisitaFormatter.toTicketText(ticket, permiso)

    @Test
    fun `la primera copia NO lleva marca de reimpresion`() {
        assertFalse(texto().contains("REIMPRESION"))
    }

    @Test
    fun `la copia lleva la marca, el numero y la hora de la primera`() {
        val papel = texto(
            permiso = PrintPermission.Reimpresion(
                previas = 1,
                primeraVez = TicketDeVisitaFixtures.PRIMERA_COPIA
            )
        )

        assertTrue(papel.contains("*** REIMPRESION ***"))
        assertTrue(papel.contains("copia 2 - primera 12:05"))
    }

    @Test
    fun `ninguna linea excede el ancho del rollo despues del fold a ASCII`() {
        val lineas = texto(
            ticket = TicketDeVisitaFixtures.ticketConPromesa(),
            permiso = PrintPermission.Reimpresion(
                previas = 1,
                primeraVez = TicketDeVisitaFixtures.PRIMERA_COPIA
            )
        ).lines()

        // Control positivo: el papel tiene contenido, así que la ausencia de
        // líneas largas significa algo.
        assertTrue(lineas.size > MINIMO_DE_LINEAS)
        lineas.forEach { linea ->
            assertTrue(
                "linea de ${linea.length} columnas: $linea",
                foldToPrintableAscii(linea).length <= ancho
            )
        }
    }

    @Test
    fun `cada desenlace imprime SU mensaje y ninguno de los otros`() {
        DesenlaceImpreso.entries.forEach { desenlace ->
            val papel = texto(TicketDeVisitaFixtures.ticket(desenlace = desenlace))
            assertTrue(
                "${desenlace.name} no imprimió su título",
                papel.contains(desenlace.titulo)
            )
            DesenlaceImpreso.entries
                .filter { it != desenlace && it.titulo != desenlace.titulo }
                .forEach { otro ->
                    assertFalse(
                        "${desenlace.name} imprimió el título de ${otro.name}",
                        papel.contains(otro.titulo)
                    )
                }
        }
    }

    @Test
    fun `el aviso de cobranza dura solo sale en se nego`() {
        val duro = "AVISO DE COBRANZA"

        assertTrue(texto(TicketDeVisitaFixtures.ticket(DesenlaceImpreso.SE_NEGO)).contains(duro))
        assertFalse(texto(TicketDeVisitaFixtures.ticketConPromesa()).contains(duro))
        assertFalse(texto().contains(duro))
    }

    @Test
    fun `la promesa imprime su fecha y su monto`() {
        val papel = texto(TicketDeVisitaFixtures.ticketConPromesa())

        assertTrue(papel.contains("SU COMPROMISO"))
        assertTrue(papel.contains("04/09/2026"))
        assertTrue(papel.contains("$220"))
    }

    @Test
    fun `una promesa sin monto NO imprime un cero`() {
        // "dijo cuándo pero no cuánto" es un caso real de campo; un `$0` en el
        // papel significaría "prometió no pagar", que es otro desenlace.
        val papel = texto(TicketDeVisitaFixtures.ticketConPromesa(monto = null))

        assertTrue(papel.contains("SU COMPROMISO"))
        assertTrue(papel.contains("04/09/2026"))
        assertFalse(papel.contains("Monto"))
        assertFalse(papel.contains("$0"))
    }

    @Test
    fun `la cita imprime dia y hora, y dice sin hora cuando no la hay`() {
        val conHora = texto(TicketDeVisitaFixtures.ticketConCita())
        val sinHora = texto(TicketDeVisitaFixtures.ticketConCita(hora = null))

        assertTrue(conHora.contains("SU CITA"))
        assertTrue(conHora.contains("03/09/2026"))
        assertTrue(conHora.contains("16:00"))
        assertTrue(sinHora.contains("sin hora"))
    }

    @Test
    fun `las cuentas y el saldo total se imprimen en peso entero`() {
        val papel = texto()

        assertTrue(papel.contains("V-5021"))
        assertTrue(papel.contains("$2,100"))
        assertTrue(papel.contains("V-5188"))
        assertTrue(papel.contains("$1,450"))
        assertTrue(papel.contains("Saldo total"))
        assertTrue(papel.contains("$3,550"))
    }

    @Test
    fun `el dinero se imprime en peso entero aunque el modelo traiga centavos`() {
        val conCentavos = TicketDeVisitaFixtures.ticketConPromesa(
            monto = Money.of(BigDecimal("220.49"))
        )

        val papel = texto(conCentavos)

        assertTrue(papel.contains("$220"))
        assertFalse(papel.contains("220.49"))
        assertEquals(BigDecimal("220.49"), conCentavos.promesa?.monto?.amount)
    }

    @Test
    fun `quien visito va en el papel`() {
        assertTrue(texto().contains("Martín Salgado"))
    }

    @Test
    fun `nada de la nota del cobrador llega al papel`() {
        // La nota es texto libre del cobrador y puede llevar cualquier cosa; el
        // ticket que se deja en la puerta del cliente no la imprime.
        val papel = texto()

        assertFalse(papel.contains("preguntar por la mañana"))
    }

    @Test
    fun `el ticket semantico y el texto plano son el MISMO contenido`() {
        val lineas = TicketDeVisitaFormatter.toTicketLines(
            TicketDeVisitaFixtures.ticket(),
            PrintPermission.PrimeraImpresion
        )

        assertEquals(
            TicketRenderer.render(lineas, PrinterProfile.PROFILE_58MM).joinToString("\n"),
            texto()
        )
    }

    @Test
    fun `la hora de la cita se imprime en 24 horas`() {
        val papel = texto(TicketDeVisitaFixtures.ticketConCita(hora = LocalTime.of(9, 5)))

        assertTrue(papel.contains("09:05"))
    }

    private companion object {
        const val MINIMO_DE_LINEAS = 20
    }
}
