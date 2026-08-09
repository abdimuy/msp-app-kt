package com.example.msp_app.features.sales.components.paymentcard

import com.example.msp_app.core.common.time.AppTime

/**
 * Whether [paymentDateIso] happened AFTER [dateInitialIso] — drives `PaymentCard`'s choice of
 * background gradient (success vs. regular) for a pago captured since the cobrador's current
 * workday/session started (`FECHA_CARGA_INICIAL`).
 *
 * Replacement for the legacy `DateUtils.isAfterIso`, which parsed both sides via
 * `parseIsoToDateTime` into a naive `LocalDateTime` and compared those instead of comparing
 * `Instant`s directly.
 *
 * **Audit finding — semantics preserved for the only shape production ever produces:** both
 * `payment.FECHA_HORA_PAGO` and `dateInitial` are always `Z`-suffixed UTC ISO strings written by
 * `AppTime.toWireFormat` (formerly `DateUtils.getIsoDateTime`). For that shape, the naive
 * `LocalDateTime` the old code compared IS the UTC wall clock (`OffsetDateTime.parse(iso)
 * .toLocalDateTime()` on a `Z` string just drops the zero offset), so its ordering was already
 * equivalent to comparing the two `Instant`s. This migration makes that equivalence explicit
 * and structural instead of an accident of always receiving `Z`-suffixed input — see
 * `PaymentCardBackgroundTest`'s realistic-input cases (same result, any device zone).
 *
 * **Edge that DOES change (out of practical reach):** [AppTime.parseWireFormat]'s legacy
 * fallback for a zone-less `T` string (`2026-04-16T00:15:00`, no offset/`Z`) interprets it in
 * `BUSINESS_ZONE`, whereas the old `parseIsoToDateTime` took the digits literally as a naive
 * `LocalDateTime` with no zone applied at all. Mixing that legacy shape on one side with a
 * proper `Z` string on the other can flip the comparison result — characterized in
 * `PaymentCardBackgroundTest` and documented there as a legacy-shape divergence, not a
 * device-zone one: neither the old nor the new code ever reads `ZoneId.systemDefault()` here.
 */
internal fun isPaymentAfterInitialLoad(paymentDateIso: String, dateInitialIso: String): Boolean =
    AppTime.parseWireFormat(paymentDateIso).isAfter(AppTime.parseWireFormat(dateInitialIso))
