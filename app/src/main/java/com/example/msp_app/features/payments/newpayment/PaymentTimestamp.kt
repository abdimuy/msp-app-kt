package com.example.msp_app.features.payments.newpayment

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import java.time.temporal.ChronoUnit

/**
 * Generates the `FECHA_HORA_PAGO` value for a payment/forgiveness being registered
 * right now, via [AppClock] instead of a direct `Instant.now().toString()` call at
 * the composable call site.
 *
 * **Task 5b — truncated to whole seconds (contract fidelity).** The stored value is
 * now `AppTime.toWireFormat(now.truncatedTo(SECONDS))` — RFC3339 UTC with NO fractional
 * seconds. This matches, byte-for-byte, the shape the payment upload already sends
 * (`PaymentV2Mappers.normalizeFechaHoraPago` truncates to seconds because Go's
 * `time.RFC3339` rejects fractional seconds) AND the shape the server returns for
 * confirmed pagos. Previously local captures kept `Instant.now().toString()`'s
 * millisecond fraction (`.SSS`), so an un-synced local pago and a server-normalized one
 * had different string widths — which breaks Room's lexicographic comparison at day
 * boundaries. Standardizing the write width to seconds makes those comparisons
 * consistent and, together with the half-open DAO ranges, removes the business-midnight
 * double-count.
 *
 * This is a deliberate money-path behavior change with zero server-visible effect: the
 * upload already truncated, so the server receives the exact same value either way; only
 * the locally-stored string changes width. Idempotency is unaffected — the upload
 * Idempotency-Key is `payment.ID` (a UUID), never derived from the timestamp.
 *
 * Both [com.example.msp_app.features.payments.components.newpaymentdialog.NewPaymentDialog]
 * and [com.example.msp_app.features.forgiveness.components.NewForgivenessDialog] generate this
 * value inline inside a `@Composable` local function — neither has a ViewModel to own the
 * clock injection — so this top-level function is the minimum-viable testable seam without
 * redesigning the payment save flow.
 */
fun currentPaymentTimestamp(clock: AppClock = AppClock.System): String =
    AppTime.toWireFormat(clock.now().truncatedTo(ChronoUnit.SECONDS))
