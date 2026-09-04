package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoImpreso
import com.example.msp_app.feature.pagos.domain.model.TicketDePago
import com.example.msp_app.feature.pagos.printing.TicketDePagoFormatter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * El ticket de pago de Victoria Flores Olmedo — el mismo cliente que
 * [PagosFixtures], para que un cambio de cifras se vea en las dos partes a la
 * vez. Nombres mexicanos, como pide el repo.
 *
 * [COBRADO_EN] es martes 1-sep-2026 a las 12:00 CDMX, el mismo "ahora" del resto
 * de las fixtures del módulo: el día del cobro y el día de las pruebas son el
 * mismo por construcción, así que "fuera del día" siempre se produce moviendo el
 * reloj y nunca cambiando el dato.
 */
object TicketFixtures {

    /** Martes 1-sep-2026, 12:00 CDMX. El día del cobro. */
    val COBRADO_EN: Instant = Instant.parse("2026-09-01T18:00:00Z")

    /** La hora de la primera copia, cuando la hubo: 12:05 CDMX del mismo día. */
    val PRIMERA_COPIA: Instant = Instant.parse("2026-09-01T18:05:00Z")

    const val PAGO_ID: String = "COB-A-10388"

    val IMPRESORA: PrinterDevice = PrinterDevice(address = "00:11:22:33:44:55", name = "PT-210")

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    fun ticket(
        ultimosPagos: List<PagoImpreso> = ultimosPagos(),
        importe: Money = dinero("350")
    ): TicketDePago = TicketDePago(
        pagoId = PAGO_ID,
        cobradoEn = COBRADO_EN,
        folio = "V-5188",
        cliente = "Victoria Flores Olmedo",
        domicilio = "C. Hidalgo 214, Centro",
        telefono = "238 162 7597",
        cobrador = "Martín Salgado",
        metodo = MetodoDeCobro.EFECTIVO,
        importe = importe,
        saldoAnterior = dinero("1800"),
        saldoActual = dinero("1450"),
        totalVenta = dinero("6400"),
        parcialidad = dinero("220"),
        abonosPagados = 12,
        abonosTotales = 20,
        ultimosPagos = ultimosPagos
    )

    fun ultimosPagos(): List<PagoImpreso> = listOf(
        PagoImpreso(LocalDate.of(2026, 8, 25), dinero("220"), MetodoDeCobro.EFECTIVO),
        PagoImpreso(LocalDate.of(2026, 8, 18), dinero("220"), MetodoDeCobro.TRANSFERENCIA)
    )

    /** El estado listo para imprimir la PRIMERA copia. */
    fun primeraImpresion(): TicketDePagoUiState = estado(PrintPermission.PrimeraImpresion)

    /** El estado cuando el papel siguiente ya sería una copia. */
    fun reimpresion(previas: Int = 1): TicketDePagoUiState =
        estado(PrintPermission.Reimpresion(previas = previas, primeraVez = PRIMERA_COPIA))

    /** El estado del día siguiente: el CTA apagado y la banda que lo explica. */
    fun fueraDelDia(): TicketDePagoUiState = estado(PrintPermission.FueraDelDia)

    /** El picker abierto con dos impresoras emparejadas. */
    fun eligiendoImpresora(): TicketDePagoUiState = primeraImpresion().copy(
        impresion = ImpresionUi(
            fase = FaseDeImpresion.ELIGIENDO,
            disponibles = listOf(IMPRESORA, PrinterDevice("AA:BB:CC:DD:EE:FF", "PT-58"))
        )
    )

    /** El fallo de impresión, con su mensaje corto es-MX. */
    fun falloDeImpresion(): TicketDePagoUiState = primeraImpresion().copy(
        impresion = ImpresionUi(
            fase = FaseDeImpresion.FALLO,
            impresora = IMPRESORA,
            mensaje = "activa el bluetooth"
        )
    )

    /** Ya salió el papel: la copia quedó registrada. */
    fun impreso(): TicketDePagoUiState = reimpresion().copy(
        impresion = ImpresionUi(fase = FaseDeImpresion.IMPRESO, impresora = IMPRESORA)
    )

    private fun estado(permiso: PrintPermission): TicketDePagoUiState {
        val ticket = ticket()
        return TicketDePagoUiState(
            cargando = false,
            ticket = ticket,
            permiso = permiso,
            vistaPrevia = TicketDePagoFormatter.toTicketText(ticket, permiso)
        )
    }
}
