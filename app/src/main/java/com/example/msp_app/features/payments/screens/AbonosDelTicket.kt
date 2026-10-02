package com.example.msp_app.features.payments.screens

import com.example.msp_app.core.database.entities.DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR
import com.example.msp_app.data.models.payment.Payment

/**
 * Los pagos que el ticket lista como abonos.
 *
 * Se quitan los marcados [DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR] (-1): el
 * servidor no los tiene (E-APP-048/E-APP-052), el saldo ya es el del servidor y
 * listarlos le enseñaría al cliente un abono que Microsip no registró —dos veces
 * donde oficina lo capturó a mano—. Decisión del dueño del 2026-10-02: ocultarlos.
 */
fun abonosDelTicket(pagos: List<Payment>): List<Payment> =
    pagos.filterNot { it.DOCTO_CC_ID == DOCTO_CC_ID_RECHAZADO_POR_EL_SERVIDOR }
