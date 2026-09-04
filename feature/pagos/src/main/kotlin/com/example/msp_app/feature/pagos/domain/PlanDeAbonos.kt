package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.RoundingMode

/**
 * El progreso de abonos que pinta la barra y la línea "18 de 24 abonos".
 *
 * Dominio PURO y en una sola pieza a propósito: los dos números tienen que
 * salir de la MISMA unidad —parcialidades— o la frase miente. Contar filas de
 * `Payment` para el numerador y dividir el total entre la parcialidad para el
 * denominador daría "20 de 24" en una venta donde el cliente pagó veinte veces
 * cincuenta pesos.
 *
 * Bordes (los que prueba `PlanDeAbonosTest`):
 *  - `parcialidad <= 0` → no hay plan: `0 de 0`, avance 0. No se divide entre
 *    cero y no se inventa un plan que el dato no sostiene.
 *  - `abonado` exactamente igual a `n * parcialidad` → `n` abonos, no `n-1`:
 *    pagar la parcialidad exacta es haber pagado, el mismo borde `>=` que usa
 *    `EstadoCuentaDeriver.estadoPorDinero`.
 *  - un peso menos que `n * parcialidad` → `n-1`.
 *  - `abonado > totalVenta` (sobrepago histórico) → el conteo se topa en
 *    [Plan.totales] y el avance en `1f`; una barra al 130% no informa nada.
 */
object PlanDeAbonos {

    /** El plan resuelto: cuántos abonos van, de cuántos, y qué fracción es eso. */
    data class Plan(val pagados: Int, val totales: Int, val avance: Float)

    /** Resuelve el plan de una venta. */
    fun de(totalVenta: Money, abonado: Money, parcialidad: Money): Plan {
        if (parcialidad <= Money.ZERO) return Plan(pagados = 0, totales = 0, avance = 0f)
        val totales = totalVenta.amount
            .divide(parcialidad.amount, 0, RoundingMode.CEILING)
            .toInt()
            .coerceAtLeast(0)
        val pagados = abonado.amount
            .coerceAtLeast(Money.ZERO.amount)
            .divide(parcialidad.amount, 0, RoundingMode.FLOOR)
            .toInt()
            .coerceIn(0, totales)
        return Plan(pagados = pagados, totales = totales, avance = avanceDe(totalVenta, abonado))
    }

    /** Decimales de la división de fracciones — precisión de layout, no de dinero. */
    private const val ESCALA_DE_FRACCION = 4

    private fun avanceDe(totalVenta: Money, abonado: Money): Float {
        if (totalVenta <= Money.ZERO) return 0f
        return abonado.amount
            .divide(totalVenta.amount, ESCALA_DE_FRACCION, RoundingMode.HALF_UP)
            .toFloat()
            .coerceIn(0f, 1f)
    }
}
