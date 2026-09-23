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
 *    acababa de prometer pagar. Ahora el texto sale del desenlace que quedó
 *    escrito (`CargarTicketDeVisita`), así que el papel y la base no se
 *    contradicen.
 * 2. **La promesa y la cita se imprimen desde sus campos**, no desde la nota. El
 *    ticket viejo no las imprimía en absoluto porque no existían como dato.
 * 3. **El dinero nunca pasa por `Double`.** [Money] escala 2 por dentro,
 *    [formatMoneyMxn] (peso entero, HALF_UP) solo en el borde del papel. El
 *    ticket viejo usaba `Double.toCurrency(noDecimals = true)`, que redondea
 *    desde un flotante.
 * 4. **Ya no se inventa nada.** El viejo imprimía "SU COMPROMISO FUE DAR ABONOS
 *    SEMANALES DE $200.00" como literal, para todos los clientes por igual, y
 *    una "fecha de vencimiento" calculada como *fecha de venta + 1 año* sin
 *    ningún respaldo. Las dos se van: un papel con una cifra inventada es peor
 *    que un papel sin ella.
 * 5. **Lenguaje visual del reporte de cobranza:** encabezado centrado, reglas de
 *    ancho completo, dos columnas con el importe a la derecha, bloques con
 *    rótulo. Y la marca de reimpresión ([ReprintMark]) arriba del todo.
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
