package com.example.msp_app.feature.visitas.ui

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.model.CitaImpresa
import com.example.msp_app.feature.visitas.domain.model.CuentaImpresa
import com.example.msp_app.feature.visitas.domain.model.DesenlaceImpreso
import com.example.msp_app.feature.visitas.domain.model.PromesaImpresa
import com.example.msp_app.feature.visitas.domain.model.TicketDeVisita
import com.example.msp_app.feature.visitas.printing.TicketDeVisitaFormatter
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * El ticket de visita de Victoria Flores Olmedo — la misma clienta de
 * [VisitasFixtures], para que un cambio de cifras se vea en las dos partes.
 *
 * [REGISTRADA_EN] es el martes 1-sep-2026 a las 12:00 CDMX, el mismo "hoy" del
 * resto de las fixtures del módulo: el día de la visita y el día de las pruebas
 * coinciden por construcción, así que "fuera del día" siempre se produce
 * moviendo el reloj y nunca cambiando el dato.
 */
object TicketDeVisitaFixtures {

    /** Martes 1-sep-2026, 12:00 CDMX. El día de la visita. */
    val REGISTRADA_EN: Instant = Instant.parse("2026-09-01T18:00:00Z")

    /** La hora de la primera copia, cuando la hubo: 12:05 CDMX del mismo día. */
    val PRIMERA_COPIA: Instant = Instant.parse("2026-09-01T18:05:00Z")

    const val VISITA_ID: String = "vis-2026-09-01-victoria"

    val IMPRESORA: PrinterDevice = PrinterDevice(address = "00:11:22:33:44:55", name = "PT-210")

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    fun ticket(
        desenlace: DesenlaceImpreso = DesenlaceImpreso.NO_ESTABA,
        promesa: PromesaImpresa? = null,
        cita: CitaImpresa? = null,
        cuentas: List<CuentaImpresa> = dosCuentas()
    ): TicketDeVisita = TicketDeVisita(
        visitaId = VISITA_ID,
        registradaEn = REGISTRADA_EN,
        cliente = "Victoria Flores Olmedo",
        domicilio = "C. Hidalgo 214, Centro",
        cobrador = "Martín Salgado",
        desenlace = desenlace,
        cuentas = cuentas,
        saldoTotal = Money.sum(cuentas.map { it.saldo }),
        promesa = promesa,
        cita = cita
    )

    fun dosCuentas(): List<CuentaImpresa> = listOf(
        CuentaImpresa(folio = "V-5021", saldo = dinero("2100")),
        CuentaImpresa(folio = "V-5188", saldo = dinero("1450"))
    )

    /** Prometió el viernes $220 — el desenlace que difiere trabajo. */
    fun ticketConPromesa(monto: Money? = dinero("220")): TicketDeVisita = ticket(
        desenlace = DesenlaceImpreso.PROMETIO,
        promesa = PromesaImpresa(fecha = LocalDate.of(2026, 9, 4), monto = monto)
    )

    /** Cita el jueves a las 16:00. */
    fun ticketConCita(hora: LocalTime? = LocalTime.of(16, 0)): TicketDeVisita = ticket(
        desenlace = DesenlaceImpreso.CITA,
        cita = CitaImpresa(fecha = LocalDate.of(2026, 9, 3), hora = hora)
    )

    /** El estado listo para imprimir la PRIMERA copia de "no estaba". */
    fun primeraImpresion(ticket: TicketDeVisita = ticket()): TicketDeVisitaUiState =
        estado(ticket, PrintPermission.PrimeraImpresion)

    /** El estado de una promesa, listo para imprimir. */
    fun conPromesa(): TicketDeVisitaUiState =
        estado(ticketConPromesa(), PrintPermission.PrimeraImpresion)

    /** El estado cuando el papel siguiente ya sería una copia. */
    fun reimpresion(previas: Int = 1): TicketDeVisitaUiState = estado(
        ticket(),
        PrintPermission.Reimpresion(previas = previas, primeraVez = PRIMERA_COPIA)
    )

    /** El día siguiente: el CTA apagado y la banda que lo explica. */
    fun fueraDelDia(): TicketDeVisitaUiState = estado(ticket(), PrintPermission.FueraDelDia)

    /** El picker abierto con dos impresoras emparejadas. */
    fun eligiendoImpresora(): TicketDeVisitaUiState = primeraImpresion().copy(
        impresion = ImpresionDeVisitaUi(
            fase = FaseDeImpresionDeVisita.ELIGIENDO,
            disponibles = listOf(IMPRESORA, PrinterDevice("AA:BB:CC:DD:EE:FF", "PT-58"))
        )
    )

    /** El fallo de impresión, con su mensaje corto es-MX. */
    fun falloDeImpresion(): TicketDeVisitaUiState = primeraImpresion().copy(
        impresion = ImpresionDeVisitaUi(
            fase = FaseDeImpresionDeVisita.FALLO,
            impresora = IMPRESORA,
            mensaje = "activa el bluetooth"
        )
    )

    private fun estado(ticket: TicketDeVisita, permiso: PrintPermission): TicketDeVisitaUiState =
        TicketDeVisitaUiState(
            cargando = false,
            ticket = ticket,
            permiso = permiso,
            vistaPrevia = TicketDeVisitaFormatter.toTicketText(ticket, permiso)
        )
}
