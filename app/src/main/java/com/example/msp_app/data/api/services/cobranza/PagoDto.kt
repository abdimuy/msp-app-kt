package com.example.msp_app.data.api.services.cobranza

import com.example.msp_app.data.local.entities.PaymentEntity

/**
 * Wire format for /v2/cobranza/sync/pagos/zona/{id}. Mirrors the backend's
 * MSP_PAGOS_VENTAS projection one-to-one.
 */
data class PagoDto(
    val impte_docto_cc_id: Int,
    val docto_cc_id: Int,
    val docto_cc_acr_id: Int,
    val cliente_id: Int,
    val zona_cliente_id: Int?,
    val folio: String,
    val concepto_cc_id: Int,
    val fecha: String,
    val importe: String,
    val impuesto: String,
    val lat: String?,
    val lon: String?,
    val cancelado: Boolean,
    val aplicado: Boolean,
    val updated_at: String
)

/**
 * Maps a sync'd pago to the local [PaymentEntity]. A pago that arrives via
 * sync is, by definition, already in Microsip — so `GUARDADO_EN_MICROSIP`
 * is `true`. NOMBRE_CLIENTE and COBRADOR are display-only placeholders; the
 * UI joins to `sales.CLIENTE` when rendering, so we leave them blank to
 * avoid a second JOIN at the backend.
 */
fun PagoDto.toEntity(): PaymentEntity = PaymentEntity(
    ID = impte_docto_cc_id.toString(),
    COBRADOR = "",
    DOCTO_CC_ACR_ID = docto_cc_acr_id,
    DOCTO_CC_ID = docto_cc_id,
    FECHA_HORA_PAGO = fecha,
    GUARDADO_EN_MICROSIP = true,
    IMPORTE = importe.toDoubleOrNull() ?: 0.0,
    LAT = lat?.toDoubleOrNull(),
    LNG = lon?.toDoubleOrNull(),
    CLIENTE_ID = cliente_id,
    COBRADOR_ID = 0,
    FORMA_COBRO_ID = concepto_cc_id,
    ZONA_CLIENTE_ID = zona_cliente_id ?: 0,
    NOMBRE_CLIENTE = ""
)
