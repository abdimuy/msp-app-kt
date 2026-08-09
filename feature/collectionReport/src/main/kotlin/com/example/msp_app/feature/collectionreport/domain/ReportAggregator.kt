package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Total + conteo de una categoría de pagos (efectivo, transferencia, ...). */
data class MethodBreakdown(val total: Money, val count: Int)

/**
 * Una barra de la sparkline: etiqueta corta + su dinero + conteo. Lleva la
 * `hour`/`date` cruda para que [ReportAggregator.mejorMomento] pueda reetiquetar
 * el pico sin re-parsear la etiqueta.
 */
data class TimelineBucket(
    val label: String,
    val total: Money,
    val count: Int,
    val hour: Int? = null,
    val date: LocalDate? = null
)

/** Sparkline completa + índice resaltado (pico en DÍA, "hoy" en SEMANA). */
data class Timeline(val buckets: List<TimelineBucket>, val highlightIndex: Int)

/** Fila del resumen semanal por día: nombre, dinero, conteo, iniciales, si es hoy. */
data class DayTrend(
    val label: String,
    val total: Money,
    val count: Int,
    val initials: String,
    val isToday: Boolean
)

/** Dirección del delta contra el periodo anterior. */
enum class DeltaDirection { UP, DOWN, FLAT, NONE }

/** Chip de comparación: texto listo para UI + dirección para el color/ícono. */
data class DeltaChip(val text: String, val direction: DeltaDirection)

/** Mejor momento (hora pico en DÍA, día pico en SEMANA) + su dinero. */
data class BestMoment(val label: String, val total: Money)

/**
 * Agregador PURO del reporte de cobranza: convierte listas de pagos/condonaciones
 * de dominio en el estado numérico del tablero. Todo dinero es [Money] (suma
 * exacta, escala-2, asociativa), NUNCA `Double`.
 *
 * **Regla de conteo único (no double-count):** cada pago cuenta UNA vez en
 * [total]/[count]; los splits por método son particiones disjuntas por
 * [PaymentMethod]. El cheque (158) — parked del brief — es su propia categoría:
 * NO entra en [efectivo]/[efectivoEnMano] (no es efectivo físico) ni en el duo,
 * pero SÍ cuenta en [total] (dinero realmente cobrado). Las [condonaciones] son
 * una lista aparte: NO son dinero cobrado, así que jamás tocan [total].
 *
 * Es UNA sola fachada pura (no varios objetos por sub-concern) a propósito —
 * mismo criterio que `AppTime`: totales, splits, ticket, timeline, delta,
 * progreso, insight y mejor-momento son, semánticamente, una superficie
 * pequeña de funciones puras del mismo tablero; partirla por conteo dispersaría
 * cálculos relacionados sin ganar legibilidad, de ahí el [Suppress].
 */
@Suppress("TooManyFunctions")
object ReportAggregator {

    // Ventana horaria fija del tablero de DÍA (mockup: barras 8h..16h).
    private const val DAY_START_HOUR = 8
    private const val DAY_END_HOUR = 16

    // Escala de trabajo para la fracción de progreso antes de truncar a Float.
    private const val PROGRESS_SCALE = 4
    private const val MONEY_SCALE = 2
    private const val PERCENT = 100
    private val HUNDRED = BigDecimal("100")

    /** `EEE` es-MX -> "lun".."dom" (barras SEMANA). */
    private val DOW_SHORT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE", BUSINESS_LOCALE)

    /** `EEEE` es-MX -> "miércoles" (mejor momento SEMANA). */
    private val DOW_FULL: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEEE", BUSINESS_LOCALE)

    /** `EEE d MMM` es-MX -> "lun 3 ago" (resumen por día). */
    private val DAY_TREND_LABEL: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM", BUSINESS_LOCALE)

    // region — totales y splits ------------------------------------------------

    /** Total cobrado (todos los pagos, cheque incluido); vacío -> $0.00. */
    fun total(payments: List<CollectionPayment>): Money = Money.sum(payments.map { it.amount })

    /** Número de pagos. */
    fun count(payments: List<CollectionPayment>): Int = payments.size

    /** Total + conteo de los pagos de una forma de cobro dada. */
    fun breakdown(payments: List<CollectionPayment>, method: PaymentMethod): MethodBreakdown {
        val matching = payments.filter { it.method == method }
        return MethodBreakdown(Money.sum(matching.map { it.amount }), matching.size)
    }

