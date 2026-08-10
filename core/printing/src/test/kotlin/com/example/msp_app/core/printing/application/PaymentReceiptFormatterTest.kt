package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PaymentReceipt
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.ReceiptHistoryLine
import com.example.msp_app.core.printing.domain.ReceiptProductLine
import com.example.msp_app.core.printing.domain.TicketLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Line-by-line (golden) tests of the pure [PaymentReceiptFormatter]. Each case
 * renders the formatter output through [TicketRenderer] (which expands
 * separators/blanks) and asserts the exact printed strings for the 58 mm (32
 * char) profile — matching the approved spec mockup. Expected strings use
 * explicit `" ".repeat(n)` so centring and column gaps are pinned exactly; an
 * off-by-one in the layout fails the test.
 */
class PaymentReceiptFormatterTest {
    private val formatter = PaymentReceiptFormatter()

    /** The rich fixture from the spec mockup: products + full saldo detail + history + client contact. */
    private fun rich() = PaymentReceipt(
        negocio = "Mueblería Bonanza",
        sucursal = "",
        folio = "COB-A-000123",
        fechaHora = "15/07/2026 14:32",
        cliente = "Juan Pérez García",
        domicilio = "Av. Reforma 123, Centro",
        telefonoCliente = "322-111-2222",
        credito = "V-1180",
        concepto = "Sala Nápoles 3 pzas",
        productos =
        listOf(
            ReceiptProductLine("Sala esquinera 3 pzas", 1, "$9,600.00"),
            ReceiptProductLine("Colchon matrimonial", 1, "$6,000.00")
        ),
        precioTotal = "$15,600.00",
        enganche = "$3,000.00",
        abono = "$350.00",
        metodo = "Efectivo",
        saldoAnterior = "$11,750.00",
        saldoActual = "$11,400.00",
        pagadoALaFecha = "$4,200.00",
        ultimosPagos =
        listOf(
            ReceiptHistoryLine("10/06/26", "$350.00", "Efec."),
            ReceiptHistoryLine("10/05/26", "$350.00", "Transf."),
            ReceiptHistoryLine("08/04/26", "$700.00", "Efec.")
        ),
        cobrador = "Beto Sesion",
        telefonos = "Tel. 322-123-4567 · WhatsApp 322-765-4321"
    )

    private fun render(
        receipt: PaymentReceipt,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ) = TicketRenderer.render(formatter.format(receipt, profile), profile)

    @Test
    fun `renders the full 58mm ticket line by line matching the mockup`() {
        val rule = "-".repeat(WIDTH_58)
        val expected =
            listOf(
                " ".repeat(7) + "Mueblería Bonanza",
                rule,
                " ".repeat(9) + "RECIBO DE PAGO",
                "Folio" + " ".repeat(15) + "COB-A-000123",
                "Fecha" + " ".repeat(11) + "15/07/2026 14:32",
                rule,
                "Cliente: Juan Pérez García",
                "Dom: Av. Reforma 123, Centro",
                "Tel: 322-111-2222",
                "Crédito" + " ".repeat(19) + "V-1180",
                rule,
                "PRODUCTOS",
                "Sala esquinera 3 pzas" + " ".repeat(9) + "x1",
                " ".repeat(23) + "$9,600.00",
                "Colchon matrimonial" + " ".repeat(11) + "x1",
                " ".repeat(23) + "$6,000.00",
                rule,
                "Total a crédito" + " ".repeat(7) + "$15,600.00",
                "Enganche" + " ".repeat(15) + "$3,000.00",
                rule,
                "ABONO" + " ".repeat(20) + "$350.00",
                "Pago" + " ".repeat(20) + "Efectivo",
                rule,
                "Saldo anterior" + " ".repeat(8) + "$11,750.00",
                "Abono" + " ".repeat(18) + "- $350.00",
                "Saldo actual" + " ".repeat(10) + "$11,400.00",
                "Pagado a la fecha" + " ".repeat(6) + "$4,200.00",
                " " + "* saldo sujeto a confirmación",
                rule,
                "ULTIMOS PAGOS",
                "10/06/26" + " ".repeat(4) + "$350.00" + " ".repeat(8) + "Efec.",
                "10/05/26" + " ".repeat(4) + "$350.00" + " ".repeat(6) + "Transf.",
                "08/04/26" + " ".repeat(4) + "$700.00" + " ".repeat(8) + "Efec.",
                rule,
                "Cobró" + " ".repeat(16) + "Beto Sesion",
                rule,
                " ".repeat(5) + "¡Gracias por su pago!",
                " ".repeat(2) + "Tel. 322-123-4567 · WhatsApp",
                " ".repeat(10) + "322-765-4321"
            )

        assertEquals(expected, render(rich()))
    }

    @Test
    fun `removes the Firma de recibido signature block entirely`() {
        val lines = render(rich())

        assertFalse(lines.any { it.contains("Firma") })
    }

    @Test
    fun `omits the PRODUCTOS block and falls back to concepto when there are no product lines`() {
        val lines = render(rich().copy(productos = emptyList(), precioTotal = null))

        assertFalse("no PRODUCTOS header", lines.any { it == "PRODUCTOS" })
        assertFalse("no Total a crédito line", lines.any { it.startsWith("Total a crédito") })
        assertFalse("no Enganche line without a total", lines.any { it.startsWith("Enganche") })
        assertTrue("concepto shown as fallback", lines.contains("Sala Nápoles 3 pzas"))
        assertNoDoubleSeparators(lines)
    }

