package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.RangeCalculator
import com.example.msp_app.feature.collectionreport.domain.ReportAggregator
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.CollectionVisit
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Forgiveness
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.domain.model.SaleForCobranza
import java.time.Instant
import java.time.LocalDate

/**
 * Ensamblador PURO (sin I/O ni corrutinas) del contenido cargado del reporte: toma las
 * listas de dominio que [CollectionReportViewModel] ya resolvió vía los puertos (Task 4) y
 * arma las piezas de [CollectionReportUiState] usando [ReportAggregator] / [SuggestedGoal] /
 * [RangeCalculator]. Vive aparte del ViewModel a propósito:
 * - Mantiene cada tipo bajo el umbral `TooManyFunctions` de detekt (el ViewModel solo
 *   orquesta I/O + `StateFlow`; este objeto solo transforma datos ya en memoria).
 * - Se puede testear sin `viewModelScope`/dispatchers — dado un lote de datos de dominio,
 *   el resultado es determinista.
 */
internal object CollectionReportStateBuilder {

    /** Parámetros que dependen del periodo/reloj/orden pedido — no de los puertos. */
    data class LoadContext(
        val period: ReportPeriod,
        val range: DateRange,
        val clock: AppClock,
        val sort: DetailSort,
        // Día del ciclo que se está mostrando en DÍA (`null` en SEMANA, y en DÍA cuando el
        // cobrador no tiene ciclo). Ya viene VALIDADO contra el ciclo vigente por
        // [CollectionReportDayStripBuilder.resolveSelectedDay] — aquí solo se propaga.
        val selectedDay: LocalDate? = null
    )

    /** Resultado crudo de consultar los puertos (Task 4) para [LoadContext.range]. */
    data class LoadedPorts(
        val cobrador: String,
        val payments: List<CollectionPayment>,
        val forgiveness: List<Forgiveness>,
        val visits: List<CollectionVisit>,
        val pending: Int,
        val priorTotal: Money,
        val historicalTotals: List<Money>,
        // "Meta de la semana" (Step B/C): ventas de crédito activas ([SalesPort], solo se
        // consultan en SEMANA — ver `CollectionReportViewModel.fetchContent`) + el inicio del
        // ciclo del cobrador (`UserCyclePort.fechaCargaInicial`, mismo valor que ya resuelve
        // `resolveRange` para el rango de SEMANA). `null` cuando el usuario no tiene ciclo
        // (Firestore sin `FECHA_CARGA_INICIAL`) o el periodo es DÍA.
        val sales: List<SaleForCobranza> = emptyList(),
        val fechaCargaInicial: Instant? = null,
        // Días elegibles del ciclo (carga -> hoy) y cobros por día `yyyy-MM-dd` — insumos de la
        // tira de días del periodo Día. Vacíos en Semana y cuando no hay tira que pintar (ver
        // [CollectionReportDayStripBuilder]); en ese caso ninguna consulta extra se paga.
        val cycleDays: List<LocalDate> = emptyList(),
        val dayGroups: Map<String, List<CollectionPayment>> = emptyMap()
    )

    /** Contenido ya listo para copiarse dentro de [CollectionReportUiState]. */
    data class LoadedContent(
        val range: DateRange,
        val cobrador: String,
        val payments: List<CollectionPayment>,
        val pending: Int,
        val hero: HeroUi,
        val efectivo: TileUi,
        val transferencia: TileUi,
        val condonado: ChipUi,
        val visitas: ChipUi,
        val detail: DetailUi,
        val dayPayments: List<List<PaymentRowUi>>,
        val condonadoRows: List<ForgivenessRowUi>,
        val visitRows: List<VisitRowUi>,
        val cycleDays: List<DayChipUi>,
        val selectedDay: LocalDate?,
        val selectedDayNote: String
    )

    /**
     * Rango del periodo: un día de negocio (Día) o el ciclo del cobrador (Semana). Puro dado
     * [fechaCargaInicial] y [selectedDay].
     *
     * [selectedDay] es el día del ciclo que el cobrador eligió ver; `null` = hoy. En AMBOS casos
     * el rango se recorta contra el inicio del ciclo ([RangeCalculator.dayRange] recibe siempre
     * [fechaCargaInicial]): el día de la carga arranca a la hora de la carga, no a medianoche.
     * Pasar `null` como [fechaCargaInicial] dejaría ese recorte INERTE — el defecto de la
     * TAREA 1, que vivía en el caller y no aquí.
     */
    fun resolveRange(
        period: ReportPeriod,
        clock: AppClock,
        fechaCargaInicial: Instant?,
        selectedDay: LocalDate? = null
    ): DateRange = when (period) {
        ReportPeriod.DIA -> if (selectedDay == null) {
            RangeCalculator.dayRange(clock, fechaCargaInicial)
        } else {
            RangeCalculator.dayRange(clock, selectedDay, fechaCargaInicial)
        }

        ReportPeriod.SEMANA -> RangeCalculator.cycleRange(clock, fechaCargaInicial)
    }

