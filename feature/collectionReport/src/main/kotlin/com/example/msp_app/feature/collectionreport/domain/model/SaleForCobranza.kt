package com.example.msp_app.feature.collectionreport.domain.model

import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje
import java.time.Instant

/**
 * Una venta de crédito no-contado, activa, del cobrador — insumo de
 * [com.example.msp_app.feature.collectionreport.domain.port.SalesPort] para la tarjeta
 * "Meta de la semana" ([CobranzaPorcentaje]). Puerto de los campos de `sales` (Room, schema
 * v27) que el cálculo necesita — ver el adapter (`RoomSalesAdapter`) para el mapeo exacto de
 * columnas.
 *
 * `abonoSemana` NO vive aquí: se calcula aparte agrupando [CollectionPayment.saleId] por
 * [doctoCcAcrId] sobre la ventana del reporte (`PaymentsPort.paymentsIn`) — esta venta es
 * "activa" independientemente de si tuvo pagos en la ventana actual.
 *
 * @property doctoCcAcrId `sales.DOCTO_CC_ACR_ID` — mismo id que [CollectionPayment.saleId].
 * @property parcialidad `sales.PARCIALIDAD` (pesos, sin centavos en el schema) como [Money].
 * @property totalImporte `sales.PRECIO_TOTAL` — total original del crédito.
 * @property saldoHoy `sales.SALDO_REST` — saldo vivo actual.
 * @property frecuencia cadencia de pago ya resuelta (`sales.FREC_PAGO` -> [CobranzaPorcentaje.Frecuencia],
 *   `null`/desconocido -> SEMANAL, ver [CobranzaPorcentaje.Frecuencia.fromWire]).
 * @property fechaCargo `sales.FECHA` (wire RFC3339 UTC) ya parseada — fecha de alta del crédito.
 */
data class SaleForCobranza(
    val doctoCcAcrId: Int,
    val parcialidad: Money,
    val totalImporte: Money,
    val saldoHoy: Money,
    val frecuencia: CobranzaPorcentaje.Frecuencia,
    val fechaCargo: Instant
)
