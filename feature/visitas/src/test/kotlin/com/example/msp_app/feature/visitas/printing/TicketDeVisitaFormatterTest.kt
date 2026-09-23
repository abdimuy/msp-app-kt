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
 *  - **las tres cartas del ticket viejo vuelven literales** y cada una cae en
 *    su desenlace. Las cifras que aquel inventaba —el vencimiento a un año, el
 *    abono semanal de $200.00— no vuelven con ellas.
 */
class TicketDeVisitaFormatterTest {

    private val ancho = PrinterProfile.PROFILE_58MM.charsPerLine

    private fun texto(
        ticket: com.example.msp_app.feature.visitas.domain.model.TicketDeVisita =
            TicketDeVisitaFixtures.ticket(),
        permiso: PrintPermission = PrintPermission.PrimeraImpresion
    ): String = TicketDeVisitaFormatter.toTicketText(ticket, permiso)

    /**
     * El papel con los saltos de línea deshechos.
     *
     * Las cartas recuperadas del ticket viejo se guardan como párrafo y
     * `TicketLayout.wrap` las corta al ancho del rollo, así que una frase de
     * doce palabras **nunca** aparece entera en una línea: afirmar sobre el
     * texto exige volver a juntarlo. El ancho se verifica aparte, sobre las
     * líneas de verdad.
     */
    private fun corrido(papel: String): String = papel.lines().joinToString(" ") { it.trim() }

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
    fun `una copia con hora desconocida sigue llevando la marca`() {
        // El registro conservó el conteo y perdió la fecha (prefs corruptas).
        // El papel dice QUE es copia y CUÁL, que es lo que la hace detectable;
        // perder el conteo para no perder la hora sería exactamente al revés.
        val papel = texto(permiso = PrintPermission.Reimpresion(previas = 1, primeraVez = null))

        assertTrue(papel.contains("*** REIMPRESION ***"))
        assertTrue(papel.contains("copia 2"))
        assertFalse(papel.contains("primera"))
    }

    @Test
    fun `ninguna linea excede el ancho del rollo despues del fold a ASCII`() {
        // Barre los CINCO desenlaces, no uno solo: las cartas recuperadas del
        // ticket viejo son el texto más largo del papel y allá venían
        // pre-cortadas a mano, así que son justo las que podrían pasarse.
        val copia = PrintPermission.Reimpresion(
            previas = 1,
            primeraVez = TicketDeVisitaFixtures.PRIMERA_COPIA
        )
        val papeles = DesenlaceImpreso.entries.map { desenlace ->
            texto(ticket = TicketDeVisitaFixtures.ticket(desenlace = desenlace), permiso = copia)
        } + texto(ticket = TicketDeVisitaFixtures.ticketConPromesa(), permiso = copia) +
            texto(
                ticket = TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.VISITE_VUELVO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta()
                ),
                permiso = copia
            )

        papeles.forEach { papel ->
            val lineas = papel.lines()
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
    }

    @Test
    fun `cada carta del ticket viejo sale en su desenlace y en ninguno mas`() {
        val cartas = mapOf(
            DesenlaceImpreso.NO_ESTABA to "PERO NO FUE POSIBLE ENCONTRARLO",
            DesenlaceImpreso.SE_NEGO to "EN REITERADAS OCASIONES HEMOS TRATADO DE ACERCARNOS",
            DesenlaceImpreso.VISITE_VUELVO to "RECUERDE QUE LA PUNTUALIDAD EN SUS PAGOS"
        )

        cartas.forEach { (desenlace, frase) ->
            val papel = corrido(texto(TicketDeVisitaFixtures.ticket(desenlace = desenlace)))
            assertTrue("${desenlace.name} no imprimió su carta", papel.contains(frase))

            cartas.filterKeys { it != desenlace }.forEach { (otro, ajena) ->
                assertFalse(
                    "${desenlace.name} imprimió la carta de ${otro.name}",
                    papel.contains(ajena)
                )
            }
        }
    }

    @Test
    fun `la carta de no lo encontramos conserva el recado completo del ticket viejo`() {
        val papel = corrido(texto())

        assertTrue(papel.contains("SU AGENTE DE COBRANZA DE MUEBLES SAN PABLO PASO A VISITAR"))
        assertTrue(papel.contains("EN CASO DE NO ENCONTRARSE LE PEDIMOS DE FAVOR NOS PUEDA"))
        assertTrue(papel.contains("O LLAMAME PARA COORDINARNOS EN EL HORARIO QUE LO PUEDA"))
    }

