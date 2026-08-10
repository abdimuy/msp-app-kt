package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.ReportPaymentLine
import com.example.msp_app.core.printing.domain.ReportTicket
import com.example.msp_app.core.printing.domain.ReportTicketMethodRow
import com.example.msp_app.core.printing.domain.TicketLine
import java.time.Instant
import java.time.ZoneId
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Line-by-line tests of the pure [ReportTicketFormatter], mirroring
 * [PaymentReceiptFormatterTest]'s discipline: expected strings use explicit
 * `.padEnd`/`.padStart`/`" ".repeat(n)` so centring, column gaps and the
 * [TicketLine.ColumnRow] layout are pinned exactly. The JVM default time zone
 * is pinned to America/Mexico_City (fixed UTC-6, no DST) for the "Detalle de
 * pagos" tests — [ReportTicketFormatter] renders each payment's date prefix
 * in the device-local zone, mirroring [PaymentReceiptMapperTest]'s discipline.
 */
class ReportTicketFormatterTest {
    private val formatter = ReportTicketFormatter()
    private lateinit var originalDefaultZone: TimeZone

    @Before
    fun pinTimeZone() {
        originalDefaultZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Mexico_City")))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalDefaultZone)
    }

    private fun diario() = ReportTicket(
        negocio = "Mueblería Bonanza",
        sucursal = "",
        title = "REPORTE DEL DÍA",
        rangeLabel = "Jueves 3 de julio",
        collector = "C. Chávez",
        methodRows =
        listOf(
            ReportTicketMethodRow(method = "Efectivo", amount = "$700.00", count = 2),
            ReportTicketMethodRow(method = "Transferencia", amount = "$350.00", count = 1)
        ),
        paymentCount = 3,
        totalLabel = "TOTAL COBRADO",
        totalAmount = "$1,050.00"
    )

    private fun corte() = ReportTicket(
        negocio = "Mueblería Bonanza",
        sucursal = "Ruta Centro",
        title = "CORTE DE PERIODO",
        rangeLabel = "07 jul – 15 jul",
        collector = "C. Chávez",
        methodRows = listOf(
            ReportTicketMethodRow(method = "Efectivo", amount = "$4,200.00", count = 9)
        ),
        paymentCount = 9,
        totalLabel = "TOTAL COBRADO",
        totalAmount = "$4,200.00",
        coverageLabel = "Cobertura 8/10 · 80%",
        notes = "Todo en orden",
        isWeekly = true
    )

    /** 2026-07-03 09:15 America/Mexico_City. */
    private fun epochMorning() = Instant.parse("2026-07-03T15:15:00Z").toEpochMilli()

    /** 2026-07-08 10:05 America/Mexico_City. */
    private fun epochLater() = Instant.parse("2026-07-08T16:05:00Z").toEpochMilli()

    private fun render(
        ticket: ReportTicket,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ) = TicketRenderer.render(formatter.format(ticket, profile), profile)

    @Test
    fun `renders the full diario ticket line by line`() {
        val rule = "-".repeat(WIDTH_58)
        val expected =
            listOf(
                " ".repeat(7) + "Mueblería Bonanza",
                rule,
                " ".repeat(8) + "REPORTE DEL DÍA",
                "Periodo" + " ".repeat(8) + "Jueves 3 de julio",
                rule,
                " ".repeat(11) + "POR MÉTODO",
                "Efectivo".padEnd(16) + "x2".padEnd(5) + "$700.00".padStart(11),
                "Transferencia".padEnd(16) + "x1".padEnd(5) + "$350.00".padStart(11),
                rule,
                "Recibos" + " ".repeat(24) + "3",
                "TOTAL COBRADO" + " ".repeat(10) + "$1,050.00",
                rule,
                "Cobró" + " ".repeat(18) + "C. Chávez",
                "",
                rule,
                " ".repeat(7) + "Firma de recibido",
                "",
                " ".repeat(8) + "FIN DEL REPORTE"
            )

        assertEquals(expected, render(diario()))
    }

    @Test
    fun `renders the full corte ticket line by line, including sucursal, coverage and notes`() {
        val rule = "-".repeat(WIDTH_58)
        val expected =
            listOf(
                " ".repeat(7) + "Mueblería Bonanza",
                " ".repeat(10) + "Ruta Centro",
                rule,
                " ".repeat(8) + "CORTE DE PERIODO",
                "Periodo" + " ".repeat(10) + "07 jul – 15 jul",
                rule,
                " ".repeat(11) + "POR MÉTODO",
                "Efectivo".padEnd(16) + "x9".padEnd(5) + "$4,200.00".padStart(11),
                rule,
                "Recibos" + " ".repeat(24) + "9",
                "TOTAL COBRADO" + " ".repeat(10) + "$4,200.00",
                " ".repeat(6) + "Cobertura 8/10 · 80%",
                rule,
                "Cobró" + " ".repeat(18) + "C. Chávez",
                "Notas: Todo en orden",
                "",
                rule,
                " ".repeat(7) + "Firma de recibido",
                "",
                " ".repeat(8) + "FIN DEL REPORTE"
            )

        assertEquals(expected, render(corte()))
    }

    @Test
    fun `omits the coverage line when null`() {
        val lines = render(diario())

        assertFalse(lines.any { it.contains("Cobertura") })
    }

