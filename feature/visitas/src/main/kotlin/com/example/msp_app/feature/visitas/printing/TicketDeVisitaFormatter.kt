package com.example.msp_app.feature.visitas.printing

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.application.ReprintMark
import com.example.msp_app.core.printing.application.TicketLayout
import com.example.msp_app.core.printing.application.TicketRenderer
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine
import com.example.msp_app.feature.visitas.domain.model.DesenlaceImpreso
import com.example.msp_app.feature.visitas.domain.model.TicketDeVisita

/**
 * El **ticket de visita**: el papel que se deja en la puerta.
 *
 * ## Qué se tomó del ticket viejo y qué cambió
 *
 * Del ticket al que reemplaza (`app/.../VisitTicketScreen.kt`, retirado por la
 * Task 21) se conserva lo que el
 * cliente ya reconoce: encabezado del negocio, "estimado cliente" con su nombre,
 * el cuerpo del mensaje, el saldo, y el cierre con el nombre del gestor. Cambia:
 *
 * 1. **El mensaje ya no se elige a mano.** El `DropdownMenu` de tres cartas
 *    —"visita", "cliente moroso", "no pago"— no tenía ninguna relación con lo
 *    registrado: se podía dejar una carta de cobranza dura donde el cliente
 *    acababa de prometer pagar. **Las tres cartas siguen siendo las mismas
 *    palabras** (ver [DesenlaceImpreso]); lo que cambia es que ahora la escoge
 *    el desenlace que quedó escrito (`CargarTicketDeVisita`), así que el papel
 *    y la base no se contradicen.
 * 2. **La promesa y la cita se imprimen desde sus campos**, no desde la nota. El
 *    ticket viejo no las imprimía en absoluto porque no existían como dato.
 * 3. **El dinero nunca pasa por `Double`.** [Money] escala 2 por dentro,
 *    [formatMoneyMxn] (peso entero, HALF_UP) solo en el borde del papel. El
 *    ticket viejo usaba `Double.toCurrency(noDecimals = true)`, que redondea
 *    desde un flotante.
 * 4. **Las cifras se leen, no se inventan.** El viejo imprimía "SU COMPROMISO
 *    FUE DAR ABONOS SEMANALES DE $200.00" como literal, para todos los clientes
 *    por igual; aquí esa línea sale de `CuentaImpresa.parcialidad`, la columna
 *    real, y se omite cuando no hay una sola cuenta que la sostenga (ver
 *    [agregaExhorto]). La "fecha de vencimiento" vuelve **acotada**: el viejo
 *    la imprimía para todas las ventas como *fecha de venta + 1 año*, sin nada
 *    que la respaldara; aquí sale solo donde el crédito sí corre a un año, y la
 *    regla —con su porqué— vive en `VencimientoDelCredito`. No vuelven "TOTAL
 *    DE COMPRA", "PAGOS VENCIDOS" ni "SUGERIDO PARA REGULARIZARSE": un papel
 *    con una cifra inventada es peor que un papel sin ella.
 * 5. **Lenguaje visual del reporte de cobranza:** encabezado centrado, reglas de
 *    ancho completo, dos columnas con el importe a la derecha, bloques con
 *    rótulo. Y la marca de reimpresión ([ReprintMark]) arriba del todo.
 *
 * El "SALDO ACTUAL" del ticket viejo tampoco se perdió: es el bloque
 * "SUS CUENTAS" con su renglón "Saldo total", que además desglosa por cuenta.
 *
 * Todo literal es ASCII: el fold del codepage **descarta** lo que no puede
 * imprimir, y un carácter descartado corre una línea ya centrada.
 */
object TicketDeVisitaFormatter {

    private const val NEGOCIO = "MUEBLES SAN PABLO"
    private const val TITULO = "TICKET DE VISITA"
    private const val SALUDO = "ESTIMADO CLIENTE"
    private const val LABEL_CUENTAS = "SUS CUENTAS"
    private const val LABEL_TOTAL = "Saldo total"
    private const val LABEL_COMPROMISO = "SU COMPROMISO"
    private const val LABEL_CITA = "SU CITA"
    private const val LABEL_FECHA = "Fecha"
    private const val LABEL_MONTO = "Monto"
    private const val LABEL_HORA = "Hora"
    private const val LABEL_ATENDIO = "Visito"
    private const val SIN_HORA = "sin hora"

    /** Primera línea del cierre de la carta 3; le sigue la cifra. */
    private const val ABONOS = "SU COMPROMISO FUE DAR ABONOS SEMANALES DE"

    /** Segunda línea del cierre de la carta 3, literal del ticket viejo. */
    private const val EXHORTO = "SE LE EXHORTA A REGULARIZARSE PARA EVITAR PENALIZACIONES."

