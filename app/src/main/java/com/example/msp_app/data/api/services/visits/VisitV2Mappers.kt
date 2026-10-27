package com.example.msp_app.data.api.services.visits

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.visitas.envio.CompromisoLocal
import com.example.msp_app.core.database.entities.VisitEntity
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
 *  - **compromiso** (contrato de visitas 3, spec visitas §5.2.1-2): los cinco
 *    campos viajan SOLO con [conCompromisos] = `true`, que el worker toma de
 *    `Capacidades.mandaCompromisos()`. Con `false` van nulos y Gson los omite:
 *    un servidor de contrato 2 decodifica con `DisallowUnknownFields` y
 *    respondería 422 a cualquier clave nueva (E-APP-097).
 */
fun VisitEntity.toCrearVisitaBody(conCompromisos: Boolean): CrearVisitaBody = CrearVisitaBody(
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
    fecha = normalizeFecha(FECHA),
    promesa_venta_id = PROMESA_VENTA_ID.takeIf { conCompromisos },
    promesa_fecha = PROMESA_FECHA.takeIf { conCompromisos },
    promesa_monto_centavos = PROMESA_MONTO_CENTAVOS.takeIf { conCompromisos },
    cita_fecha = CITA_FECHA.takeIf { conCompromisos },
    cita_hora = CITA_HORA.takeIf { conCompromisos }
)

/**
 * El compromiso local de la visita, o `null` si no tiene promesa ni cita. Es
 * lo que el servidor debe devolver igual para que el bloque cuente como
 * entregado (spec visitas §5.2.5).
 */
fun VisitEntity.compromisoLocal(): CompromisoLocal? = CompromisoLocal(
    promesaVentaId = PROMESA_VENTA_ID,
    promesaFecha = PROMESA_FECHA,
    promesaMontoCentavos = PROMESA_MONTO_CENTAVOS,
    citaFecha = CITA_FECHA,
    citaHora = CITA_HORA
).takeUnless { it.vacio }

/** Los bloques que trae la respuesta del POST (fila completa en contrato 3). */
fun VisitaDTO.compromiso(): CompromisoLocal = CompromisoLocal(
    promesaVentaId = promesa_venta_id,
    promesaFecha = promesa_fecha,
    promesaMontoCentavos = promesa_monto_centavos,
    citaFecha = cita_fecha,
    citaHora = cita_hora
)

/** Los bloques que trae `GET /v2/visitas/{id}` (todos nulos en un servidor viejo). */
fun VisitaDetalleDTO.compromiso(): CompromisoLocal = CompromisoLocal(
    promesaVentaId = promesa_venta_id,
    promesaFecha = promesa_fecha,
    promesaMontoCentavos = promesa_monto_centavos,
    citaFecha = cita_fecha,
    citaHora = cita_hora
)

/** RFC3339 UTC, no fractional seconds; falls back to the raw string if unparseable. */
private fun normalizeFecha(raw: String): String {
    val instant = AppTime.parseWireFormatOrNull(raw) ?: return raw
    return AppTime.toWireFormat(instant.truncatedTo(ChronoUnit.SECONDS))
}
