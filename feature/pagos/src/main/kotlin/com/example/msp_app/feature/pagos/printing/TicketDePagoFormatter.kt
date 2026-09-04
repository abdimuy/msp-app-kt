package com.example.msp_app.feature.pagos.printing

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
import com.example.msp_app.feature.pagos.domain.model.TicketDePago

/**
 * El **ticket de pago**: qué se imprime y cómo se ve en 32 caracteres.
 *
 * ## Qué se tomó del ticket viejo y qué cambió
 *
 * El ticket de hoy (`app/.../PaymentTicketScreen.kt`) se arma dentro de un
 * `LaunchedEffect` de un Composable de 680 líneas, con `Double.toCurrency` para
 * cada monto y con la venta, los productos, el usuario y el historial leídos por
 * cuatro ViewModels distintos. Se conserva **el contenido que el cobrador y el
 * cliente ya reconocen** —folio, cliente, domicilio, saldo anterior / abonado /
 * saldo actual, últimos pagos, quién cobró y el pie de "exija su comprobante"— y
 * cambia todo lo demás:
 *
 * 1. **Es una función pura.** Recibe un [TicketDePago] y devuelve
 *    [TicketLine]s: se prueba sin Android, sin Room y sin impresora.
 * 2. **El dinero nunca pasa por `Double`.** Cada importe es [Money] (escala 2) y
 *    se renderiza con [formatMoneyMxn], que redondea **al peso** solo en el
 *    borde de display — la misma resolución que ya usan el tablero y el ticket
 *    de cobranza. `Double.toCurrency(noDecimals = true)` redondeaba desde un
 *    flotante, que es de dónde salen los centavos fantasma.
 * 3. **Bloque de dinero primero.** El ticket viejo abre con quince renglones de
 *    la venta y entierra el abono a la mitad. Aquí lo primero después del
 *    encabezado es lo que el cliente vino a ver: cuánto pagó y cuánto debe.
 * 4. **Lenguaje visual del reporte de cobranza:** encabezado centrado, reglas de
 *    ancho completo, filas de dos columnas con el importe pegado a la derecha
 *    ([TicketLayout.twoCol]) y bloques separados por rótulo — igual que
 *    `CollectionReportFormatter`.
 * 5. **La marca de reimpresión** ([ReprintMark]) va arriba del todo, donde no se
 *    puede no ver.
 *
 * Se dejaron fuera los productos de la venta (el ticket viejo los lista): son
 * de la venta, no del abono, y ocupan la mitad del papel en un ticket cuyo
 * asunto es un pago. El detalle de venta (Task 16) los muestra en pantalla.
 *
 * Todo literal es ASCII: el fold del codepage
 * (`foldToPrintableAscii`) **descarta** lo que no puede imprimir, y un carácter
 * descartado corre una línea ya centrada. Los nombres de cliente sí llevan
 * acentos y esos el fold los mapea 1:1.
 */
object TicketDePagoFormatter {

    private const val NEGOCIO = "MUEBLES SAN PABLO"
    private const val TITULO = "TICKET DE PAGO"
    private const val PIE_GRACIAS = "GRACIAS POR SU PAGO"
    private const val PIE_COMPROBANTE = "EXIJA SU COMPROBANTE"
    private const val LABEL_FOLIO = "Folio"
    private const val LABEL_FECHA = "Fecha"
    private const val LABEL_ABONO = "ABONO"
    private const val LABEL_SALDO_ANTERIOR = "Saldo anterior"
    private const val LABEL_SALDO_ACTUAL = "Saldo actual"
    private const val LABEL_PLAN = "PLAN DE PAGOS"
    private const val LABEL_TOTAL = "Total a credito"
    private const val LABEL_PARCIALIDAD = "Parcialidad"
    private const val LABEL_ABONOS = "Abonos"
    private const val LABEL_ULTIMOS = "ULTIMOS PAGOS"
    private const val LABEL_COBRO = "Cobro"
    private const val SALTO = "\n"
    private const val PATRON_FECHA_LARGA = "dd/MM/yyyy HH:mm"
    private const val PATRON_FECHA_CORTA = "dd/MM/yy"

    /**
     * El ticket semántico para [profile]. [permiso] solo decide si se estampa la
     * marca de reimpresión: **decidir si se puede imprimir no es del formatter**,
     * es de `PrintDayRule` y del ViewModel, que apagan el botón mucho antes.
     */
    fun toTicketLines(
        ticket: TicketDePago,
        permiso: PrintPermission,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): PrintableTicket {
        val ancho = profile.charsPerLine
        return buildList {
            add(TicketLine.Header(TicketLayout.center(NEGOCIO, ancho)))
            add(TicketLine.CenteredLine(TicketLayout.center(TITULO, ancho)))
            addAll(ReprintMark.lines(permiso, ancho))
            add(TicketLine.Separator())
            agregaIdentidad(ticket, ancho)
            add(TicketLine.Separator())
            agregaDinero(ticket, ancho)
            add(TicketLine.Separator())
            agregaPlan(ticket, ancho)
            agregaUltimosPagos(ticket, ancho)
            add(TicketLine.Separator())
            add(TicketLine.Line(TicketLayout.twoCol(LABEL_COBRO, ticket.cobrador, ancho)))
            add(TicketLine.Separator())
            add(TicketLine.Bold(TicketLayout.center(PIE_GRACIAS, ancho)))
            add(TicketLine.CenteredLine(TicketLayout.center(PIE_COMPROBANTE, ancho)))
        }
    }

