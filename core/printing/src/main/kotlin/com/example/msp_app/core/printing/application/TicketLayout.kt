package com.example.msp_app.core.printing.application

/**
 * The three width-baking helpers every ticket formatter needs: centre a line,
 * push two fields to the two edges of one line, and wrap a paragraph.
 *
 * They live here, in the module both tickets already depend on, instead of being
 * copied into each feature. `CollectionReportFormatter` predates this object and
 * keeps its own private copies — that copy is not touched by this task, but a
 * third copy is where the drift would have started.
 *
 * Pure and total: no ticket ever fails to lay out. Anything that does not fit is
 * truncated or hard-split rather than thrown away, because a clipped line still
 * tells the customer something and a crash in front of them tells them nothing.
 */
object TicketLayout {

    /** Centres [text] with left padding; truncates when it is wider than [width]. */
    fun center(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        return " ".repeat((width - text.length) / 2) + text
    }

    /**
     * [left] flush left and [right] flush right on one [width] line. When they
     * cannot both fit, the LEFT side is the one that gets cut: the right side is
     * the amount, and a truncated amount is a lie on a receipt.
     */
    fun twoCol(left: String, right: String, width: Int): String {
        val gap = width - left.length - right.length
        if (gap >= 1) return left + " ".repeat(gap) + right
        val keep = (width - right.length - 1).coerceAtLeast(0)
        return "${left.take(keep)} $right".take(width)
    }

    /** Wraps [text] to [width]; hard-splits any single token longer than a line. */
    fun wrap(text: String, width: Int): List<String> {
        if (text.length <= width) return listOf(text)
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (rawWord in text.split(" ").filter { it.isNotEmpty() }) {
            var word = rawWord
            while (word.length > width) {
                flush(current, out)
                out.add(word.take(width))
                word = word.drop(width)
            }
            when {
                word.isEmpty() -> Unit
                current.isEmpty() -> current.append(word)
                current.length + 1 + word.length <= width -> current.append(' ').append(word)
                else -> {
                    flush(current, out)
                    current.append(word)
                }
            }
        }
        flush(current, out)
        return out
    }

    private fun flush(buffer: StringBuilder, out: MutableList<String>) {
        if (buffer.isNotEmpty()) {
            out.add(buffer.toString())
            buffer.clear()
        }
    }
}
