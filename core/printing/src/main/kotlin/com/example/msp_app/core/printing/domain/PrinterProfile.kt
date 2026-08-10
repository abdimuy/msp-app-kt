package com.example.msp_app.core.printing.domain

/**
 * The physical characteristics of a thermal printer that the width-aware
 * [com.example.msp_app.core.printing.application.PaymentReceiptFormatter] needs
 * to lay out a ticket. Keeping [charsPerLine] on a first-class profile (rather
 * than a literal `32` sprinkled through the code) is what lets the same
 * formatter target both the default 58 mm printer and an 80 mm one.
 *
 * @property dpi print head resolution (dots per inch); 203 dpi is the tested default.
 * @property widthMm printable paper width in millimetres.
 * @property charsPerLine monospaced characters that fit on one line at font A, size 1×1.
 * @property cutAfterPrint whether the adapter should issue a paper-cut command after printing.
 */
data class PrinterProfile(
    val dpi: Int = DEFAULT_DPI,
    val widthMm: Float = DEFAULT_WIDTH_MM,
    val charsPerLine: Int = DEFAULT_CHARS_PER_LINE,
    val cutAfterPrint: Boolean = false
) {
    companion object {
        private const val DEFAULT_DPI = 203
        private const val DEFAULT_WIDTH_MM = 48f
        private const val DEFAULT_CHARS_PER_LINE = 32

        private const val CHARS_PER_LINE_80MM = 48
        private const val WIDTH_MM_80MM = 72f

        /** 58 mm / 32 char / 203 dpi — the tested default (spec §3.5). */
        val PROFILE_58MM = PrinterProfile()

        /** 80 mm / 48 char / 203 dpi — first-class alternate profile (spec §3.5). */
        val PROFILE_80MM =
            PrinterProfile(
                charsPerLine = CHARS_PER_LINE_80MM,
                widthMm = WIDTH_MM_80MM
            )
    }
}