    /** El MISMO contenido como texto plano — la vista previa en pantalla. */
    fun toTicketText(
        ticket: TicketDePago,
        permiso: PrintPermission,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): String = TicketRenderer.render(toTicketLines(ticket, permiso, profile), profile)
        .joinToString(SALTO)

    private fun MutableList<TicketLine>.agregaIdentidad(ticket: TicketDePago, ancho: Int) {
        add(TicketLine.Line(TicketLayout.twoCol(LABEL_FOLIO, ticket.folio, ancho)))
        TicketLayout.wrap(ticket.cliente, ancho).forEach { add(TicketLine.Bold(it)) }
        if (ticket.domicilio.isNotBlank()) {
            TicketLayout.wrap(ticket.domicilio, ancho).forEach { add(TicketLine.Line(it)) }
        }
        if (ticket.telefono.isNotBlank()) {
            add(TicketLine.Line(TicketLayout.twoCol("Tel", ticket.telefono, ancho)))
        }
        add(
            TicketLine.Line(
                TicketLayout.twoCol(
                    LABEL_FECHA,
                    AppTime.formatForDisplay(ticket.cobradoEn, PATRON_FECHA_LARGA),
                    ancho
                )
            )
        )
    }

    /**
     * El bloque que el cliente lee primero: cuánto pagó y en qué quedó su saldo.
     * El abono va en [TicketLine.Bold] — es el número por el que se firma.
     */
    private fun MutableList<TicketLine>.agregaDinero(ticket: TicketDePago, ancho: Int) {
        add(
            TicketLine.Bold(
                TicketLayout.twoCol(LABEL_ABONO, dinero(ticket.importe), ancho)
            )
        )
        add(
            TicketLine.Line(
                TicketLayout.twoCol("Forma", ticket.metodo.etiqueta, ancho)
            )
        )
        add(
            TicketLine.Line(
                TicketLayout.twoCol(LABEL_SALDO_ANTERIOR, dinero(ticket.saldoAnterior), ancho)
            )
        )
        add(
            TicketLine.Bold(
                TicketLayout.twoCol(LABEL_SALDO_ACTUAL, dinero(ticket.saldoActual), ancho)
            )
        )
    }

    private fun MutableList<TicketLine>.agregaPlan(ticket: TicketDePago, ancho: Int) {
        add(TicketLine.Bold(LABEL_PLAN))
        add(
            TicketLine.Line(
                TicketLayout.twoCol(LABEL_TOTAL, dinero(ticket.totalVenta), ancho)
            )
        )
        add(
            TicketLine.Line(
                TicketLayout.twoCol(LABEL_PARCIALIDAD, dinero(ticket.parcialidad), ancho)
            )
        )
        add(
            TicketLine.Line(
                TicketLayout.twoCol(
                    LABEL_ABONOS,
                    "${ticket.abonosPagados} de ${ticket.abonosTotales}",
                    ancho
                )
            )
        )
    }

    /**
     * Los abonos anteriores. Si no hay ninguno **el bloque entero se omite** en
     * vez de imprimir un rótulo vacío: es el primer pago de la venta, y un
     * "ULTIMOS PAGOS" seguido de nada se lee como un error de la impresora.
     */
    private fun MutableList<TicketLine>.agregaUltimosPagos(ticket: TicketDePago, ancho: Int) {
        if (ticket.ultimosPagos.isEmpty()) return
        add(TicketLine.Separator())
        add(TicketLine.Bold(LABEL_ULTIMOS))
        ticket.ultimosPagos.forEach { pago ->
            val fecha = AppTime.formatDate(pago.fecha, PATRON_FECHA_CORTA)
            add(
                TicketLine.Line(
                    TicketLayout.twoCol(
                        "$fecha ${pago.metodo.etiqueta}",
                        dinero(pago.importe),
                        ancho
                    )
                )
            )
        }
    }

    /**
     * **El único borde donde el dinero se vuelve texto**, en peso entero
     * ([formatMoneyMxn], HALF_UP). El [Money] de escala 2 sigue exacto por
     * dentro; el papel es el que no lleva centavos.
     */
    private fun dinero(monto: Money): String = formatMoneyMxn(monto.amount)
}
