package com.example.msp_app.feature.collectionreport.domain.model

import java.time.Instant

/**
 * Pago de cobranza — modelo de dominio (puro, sin acoplarse al schema Room).
 *
 * El monto es [Money] (BigDecimal escala-2), NUNCA `Double`: el puente desde el
 * `Double` de `IMPORTE` (schema v27) ocurre UNA sola vez en el borde de datos
 * (Task 4) vía `Money.of(Double)`; de aquí en adelante la aritmética es exacta.
 *
 * @property id UUID del pago.
 * @property cliente nombre del cliente (para la fila/avatar).
 * @property ventaLabel etiqueta de la venta asociada (p. ej. "Muebles Bahía").
 * @property amount importe cobrado, exacto.
 * @property method forma de cobro ya clasificada ([PaymentMethod]).
 * @property paidAt instante del pago en UTC; la hora/día de negocio se deriva
 *   siempre en zona negocio vía `AppTime`, nunca la del dispositivo.
 * @property synced si ya subió al servidor (los pendientes se marcan en la UI).
 */
data class CollectionPayment(
    val id: String,
    val cliente: String,
    val ventaLabel: String,
    val amount: Money,
    val method: PaymentMethod,
    val paidAt: Instant,
    val synced: Boolean
)
