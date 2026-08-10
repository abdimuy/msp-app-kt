package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the presentation-only expansion [TicketRenderer] performs on top of the
 * width-baked text lines: full-width separators, blank lines, and the weighted
 * [TicketLine.ColumnRow] layout (the reserved column type the payment formatter
 * pre-renders to text but other tickets/adapters may emit directly).
 */
class TicketRendererTest {
    @Test
    fun `expands a separator to the full profile width and blank to empty`() {
        val lines =
            TicketRenderer.render(
                listOf(TicketLine.Separator('='), TicketLine.Blank, TicketLine.Separator()),
                PrinterProfile.PROFILE_58MM
            )

        assertEquals(listOf("=".repeat(WIDTH_58), "", "-".repeat(WIDTH_58)), lines)
    }

    @Test
    fun `passes width-baked text lines through verbatim`() {
        val lines =
            TicketRenderer.render(
                listOf(
                    TicketLine.Header("   TITLE"),
                    TicketLine.CenteredLine("  centered"),
                    TicketLine.Bold("bold"),
                    TicketLine.Line("plain")
                ),
                PrinterProfile.PROFILE_58MM
            )

        assertEquals(listOf("   TITLE", "  centered", "bold", "plain"), lines)
    }

    @Test
    fun `lays out a two-column row left- and right-aligned`() {
        val lines =
            TicketRenderer.render(
                listOf(TicketLine.ColumnRow(listOf("ABONO", "$350.00"), listOf(1, 1))),
                PrinterProfile.PROFILE_58MM
            )

        assertEquals(listOf("ABONO" + " ".repeat(20) + "$350.00"), lines)
        assertEquals(WIDTH_58, lines.single().length)
    }

    @Test
    fun `distributes a three-column row by weights`() {
        val lines =
            TicketRenderer.render(
                listOf(TicketLine.ColumnRow(listOf("A", "B", "C"), listOf(1, 1, 2))),
                PrinterProfile.PROFILE_58MM
            )

        assertEquals(
            listOf("A" + " ".repeat(7) + "B" + " ".repeat(7) + " ".repeat(15) + "C"),
            lines
        )
        assertEquals(WIDTH_58, lines.single().length)
    }

    private companion object {
        const val WIDTH_58 = 32
    }
}
