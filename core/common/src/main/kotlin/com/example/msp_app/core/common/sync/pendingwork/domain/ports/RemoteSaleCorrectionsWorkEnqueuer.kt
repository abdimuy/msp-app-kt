package com.example.msp_app.core.common.sync.pendingwork.domain.ports

/**
 * Encola la entrega de la corrección de una venta que YA subió — la segunda
 * cola de ventas, aparte de [LocalSalesWorkEnqueuer].
 *
 * Son dos puertos y no uno porque son dos caminos distintos sobre la misma
 * fila: uno sube la venta que nunca salió (`POST /v2/ventas`) y el otro
 * entrega una corrección de líneas contra una venta que el servidor ya tiene
 * (`PUT /v2/ventas/{id}/lineas`). Mezclarlos en un solo puerto obligaría al
 * llamador a saber en cuál de los dos estados está la venta, que es
 * exactamente lo que cada worker decide por sí mismo al leer la fila.
 */
interface RemoteSaleCorrectionsWorkEnqueuer {
    fun enqueue(localSaleId: String, userEmail: String)
}