    /** Efectivo (157). */
    fun efectivo(payments: List<CollectionPayment>): MethodBreakdown =
        breakdown(payments, PaymentMethod.EFECTIVO)

    /** Transferencia (52569). */
    fun transferencia(payments: List<CollectionPayment>): MethodBreakdown =
        breakdown(payments, PaymentMethod.TRANSFERENCIA)

    /** Cheque (158) — su propia categoría (parked); no es efectivo físico. */
    fun cheque(payments: List<CollectionPayment>): MethodBreakdown =
        breakdown(payments, PaymentMethod.CHEQUE)

    /** Condonado — total + conteo de la lista de condonaciones (NO es cobro). */
    fun condonado(forgiveness: List<Forgiveness>): MethodBreakdown =
        MethodBreakdown(Money.sum(forgiveness.map { it.amount }), forgiveness.size)

    /**
     * Efectivo EN MANO = solo efectivo físico (157). El cheque (158) queda fuera
     * a propósito (parked): no es efectivo que el cobrador trae encima. Hoy
     * coincide con `efectivo(...).total`; se expone como función propia porque su
     * intención (arqueo de caja) es distinta y puede divergir si mañana se suma
     * otra forma "en mano".
     */
    fun efectivoEnMano(payments: List<CollectionPayment>): Money = efectivo(payments).total

    /** Ticket promedio = total / conteo (HALF_UP); vacío -> $0.00 sin dividir. */
    fun ticketPromedio(payments: List<CollectionPayment>): Money {
        if (payments.isEmpty()) return Money.ZERO
        val avg = total(payments).amount
            .divide(BigDecimal(payments.size), MONEY_SCALE, RoundingMode.HALF_UP)
        return Money.of(avg)
    }

    // region — timeline / sparkline -------------------------------------------

    /**
     * Sparkline por periodo. [range] se usa en SEMANA para enumerar TODOS los
     * días del ciclo (aun los sin pagos) y ubicar "hoy"; en DÍA se ignora.
     *
     * - DÍA: barras fijas 8h..16h; los pagos fuera de esa franja se acotan al
     *   borde más cercano para no perderse de la sparkline; resalta el pico.
     * - SEMANA: una barra por día del ciclo (etiqueta "lun".."dom"); resalta
     *   "hoy" (último día del rango), como el mockup.
     */
    fun timeline(
        payments: List<CollectionPayment>,
        period: ReportPeriod,
        range: DateRange
    ): Timeline = when (period) {
        ReportPeriod.DIA -> dayTimeline(payments)
        ReportPeriod.SEMANA -> weekTimeline(payments, range)
    }

    private fun dayTimeline(payments: List<CollectionPayment>): Timeline {
        val byHour = payments.groupBy {
            AppTime.toBusinessDateTime(it.paidAt).hour.coerceIn(DAY_START_HOUR, DAY_END_HOUR)
        }
        val buckets = (DAY_START_HOUR..DAY_END_HOUR).map { hour ->
            val ps = byHour[hour].orEmpty()
            TimelineBucket("${hour}h", Money.sum(ps.map { it.amount }), ps.size, hour = hour)
        }
        return Timeline(buckets, peakIndex(buckets))
    }

    private fun weekTimeline(payments: List<CollectionPayment>, range: DateRange): Timeline {
        val byDate = payments.groupBy { AppTime.toBusinessDate(it.paidAt) }
        val buckets = businessDays(range).map { day ->
            val ps = byDate[day].orEmpty()
            TimelineBucket(
                day.format(DOW_SHORT),
                Money.sum(ps.map { it.amount }),
                ps.size,
                date = day
            )
        }
        // "hoy" = último día del ciclo (fin inclusivo del rango).
        return Timeline(buckets, (buckets.size - 1).coerceAtLeast(0))
    }

    /** Índice de la barra con más dinero (pico); empate -> la más temprana; vacío -> 0. */
    private fun peakIndex(buckets: List<TimelineBucket>): Int =
        buckets.indices.maxByOrNull { buckets[it].total } ?: 0

    // region — resumen por día (SEMANA) ---------------------------------------

