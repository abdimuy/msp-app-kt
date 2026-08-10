package com.example.msp_app.core.printing.domain

/**
 * One payment method's contribution row on a [ReportTicket]. Printing-local —
 * deliberately independent of `:core:domain`'s `ReportMethodTotal` (see
 * [ReportTicket]'s kdoc for the module-boundary rationale). [amount] is a
 * pre-formatted decimal-safe display string (e.g. `"$700.00"`), never
 * `Double`/`Int` — the formatter only places it, it never does arithmetic
 * (docs/MONEY.md, mirrors [PaymentReceipt]).
 *
 * @property method the free-form payment-method label (e.g. `"Efectivo"`).
 * @property amount pre-formatted total collected for this method.
 * @property count number of payments merged into this method's row.
 */
data class ReportTicketMethodRow(
    val method: String,
    val amount: String,
    val count: Int
)

/**
 * One printed row of a [ReportTicket]'s "Detalle de pagos" section — a single
 * collected abono. Printing-local, mirrors [ReportTicketMethodRow]'s
 * independence from `:core:domain`. [monto] is a pre-formatted decimal-safe
 * display string (never `Double`/`Int`, docs/MONEY.md); [recordedAtEpochMillis]
 * is the raw instant the abono was recorded so [ReportTicketFormatter] can
 * render it as hora (día) or fecha+hora (semana) per [ReportTicket.isWeekly] —
 * the one date-formatting decision this ticket type defers to the formatter
 * rather than pre-formatting in the feature-layer mapper (the report window
 * boundary, not this per-row prefix, is the mapper's own zone-sensitive
 * formatting concern).
 *
 * @property cliente the paying customer's full name.
 * @property monto pre-formatted collected amount.
 * @property recordedAtEpochMillis the raw instant the abono was recorded.
 */
data class ReportPaymentLine(
    val cliente: String,
    val monto: String,
    val recordedAtEpochMillis: Long
)

/**
 * The business content of a collector report ticket — both the **diario**
 * (today) and **corte** (period hand-off) variants are the same shape,
 * rendered by [com.example.msp_app.core.printing.application.ReportTicketFormatter];
 * only [title]/[rangeLabel]/[coverageLabel]/[notes] differ between the two
 * call sites (`:feature:reportes`'s `ReportesViewModel`).
 *
 * Kept independent of `:core:domain`'s `DailyReport`/`WeeklyReport`/
 * `CoverageStat` — `:core:printing` stays a pure outer-ring module that never
 * has to know how a report window/merge/coverage was computed; the feature
 * maps those domain models into this holder (task brief §3). Money fields are
 * pre-formatted decimal-safe display strings (docs/MONEY.md); the formatter
 * only places them.
 *
 * @property negocio business name header (mirrors [PaymentReceipt.negocio]).
 * @property sucursal branch line under the business name, or blank to omit it.
 * @property title ticket title, e.g. `"REPORTE DEL DÍA"` / `"CORTE DE PERIODO"`.
 * @property rangeLabel the printed date/period range, e.g. `"Jueves 3 de julio"`
 *   or `"07 jul – 15 jul"`.
 * @property collector collector name printed on the "Cobró" row.
 * @property methodRows per-method breakdown rows; an empty list prints a
 *   "sin pagos" placeholder line instead of an empty section.
 * @property paymentCount total number of payments in the window (the
 *   "Recibos" row).
 * @property totalLabel the grand-total row's label, e.g. `"TOTAL COBRADO"`.
 * @property totalAmount pre-formatted grand total.
 * @property coverageLabel pre-formatted visit-coverage caption (e.g.
 *   `"Cobertura 8/10 · 80%"`), or `null` to omit the coverage line (the
 *   diario ticket never carries one; the corte ticket omits it when coverage
 *   is unknown/0-due).
 * @property notes optional free-text hand-off notes (corte only), or `null`/
 *   blank to omit the notes line.
 * @property payments the individual payments behind [totalAmount] (Track 2:
 *   "Detalle de pagos"), newest-first — the SAME merged/windowed set the
 *   report totals sum, so the list adds up to [totalAmount] by construction.
 *   Empty when there are no payments in the window (the whole section is
 *   omitted, mirroring [methodRows]' `EMPTY_METHODS` placeholder instead).
 * @property isWeekly which per-row date prefix [ReportTicketFormatter] picks
 *   for [payments]: `false` (diario) → hora (`HH:mm`); `true` (corte/semana)
 *   → fecha+hora (`dd/MM HH:mm`).
 */
data class ReportTicket(
    val negocio: String,
    val sucursal: String,
    val title: String,
    val rangeLabel: String,
    val collector: String,
    val methodRows: List<ReportTicketMethodRow>,
    val paymentCount: Int,
    val totalLabel: String,
    val totalAmount: String,
    val coverageLabel: String? = null,
    val notes: String? = null,
    val payments: List<ReportPaymentLine> = emptyList(),
    val isWeekly: Boolean = false
)
