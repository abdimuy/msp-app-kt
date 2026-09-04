package com.example.msp_app.core.printing.domain

import java.time.Instant

/**
 * What the device knows about the prints of ONE ticket.
 *
 * @property ticketId the printed document's stable identity — the payment id
 *   for a payment ticket, the visit id for a visit ticket. Never a composite of
 *   customer + amount: the identity has to be the same string on a reprint, and
 *   a customer's name or a balance can change between the two prints.
 * @property prints how many times that ticket has been physically sent to a
 *   printer. `1` after the first print; `>= 2` means every later copy is a
 *   reprint.
 * @property firstPrintedAt the instant of the FIRST print, or `null` when the
 *   count survived but the stored instant did not (a corrupted prefs file, an
 *   older format). `null` means **"this is a copy, but the hour of the first one
 *   is unknown"** — deliberately different from "never printed", which is a
 *   `null` [PrintRecord]. The banner degrades to `copia N` without an hour
 *   rather than silently losing the count, which would print the next copy as if
 *   it were the first. See [PrintLogStore]'s second limit.
 */
data class PrintRecord(
    val ticketId: String,
    val prints: Int,
    val firstPrintedAt: Instant?
)

/**
 * The durable log of "this ticket was printed".
 *
 * ## Why a port, and why not Room
 *
 * The business rule is *every print is registered, so a reprint can be
 * detected*. That needs persistence, and the additive Room migration of Task 26
 * did not carry a table for it — inventing a column now, or squeezing a print
 * counter into a field that means something else, is exactly the defect the
 * structured-promise work spent two tasks undoing. So the log lives in its own
 * `SharedPreferences` file, the same mechanism this module already uses for
 * [PreferredPrinterStore] (and `:app`'s legacy picker before it).
 *
 * ## Why that is sound, and where it stops being sound
 *
 * `SharedPreferences` and the Room database live in the same private app
 * directory and share one lifecycle: an uninstall or a "clear data" wipes both,
 * nothing wipes only one of them. And the window in which a reprint is even
 * possible is a single calendar day, because the OTHER half of the rule
 * ([com.example.msp_app.core.printing.application.PrintDayRule]) forbids
 * printing at all once the collection day is over. So the log only has to
 * survive as long as printing is permitted, and inside that day the only thing
 * that can destroy it also destroys the record the ticket prints from.
 *
 * **The honest limit:** if the collector clears the app's data (or reinstalls)
 * on the same day and the payment comes back from the server on the next sync,
 * this log will be empty and that ticket will print as a first copy. There is no
 * way to close that hole without a schema change, and this task must not add
 * one. It is written down here so it is a known limitation and not a surprise.
 *
 * **The second limit — a corrupted stored date.** If the counter survives but the
 * instant next to it cannot be parsed, the record keeps its count and loses only
 * the hour ([PrintRecord.firstPrintedAt] `null`): the copy is still detected and
 * still marked, it just says `copia 2` instead of `copia 2 - primera 12:05`. The
 * earlier design dropped the whole entry there, which cost exactly one
 * **undetectable** copy — the next print came out clean, with no banner. That is
 * the failure this nullability exists to prevent, not a cosmetic detail.
 *
 * The log is also per-device: a ticket printed from another phone is invisible
 * here. A collector carries one phone and the payment is registered on it, so
 * that is the same boundary the rest of the offline stack already has.
 *
 * Implementations must be **total**: a failure to read or write the log must
 * never stop a print, because the paper the customer is waiting for matters more
 * than the counter. See the adapter for how it reports the failure instead of
 * swallowing it.
 */
interface PrintLogStore {

    /** What is known about [ticketId], or `null` if it was never printed here. */
    fun find(ticketId: String): PrintRecord?

    /**
     * Registers one more print of [ticketId] at [at] and returns the resulting
     * record. The first call stores `prints = 1` and `firstPrintedAt = at`;
     * every later call only increments the counter and **keeps the original
     * [PrintRecord.firstPrintedAt]** — the first copy's timestamp is the one
     * that identifies which paper came first, so it is never overwritten. When a
     * previous record exists with an unknown first instant, it stays unknown:
     * stamping [at] there would claim this copy was the first one.
     */
    fun record(ticketId: String, at: Instant): PrintRecord
}