    /** Rótulo del vencimiento, literal del ticket viejo; le sigue la fecha. */
    private const val VENCIMIENTO = "SU FECHA DE VENCIMIENTO DE SU CREDITO ES EL DIA:"

    /** Los desenlaces cuyas cartas llevaban el bloque de números en el viejo. */
    private val CON_VENCIMIENTO = setOf(DesenlaceImpreso.SE_NEGO, DesenlaceImpreso.VISITE_VUELVO)

    private const val SALTO = "\n"
    private const val PATRON_FECHA_LARGA = "dd/MM/yyyy HH:mm"
    private const val PATRON_FECHA_CORTA = "dd/MM/yyyy"
    private const val PATRON_HORA = "HH:mm"

    /**
     * El ticket semántico para [profile]. [permiso] solo decide si se estampa la
     * marca de reimpresión: decidir si se PUEDE imprimir es de `PrintDayRule`.
     */
    fun toTicketLines(
        ticket: TicketDeVisita,
        permiso: PrintPermission,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): PrintableTicket {
        val ancho = profile.charsPerLine
        return buildList {
            add(TicketLine.Header(TicketLayout.center(NEGOCIO, ancho)))
            add(TicketLine.CenteredLine(TicketLayout.center(TITULO, ancho)))
            addAll(ReprintMark.lines(permiso, ancho))
            add(TicketLine.Separator())
            add(
                TicketLine.CenteredLine(
                    TicketLayout.center(
                        AppTime.formatForDisplay(ticket.registradaEn, PATRON_FECHA_LARGA),
                        ancho
                    )
                )
            )
            add(TicketLine.Separator())
            agregaCliente(ticket, ancho)
            add(TicketLine.Separator())
            agregaMensaje(ticket, ancho)
            add(TicketLine.Separator())
            agregaCompromiso(ticket, ancho)
            agregaCuentas(ticket, ancho)
            add(TicketLine.Separator())
            add(TicketLine.Line(TicketLayout.twoCol(LABEL_ATENDIO, ticket.cobrador, ancho)))
        }
    }

    /** El MISMO contenido como texto plano — la vista previa en pantalla. */
    fun toTicketText(
        ticket: TicketDeVisita,
        permiso: PrintPermission,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): String = TicketRenderer.render(toTicketLines(ticket, permiso, profile), profile)
        .joinToString(SALTO)

    private fun MutableList<TicketLine>.agregaCliente(ticket: TicketDeVisita, ancho: Int) {
        add(TicketLine.Line(SALUDO))
        TicketLayout.wrap(ticket.cliente, ancho).forEach { add(TicketLine.Bold(it)) }
        if (ticket.domicilio.isNotBlank()) {
            TicketLayout.wrap(ticket.domicilio, ancho).forEach { add(TicketLine.Line(it)) }
        }
    }

    private fun MutableList<TicketLine>.agregaMensaje(ticket: TicketDeVisita, ancho: Int) {
        add(TicketLine.Bold(TicketLayout.center(ticket.desenlace.titulo, ancho)))
        add(TicketLine.Blank)
        ticket.desenlace.mensaje.forEachIndexed { indice, parrafo ->
            if (indice > 0) add(TicketLine.Blank)
            TicketLayout.wrap(parrafo, ancho).forEach { add(TicketLine.Line(it)) }
        }
        agregaExhorto(ticket, ancho)
        agregaVencimiento(ticket, ancho)
    }

    /**
     * La línea "SU FECHA DE VENCIMIENTO DE SU CREDITO ES EL DIA: ...", del
     * bloque de números que el ticket viejo imprimía bajo las cartas 2 y 3.
     *
     * **La fecha no se calcula aquí**: llega resuelta en
     * `CuentaImpresa.vencimiento` por `VencimientoDelCredito`, que es donde
     * está escrito por qué un crédito de cuatro meses de corto plazo sí vence a
     * un año y por qué fuera de ese caso no se afirma nada. `null` = no se
     * puede afirmar, y entonces la línea no sale.
     *
     * Y **solo con una cuenta en el papel**, por el mismo motivo que el abono
     * por periodo: dos ventas pueden vencer en fechas distintas, y una sola
     * fecha suelta bajo dos folios mentiría sobre una de las dos. El cobrador
     * llega a este papel con una cuenta en la mano siempre que entró por una
     * venta, que es el caso en que esta línea sirve.
     */
    private fun MutableList<TicketLine>.agregaVencimiento(ticket: TicketDeVisita, ancho: Int) {
        if (ticket.desenlace !in CON_VENCIMIENTO) return
        val vencimiento = ticket.cuentas.singleOrNull()?.vencimiento ?: return
        add(TicketLine.Blank)
        val fecha = AppTime.formatDate(vencimiento, PATRON_FECHA_CORTA)
        TicketLayout.wrap("$VENCIMIENTO $fecha", ancho).forEach { add(TicketLine.Line(it)) }
    }

