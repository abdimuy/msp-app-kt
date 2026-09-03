package com.example.msp_app.core.common.cobranza.domain

/**
 * Alcance del efecto de una visita sobre las ventas del cliente
 * (expediente §4/§5 del plan `pagos-y-visitas`).
 *
 * [CLIENTE] — el resultado es una condición del domicilio o de la
 * disponibilidad de contacto, no de una venta en particular: se propaga a
 * TODAS las ventas activas del cliente ("no estaba", "cita a una hora").
 *
 * [VENTA] — el resultado es sobre esa deuda específica (una promesa, una
 * negativa, "vuelvo"): toca solo la venta que el cobrador tenía abierta
 * ("vuelvo", "se negó", "prometió").
 *
 * ## Por qué vive aquí y no en `:app`
 *
 * Lo creó la Task 13 en `com.example.msp_app.core.utils.VisitScopeMapper`
 * (`:app`) porque su único consumidor era `VisitsLocalDataSource`. La Task 14
 * le suma un segundo consumidor —el catálogo de ocho estados— que vive en
 * `:core:common` para que `:feature:pagos` y `:feature:visitas` (Task 15) lo
 * usen sin moverlo después. Un enum compartido por dos módulos no puede
 * quedarse en `:app`: `:core:common` no puede depender de `:app`. Así que el
 * tipo se mudó acá y `:app` conserva un `typealias` — cero call sites tocados,
 * cero segunda clasificación.
 */
enum class VisitScope {
    CLIENTE,
    VENTA
}
