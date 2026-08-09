package com.example.msp_app.feature.collectionreport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.collectionreport.domain.ReportAggregator
import com.example.msp_app.feature.collectionreport.domain.SuggestedGoal
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.domain.port.HistoricalTotalsPort
import com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.example.msp_app.feature.collectionreport.domain.port.VisitsPort
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orquesta dominio + puertos del reporte de cobranza en un único [StateFlow] observable
 * (`state`) que la UI (Tasks 6-9) consume, y expone los eventos de interacción del tablero
 * (periodo, máscara, tema, orden, sheet). El armado puro del contenido (dado un lote de
 * pagos/visitas/condonaciones ya resuelto) vive en [CollectionReportStateBuilder] — este
 * ViewModel solo hace I/O (llamadas suspend a los puertos) y actualiza el `StateFlow`.
 *
 * `@HiltViewModel` (no `@Singleton`, kill-switch): ninguno de los puertos inyectados
 * sostiene una sesión/red que deba sobrevivir más allá del ciclo del ViewModel — son
 * lectores Room baratos ([PaymentsPort]/[VisitsPort]/[HistoricalTotalsPort]) o dependen del
 * contexto de usuario ([UserCyclePort], cuya implementación real vive en `:app`, ver
 * `CollectionReportPorts.kt`).
 */
