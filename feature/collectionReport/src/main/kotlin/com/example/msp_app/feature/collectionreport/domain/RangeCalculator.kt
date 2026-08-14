package com.example.msp_app.feature.collectionreport.domain

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import java.time.Instant
import java.time.LocalDate

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
 *
 * ## El ciclo abre en el INSTANTE de la carga, no a medianoche
 *
 * `FECHA_CARGA_INICIAL` (Firestore) marca el momento exacto en que el cobrador
 * cargó su ruta: ahí abre su ciclo. La versión anterior truncaba ese instante a
 * medianoche (`startOfDay(toBusinessDate(carga))`) y eso produjo un error real,
 * medido contra producción el 13-ago-2026 en la ruta 34: el cobrador cargó el
 * jueves 6 a las 19:33, pero ESE MISMO jueves, entre las 08:27 y las 17:05,
 * había cobrado 18 pagos por $3,150 que pertenecían al ciclo ANTERIOR. Al abrir
 * el rango a las 00:00 del día 6 esos 18 pagos se contaban por segunda vez y el
 * reporte mostraba $48,200 contra los $43,850 reales (el cobrador y Microsip
 * coincidían en $43,850).
 *
 * La regla, decidida por el dueño del producto, es una sola y aplica igual al
 * ciclo y a cada día:
 *
 * ```
 * inicioEfectivo(día) = max(startOfDay(día), fechaCargaInicial)
 * ```
 *
 * Consecuencia ACEPTADA y buscada: si el cobrador cargó de noche y ya no cobró,
 * el día de la carga se ve en **$0** — visible, no oculto. La transparencia es
 * el requisito; no se maquilla escondiendo el día ni recorriendo el inicio del
 * ciclo al día siguiente. Por eso [DateRange.days] sigue contando días
 * NATURALES (el ejemplo de arriba da 8: jue 6 … jue 13, con el primero parcial)
 * y la etiqueta del ciclo no cambia de forma. Si alguien lo lee como bug y lo
 * "arregla", los tests de esta decisión fallan a propósito.
 *
 * ## Invariante que amarra la suma
 *
 * `dayRange` de cada día de [cycleDays] embona con el siguiente sin huecos ni
 * traslapes y la unión de todos cubre exactamente [cycleRange]. Eso es lo que
 * garantiza que la suma de los días cuadre con el total de la semana — que es
 * justo lo que NO cuadraba antes de este cambio.
 */
object RangeCalculator {

    /**
     * Ciclo del cobrador: `[fechaCargaInicial, startOfNextDay(hoy))`. Sin truncar
     * la hora de la carga (ver el KDoc del objeto).
     *
     * Si [fechaCargaInicial] es `null` (aún sin carga), cae al día de hoy
     * completo — un ciclo de un día, el fallback ya documentado.
     */
    fun cycleRange(clock: AppClock, fechaCargaInicial: Instant?): DateRange {
        val today = AppTime.todayInBusinessZone(clock)
        val end = AppTime.startOfNextDay(today)
        return wire(cycleStart(fechaCargaInicial, today, end), end)
    }

    /** Día de HOY, recortado contra el inicio del ciclo. */
    fun dayRange(clock: AppClock, fechaCargaInicial: Instant?): DateRange =
        dayRange(clock, AppTime.todayInBusinessZone(clock), fechaCargaInicial)

    /**
     * Un día CUALQUIERA del ciclo, recortado contra el inicio del ciclo:
     * `[max(startOfDay(day), carga), min(startOfNextDay(day), finDelCiclo))`.
     *
     * Para el día de la carga eso significa arrancar a la hora de la carga
     * (19:33 en el incidente de la ruta 34), no a medianoche; para el resto de
     * los días el recorte no aplica y el rango es el día natural completo.
     *
     * **Rango vacío en vez de rango invertido:** si [day] queda fuera del ciclo
     * (anterior a la carga, o posterior a hoy) el `max`/`min` daría un inicio
     * POSTERIOR al fin, es decir un rango invertido que cualquier consumidor
     * — un `WHERE fecha >= ? AND fecha < ?`, un filtro en memoria, el cálculo de
     * [DateRange.days] — interpretaría mal y en silencio. Se devuelve entonces un
     * rango vacío bien formado (`startIso == endExclusiveIso`, `days == 0`,
     * `contains` falso para cualquier instante), anclado a la medianoche del día
     * PEDIDO y no al borde del ciclo: así la etiqueta sigue nombrando el día por
     * el que se preguntó en lugar de disfrazarlo del primer día del ciclo.
     * [cycleDays] nunca lista esos días, pero la función es segura si la llaman
     * igual.
     */
    fun dayRange(clock: AppClock, day: LocalDate, fechaCargaInicial: Instant?): DateRange {
        val today = AppTime.todayInBusinessZone(clock)
        val cycleEnd = AppTime.startOfNextDay(today)
        val dayStart = AppTime.startOfDay(day)
        val start = maxOf(dayStart, cycleStart(fechaCargaInicial, today, cycleEnd))
        val end = minOf(AppTime.startOfNextDay(day), cycleEnd)
        if (!start.isBefore(end)) return wire(dayStart, dayStart)
        return wire(start, end)
    }

    /**
     * Días elegibles del ciclo, de la carga a hoy inclusive, en orden ascendente.
     *
     * El primer día es la fecha de negocio de la carga (parcial: abre a la hora
     * de la carga, no a medianoche) y el último es hoy. Con [fechaCargaInicial]
     * `null` devuelve solo hoy, igual que el fallback de [cycleRange]. Devuelve
     * lista vacía únicamente en el caso defensivo de una carga en el futuro
     * (ver [cycleStart]), donde el ciclo mismo es vacío.
     */
    fun cycleDays(clock: AppClock, fechaCargaInicial: Instant?): List<LocalDate> {
        val today = AppTime.todayInBusinessZone(clock)
        val cycleEnd = AppTime.startOfNextDay(today)
        val first = AppTime.toBusinessDate(cycleStart(fechaCargaInicial, today, cycleEnd))
        if (first.isAfter(today)) return emptyList()
        return generateSequence(first) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .toList()
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

    /**
     * Inicio efectivo del ciclo: el instante de la carga tal cual.
     *
     * Dos guardas, ambas contra datos, no contra el caso normal:
     *  - sin carga (`null`) el ciclo es el día de hoy completo;
     *  - una carga en el FUTURO (reloj del dispositivo corrido, dato sucio en
     *    Firestore) se topa contra [cycleEnd] para que el rango salga vacío
     *    (`[fin, fin)`) en vez de invertido. Antes esto no podía pasar porque el
     *    truncado a medianoche lo escondía; al conservar la hora sí puede.
     */
    private fun cycleStart(
        fechaCargaInicial: Instant?,
        today: LocalDate,
        cycleEnd: Instant
    ): Instant {
        val carga = fechaCargaInicial ?: return AppTime.startOfDay(today)
        return minOf(carga, cycleEnd)
    }

    private fun wire(start: Instant, endExclusive: Instant): DateRange = DateRange(
        startIso = AppTime.toWireFormat(start),
        endExclusiveIso = AppTime.toWireFormat(endExclusive)
    )
}
