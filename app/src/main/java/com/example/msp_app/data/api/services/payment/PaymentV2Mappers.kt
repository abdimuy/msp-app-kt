package com.example.msp_app.data.api.services.payment

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.entities.PaymentEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.temporal.ChronoUnit

/**
 * Maps a locally-stored [PaymentEntity] to the v2 [CrearPagoBody].
 *
 * The three fiddly conversions the Go side is strict about:
 *  - **importe**: a 2-decimal fixed string (`BigDecimal.setScale(2, HALF_UP)`),
 *    never a float — the server parses it as an exact decimal.
 *  - **fecha_hora_pago**: RFC3339 UTC truncated to whole seconds. Go's
 *    `time.RFC3339` rejects fractional seconds, so we normalise via AppTime and
 *    `truncatedTo(SECONDS)`. If the stored string can't be parsed we forward it
 *    as-is; the server will 422 and (thanks to the cobranza failed-intent
 *    capture) the pago is preserved server-side for correction from the desk.
 *  - **lat/lon**: optional strings. Omitted when null or 0.0 (a 0/0 coordinate
 *    is the "no fix" sentinel, not a real location). The wire name is `lon`.
 */
fun PaymentEntity.toCrearPagoBody(): CrearPagoBody = CrearPagoBody(
    id = ID,
    cargo_docto_cc_id = DOCTO_CC_ACR_ID,
    cliente_id = CLIENTE_ID,
    cobrador_id = COBRADOR_ID,
    cobrador = COBRADOR,
    importe = BigDecimal.valueOf(IMPORTE).setScale(2, RoundingMode.HALF_UP).toPlainString(),
    forma_cobro_id = FORMA_COBRO_ID,
    fecha_hora_pago = normalizeFechaHoraPago(FECHA_HORA_PAGO),
    lat = LAT.toOptionalCoord(),
    lon = LNG.toOptionalCoord()
)

/** RFC3339 UTC, no fractional seconds; falls back to the raw string if unparseable. */
private fun normalizeFechaHoraPago(raw: String): String {
    val instant = AppTime.parseWireFormatOrNull(raw) ?: return raw
    return AppTime.toWireFormat(instant.truncatedTo(ChronoUnit.SECONDS))
}

/** A coordinate string, or null when absent or the 0.0 "no fix" sentinel. */
private fun Double?.toOptionalCoord(): String? =
    this?.takeIf { it != 0.0 }?.let { BigDecimal.valueOf(it).toPlainString() }
