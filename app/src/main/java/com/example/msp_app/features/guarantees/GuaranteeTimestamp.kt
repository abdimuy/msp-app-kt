package com.example.msp_app.features.guarantees

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime

/**
 * Generates the `FECHA_SOLICITUD` value for a guarantee being created right now, via
 * [AppClock] instead of a direct `DateTimeFormatter.ISO_INSTANT.format(Instant.now())` call
 * at the composable/ViewModel call site.
 *
 * **Task 11 — fechas/AppTime migration, bug #4 fix.** Both call sites this replaces
 * (`GuaranteesScreen.GuaranteeScreen` and `CreateGuaranteeViewModel.saveGuarantee`) already
 * emitted `Z`-UTC (`ISO_INSTANT` on an `Instant` is Z-UTC by construction), so this is not a
 * wire-format fix for those two — it is the testability seam: `GuaranteeScreen` is a bare
 * `@Composable` with no ViewModel to own a clock, and `CreateGuaranteeViewModel` is an
 * `AndroidViewModel` not unit-tested directly in this codebase (same rationale as
 * `features.payments.newpayment.currentPaymentTimestamp`). The actual wire-format bug lived in
 * `GuaranteesLocalDataSource.updateGuaranteeStatusAndInsertEvent` (`FECHA_EVENTO`), fixed
 * separately with its own injected `AppClock`.
 */
fun currentGuaranteeTimestamp(clock: AppClock = AppClock.System): String =
    AppTime.toWireFormat(clock.now())
