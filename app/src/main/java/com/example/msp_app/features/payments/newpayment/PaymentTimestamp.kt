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
 * seconds. The reason is NOT that the server rejects fractions (Go's `time.RFC3339`
 * parses fractional seconds fine): it is that the upload mapper
 * (`PaymentV2Mappers.normalizeFechaHoraPago`) already truncates to whole seconds
 * independently, and the server returns confirmed pagos at that same second precision.
 * Previously local captures kept `Instant.now().toString()`'s millisecond fraction
 * (`.SSS`), so an un-synced local pago and a server-normalized one had DIFFERENT string
 * widths — which breaks Room's lexicographic (SQLite BINARY collation) comparison at day
 * boundaries. Aligning the write width to seconds makes those comparisons consistent
 * across synced and unsynced rows and, together with the half-open DAO ranges, removes
 * the business-midnight double-count.
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
