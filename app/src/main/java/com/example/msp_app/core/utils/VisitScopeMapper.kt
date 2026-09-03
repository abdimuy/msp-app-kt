package com.example.msp_app.core.utils

/**
 * Alcance del efecto de una visita sobre las ventas del cliente
 * (expediente §4/§5 del plan `pagos-y-visitas`,
 * `docs/superpowers/plans/2026-09-01-pagos-y-visitas-EXPEDIENTE.md`).
 *
 * [CLIENTE] — el resultado es una condición del domicilio o de la
 * disponibilidad de contacto, no de una venta en particular: se propaga a
 * TODAS las ventas activas del cliente ("no estaba", "cita a una hora").
 *
 * [VENTA] — el resultado es sobre esa deuda específica (una promesa, una
 * negativa, "vuelvo"): toca solo la venta que el cobrador tenía abierta
 * ("vuelvo", "se negó", "prometió").
 */
enum class VisitScope {
    CLIENTE,
    VENTA
}

/**
 * Deriva el [VisitScope] de un `TIPO_VISITA` — texto libre en el wire
 * (`visita.go:15/202`, máx 100 chars) pero acotado en la práctica a las
 * etiquetas de `NewVisitDialog.visitConditionForm` (`Constants.kt:31-41`).
 *
 * Fuente única de la clasificación cliente/venta: `VisitsLocalDataSource`
 * la consume para decidir cómo escribir (Task 13), y el catálogo de ocho
 * estados (Task 14) la consume para decidir cómo propagar — ninguno de los
 * dos repite esta lista de literales.
 *
 * Agrupación idéntica a la del expediente y a `task-14-brief.md`: "No
 * estaba" es la única familia de alcance cliente entre las diez etiquetas
 * activas hoy en `visitConditionForm`. Cualquier `TIPO_VISITA` fuera de esa
 * familia — incluida una etiqueta futura o desconocida (p.ej.
 * `Constants.PIDE_TIEMPO`, definida pero ya no ofrecida en el diálogo) —
 * cae en [VisitScope.VENTA]: el alcance más angosto es el default seguro.
 * Una etiqueta mal clasificada como VENTA simplemente deja de propagar; mal
 * clasificada como CLIENTE toca ventas que el cobrador no visitó — el bug
 * que esta tarea existe para evitar, no para reintroducir por otro lado.
 */
object VisitScopeMapper {
    private val alcanceCliente = listOf(
        Constants.NO_SE_ENCONTRABA,
        Constants.CASA_CERRADA,
        Constants.SOLO_MENORES
    )

    fun map(tipoVisita: String): VisitScope = when (tipoVisita) {
        in alcanceCliente -> VisitScope.CLIENTE
        else -> VisitScope.VENTA
    }
}
