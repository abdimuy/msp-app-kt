package com.example.msp_app.core.common.visitas.domain

import com.example.msp_app.core.common.time.AppTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Cuándo fue un pago para la agenda: [instante] nulo = pago de oficina sin hora (DA-7); [fecha] siempre. */
data class MomentoDePago(val instante: Instant?, val fecha: LocalDate)

private val SOLO_EL_DIA = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")

/**
 * Lee `Payment.FECHA_HORA_PAGO` como lo lee la agenda del servidor
 * (`visitasfb.pagoAplicadoDeFila`), E-APP-112.
 *
 * El servidor sabe que un pago aplicado no tiene hora porque no tiene fila en
 * `MSP_PAGOS_RECIBIDOS`; el teléfono no ve esa tabla. Lo que sí ve es el
 * formato real del sync (`testdata/fecha-pago-sync`, medido en dev el
 * 2026-10-26): `MSP_PAGOS_VENTAS.FECHA` de un pago de oficina es
 * `DOCTOS_CC.FECHA` a las 00:00 locales, y el sync lo manda como el instante
 * UTC de esa medianoche (`2026-08-24T06:00:00Z`; `…T05:00:00Z` con el horario
 * de verano de antes de 2022). Por eso es pago sin hora la fila que:
 *  - vino del sync (`ID` numérico: `PagoDto.toEntity` usa `impte_docto_cc_id`;
 *    la captura local lleva UUID y siempre tiene hora, como en el servidor),
 *  - no trae `pago_recibido_id` (si lo trae, el servidor tiene su hora), y
 *  - cae exactamente en la medianoche de [zona].
 * La fecha sola (`2026-10-16`, formato heredado) también es pago sin hora.
 *
 * Diferencia que queda: un pago de la app ligado a `MSP_PAGOS_RECIBIDOS` solo
 * por `DOCTO_CC_ID` (sin `pago_recibido_id`) y capturado justo a las
 * 00:00:00 locales se lee aquí como sin hora y en el servidor como con hora.
 *
 * Ilegible → null (no se le inventa fecha).
 */
fun momentoDePago(
    id: String,
    fechaHoraPago: String,
    pagoRecibidoId: String?,
    zona: ZoneId
): MomentoDePago? {
    if (SOLO_EL_DIA.matches(fechaHoraPago)) {
        val dia = AppTime.parseWireDateOrNull(fechaHoraPago) ?: return null
        return MomentoDePago(null, dia)
    }
    val instante = AppTime.parseWireFormatOrNull(fechaHoraPago) ?: return null
    val local = instante.atZone(zona)
    val sinHora = esFilaDelSync(id) && pagoRecibidoId == null && local.toLocalTime() == LocalTime.MIDNIGHT
    return MomentoDePago(if (sinHora) null else instante, local.toLocalDate())
}

private fun esFilaDelSync(id: String): Boolean = id.isNotEmpty() && id.all { it in '0'..'9' }
