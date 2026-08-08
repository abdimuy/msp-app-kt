package com.example.msp_app.features.payments.newpayment

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime

/**
 * Generates the `FECHA_HORA_PAGO` value for a payment/forgiveness being registered
 * right now, via [AppClock] instead of a direct `Instant.now().toString()` call at
 * the composable call site.
 *
 * Behavior-neutral by construction: [AppTime.toWireFormat] formats with
 * [java.time.format.DateTimeFormatter.ISO_INSTANT], the same formatter
 * `Instant.toString()` uses internally, so the emitted string is byte-identical to
 * what the old `Instant.now().toString()` call produced. The only change is that the
 * "now" comes from an injectable [AppClock], which makes the call testable with a
 * fixed [com.example.msp_app.core.testing.time.FakeClock] instead of being pinned to
 * wall-clock time.
 *
 * Both [com.example.msp_app.features.payments.components.newpaymentdialog.NewPaymentDialog]
 * and [com.example.msp_app.features.forgiveness.components.NewForgivenessDialog] generate this
 * value inline inside a `@Composable` local function — neither has a ViewModel to own the
 * clock injection — so this top-level function is the minimum-viable testable seam without
 * redesigning the payment save flow.
 */
fun currentPaymentTimestamp(clock: AppClock = AppClock.System): String =
    AppTime.toWireFormat(clock.now())
