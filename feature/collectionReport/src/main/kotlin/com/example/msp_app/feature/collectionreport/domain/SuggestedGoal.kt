package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.feature.collectionreport.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Meta sugerida de cobranza (Fase 1, local). Puro, sin dependencias de reloj.
 *
 * **Diseño:** la meta DIARIA es la **mediana** de los últimos ~[DEFAULT_WINDOW]
 * días con dinero cobrado — se elige mediana sobre promedio por robustez a
 * outliers: un solo día atípico (un cierre grande, o un día casi en cero) no
 * arrastra la meta. La meta de la SEMANA es la meta diaria × días del ciclo
 * ([forCycle]) — se prefiere multiplicar por robustez: reusa la mediana ya
 * estabilizada en vez de re-mediar totales de ciclos de largo variable.
 *
 * **Fase 2 (no cableada):** una meta fijada por oficina (Firestore / msp-api)
 * reemplazará a la sugerida. Se deja el puerto abierto conceptualmente; no se
 * introduce abstracción hasta que exista el consumidor real (YAGNI).
 */
object SuggestedGoal {

    /** Ventana por defecto: ~2 semanas de historial. */
    const val DEFAULT_WINDOW = 14

    private val TWO = BigDecimal("2")
    private const val MONEY_SCALE = 2

    /**
     * Meta diaria = mediana de los últimos [window] totales diarios. Historial
     * vacío o [window] <= 0 -> $0.00. Con menos de [window] días usa los que
     * haya. Mediana de tamaño par = promedio de los dos centrales (HALF_UP).
     */
    fun suggest(historicalDailyTotals: List<Money>, window: Int = DEFAULT_WINDOW): Money {
        if (historicalDailyTotals.isEmpty() || window <= 0) return Money.ZERO
        val sample = historicalDailyTotals.takeLast(window).sortedBy { it.amount }
        val mid = sample.size / 2
        if (sample.size % 2 == 1) return sample[mid]
        val median = sample[mid - 1].amount
            .add(sample[mid].amount)
            .divide(TWO, MONEY_SCALE, RoundingMode.HALF_UP)
        return Money.of(median)
    }

    /** Meta de la semana = meta diaria × días del ciclo; [cycleDays] <= 0 -> $0.00. */
    fun forCycle(dailyGoal: Money, cycleDays: Int): Money {
        if (cycleDays <= 0) return Money.ZERO
        return Money.of(dailyGoal.amount.multiply(BigDecimal(cycleDays)))
    }
}
