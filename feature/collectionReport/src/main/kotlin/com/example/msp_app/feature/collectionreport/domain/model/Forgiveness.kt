package com.example.msp_app.feature.collectionreport.domain.model

/**
 * Condonación — monto perdonado a un cliente (saldo mínimo, ajuste de
 * intereses, redondeo de cierre, etc.).
 *
 * Se modela aparte de [CollectionPayment] a propósito: una condonación NO es
 * efectivo cobrado, así que NO suma al total cobrado ni a los tiles de
 * efectivo/transferencia; vive en su propio chip "Condonado".
 *
 * @property cliente nombre del cliente.
 * @property motivo razón de la condonación (para el sheet).
 * @property amount monto condonado, exacto ([Money]).
 */
data class Forgiveness(
    val cliente: String,
    val motivo: String,
    val amount: Money
)
