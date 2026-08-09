package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.feature.collectionreport.domain.model.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Robustez SUPREMA de la meta sugerida: mediana de la ventana (impar/par),
 * ventana más corta que el historial, ventana que recorta a los últimos N,
 * robustez a outliers, casos vacíos/degenerados y meta de ciclo. JVM puro, sin
 * reloj (la meta no depende de "ahora").
 */
class SuggestedGoalTest {

    private fun money(v: String) = Money.of(BigDecimal(v))

    @Test
    fun `mediana de tamaño impar es el central`() {
        val totals = listOf(money("100"), money("300"), money("200"))
        assertEquals(money("200.00"), SuggestedGoal.suggest(totals))
    }

    @Test
    fun `mediana de tamaño par promedia los dos centrales con HALF_UP`() {
        val totals = listOf(money("100"), money("200"), money("300"), money("400"))
        assertEquals(money("250.00"), SuggestedGoal.suggest(totals))
    }

    @Test
    fun `mediana par de centrales impares redondea el medio hacia arriba`() {
        // centrales 100 y 101 -> 100.5 -> 100.51? no: 100.50 exacto
        val totals = listOf(money("50"), money("100"), money("101"), money("400"))
        assertEquals(money("100.50"), SuggestedGoal.suggest(totals))
    }

    @Test
    fun `con menos dias que la ventana usa todos los disponibles`() {
        assertEquals(money("100.00"), SuggestedGoal.suggest(listOf(money("100"))))
    }

    @Test
    fun `la ventana recorta a los ultimos N dias`() {
        // 20 días: 1..20; ventana 14 -> últimos 14 (7..20), mediana = (13+14)/2 = 13.5
        val totals = (1..20).map { money(it.toString()) }
        assertEquals(money("13.50"), SuggestedGoal.suggest(totals, window = 14))
    }

    @Test
    fun `la mediana es robusta a un outlier (no lo arrastra como el promedio)`() {
        // Promedio sería 2080; la mediana ignora el cierre atípico de 10000.
        val totals = listOf(money("100"), money("100"), money("100"), money("100"), money("10000"))
        assertEquals(money("100.00"), SuggestedGoal.suggest(totals))
    }

    @Test
    fun `historial vacio sugiere cero`() {
        assertEquals(Money.ZERO, SuggestedGoal.suggest(emptyList()))
    }

    @Test
    fun `ventana cero o negativa sugiere cero`() {
        val totals = listOf(money("100"), money("200"))
        assertEquals(Money.ZERO, SuggestedGoal.suggest(totals, window = 0))
        assertEquals(Money.ZERO, SuggestedGoal.suggest(totals, window = -3))
    }

    @Test
    fun `la ventana por defecto es catorce dias`() {
        assertEquals(14, SuggestedGoal.DEFAULT_WINDOW)
    }

    @Test
    fun `meta de ciclo es la meta diaria por los dias del ciclo`() {
        assertEquals(money("1000.00"), SuggestedGoal.forCycle(money("200"), cycleDays = 5))
    }

    @Test
    fun `meta de ciclo con cero dias es cero`() {
        assertEquals(Money.ZERO, SuggestedGoal.forCycle(money("200"), cycleDays = 0))
        assertEquals(Money.ZERO, SuggestedGoal.forCycle(money("200"), cycleDays = -1))
    }
}
