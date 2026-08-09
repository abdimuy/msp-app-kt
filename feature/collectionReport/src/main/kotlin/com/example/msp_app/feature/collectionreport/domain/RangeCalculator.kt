package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import java.time.Instant

/**
 * Resumen del ciclo del cobrador: días cubiertos y etiquetas listas para la UI.
 */
data class CycleInfo(
    val days: Int,
    val cycleLabel: String,
    val dayLabel: String
)

/**
 * Cálculo puro de rangos half-open del reporte de cobranza, en zona negocio
 * (`America/Mexico_City`) vía [AppClock]/[AppTime]. Todos los rangos son
 * `[desde, hasta)` con fin EXCLUSIVO — el mismo contrato que el backend Go.
 *
 * El fin exclusivo (`startOfNextDay(hoy)`) es deliberado: usar `now()` crudo
 * como fin subcuenta los pagos del propio día (un pago a las 23:59:59 quedaría
 * fuera). Con `[startOfDay, startOfNextDay)` ese pago SÍ cae dentro.
 */
object RangeCalculator {

    /** Día de negocio de hoy: `[startOfDay(hoy), startOfNextDay(hoy))`. */
    fun dayRange(clock: AppClock): DateRange {
        val today = AppTime.todayInBusinessZone(clock)
        return DateRange(
            startIso = AppTime.toWireFormat(AppTime.startOfDay(today)),
            endExclusiveIso = AppTime.toWireFormat(AppTime.startOfNextDay(today))
        )
    }

    /**
     * Ciclo del cobrador: `[startOfDay(fechaCargaInicial), startOfNextDay(hoy))`.
     *
     * Si [fechaCargaInicial] es `null` (aún sin carga), cae a [dayRange] — un
     * ciclo de un día (hoy), como fallback documentado.
     */
    fun cycleRange(clock: AppClock, fechaCargaInicial: Instant?): DateRange {
        if (fechaCargaInicial == null) return dayRange(clock)
        val start = AppTime.toBusinessDate(fechaCargaInicial)
        val today = AppTime.todayInBusinessZone(clock)
        return DateRange(
            startIso = AppTime.toWireFormat(AppTime.startOfDay(start)),
            endExclusiveIso = AppTime.toWireFormat(AppTime.startOfNextDay(today))
        )
    }

    /** Días del ciclo + etiquetas de ciclo y de día para la UI. */
    fun cycleInfo(clock: AppClock, fechaCargaInicial: Instant?): CycleInfo {
        val range = cycleRange(clock, fechaCargaInicial)
        return CycleInfo(
            days = range.days,
            cycleLabel = range.cycleLabel(),
            dayLabel = range.dayLabel()
        )
    }
}