    /**
     * Rango previo — mismo tamaño que [range], justo antes de su inicio — oracle de
     * [ReportAggregator.delta] ("vs ayer" en Día / "vs ciclo" en Semana). Lectura fiel al
     * parked de Task 3: se resuelve con una segunda consulta de pagos sobre este rango.
     */
    fun priorRange(range: DateRange): DateRange {
        val priorEnd = range.startDate
        val priorStart = priorEnd.minusDays(range.days.toLong())
        return DateRange(
            startIso = AppTime.toWireFormat(AppTime.startOfDay(priorStart)),
            endExclusiveIso = AppTime.toWireFormat(AppTime.startOfDay(priorEnd))
        )
    }

    /** Arma el [LoadedContent] completo a partir de [context] + [ports]. */
    fun buildContent(context: LoadContext, ports: LoadedPorts): LoadedContent {
        val efectivo = ReportAggregator.efectivo(ports.payments)
        val transferencia = ReportAggregator.transferencia(ports.payments)
        val condonado = ReportAggregator.condonado(ports.forgiveness)
        return LoadedContent(
            range = context.range,
            cobrador = ports.cobrador,
            payments = ports.payments,
            pending = ports.pending,
            hero = buildHero(context, ports),
            efectivo = TileUi("Efectivo", efectivo.total, efectivo.count),
            transferencia = TileUi("Transferencia", transferencia.total, transferencia.count),
            condonado = ChipUi(label = "Condonado", amount = condonado.total),
            visitas = ChipUi(label = "Visitas", count = ports.visits.size),
            detail = buildDetailUi(context, ports.payments),
            // Pagos individuales por día del ciclo (Semana) para el sheet [SheetKind.DIA_CICLO],
            // alineados 1:1 por índice con las filas de [DetailUi.Days] — delega el reparto por
            // día en [ReportAggregator.paymentsByDay] (misma enumeración `businessDays(range)`
            // que `dailyTrend`, así el índice del sheet apunta al mismo día que el resumen). Los
            // pagos ya vienen ordenados cronológicamente del agregador; aquí solo se mapean a
            // [PaymentRowUi]. Día -> vacío (el detalle Día YA es la lista de pagos).
            dayPayments = when (context.period) {
                ReportPeriod.DIA -> emptyList()
                ReportPeriod.SEMANA -> ReportAggregator.paymentsByDay(ports.payments, context.range)
                    .map { dayPayments -> dayPayments.map { it.toPaymentRowUi() } }
            },
            condonadoRows = ports.forgiveness.map {
                ForgivenessRowUi(cliente = it.cliente, motivo = it.motivo, amount = it.amount)
            },
            visitRows = ports.visits.map {
                VisitRowUi(
                    cliente = it.cliente,
                    nota = it.nota,
                    tipo = it.tipo,
                    visitedAt = it.visitedAt
                )
            },
            // Tira de días SOLO en Día: Semana ya muestra el ciclo entero en su detalle
            // (`DetailUi.Days`), así que una tira ahí sería el mismo dato dos veces.
            cycleDays = when (context.period) {
                ReportPeriod.DIA -> CollectionReportDayStripBuilder.chips(
                    cycleDays = ports.cycleDays,
                    selectedDay = context.selectedDay,
                    today = AppTime.todayInBusinessZone(context.clock),
                    dayGroups = ports.dayGroups
                )

                ReportPeriod.SEMANA -> emptyList()
            },
            selectedDay = context.selectedDay,
            selectedDayNote = CollectionReportDayStripBuilder.startNote(
                context.selectedDay,
                ports.fechaCargaInicial
            )
        )
    }

    /**
     * Filas de pago del detalle Día, ordenadas por [sort]. Pública porque
     * [CollectionReportViewModel.setSort] la reusa para reordenar SIN volver a consultar los
     * puertos (reordenar es puro, dado el último lote de pagos ya cargado). Delega en
     * [CollectionReportSort] (Task 4: separado en su propio archivo para no cruzar el umbral
     * `TooManyFunctions` de detekt en este objeto) — la firma pública no cambia, solo dónde
     * vive el cómputo. El reordenado de Semana ([CollectionReportViewModel.setSort]) llama
     * [CollectionReportSort.dayPaymentRows] directo, sin pasar por aquí.
     */
    fun sortedPaymentRows(payments: List<CollectionPayment>, sort: DetailSort): List<PaymentRowUi> =
        CollectionReportSort.paymentRows(payments, sort)

