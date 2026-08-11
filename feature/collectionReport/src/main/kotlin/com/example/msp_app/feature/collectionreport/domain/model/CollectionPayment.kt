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
 * @property folio folio comercial de la venta asociada (p. ej. "A-10482"),
 *   resuelto por el adapter con un join a `sales` sobre `DOCTO_CC_ACR_ID`
 *   (mismo cruce que usa la app en `PaymentTicketScreen`). Vacío si la venta
 *   ya no está en local — nunca se inventa.
 * @property saldo saldo restante ACTUAL de la venta asociada (`sales.SALDO_REST`),
 *   el mismo valor que la app muestra como "saldo actual"; `null` si la venta no
 *   está en local. Es el saldo vivo, no el saldo al momento de este pago.
 * @property saleId `DOCTO_CC_ACR_ID` de la venta asociada (mismo valor que hoy viaja como texto
 *   en [ventaLabel]) — id tipado para atribuir este pago a su venta sin re-parsear
 *   [ventaLabel]. Alimenta el `abonoSemana` por venta que consume
 *   [com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje] (Meta de la
 *   semana). `0` cuando el pago no trae una venta resuelta (nunca ocurre en producción — el
 *   adapter siempre lo puebla desde `PaymentEntity.DOCTO_CC_ACR_ID` — pero es un default seguro
 *   para no romper los fixtures de test existentes que construyen `CollectionPayment` sin él).
 */
data class CollectionPayment(
    val id: String,
    val cliente: String,
    val ventaLabel: String,
    val amount: Money,
    val method: PaymentMethod,
    val paidAt: Instant,
    val synced: Boolean,
    val folio: String = "",
    val saldo: Money? = null,
    val saleId: Int = 0
)