    @Test
    fun `shows Total a credito, and Enganche, even with no product lines`() {
        val lines = render(rich().copy(productos = emptyList()))

        assertFalse("no PRODUCTOS header", lines.any { it == "PRODUCTOS" })
        assertTrue(lines.contains("Total a crédito" + " ".repeat(7) + "$15,600.00"))
        assertTrue(lines.contains("Enganche" + " ".repeat(15) + "$3,000.00"))
        assertNoDoubleSeparators(lines)
    }

    @Test
    fun `Enganche prints right after Total a credito, before the closing separator`() {
        val lines = render(rich())

        val totalIndex = lines.indexOf("Total a crédito" + " ".repeat(7) + "$15,600.00")
        assertTrue("Total a crédito line present", totalIndex >= 0)
        assertEquals("Enganche" + " ".repeat(15) + "$3,000.00", lines[totalIndex + 1])
    }

    @Test
    fun `Enganche line is omitted when null (mapper gates zero-or-absent down payments)`() {
        val lines = render(rich().copy(enganche = null))

        assertFalse(lines.any { it.startsWith("Enganche") })
        assertNoDoubleSeparators(lines)
    }

    @Test
    fun `omits the ULTIMOS PAGOS block when there is no prior history`() {
        val lines = render(rich().copy(ultimosPagos = emptyList()))

        assertFalse(lines.any { it == "ULTIMOS PAGOS" })
        assertNoDoubleSeparators(lines)
    }

    @Test
    fun `omits every saldo line and the disclaimer when saldo and pagado are null`() {
        val lines =
            render(rich().copy(saldoAnterior = null, saldoActual = null, pagadoALaFecha = null))

        assertFalse(lines.any { it.startsWith("Saldo anterior") })
        assertFalse(lines.any { it.startsWith("Saldo actual") })
        assertFalse(lines.any { it.startsWith("Pagado a la fecha") })
        assertFalse(lines.any { it.startsWith("Abono") })
        assertFalse(lines.any { it.contains("sujeto a confirmación") })
        assertNoDoubleSeparators(lines)
    }

    @Test
    fun `omits the Dom and Tel lines when client contact is missing`() {
        val lines = render(rich().copy(domicilio = null, telefonoCliente = null))

        assertFalse(lines.any { it.startsWith("Dom:") })
        assertFalse(lines.any { it.startsWith("Tel:") })
    }

    @Test
    fun `degrades to a minimal ticket with no optional blocks and no stray separators`() {
        val lines =
            render(
                rich().copy(
                    domicilio = null,
                    telefonoCliente = null,
                    concepto = "",
                    productos = emptyList(),
                    precioTotal = null,
                    enganche = null,
                    saldoAnterior = null,
                    saldoActual = null,
                    pagadoALaFecha = null,
                    ultimosPagos = emptyList()
                )
            )

        assertFalse(lines.any { it == "PRODUCTOS" })
        assertFalse(lines.any { it == "ULTIMOS PAGOS" })
        assertNoDoubleSeparators(lines)
    }

    @Test
    fun `word-wraps a long customer name instead of truncating it`() {
        val lines = render(rich().copy(cliente = "Juan Guillermo de la Cruz Villaseñor"))

        val start = lines.indexOf("Cliente: Juan Guillermo de la")
        assertTrue("wrapped first line present", start >= 0)
        assertEquals("Cruz Villaseñor", lines[start + 1])
    }

    @Test
    fun `word-wraps a long product description across lines keeping the whole name`() {
        val lines =
            render(
                rich().copy(
                    productos =
                    listOf(
                        ReceiptProductLine(
                            descripcion = "Comedor rectangular 6 sillas tapizadas caoba",
                            cantidad = 2,
                            importe = "$18,000.00"
                        )
                    ),
                    precioTotal = null
                )
            )

        // The full description survives across wrapped lines (never truncated).
        val joined = lines.joinToString(" ")
        assertTrue(joined.contains("Comedor rectangular 6 sillas"))
        assertTrue(joined.contains("tapizadas caoba"))
        // The quantity and the line total both appear on their own right-aligned lines.
        assertTrue(lines.any { it.trimEnd().endsWith("x2") })
        assertTrue(lines.contains(" ".repeat(WIDTH_58 - "$18,000.00".length) + "$18,000.00"))
    }

    @Test
    fun `emits the block headers as Bold and titles as Header on the raw ticket`() {
        val ticket = formatter.format(rich(), PrinterProfile.PROFILE_58MM)
        val boldTexts = ticket.filterIsInstance<TicketLine.Bold>().map { it.text.trim() }
        val headerTexts = ticket.filterIsInstance<TicketLine.Header>().map { it.text.trim() }

        assertTrue("PRODUCTOS is bold", boldTexts.any { it == "PRODUCTOS" })
        assertTrue("ULTIMOS PAGOS is bold", boldTexts.any { it == "ULTIMOS PAGOS" })
        assertTrue("ABONO row is bold", boldTexts.any { it.startsWith("ABONO") })
        assertTrue("Saldo actual row is bold", boldTexts.any { it.startsWith("Saldo actual") })
        assertTrue("RECIBO DE PAGO is a header", headerTexts.any { it == "RECIBO DE PAGO" })
        assertTrue("thanks is a header", headerTexts.any { it == "¡Gracias por su pago!" })
    }

    /** No optional block ever collapses into two adjacent separators (an empty section). */
    private fun assertNoDoubleSeparators(lines: List<String>) {
        val rule = "-".repeat(WIDTH_58)
        val doubled = (0 until lines.size - 1).any { lines[it] == rule && lines[it + 1] == rule }
        assertFalse("no two separators in a row", doubled)
    }

    private companion object {
        const val WIDTH_58 = 32
    }
}