    @Test
    fun `la carta de cobranza dura conserva el aviso del cobro por otra via`() {
        val papel = corrido(texto(TicketDeVisitaFixtures.ticket(DesenlaceImpreso.SE_NEGO)))

        assertTrue(papel.contains("NO HEMOS TENIDO UNA RESPUESTA FAVORABLE"))
        assertTrue(papel.contains("CONTINUAR CON EL PROCESO DE COBRO POR OTRA VIA"))
        assertTrue(papel.contains("EN DEFINITIVA ESTA SITUACION"))
    }

    @Test
    fun `los desenlaces sin carta vieja conservan su propio texto`() {
        // El ticket legado no conocía la promesa ni la cita —no existían como
        // dato—, así que ninguna de las tres cartas les corresponde. Prestarles
        // una dejaría el recado equivocado en la puerta.
        val promesa = corrido(texto(TicketDeVisitaFixtures.ticketConPromesa()))
        val cita = corrido(texto(TicketDeVisitaFixtures.ticketConCita()))

        assertTrue(promesa.contains("Le agradecemos el compromiso de pago"))
        assertTrue(cita.contains("Acordamos vernos en la fecha y hora"))
        listOf(promesa, cita).forEach { papel ->
            assertFalse(papel.contains("PERO NO FUE POSIBLE ENCONTRARLO"))
            assertFalse(papel.contains("EN REITERADAS OCASIONES"))
            assertFalse(papel.contains("SE LE EXHORTA A REGULARIZARSE"))
        }
    }

    @Test
    fun `el abono por periodo sale de la cuenta y no del literal del ticket viejo`() {
        val papel = corrido(
            texto(
                TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.VISITE_VUELVO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta(Money.of(BigDecimal("185")))
                )
            )
        )

        assertTrue(papel.contains("SU COMPROMISO FUE DAR ABONOS SEMANALES DE $185"))
        // El viejo imprimía "$200.00" como literal, igual para todos.
        assertFalse(papel.contains("$200"))
        assertTrue(papel.contains("SE LE EXHORTA A REGULARIZARSE PARA EVITAR PENALIZACIONES"))
    }

    @Test
    fun `con dos cuentas el abono por periodo se omite y el exhorto se queda`() {
        // "Abonos semanales de $X" con dos cuentas no dice de cuál de las dos
        // es; el exhorto no depende de ninguna cifra y se sostiene solo.
        val papel = corrido(texto(TicketDeVisitaFixtures.ticket(DesenlaceImpreso.VISITE_VUELVO)))

        assertFalse(papel.contains("ABONOS SEMANALES"))
        assertTrue(papel.contains("SE LE EXHORTA A REGULARIZARSE PARA EVITAR PENALIZACIONES"))
    }

    @Test
    fun `una parcialidad en cero NO imprime un cero en el papel`() {
        // Mismo criterio que la promesa sin monto: un `$0` impreso significaría
        // un compromiso de no pagar, que es otra cosa de la que dice la cuenta.
        val papel = corrido(
            texto(
                TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.VISITE_VUELVO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta(Money.ZERO)
                )
            )
        )

        assertFalse(papel.contains("ABONOS SEMANALES"))
        assertFalse(papel.contains("$0"))
        assertTrue(papel.contains("SE LE EXHORTA A REGULARIZARSE"))
    }

    @Test
    fun `las cifras que el ticket viejo inventaba NO vuelven al papel`() {
        // El vencimiento era "fecha de venta + 1 año" sin respaldo —Microsip no
        // guarda el plazo—, y el total de compra, los pagos vencidos y el
        // sugerido no se leen desde este módulo.
        DesenlaceImpreso.entries.forEach { desenlace ->
            val papel = corrido(texto(TicketDeVisitaFixtures.ticket(desenlace = desenlace)))

            assertFalse(desenlace.name, papel.contains("SU FECHA DE VENCIMIENTO"))
            assertFalse(desenlace.name, papel.contains("TOTAL DE COMPRA"))
            assertFalse(desenlace.name, papel.contains("PAGOS VENCIDOS"))
            assertFalse(desenlace.name, papel.contains("SUGERIDO PARA"))
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
