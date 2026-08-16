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
 *
 * ## Sin fecha de carga NO hay semana (defecto D5)
 *
 * [cycleRange] devolvía el día de HOY cuando `fechaCargaInicial` era `null`. Ese
 * "fallback documentado" es el defecto que se vio en campo: el tablero mostraba
 * $0.00 cobrado en la semana con la tabla de pagos llena, porque la ventana se
 * había encogido a un día sin que nada lo dijera. La prueba de que la causa era
 * el rango y no una resincronización: el contador de VENTAS (103, sin filtro de
 * fecha) sobrevivía intacto mientras los pagos daban 0.
 *
 * Ahora la ausencia de semana es REPRESENTABLE: [cycleRange] devuelve `null` y
 * el llamador decide qué decir (el reporte lo dice, no lo disfraza de $0). Se
 * elimina la única forma en que un dato faltante podía leerse como una cifra
 * real.
 */
object RangeCalculator {

    /**
     * Ciclo del cobrador: `[fechaCargaInicial, startOfNextDay(hoy))`. Sin truncar
     * la hora de la carga (ver el KDoc del objeto).
     *
     * `null` cuando NO hay una semana utilizable, por cualquiera de las dos vías:
     *  - [fechaCargaInicial] ausente (Firestore sin el dato, o caído);
     *  - [fechaCargaInicial] en el FUTURO respecto del fin del ciclo, donde
     *    [cycleStart] la topa contra el borde y el rango sale permanentemente
     *    vacío (ver el KDoc de [cycleStart]: la guarda sigue intacta, lo que
     *    cambia es que ese vacío ya no se sirve como si fuera una semana real).
     *
     * Deliberadamente NO cae a "hoy": una ventana de un día presentada como la
     * semana es indistinguible de una semana sin cobros.
     */
    fun cycleRange(clock: AppClock, fechaCargaInicial: Instant?): DateRange? {
        val today = AppTime.todayInBusinessZone(clock)
        val end = AppTime.startOfNextDay(today)
        val start = cycleStart(fechaCargaInicial, today, end)
        // Rango vacío o degenerado == no hay semana que reportar.
        if (fechaCargaInicial == null || !start.isBefore(end)) return null
        return wire(start, end)
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

    /**
     * Días del ciclo + etiquetas de ciclo y de día para la UI. `null` cuando no
     * hay semana utilizable — no se inventa una etiqueta para un rango que no
     * existe (ver [cycleRange]).
     */
    fun cycleInfo(clock: AppClock, fechaCargaInicial: Instant?): CycleInfo? {
        val range = cycleRange(clock, fechaCargaInicial) ?: return null
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
     *  - sin carga (`null`) el inicio es la medianoche de hoy — lo que hace que
     *    [dayRange] deje de recortar (un día natural completo, que es la lectura
     *    correcta de "hoy" aunque no se sepa dónde abre la semana). [cycleRange]
     *    NO usa esta rama: sin carga no hay semana, devuelve `null`.
     *  - una carga en el FUTURO (reloj del dispositivo corrido, dato sucio en
     *    Firestore) se topa contra [cycleEnd] para que el rango salga vacío
     *    (`[fin, fin)`) en vez de invertido. Antes esto no podía pasar porque el
     *    truncado a medianoche lo escondía; al conservar la hora sí puede.
     *
     * La guarda del futuro se conserva TAL CUAL: es la que impide que un
     * `WHERE fecha >= ? AND fecha < ?` reciba un rango invertido y lo interprete
     * mal en silencio, y sigue protegiendo a [dayRange]/[cycleDays]. Lo que se
     * corrigió aguas arriba es distinto: [cycleRange] ya no SIRVE ese vacío como
     * si fuera una semana real (devuelve `null`), porque "$0 cobrado" y "no sé
     * cuándo abre tu semana" no son la misma respuesta.
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
