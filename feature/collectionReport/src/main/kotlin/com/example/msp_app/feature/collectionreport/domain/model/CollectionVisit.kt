package com.example.msp_app.feature.collectionreport.domain.model

import java.time.Instant

/**
 * Visita de cobranza — modelo de dominio. No mueve dinero (una visita puede no
 * derivar en pago), así que no lleva [Money]; el tablero solo la cuenta y lista
 * el motivo/nota (chip "Visitas" + sheet).
 *
 * @property id UUID de la visita.
 * @property cliente nombre del cliente visitado.
 * @property nota nota del cobrador (p. ej. "Promesa de pago mañana").
 * @property visitedAt instante de la visita en UTC.
 */
data class CollectionVisit(
    val id: String,
    val cliente: String,
    val nota: String,
    val visitedAt: Instant
)