    /**
     * El cierre de la **carta 3 del ticket viejo** ("Ticket de no Pago"): el
     * recordatorio del abono por periodo y el exhorto a regularizarse.
     *
     * Vive aquí y no en [DesenlaceImpreso] por una razón concreta: la primera
     * línea lleva una cifra, y una cifra no cabe en un `enum`.
     *
     * **La cifra se lee, no se inventa.** El ticket viejo imprimía
     * "SU COMPROMISO FUE DAR ABONOS SEMANALES DE $200.00" como literal, igual
     * para todos; aquí sale de `CuentaImpresa.parcialidad`, la misma columna
     * cruda que pinta `HojaDeAbono` en `:feature:pagos`. Y solo se imprime
     * cuando el papel nombra **una sola** cuenta con parcialidad positiva: con
     * dos cuentas "abonos semanales de $X" no dice de cuál es, y un `$0`
     * significaría un compromiso de no pagar. Sin la cifra queda el exhorto,
     * que se sostiene solo — es exactamente el mismo criterio que
     * [agregaCompromiso] aplica a una promesa sin monto.
     */
    private fun MutableList<TicketLine>.agregaExhorto(ticket: TicketDeVisita, ancho: Int) {
        if (ticket.desenlace != DesenlaceImpreso.VISITE_VUELVO) return
        add(TicketLine.Blank)
        ticket.cuentas.singleOrNull()
            ?.parcialidad
            ?.takeIf { it > Money.ZERO }
            ?.let { parcialidad ->
                TicketLayout.wrap("$ABONOS ${dinero(parcialidad)}", ancho)
                    .forEach { add(TicketLine.Line(it)) }
            }
        TicketLayout.wrap(EXHORTO, ancho).forEach { add(TicketLine.Line(it)) }
    }

    /**
     * Promesa y cita, cada una desde SU campo. Un monto ausente no se imprime
     * como `$0` —"dijo cuándo pero no cuánto" es un caso real de campo y un cero
     * significaría "prometió no pagar"—; el renglón se omite entero. Lo mismo
     * con la hora de la cita, que dice "sin hora" en vez de inventar una.
     */
    private fun MutableList<TicketLine>.agregaCompromiso(ticket: TicketDeVisita, ancho: Int) {
        ticket.promesa?.let { promesa ->
            add(TicketLine.Bold(LABEL_COMPROMISO))
            add(
                TicketLine.Line(
                    TicketLayout.twoCol(
                        LABEL_FECHA,
                        AppTime.formatDate(promesa.fecha, PATRON_FECHA_CORTA),
                        ancho
                    )
                )
            )
            promesa.monto?.let {
                add(TicketLine.Bold(TicketLayout.twoCol(LABEL_MONTO, dinero(it), ancho)))
            }
            add(TicketLine.Separator())
        }
        ticket.cita?.let { cita ->
            add(TicketLine.Bold(LABEL_CITA))
            add(
                TicketLine.Line(
                    TicketLayout.twoCol(
                        LABEL_FECHA,
                        AppTime.formatDate(cita.fecha, PATRON_FECHA_CORTA),
                        ancho
                    )
                )
            )
            val hora = cita.hora?.let { AppTime.formatTime(it, PATRON_HORA) } ?: SIN_HORA
            add(TicketLine.Line(TicketLayout.twoCol(LABEL_HORA, hora, ancho)))
            add(TicketLine.Separator())
        }
    }

    private fun MutableList<TicketLine>.agregaCuentas(ticket: TicketDeVisita, ancho: Int) {
        if (ticket.cuentas.isEmpty()) return
        add(TicketLine.Bold(LABEL_CUENTAS))
        ticket.cuentas.forEach { cuenta ->
            add(TicketLine.Line(TicketLayout.twoCol(cuenta.folio, dinero(cuenta.saldo), ancho)))
        }
        add(TicketLine.Bold(TicketLayout.twoCol(LABEL_TOTAL, dinero(ticket.saldoTotal), ancho)))
    }

    /**
     * **El único borde donde el dinero se vuelve texto**, en peso entero
     * ([formatMoneyMxn], HALF_UP). El [Money] de escala 2 sigue exacto por
     * dentro; el papel es el que no lleva centavos.
     */
    private fun dinero(monto: Money): String = formatMoneyMxn(monto.amount)
}