    private fun CollectionPayment.toPaymentRowUi(): PaymentRowUi = PaymentRowUi(
        id = id,
        cliente = cliente,
        ventaLabel = ventaLabel,
        paidAt = paidAt,
        amount = amount,
        method = method,
        synced = synced,
        folio = folio,
        saldo = saldo
    )

    private fun buildHero(context: LoadContext, ports: LoadedPorts): HeroUi {
        val total = ReportAggregator.total(ports.payments)
        // "Meta de la semana" (reemplaza la meta de mediana + wells retirados, ver KDoc de
        // HeroUi): solo se calcula en SEMANA — DÍA no tiene ventana de ciclo, y recalcular
        // "cobro"/"cuentas" para una sola jornada requeriría reinterpretar `aplicaEnVentana`
        // fuera de su semántica de ciclo semanal (decisión documentada, no un hueco).
        val cobranza = when (context.period) {
            ReportPeriod.DIA -> CobranzaPorcentaje.CobranzaSemanal(null, null, 0, 0)
            ReportPeriod.SEMANA -> CollectionReportMetaBuilder.cobranzaSemanal(ports, context.clock)
        }
        // El insight ("vas al X% de tu meta") ahora se alimenta del ponderado real (capado a
        // [0,1] para la frase, aunque el ring de la tarjeta muestre el valor SIN capar — puede
        // exceder 100%, ver KDoc de CobranzaPorcentaje.resumenPonderado) en vez de la fracción
        // contra una meta de mediana retirada.
        val progress = ((cobranza.porcentajeCobro?.toFloat() ?: 0f) / PERCENT_SCALE).coerceIn(
            0f,
            1f
        )
        return HeroUi(
            overline = heroOverline(context.period, context.range),
            delta = ReportAggregator.delta(total, ports.priorTotal, context.period),
            monto = total,
            insight = resolveInsight(context.period, ports.payments, progress, context.range),
            sparkline = ReportAggregator.timeline(ports.payments, context.period, context.range),
            porcentajeCobro = cobranza.porcentajeCobro?.toFloat() ?: 0f,
            porcentajeCuentas = cobranza.porcentajeCuentas?.toFloat() ?: 0f,
            clientesPagaron = cobranza.clientesPagaron,
            clientesTotal = cobranza.clientesTotal
        )
    }

    private const val PERCENT_SCALE = 100f

    /**
     * `projection` (Día, "a este ritmo cierras en $Y") queda PARKED a propósito — ver el
     * reporte de Task 5: no hay todavía un oracle de proyección de cierre verificado; se
     * degrada a `null` (que [Insight.Daily] modela explícitamente) en vez de inventar una
     * fórmula de negocio sin validar, mismo criterio que el delta con `prior <= 0`.
     *
     * `cycleDay`/`cycleDays` (Semana) son iguales entre sí a propósito: [RangeCalculator]
     * siempre cierra el ciclo en "hoy" (no hay una duración de ciclo fija conocida de
     * antemano), así que el día en curso ES el último día del ciclo — `range.days` sirve
     * para ambos sin inventar un segundo cálculo.
     */
    private fun resolveInsight(
        period: ReportPeriod,
        payments: List<CollectionPayment>,
        progress: Float,
        range: DateRange
    ): Insight {
        val count = ReportAggregator.count(payments)
        return when (period) {
            ReportPeriod.DIA -> ReportAggregator.insight(period, count, progress, projection = null)
            ReportPeriod.SEMANA -> ReportAggregator.insight(
                period,
                count,
                progress,
                projection = null,
                cycleDay = range.days,
                cycleDays = range.days
            )
        }
    }

    /**
     * Overline corto del hero. Día reusa [DateRange.dayLabel] completo (p. ej.
     * "Cobrado · viernes 7 ago 2026") en vez de la forma abreviada del mockup
     * ("Cobrado · vie 7 ago"): desviación consciente — evita inventar un segundo formateador
     * de fecha corto no provisto por el dominio (Task 2); la UI (Task 6+) puede recortarlo si
     * se requiere paridad pixel-a-pixel con el mockup.
     */
    private fun heroOverline(period: ReportPeriod, range: DateRange): String = when (period) {
        ReportPeriod.DIA -> "Cobrado · ${range.dayLabel()}"
        ReportPeriod.SEMANA -> "Cobrado · semana actual"
    }

    private fun buildDetailUi(context: LoadContext, payments: List<CollectionPayment>): DetailUi =
        when (context.period) {
            ReportPeriod.DIA -> DetailUi.Payments(sortedPaymentRows(payments, context.sort))
            ReportPeriod.SEMANA -> DetailUi.Days(
                ReportAggregator.dailyTrend(payments, context.range, context.clock).map { trend ->
                    DayRowUi(trend.label, trend.total, trend.count, trend.initials, trend.isToday)
                }
            )
        }
}