@HiltViewModel
class CollectionReportViewModel @Inject constructor(
    private val paymentsPort: PaymentsPort,
    private val visitsPort: VisitsPort,
    private val userCyclePort: UserCyclePort,
    private val historicalTotalsPort: HistoricalTotalsPort,
    private val clock: AppClock,
    private val telemetry: Telemetry
) : ViewModel() {

    private val mutableState = MutableStateFlow(CollectionReportUiState())
    val state: StateFlow<CollectionReportUiState> = mutableState.asStateFlow()

    // Último lote de pagos cargado — permite a [setSort] reordenar el detalle SIN volver a
    // consultar los puertos (reordenar es puro, ver CollectionReportStateBuilder).
    private var lastPayments: List<CollectionPayment> = emptyList()

    // Carga en curso: se cancela antes de lanzar la siguiente, para que un `setPeriod` que
    // no alcanzó a resolver no pise el estado de la carga más reciente al llegar tarde.
    private var loadJob: Job? = null

    init {
        telemetry.screenView(SCREEN)
        load(ReportPeriod.DIA)
    }

    /** Cambia el periodo (Día/Semana) y dispara una nueva carga con su rango. */
    fun setPeriod(period: ReportPeriod) {
        telemetry.tap(SCREEN, "period_${period.name.lowercase()}")
        load(period)
    }

    /**
     * Alterna ocultar cifras. La máscara es de RENDER (UI decide `MASKED_MONEY`), no de
     * datos: los `Money` del estado NUNCA se mutan, así des-enmascarar no requiere recargar.
     */
    fun toggleMask() {
        telemetry.tap(SCREEN, "mask_toggle")
        mutableState.update { it.copy(masked = !it.masked) }
    }

    /**
     * Espejo local del tema oscuro — el ViewModel NO es dueño de la fuente de verdad. El
     * toggle real (y el reveal circular) vive en el composition root / Task 9, que necesita
     * el frame de la pantalla para animar; este flag solo deja que la UI refleje el estado.
     */
    fun toggleTheme() {
        telemetry.tap(SCREEN, "theme_toggle")
        mutableState.update { it.copy(darkTheme = !it.darkTheme) }
    }

    /** Reordena el detalle Día (Hora/Nombre); Semana lo ignora (siempre cronológico). */
    fun setSort(sort: DetailSort) {
        telemetry.tap(SCREEN, "sort_${sort.name.lowercase()}")
        mutableState.update { current ->
            val detail = if (current.period == ReportPeriod.DIA) {
                DetailUi.Payments(
                    CollectionReportStateBuilder.sortedPaymentRows(lastPayments, sort)
                )
            } else {
                current.detail
            }
            current.copy(sort = sort, detail = detail)
        }
    }

    /** Abre el sheet [kind] (opcionalmente con [argument], p. ej. id de pago o índice de día). */
    fun openSheet(kind: SheetKind, argument: String? = null) {
        telemetry.tap(SCREEN, "sheet_${kind.name.lowercase()}")
        mutableState.update { it.copy(sheet = SheetUi(kind, argument)) }
    }

    /** Cierra el sheet abierto (si lo hay). */
    fun closeSheet() {
        mutableState.update { it.copy(sheet = null) }
    }

    // Catch genérico deliberado: un puerto puede fallar con cualquier excepción (I/O de Room,
    // parseo de fecha corrupta, Firestore en la implementación real de UserCyclePort) y el
    // contrato de esta pantalla es degradar SIEMPRE a estado de error, nunca crashear — no hay
    // un tipo cerrado de excepciones que enumerar. CancellationException se re-lanza aparte
    // (cooperación de corrutinas: cancelar un `loadJob` no debe verse como un fallo del puerto).
    @Suppress("TooGenericExceptionCaught")
    private fun load(period: ReportPeriod) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            mutableState.update { it.copy(period = period, loading = true, error = null) }
            try {
                val sort = mutableState.value.sort
                val content = fetchContent(period, sort)
                lastPayments = content.payments
                mutableState.update { applyContent(it, period, content) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                telemetry.error(
                    code = "collection_report_load_failed",
                    message = failure.message ?: failure::class.simpleName.orEmpty(),
                    props = mapOf("period" to period.name)
                )
                mutableState.update { applyError(it, period) }
            }
        }
    }

    private suspend fun fetchContent(
        period: ReportPeriod,
        sort: DetailSort
    ): CollectionReportStateBuilder.LoadedContent {
        // fechaCargaInicial solo se pide en Semana (Día no depende del ciclo del cobrador).
        val fechaCargaInicial = if (period == ReportPeriod.SEMANA) userCyclePort.fechaCargaInicial() else null
        val range = CollectionReportStateBuilder.resolveRange(period, clock, fechaCargaInicial)
        val cobrador = userCyclePort.cobradorNombre()
        val payments = paymentsPort.paymentsIn(range)
        val forgiveness = paymentsPort.forgivenessIn(range)
        val visits = visitsPort.visitsIn(range)
        val pending = paymentsPort.pendingCount()
        val priorTotal = ReportAggregator.total(
            paymentsPort.paymentsIn(CollectionReportStateBuilder.priorRange(range))
        )
        val historicalTotals = historicalTotalsPort.dailyTotals(SuggestedGoal.DEFAULT_WINDOW)

        val context = CollectionReportStateBuilder.LoadContext(period, range, clock, sort)
        val ports = CollectionReportStateBuilder.LoadedPorts(
            cobrador = cobrador,
            payments = payments,
            forgiveness = forgiveness,
            visits = visits,
            pending = pending,
            priorTotal = priorTotal,
            historicalTotals = historicalTotals
        )
        return CollectionReportStateBuilder.buildContent(context, ports)
    }

    private fun applyContent(
        current: CollectionReportUiState,
        period: ReportPeriod,
        content: CollectionReportStateBuilder.LoadedContent
    ): CollectionReportUiState = current.copy(
        period = period,
        loading = false,
        error = null,
        cobrador = content.cobrador,
        rangeLabel = if (period == ReportPeriod.DIA) content.range.dayLabel() else content.range.cycleLabel(),
        pendingCount = content.pending,
        hero = content.hero,
        efectivo = content.efectivo,
        transferencia = content.transferencia,
        condonado = content.condonado,
        visitas = content.visitas,
        detail = content.detail,
        condonadoRows = content.condonadoRows,
        visitRows = content.visitRows
    )

    /**
     * Estado de error MANTENIENDO [period] consistente con el resto del contenido — fix
     * round 1: el toggle NO "rebota" solo a escondidas del usuario (si tocó Semana y falló,
     * sigue viendo Semana seleccionada), pero TAMPOCO se queda mezclando el [period] nuevo
     * con `hero`/`detail`/`rangeLabel` del periodo anterior (eso mostraría "Semana"
     * seleccionada con las cifras/etiqueta de Día). Se elige blanquear todo el contenido
     * dependiente del rango (opción "b" del fix, no un rollback de `period`): la UI (Task 6+)
     * pinta el banner de error sobre un tablero en blanco para el periodo pedido. `cobrador`
     * NO se blanquea — es identidad del usuario, no depende del rango, y no genera la mezcla
     * que este fix corrige.
     */
    private fun applyError(
        current: CollectionReportUiState,
        period: ReportPeriod
    ): CollectionReportUiState = current.copy(
        period = period,
        loading = false,
        error = ERROR_MESSAGE,
        rangeLabel = "",
        pendingCount = 0,
        hero = HeroUi(),
        efectivo = TileUi(label = "Efectivo"),
        transferencia = TileUi(label = "Transferencia"),
        condonado = ChipUi(label = "Condonado"),
        visitas = ChipUi(label = "Visitas"),
        detail = DetailUi.Payments(emptyList()),
        condonadoRows = emptyList(),
        visitRows = emptyList()
    )

    private companion object {
        const val SCREEN = "collection_report"
        const val ERROR_MESSAGE = "no se pudo cargar el reporte de cobranza"
    }
}