    /**
     * Resumen del ciclo por día: una fila por día del [range] (aun los sin
     * pagos), con nombre "lun 3 ago" (+ " (hoy)" en el día en curso), total,
     * conteo e iniciales "L3" (inicial del día + día del mes) para el avatar.
     */
    fun dailyTrend(
        payments: List<CollectionPayment>,
        range: DateRange,
        clock: AppClock
    ): List<DayTrend> {
        val today = AppTime.todayInBusinessZone(clock)
        val byDate = payments.groupBy { AppTime.toBusinessDate(it.paidAt) }
        return businessDays(range).map { day ->
            val ps = byDate[day].orEmpty()
            val isToday = day == today
            val base = day.format(DAY_TREND_LABEL)
            DayTrend(
                label = if (isToday) "$base (hoy)" else base,
                total = Money.sum(ps.map { it.amount }),
                count = ps.size,
                initials = initialsFor(day),
                isToday = isToday
            )
        }
    }

    private fun initialsFor(day: LocalDate): String =
        "${day.format(DOW_SHORT).first().uppercaseChar()}${day.dayOfMonth}"

    private fun businessDays(range: DateRange): List<LocalDate> =
        generateSequence(range.startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(range.endInclusiveDate) }
            .toList()

    // region — delta / progreso / insight / mejor momento ---------------------

    /**
     * Chip "▲/▼ X% vs ayer" (DÍA) / "vs ciclo" (SEMANA). El signo sale de la
     * diferencia; el % es |diff|/prior redondeado HALF_UP. Si [prior] es 0 (o
     * negativo) no hay base de comparación -> "—" (parked: degradar sin romper).
     */
    fun delta(current: Money, prior: Money, period: ReportPeriod): DeltaChip {
        val suffix = if (period == ReportPeriod.DIA) "vs ayer" else "vs ciclo"
        if (prior.amount.signum() <= 0) return DeltaChip("—", DeltaDirection.NONE)
        val diff = current.amount.subtract(prior.amount)
        val pct = diff.abs().multiply(HUNDRED)
            .divide(prior.amount, 0, RoundingMode.HALF_UP).toInt()
        return when {
            diff.signum() > 0 -> DeltaChip("▲ $pct% $suffix", DeltaDirection.UP)
            diff.signum() < 0 -> DeltaChip("▼ $pct% $suffix", DeltaDirection.DOWN)
            else -> DeltaChip("0% $suffix", DeltaDirection.FLAT)
        }
    }

    /** Fracción de progreso `total/goal` en `[0, 1]`; goal 0 (o negativo) -> 0. */
    fun progressFraction(total: Money, goal: Money): Float {
        if (goal.amount.signum() <= 0) return 0f
        return total.amount
            .divide(goal.amount, PROGRESS_SCALE, RoundingMode.HALF_UP)
            .toFloat()
            .coerceIn(0f, 1f)
    }

    /**
     * Frase-insight del hero. El % se TRUNCA (piso), consistente con el ancho de
     * la barra del mockup (0.915 -> 91%, no 92%).
     *
     * - DÍA: "N pagos · vas al X% de tu meta · a este ritmo cierras en $Y"
     *   ([projection] = proyección a cierre; null -> "—").
     * - SEMANA: "N pagos · vas al X% de la meta · día D de T del ciclo".
     */
    fun insight(
        period: ReportPeriod,
        count: Int,
        progress: Float,
        projection: Money?,
        cycleDay: Int = 0,
        cycleDays: Int = 0
    ): String {
        val pct = (progress.coerceIn(0f, 1f) * PERCENT).toInt()
        return when (period) {
            ReportPeriod.DIA -> {
                val closing = projection?.let { formatMoneyMxn(it.amount) } ?: "—"
                "$count pagos · vas al $pct% de tu meta · a este ritmo cierras en $closing"
            }
            ReportPeriod.SEMANA ->
                "$count pagos · vas al $pct% de la meta · día $cycleDay de $cycleDays del ciclo"
        }
    }

    /**
     * Mejor momento para el sheet-hero: la barra pico de la [timeline]. En DÍA la
     * reetiqueta a rango horario ("9–10 h"); en SEMANA a día completo
     * ("miércoles"). Si no hay dinero (todas las barras en 0) -> null.
     */
    fun mejorMomento(timeline: Timeline, period: ReportPeriod): BestMoment? {
        val peak = timeline.buckets
            .filter { it.total > Money.ZERO }
            .maxByOrNull { it.total }
            ?: return null
        val label = when (period) {
            ReportPeriod.DIA -> peak.hour?.let { "$it–${it + 1} h" } ?: peak.label
            ReportPeriod.SEMANA -> peak.date?.format(DOW_FULL) ?: peak.label
        }
        return BestMoment(label, peak.total)
    }
}