    @Test
    fun `omits the notes line when null`() {
        val lines = render(diario())

        assertFalse(lines.any { it.startsWith("Notas:") })
    }

    @Test
    fun `blank notes are treated as absent`() {
        val lines = render(corte().copy(notes = "   "))

        assertFalse(lines.any { it.startsWith("Notas:") })
    }

    @Test
    fun `an empty method list prints a placeholder instead of no rows`() {
        val lines = render(diario().copy(methodRows = emptyList()))

        assertTrue(lines.any { it.contains("Sin pagos registrados") })
    }

    @Test
    fun `word-wraps long notes across lines`() {
        val lines = render(corte().copy(notes = "Cierre puntual sin novedades reportadas"))

        val start = lines.indexOf("Notas: Cierre puntual sin")
        assertTrue("wrapped first line present, got: $lines", start >= 0)
        assertEquals("novedades reportadas", lines[start + 1])
    }

    @Test
    fun `emits the total as Bold and the section titles as Header on the raw ticket`() {
        val ticket = formatter.format(diario(), PrinterProfile.PROFILE_58MM)
        val headerTexts = ticket.filterIsInstance<TicketLine.Header>().map { it.text.trim() }
        val boldLine = ticket.filterIsInstance<TicketLine.Bold>().find {
            it.text.contains(
                "$1,050.00"
            )
        }

        assertTrue("negocio should be a Header", headerTexts.any { it == "Mueblería Bonanza" })
        assertTrue("title should be a Header", headerTexts.any { it == "REPORTE DEL DÍA" })
        assertTrue("closing line should be a Header", headerTexts.any { it == "FIN DEL REPORTE" })
        assertTrue("expected the total to be a Bold line", boldLine != null)
    }

    @Test
    fun `emits method rows as ColumnRow on the raw ticket`() {
        val ticket = formatter.format(diario(), PrinterProfile.PROFILE_58MM)
        val columnRows = ticket.filterIsInstance<TicketLine.ColumnRow>()

        assertEquals(2, columnRows.size)
        assertEquals(listOf("Efectivo", "x2", "$700.00"), columnRows[0].cols)
        assertEquals(listOf("Transferencia", "x1", "$350.00"), columnRows[1].cols)
    }

    @Test
    fun `every separator line spans the full profile width`() {
        val lines58 = render(diario(), PrinterProfile.PROFILE_58MM)
        val lines80 = render(diario(), PrinterProfile.PROFILE_80MM)

        assertTrue(lines58.contains("-".repeat(WIDTH_58)))
        assertTrue(lines80.contains("-".repeat(WIDTH_80)))
        assertTrue(lines58.none { it.length > WIDTH_58 })
        assertTrue(lines80.none { it.length > WIDTH_80 })
    }

    // --- New: Track 2 "Detalle de pagos" ---

    @Test
    fun `Detalle de pagos section is omitted when there are no payments`() {
        val lines = render(diario())

        assertFalse(lines.any { it.contains("DETALLE DE PAGOS") })
    }

    @Test
    fun `diario ticket lists Detalle de pagos with an hora prefix, before metodo and total`() {
        val ticket =
            diario().copy(
                payments =
                listOf(
                    ReportPaymentLine(
                        cliente = "Juan Pérez",
                        monto = "$700.00",
                        recordedAtEpochMillis = epochMorning()
                    ),
                    ReportPaymentLine(
                        cliente = "María Guzmán",
                        monto = "$350.00",
                        recordedAtEpochMillis = epochLater()
                    )
                )
            )
        val lines = render(ticket)

        val detalleIndex = lines.indexOf("DETALLE DE PAGOS")
        val metodoIndex = lines.indexOf(" ".repeat(11) + "POR MÉTODO")
        val totalIndex = lines.indexOf("TOTAL COBRADO" + " ".repeat(10) + "$1,050.00")

        assertTrue("DETALLE DE PAGOS header present", detalleIndex >= 0)
        assertEquals("09:15 Juan Pérez" + " ".repeat(9) + "$700.00", lines[detalleIndex + 1])
        assertEquals("10:05 María Guzmán" + " ".repeat(7) + "$350.00", lines[detalleIndex + 2])
        assertTrue("Detalle de pagos precedes POR MÉTODO", detalleIndex < metodoIndex)
        assertTrue("POR MÉTODO precedes the total", metodoIndex < totalIndex)
    }

    @Test
    fun `corte ticket lists Detalle de pagos with a fecha+hora prefix`() {
        val ticket =
            corte().copy(
                payments =
                listOf(
                    ReportPaymentLine(
                        cliente = "Juan Pérez",
                        monto = "$700.00",
                        recordedAtEpochMillis = epochMorning()
                    ),
                    ReportPaymentLine(
                        cliente = "María Guzmán",
                        monto = "$350.00",
                        recordedAtEpochMillis = epochLater()
                    )
                )
            )
        val lines = render(ticket)

        assertTrue(lines.contains("03/07 09:15 Juan Pérez" + " ".repeat(3) + "$700.00"))
        assertTrue(lines.any { it.contains("08/07 10:05 María Guzmán") })
    }

    private companion object {
        const val WIDTH_58 = 32
        const val WIDTH_80 = 48
    }
}
