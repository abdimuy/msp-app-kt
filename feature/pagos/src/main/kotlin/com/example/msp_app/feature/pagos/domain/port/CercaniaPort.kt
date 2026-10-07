package com.example.msp_app.feature.pagos.domain.port

/**
 * Puerto hacia la distancia del cobrador a cada venta, para el orden
 * "Más cerca" de la lista de clientes (decisión del dueño, 2026-10-05).
 *
 * El adaptador real (`CentroidesCercaniaAdapter`, en `:app`) resuelve la
 * posición actual del cobrador y la compara contra el centroide de pagos más
 * cercano que ya se calculó para cada venta — identificado por
 * `DOCTO_CC_ACR_ID`, no por el cliente, porque dos ventas del mismo cliente
 * pueden cobrarse en lugares distintos.
 */
interface CercaniaPort {

    /**
     * Metros desde la posición actual del cobrador hasta el centroide de
     * pagos más cercano de cada venta, por `DOCTO_CC_ACR_ID`.
     *
     * Mapa vacío si falta el permiso de ubicación, si todavía no hay una
     * posición, o si la venta no tiene ningún centroide calculado — nunca una
     * excepción. `OrdenDeLaLista.MAS_CERCA` ya sabe mandar al final a quien no
     * aparece aquí.
     */
    suspend fun distanciasPorVenta(): Map<Int, Double>
}
