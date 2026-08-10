package com.example.msp_app.core.printing.adapters

import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.TicketLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure coverage of the only load-bearing DantSu translation rule: emphasised
 * lines ([TicketLine.Header]/[TicketLine.Bold]) get wrapped in `<b>…</b>`, every
 * other line prints its plain rendered text, and lines join with `\n`. Layout is
 * already baked in by the renderer, so we assert wrapping and pass-through, not
 * spacing.
 */
class DantSuTicketTranslatorTest {
    private val profile = PrinterProfile.PROFILE_58MM

    @Test
    fun `header and bold are wrapped, plain and centered lines pass through`() {
        val ticket =
            listOf(
                TicketLine.Header("MUEBLERIA BONANZA"),
                TicketLine.Line("Cliente: Juan"),
                TicketLine.Bold("ABONO \$100.00"),
                TicketLine.CenteredLine("Gracias")
            )

        val text = DantSuTicketTranslator.translate(ticket, profile)

        assertEquals(
            listOf(
                "<b>MUEBLERIA BONANZA</b>",
                "Cliente: Juan",
                "<b>ABONO \$100.00</b>",
                "Gracias"
            ).joinToString("\n"),
            text
        )
    }

    @Test
    fun `separator is expanded to full width and blank stays empty, both unwrapped`() {
        val ticket = listOf(TicketLine.Separator(), TicketLine.Blank)

        val text = DantSuTicketTranslator.translate(ticket, profile)

        assertEquals("-".repeat(profile.charsPerLine) + "\n", text)
    }

    @Test
    fun `accented ticket content is ascii-folded before it reaches the printer`() {
        val ticket =
            listOf(
                TicketLine.Header("MUEBLERÍA BONANZA"),
                TicketLine.Line("Cliente: José Muñoz"),
                TicketLine.CenteredLine("¡Gracias por su pago!")
            )

        val text = DantSuTicketTranslator.translate(ticket, profile)

        assertEquals(
            listOf(
                "<b>MUEBLERIA BONANZA</b>",
                "Cliente: Jose Munoz",
                "Gracias por su pago!"
            ).joinToString("\n"),
            text
        )
        assertTrue(text.all { it.code < 128 })
    }
}
