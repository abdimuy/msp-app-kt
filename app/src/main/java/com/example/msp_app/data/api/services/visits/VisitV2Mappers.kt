package com.example.msp_app.data.api.services.visits

import com.example.msp_app.core.time.AppTime
import com.example.msp_app.data.local.entities.VisitEntity
import java.time.temporal.ChronoUnit

/**
 * Maps a locally-stored [VisitEntity] to the v2 [CrearVisitaBody].
 *
 * The fiddly conversions the Go side is strict about:
 *  - **fecha**: RFC3339 UTC truncated to whole seconds. Go's `time.RFC3339`
 *    rejects fractional seconds, so we normalise via AppTime and
 *    `truncatedTo(SECONDS)`. If the stored string can't be parsed we forward
 *    it as-is; the server will 422 and (thanks to the cobranza failed-intent
 *    capture) the visita is preserved server-side for correction from the
 *    desk.
 *  - **impte_docto_cc_id**: `0` is the local "none" sentinel — omitted
 *    (mapped to `null`) rather than sent as a literal 0.
 *  - **lat/lng**: JSON numbers, sent as-is; `0.0` is a valid (if untrusted)
 *    coordinate server-side, unlike pagos' string encoding.
 */
fun VisitEntity.toCrearVisitaBody(): CrearVisitaBody = CrearVisitaBody(
    id = ID,
    cliente_id = CLIENTE_ID,
    cobrador_id = COBRADOR_ID,
    cobrador = COBRADOR,
    forma_cobro_id = FORMA_COBRO_ID,
    lat = LAT,
    lng = LNG,
    nota = NOTA,
    tipo_visita = TIPO_VISITA,
    zona_cliente_id = ZONA_CLIENTE_ID,
    impte_docto_cc_id = IMPTE_DOCTO_CC_ID.takeIf { it > 0 },
    fecha = normalizeFecha(FECHA)
)

/** RFC3339 UTC, no fractional seconds; falls back to the raw string if unparseable. */
private fun normalizeFecha(raw: String): String {
    val instant = AppTime.parseWireFormatOrNull(raw) ?: return raw
    return AppTime.toWireFormat(instant.truncatedTo(ChronoUnit.SECONDS))
}
