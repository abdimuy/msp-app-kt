package com.example.msp_app.feature.collectionreport.ui.actions.pdf

/**
 * Envuelve texto libre (la nota completa de una visita, ver `VisitRowUi.nota`) a un ancho en
 * puntos — a diferencia de `CollectionReportFormatter.wrap` (privado, ticket térmico), que
 * envuelve por CONTEO de caracteres porque el ticket usa una fuente monoespaciada de ancho
 * fijo. Aquí el PDF usa `Typeface.DEFAULT` (proporcional) para el cuerpo de texto, así que el
 * ancho real de cada palabra depende del glifo — [measure] es la única función pura que puede
 * medirlo (`Paint.measureText` en producción, ver
 * [com.example.msp_app.feature.collectionreport.ui.actions.pdf.buildPdfBlocks]).
 *
 * Inyectar [measure] (en vez de tomar un `Paint` real) es lo que mantiene este helper
 * testeable en JVM puro sin Robolectric — el mismo criterio documentado en el KDoc de
 * [PdfLayout] para toda la paginación.
 */
internal object PdfTextWrap {

    /**
     * Parte [text] en líneas que caben en [maxWidth] según [measure]. Nunca trunca: una
     * palabra más ancha que [maxWidth] se corte en el prefijo más largo que SÍ cabe (búsqueda
     * binaria sobre [measure]), y el resto continúa en la siguiente línea — ningún carácter se
     * pierde. Texto en blanco -> lista vacía (sin línea "fantasma").
     */
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<String>()
        val current = StringBuilder()
        text.split(" ").filter { it.isNotEmpty() }.forEach { rawWord ->
            var word = rawWord
            while (measure(word) > maxWidth && word.length > 1) {
                val cut = longestPrefixFitting(word, maxWidth, measure)
                flush(current, out)
                out.add(word.take(cut))
                word = word.drop(cut)
            }
            appendWord(current, out, word, maxWidth, measure)
        }
        flush(current, out)
        return out
    }

    private fun appendWord(
        current: StringBuilder,
        out: MutableList<String>,
        word: String,
        maxWidth: Float,
        measure: (String) -> Float
    ) {
        when {
            word.isEmpty() -> Unit
            current.isEmpty() -> current.append(word)
            measure("$current $word") <= maxWidth -> current.append(' ').append(word)
            else -> {
                flush(current, out)
                current.append(word)
            }
        }
    }

    /** Búsqueda binaria del prefijo más largo de [word] que mide `<= [maxWidth]` bajo [measure]. */
    private fun longestPrefixFitting(
        word: String,
        maxWidth: Float,
        measure: (String) -> Float
    ): Int {
        var lo = 1
        var hi = word.length
        var best = 1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (measure(word.take(mid)) <= maxWidth) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }

    private fun flush(buffer: StringBuilder, out: MutableList<String>) {
        if (buffer.isNotEmpty()) {
            out.add(buffer.toString())
            buffer.clear()
        }
    }
}
