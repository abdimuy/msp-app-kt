package com.example.msp_app.data.api.services.cobranza

import com.example.msp_app.data.local.entities.SaleEntity
import com.example.msp_app.data.models.sale.EstadoCobranza
import com.example.msp_app.data.models.sale.FrecuenciaPago
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Wire format for /v2/cobranza/sync/ventas/zona/{id}.
 *
 * Field names match the backend's JSON keys (snake_case) to keep Gson
 * happy without custom annotations.
 */
data class VentaDto(
    val docto_cc_id: Int,
    val docto_pv_id: Int?,
    val cliente_id: Int,
    val zona_cliente_id: Int?,
    val folio: String,
    val fecha_cargo: String,
    val fecha_venta: String?,
    val precio_total: String,
    val total_importe: String,
    val impte_rest: String,
    val saldo: String,
    val num_pagos: Int,
    val fecha_ult_pago: String?,
    val cargo_cancelado: Boolean,
    val updated_at: String,

    val cliente_nombre: String,
    val limite_credito: String?,
    val cliente_notas: String,
    val cobrador_id: Int?,
    val nombre_cobrador: String,

    val zona_nombre: String,

    val calle: String,
    val ciudad: String,
    val estado: String,
    val telefono: String,

    val parcialidad: Int?,
    val enganche: String?,
    val tiempo_corto_plazo_meses: Int?,
    val monto_corto_plazo: String?,
    val precio_de_contado: String?,
    val aval_o_responsable: String,
    val vendedor_1: String,
    val vendedor_2: String,
    val vendedor_3: String,
    val frec_pago: String
)

/**
 * Builds a [SaleEntity] from the wire DTO using the merge contract:
 *
 *   - `ESTADO_COBRANZA` defaults to `PENDIENTE` for fresh rows; the
 *     [com.example.msp_app.core.sync.cobranza.CobranzaSyncManager] preserves
 *     the local value when merging into an existing row.
 *   - `DIA_TEMPORAL_COBRANZA` defaults to `DIA_COBRANZA`; same merge rule.
 *   - `FREC_PAGO` defaults to SEMANAL (Microsip side does not expose this
 *     field today; the backend will start populating it in Phase 2).
 *   - `IMPORTE_PAGO_PROMEDIO` = total_importe / num_pagos, locally computed.
 */
fun VentaDto.toEntity(): SaleEntity {
    val precioTotal = parseAmount(precio_total)
    val totalImporte = parseAmount(total_importe)
    val impteRest = parseAmount(impte_rest)
    val saldoRest = parseAmount(saldo)
    val limiteCredito = parseAmount(limite_credito)
    val enganche = parseAmount(enganche)
    val montoCortoPlazo = parseAmount(monto_corto_plazo)
    val precioDeContado = parseAmount(precio_de_contado)
    val frecPago = frec_pago.ifBlank { FrecuenciaPago.SEMANAL.name }
    val diaCobranza = computeDiaCobranza(fecha_cargo, frecPago)
    val importePromedio = if (num_pagos > 0) totalImporte / num_pagos else null

    return SaleEntity(
        DOCTO_CC_ACR_ID = docto_cc_id,
        DOCTO_CC_ID = docto_cc_id,
        FOLIO = folio,
        CLIENTE_ID = cliente_id,
        APLICADO = "S",
        COBRADOR_ID = cobrador_id ?: 0,
        CLIENTE = cliente_nombre,
        ZONA_CLIENTE_ID = zona_cliente_id ?: 0,
        LIMITE_CREDITO = limiteCredito,
        NOTAS = cliente_notas,
        ZONA_NOMBRE = zona_nombre,
        IMPORTE_PAGO_PROMEDIO = importePromedio,
        TOTAL_IMPORTE = totalImporte,
        NUM_IMPORTES = num_pagos,
        FECHA = fecha_venta ?: fecha_cargo,
        PARCIALIDAD = parcialidad ?: 0,
        ENGANCHE = enganche,
        TIEMPO_A_CORTO_PLAZOMESES = tiempo_corto_plazo_meses ?: 0,
        MONTO_A_CORTO_PLAZO = montoCortoPlazo,
        VENDEDOR_1 = vendedor_1,
        VENDEDOR_2 = vendedor_2,
        VENDEDOR_3 = vendedor_3,
        PRECIO_TOTAL = precioTotal,
        IMPTE_REST = impteRest,
        SALDO_REST = saldoRest,
        FECHA_ULT_PAGO = fecha_ult_pago,
        CALLE = calle,
        CIUDAD = ciudad,
        ESTADO = estado,
        TELEFONO = telefono,
        NOMBRE_COBRADOR = nombre_cobrador,
        ESTADO_COBRANZA = EstadoCobranza.PENDIENTE.name,
        DIA_COBRANZA = diaCobranza,
        DIA_TEMPORAL_COBRANZA = diaCobranza,
        PRECIO_DE_CONTADO = precioDeContado,
        AVAL_O_RESPONSABLE = aval_o_responsable,
        FREC_PAGO = frecPago
    )
}

private fun parseAmount(raw: String?): Double = raw?.toDoubleOrNull() ?: 0.0

/**
 * Returns the localized weekday name of the cargo's first scheduled payment
 * date. Matches the format the existing Node endpoint returns
 * (`LUNES`, `MARTES`...), which the UI reads when bucketing the route.
 */
private fun computeDiaCobranza(fechaCargoIso: String, frecPago: String): String {
    val date = parseDateOrNull(fechaCargoIso) ?: return ""
    val firstDue = when (frecPago) {
        FrecuenciaPago.SEMANAL.name -> date.plusWeeks(1)
        FrecuenciaPago.QUINCENAL.name -> date.plusWeeks(2)
        FrecuenciaPago.MENSUAL.name -> date.plusMonths(1)
        else -> date.plusWeeks(1)
    }
    return weekdayName(firstDue)
}

private fun parseDateOrNull(iso: String): LocalDate? {
    return try {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    } catch (e: Exception) {
        null
    }
}

private fun weekdayName(date: LocalDate): String {
    return date.dayOfWeek
        .getDisplayName(TextStyle.FULL, Locale("es", "MX"))
        .uppercase(Locale("es", "MX"))
        .replace('Á', 'A').replace('É', 'E').replace('Í', 'I')
        .replace('Ó', 'O').replace('Ú', 'U')
}
