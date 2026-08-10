package com.example.msp_app.core.printing.domain

/**
 * The business content of a payment receipt, decoupled from both the database
 * entities (assembled in `:core:data`) and the printer layout (produced by the
 * formatter).
 *
 * Money fields ([abono], [precioTotal], [saldoAnterior], [saldoActual],
 * [pagadoALaFecha], the product [ReceiptProductLine.importe] and history
 * [ReceiptHistoryLine.monto]) are **pre-formatted decimal-safe display strings**
 * (e.g. `"$350.00"`), never `Double`/`Int` — the formatter only places them, it
 * never does arithmetic (spec §3.2). [fechaHora] is likewise pre-formatted so
 * timezone/formatting decisions stay in the mapper and the domain remains free
 * of `java.time`.
 *
 * @property negocio business name header (e.g. `"Mueblería Bonanza"`).
 * @property sucursal branch line under the business name, or blank to omit it.
 * @property folio the official, immutable local folio (never "provisional", spec §4).
 * @property fechaHora pre-formatted payment date/time (e.g. `"08/07 15:42"`).
 * @property cliente customer full name.
 * @property domicilio customer address line, or `null` when unavailable offline
 *   (the `Dom:` line is omitted).
 * @property telefonoCliente customer phone, or `null` when unavailable offline
 *   (the `Tel:` line is omitted).
 * @property credito credit identifier (e.g. `"V-1180"`).
 * @property concepto what the credit is for (e.g. `"Sala Nápoles 3 pzas"`);
 *   printed only as a fallback when [productos] is empty — a full product list
 *   supersedes it (spec mockup).
 * @property productos the sale's product lines, or empty when no items are
 *   synced locally (the whole `PRODUCTOS` block is omitted).
 * @property precioTotal pre-formatted long-term-plan sale total, or `null` when
 *   the sale can't be resolved offline (the `Total a crédito` line is omitted).
 * @property enganche pre-formatted credit down payment, or `null` when it is
 *   unavailable offline or not strictly positive — an informational line only
 *   (never a payment), omitted whole rather than printing `"$0.00"` (design
 *   doc Track 1).
 * @property abono pre-formatted payment amount (e.g. `"$350.00"`).
 * @property metodo payment method label (e.g. `"Efectivo"`, `"Transferencia"`).
 * @property saldoAnterior pre-formatted pre-payment balance (last server-synced
 *   sale balance), or `null` when unavailable offline (line omitted).
 * @property saldoActual pre-formatted post-payment balance
 *   (`max(saldoAnterior − abono, 0)`), or `null` when the base balance is
 *   unavailable offline (line omitted).
 * @property pagadoALaFecha pre-formatted cumulative amount paid including this
 *   abono, or `null` when unavailable offline (line omitted).
 * @property ultimosPagos up to the last 5 prior payments (newest first),
 *   excluding the current one; empty when there is no prior history (the
 *   `ULTIMOS PAGOS` block is omitted).
 * @property cobrador collector name printed on the "Cobró" row.
 * @property telefonos footer contact line, or blank to omit it.
 */
data class PaymentReceipt(
    val negocio: String,
    val sucursal: String,
    val folio: String,
    val fechaHora: String,
    val cliente: String,
    val domicilio: String?,
    val telefonoCliente: String?,
    val credito: String,
    val concepto: String,
    val productos: List<ReceiptProductLine>,
    val precioTotal: String?,
    val enganche: String?,
    val abono: String,
    val metodo: String,
    val saldoAnterior: String?,
    val saldoActual: String?,
    val pagadoALaFecha: String?,
    val ultimosPagos: List<ReceiptHistoryLine>,
    val cobrador: String,
    val telefonos: String
)

/**
 * One printed product line: description, quantity and the pre-formatted line
 * total ([importe], e.g. `"$9,600.00"` — already money-formatted by the mapper,
 * never recomputed by the formatter).
 */
data class ReceiptProductLine(
    val descripcion: String,
    val cantidad: Int,
    val importe: String
)

/**
 * One printed row of the sale's recent payment history: pre-formatted date
 * ([fecha], `dd/MM/yy`), pre-formatted amount ([monto]) and an abbreviated
 * method label ([metodo], e.g. `"Efec."`) sized for the 32-char ticket.
 */
data class ReceiptHistoryLine(
    val fecha: String,
    val monto: String,
    val metodo: String
)
