package com.example.msp_app.data.api.services.cobranza

import com.example.msp_app.data.local.entities.PaymentEntity

/**
 * Wire format for /v2/cobranza/sync/pagos/zona/{id}.
 *
 * Coincide 1:1 con la proyeccion enriquecida del backend (cache
 * MSP_PAGOS_VENTAS + JOINs con DOCTOS_CC, CLIENTES y FORMAS_COBRO_DOCTOS).
 *
 * - `importe` ya viene con IVA incluido (IMPORTE + IMPUESTO en SQL).
 * - `forma_cobro_id` es el medio de pago real (efectivo, cheque,
 *   transferencia), distinto de `concepto_cc_id` que clasifica la naturaleza
 *   del cargo en Microsip.
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
    val updated_at: String,
    val cobrador: String,
    val cobrador_id: Int?,
    val nombre_cliente: String,
    val forma_cobro_id: Int?,
    /**
     * UUID local original que el app generó para este pago cuando se
     * capturó offline (Payment.ID de la fila UUID). Non-null solo cuando
     * el pago se aplicó a través del server v2 y este puede resolver su
     * origen; null/ausente para pagos legacy nunca aplicados por ese
     * camino. Usado por CobranzaSyncManager.mergePagos para colapsar el
     * gemelo local UUID una vez que su versión numérica llega por sync —
     * ver bug "pago duplicado en Historial de pagos".
     */
    val pago_recibido_id: String? = null
)

/**
 * Mapea un PagoDto sincronizado a la entidad local [PaymentEntity]. Por
 * definicion todo pago que llega via /sync/pagos ya esta en Microsip
 * (GUARDADO_EN_MICROSIP=true). Los campos enriquecidos (COBRADOR,
 * NOMBRE_CLIENTE, COBRADOR_ID, FORMA_COBRO_ID) ya vienen resueltos del
 * backend — antes los dejabamos en blanco, lo que rompia la UI del
 * "historial de pagos" en el detalle de venta.
 */
fun PagoDto.toEntity(): PaymentEntity = PaymentEntity(
    ID = impte_docto_cc_id.toString(),
    COBRADOR = cobrador,
    DOCTO_CC_ACR_ID = docto_cc_acr_id,
    DOCTO_CC_ID = docto_cc_id,
    FECHA_HORA_PAGO = fecha,
    GUARDADO_EN_MICROSIP = true,
    IMPORTE = importe.toDoubleOrNull() ?: 0.0,
    LAT = lat?.toDoubleOrNull(),
    LNG = lon?.toDoubleOrNull(),
    CLIENTE_ID = cliente_id,
    COBRADOR_ID = cobrador_id ?: 0,
    FORMA_COBRO_ID = forma_cobro_id ?: concepto_cc_id,
    ZONA_CLIENTE_ID = zona_cliente_id ?: 0,
    NOMBRE_CLIENTE = nombre_cliente,
    PAGO_RECIBIDO_ID = pago_recibido_id
)
