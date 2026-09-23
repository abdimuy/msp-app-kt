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
 *    su desenlace.
 *  - **ninguna cifra se estampa sin el dato que la sostiene.** El bloque de
 *    números del viejo vuelve entero, pero cada línea sale de su columna y se
 *    calla en cero, sin dato o con más de una cuenta en el papel.
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
            // Con UNA cuenta, que es cuando salen las dos líneas más largas del
            // papel: el abono por periodo y el vencimiento.
            listOf(DesenlaceImpreso.VISITE_VUELVO, DesenlaceImpreso.SE_NEGO).map { desenlace ->
                texto(
                    ticket = TicketDeVisitaFixtures.ticket(
                        desenlace = desenlace,
                        cuentas = TicketDeVisitaFixtures.unaCuenta()
                    ),
                    permiso = copia
                )
            }

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

    /**
     * **Sucesora de `las cifras que el ticket viejo inventaba NO vuelven al
     * papel`**, que se quedó sin nada que vigilar: las cuatro cifras del bloque
     * tienen hoy su columna —`PRECIO_TOTAL`, `NUM_PAGOS_ATRASADOS`, la
     * parcialidad y la regla de `VencimientoDelCredito`—, así que ninguna está
     * prohibida por sí misma y una lista de rótulos vetados sería cobertura de
     * adorno.
     *
     * Lo que sigue necesitando guardia es **el otro lado de la misma moneda**:
     * que ninguna cifra se estampe sin el dato que la sostiene. Una cuenta que
     * no tiene nada que decir no puede producir un papel con cifras.
     */
    @Test
    fun `sin datos que las sostengan, ninguna cifra del bloque sale al papel`() {
        val papel = corrido(
            texto(
                TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.SE_NEGO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta(
                        parcialidad = Money.ZERO,
                        vencimiento = null,
                        totalDeCompra = Money.ZERO,
                        pagosVencidos = 0
                    )
                )
            )
        )

        assertFalse(papel.contains("SU FECHA DE VENCIMIENTO"))
        assertFalse(papel.contains("TOTAL DE COMPRA"))
        assertFalse(papel.contains("PAGOS VENCIDOS"))
        assertFalse(papel.contains("SUGERIDO PARA"))
        assertFalse(papel.contains("ABONOS SEMANALES"))
        assertFalse(papel.contains("$0"))
        // Control positivo: la carta sí se imprimió, así que el papel existe y
        // la ausencia de las cifras significa algo.
        assertTrue(papel.contains("NO HEMOS TENIDO UNA RESPUESTA FAVORABLE"))
    }

    @Test
    fun `el bloque de numeros sale completo cuando la cuenta lo sostiene`() {
        // Cartas 2 y 3 del ticket viejo, en su orden: vencimiento, total de
        // compra, pagos vencidos y sugerido. El sugerido son 3 x $220.
        listOf(DesenlaceImpreso.SE_NEGO, DesenlaceImpreso.VISITE_VUELVO).forEach { desenlace ->
            val papel = corrido(
                texto(
                    TicketDeVisitaFixtures.ticket(
                        desenlace = desenlace,
                        cuentas = TicketDeVisitaFixtures.unaCuenta()
                    )
                )
            )

            assertTrue(desenlace.name, papel.contains("TOTAL DE COMPRA: $8,400"))
            assertTrue(desenlace.name, papel.contains("PAGOS VENCIDOS: 3"))
            assertTrue(desenlace.name, papel.contains("SUGERIDO PARA REGULARIZARSE: $660"))
        }
    }

    @Test
    fun `un total de compra en cero NO se imprime`() {
        // "TOTAL DE COMPRA: $0" es un dato falso, no un dato vacío.
        val papel = corrido(
            texto(
                TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.SE_NEGO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta(totalDeCompra = Money.ZERO)
                )
            )
        )

        assertFalse(papel.contains("TOTAL DE COMPRA"))
        // Control positivo: el resto del bloque sí salió.
        assertTrue(papel.contains("PAGOS VENCIDOS: 3"))
        assertTrue(papel.contains("SUGERIDO PARA REGULARIZARSE: $660"))
    }

    @Test
    fun `sin pagos vencidos no se imprime ni el atraso ni el sugerido`() {
        // Una cuenta al corriente no tiene atraso que reportar, y sin atraso no
        // hay nada que regularizar: las dos líneas se van juntas.
        val papel = corrido(
            texto(
                TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.SE_NEGO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta(pagosVencidos = 0)
                )
            )
        )

        assertFalse(papel.contains("PAGOS VENCIDOS"))
        assertFalse(papel.contains("SUGERIDO PARA"))
        // Control positivo: las otras dos cifras del bloque sí salieron.
        assertTrue(papel.contains("TOTAL DE COMPRA: $8,400"))
        assertTrue(papel.contains("SU FECHA DE VENCIMIENTO DE SU CREDITO ES EL DIA: 14/03/2027"))
    }

    @Test
    fun `el sugerido falta si falta cualquiera de sus dos entradas`() {
        val sinParcialidad = corrido(
            texto(
                TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.SE_NEGO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta(parcialidad = Money.ZERO)
                )
            )
        )
        val sinAtraso = corrido(
            texto(
                TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.SE_NEGO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta(pagosVencidos = 0)
                )
            )
        )

        assertFalse(sinParcialidad.contains("SUGERIDO PARA"))
        assertFalse(sinAtraso.contains("SUGERIDO PARA"))
        // Control positivo en cada uno: la otra entrada sí se imprimió sola.
        assertTrue(sinParcialidad.contains("PAGOS VENCIDOS: 3"))
        assertTrue(sinAtraso.contains("TOTAL DE COMPRA: $8,400"))
    }

    @Test
    fun `con dos cuentas ninguna cifra del bloque sale`() {
        // Cada venta tiene su precio, su atraso y su fecha; una cifra suelta
        // bajo dos folios mentiría sobre una de las dos.
        val papel = corrido(texto(TicketDeVisitaFixtures.ticket(DesenlaceImpreso.SE_NEGO)))

        assertFalse(papel.contains("TOTAL DE COMPRA"))
        assertFalse(papel.contains("PAGOS VENCIDOS"))
        assertFalse(papel.contains("SUGERIDO PARA"))
        assertTrue(papel.contains("NO HEMOS TENIDO UNA RESPUESTA FAVORABLE"))
    }

    @Test
    fun `los desenlaces sin bloque de numeros no imprimen ninguna cifra`() {
        listOf(
            DesenlaceImpreso.NO_ESTABA,
            DesenlaceImpreso.PROMETIO,
            DesenlaceImpreso.CITA
        ).forEach { desenlace ->
            val papel = corrido(
                texto(
                    TicketDeVisitaFixtures.ticket(
                        desenlace = desenlace,
                        cuentas = TicketDeVisitaFixtures.unaCuenta()
                    )
                )
            )

            assertFalse(desenlace.name, papel.contains("TOTAL DE COMPRA"))
            assertFalse(desenlace.name, papel.contains("PAGOS VENCIDOS"))
            assertFalse(desenlace.name, papel.contains("SUGERIDO PARA"))
            // Control positivo: el papel de ese desenlace sí se imprimió.
            assertTrue(desenlace.name, papel.contains(desenlace.titulo))
        }
    }

    @Test
    fun `el vencimiento se imprime con su fecha en las cartas que lo llevaban`() {
        // Cartas 2 y 3 del ticket viejo: las dos que llevaban el bloque de
        // números debajo del recado.
        listOf(DesenlaceImpreso.SE_NEGO, DesenlaceImpreso.VISITE_VUELVO).forEach { desenlace ->
            val papel = corrido(
                texto(
                    TicketDeVisitaFixtures.ticket(
                        desenlace = desenlace,
                        cuentas = TicketDeVisitaFixtures.unaCuenta()
                    )
                )
            )

            assertTrue(
                desenlace.name,
                papel.contains("SU FECHA DE VENCIMIENTO DE SU CREDITO ES EL DIA: 14/03/2027")
            )
        }
    }

    @Test
    fun `las cartas que no llevaban el bloque de numeros no imprimen vencimiento`() {
        listOf(
            DesenlaceImpreso.NO_ESTABA,
            DesenlaceImpreso.PROMETIO,
            DesenlaceImpreso.CITA
        ).forEach { desenlace ->
            val papel = corrido(
                texto(
                    TicketDeVisitaFixtures.ticket(
                        desenlace = desenlace,
                        cuentas = TicketDeVisitaFixtures.unaCuenta()
                    )
                )
            )

            assertFalse(desenlace.name, papel.contains("SU FECHA DE VENCIMIENTO"))
        }
    }

    @Test
    fun `una cuenta sin vencimiento conocido NO imprime la linea`() {
        // Es el caso de todo plazo que no sea cuatro meses, y el de una
        // `sales.FECHA` ilegible: `VencimientoDelCredito` devuelve null y el
        // papel calla, en vez de afirmar un año que nadie respalda.
        val papel = corrido(
            texto(
                TicketDeVisitaFixtures.ticket(
                    desenlace = DesenlaceImpreso.VISITE_VUELVO,
                    cuentas = TicketDeVisitaFixtures.unaCuenta(vencimiento = null)
                )
            )
        )

        assertFalse(papel.contains("SU FECHA DE VENCIMIENTO"))
        // Control positivo: la carta sí salió, así que la ausencia significa algo.
        assertTrue(papel.contains("RECUERDE QUE LA PUNTUALIDAD EN SUS PAGOS"))
    }

    @Test
    fun `con dos cuentas el vencimiento se omite porque no vencen el mismo dia`() {
        // Las dos cuentas de la fixture vencen en días distintos a propósito:
        // una sola fecha suelta bajo dos folios mentiría sobre una de las dos.
        val papel = corrido(texto(TicketDeVisitaFixtures.ticket(DesenlaceImpreso.SE_NEGO)))

        assertFalse(papel.contains("SU FECHA DE VENCIMIENTO"))
        assertFalse(papel.contains("14/03/2027"))
        assertFalse(papel.contains("02/07/2027"))
        assertTrue(papel.contains("NO HEMOS TENIDO UNA RESPUESTA FAVORABLE"))
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
