package com.example.msp_app.core.printing.adapters

import java.text.Normalizer

/** Codepoints from here up are outside plain ASCII and are dropped. */
private const val ASCII_LIMIT = 128

/**
 * Folds [text] to the plain-ASCII subset every ESC/POS thermal printer in the
 * field can reliably render. The owner's call: no accents on a printed
 * ticket, ever — a limited printer codepage turns UTF-8 diacritics into
 * garbage, and the fix is to never send them.
 *
 * Spanish vowels with acute accents (`áéíóú`/`ÁÉÍÓÚ`) and the diaeresis
 * (`ü`/`Ü`) are stripped via Unicode NFD decomposition (each canonically
 * decomposes to its base letter + a combining mark, which is then dropped);
 * `ñ`/`Ñ` are mapped explicitly to `n`/`N`. Any other non-ASCII codepoint
 * that survives (`¿`, `¡`, curly quotes, em dashes, …) is dropped outright
 * rather than risk it printing as garbage.
 *
 * Folding is 1:1 per visible character — one accented letter maps to exactly
 * one ASCII letter — so it never changes a rendered line's length; the
 * fixed-width layout baked in by the ticket formatters and [TicketRenderer]
 * is preserved.
 *
 * Applied once, centrally, in [DantSuTicketTranslator.translate] on the
 * final formatted string right before it is handed to the printer, so every
 * ticket type — report tickets and payment receipts alike — is covered by
 * this one pure, unit-tested seam.
 */
fun foldToPrintableAscii(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
    return buildString(normalized.length) {
        for (ch in normalized) {
            when {
                Character.getType(ch) == Character.NON_SPACING_MARK.toInt() -> Unit // strip combining diacritic
                ch == 'ñ' -> append('n')
                ch == 'Ñ' -> append('N')
                ch.code < ASCII_LIMIT -> append(ch)
                else -> Unit // drop anything the printer's codepage can't render
            }
        }
    }
}
