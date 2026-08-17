package com.example.msp_app.feature.collectionreport.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.printing.domain.PreferredPrinterStore
import com.example.msp_app.core.printing.domain.PrintError
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.feature.collectionreport.di.DefaultDispatcher
import com.example.msp_app.feature.collectionreport.domain.RangeCalculator
import com.example.msp_app.feature.collectionreport.domain.ReportAggregator
import com.example.msp_app.feature.collectionreport.domain.SuggestedGoal
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.domain.port.CycleStart
import com.example.msp_app.feature.collectionreport.domain.port.HistoricalTotalsPort
import com.example.msp_app.feature.collectionreport.domain.port.PaymentsPort
import com.example.msp_app.feature.collectionreport.domain.port.ReportThemePort
import com.example.msp_app.feature.collectionreport.domain.port.SalesPort
import com.example.msp_app.feature.collectionreport.domain.port.UserCyclePort
import com.example.msp_app.feature.collectionreport.domain.port.VisitsPort
import com.example.msp_app.feature.collectionreport.printing.CollectionReportFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
// `TooManyFunctions`/`LongParameterList` suprimidos con justificación: este ViewModel expone
// UN estado observable único (`state`) para toda la pantalla, así que agrega dos superficies de
// eventos cohesivas — las interacciones del tablero (periodo/máscara/tema/orden/sheet) y el
// flujo de impresión térmica (P2) — y Hilt le inyecta los puertos que ambas necesitan.
// Partir el flujo de impresión en otro tipo inyectado fragmentaría ese estado único (dos
// `StateFlow` que la UI tendría que recombinar) y agrupar los puertos en un holder artificial
// solo escondería el wiring — ninguno mejora la legibilidad real (mismo criterio que los
// suppress documentados en `ReportActionsController`/`BluetoothPrinterDiscovery`).
@Suppress("TooManyFunctions", "LongParameterList")
@HiltViewModel
class CollectionReportViewModel @Inject constructor(
    private val paymentsPort: PaymentsPort,
    private val visitsPort: VisitsPort,
    private val userCyclePort: UserCyclePort,
    private val historicalTotalsPort: HistoricalTotalsPort,
    private val salesPort: SalesPort,
    private val printerPort: PrinterPort,
    private val preferredPrinterStore: PreferredPrinterStore,
    private val clock: AppClock,
    private val telemetry: Telemetry,
    private val reportThemePort: ReportThemePort,
    @DefaultDispatcher private val backgroundDispatcher: CoroutineDispatcher
) : ViewModel() {

    // Sembrado con el tema GLOBAL vigente (no `false` a ciegas): así el primer frame del
    // reporte ya nace en el tema correcto, sin un parpadeo claro->oscuro cuando la app entera
    // está en oscuro (mismo criterio que `ConfiguracionViewModel` siembra su `themeMode` desde
    // `AppThemePort.currentThemeMode()`).
    private val mutableState = MutableStateFlow(
        CollectionReportUiState(darkTheme = reportThemePort.currentIsDark())
    )
    val state: StateFlow<CollectionReportUiState> = mutableState.asStateFlow()

    // Último lote de pagos cargado — permite a [setSort] reordenar el detalle SIN volver a
    // consultar los puertos (reordenar es puro, ver CollectionReportStateBuilder).
    private var lastPayments: List<CollectionPayment> = emptyList()

    // Rango del último lote — [setSort] lo necesita en Semana para re-agrupar `dayPayments`
    // por día ([CollectionReportSort.dayPaymentRows]); en Día no se usa.
    private var lastRange: DateRange? = null

    // Carga en curso: se cancela antes de lanzar la siguiente, para que un `setPeriod` que
    // no alcanzó a resolver no pise el estado de la carga más reciente al llegar tarde.
    private var loadJob: Job? = null

    // Día del ciclo que el usuario PIDIÓ ver en el periodo Día (`null` = hoy, el default). Vive
    // aquí y no en un `rememberSaveable` de la UI a propósito: el día elegido no es adorno de
    // pantalla — decide el rango con que se consultan los puertos y, por lo tanto, el total, la
    // lista de pagos y lo que se llevan Compartir/Imprimir/PDF (que leen `state`, no la UI). Un
    // segundo dueño del dato en el árbol de composición sería justo el defecto de la TAREA 1
    // otra vez: un control que se ve conectado y no mueve los datos. Al vivir en el ViewModel
    // queda por ENCIMA del `AnimatedContent` del toggle Día↔Semana (cada slot es un subárbol
    // nuevo) y sobrevive a rotación/cambio de tamaño de letra igual que el resto del estado.
    // Es la PETICIÓN, no la verdad: cada carga la valida contra el ciclo vigente
    // ([CollectionReportDayStripBuilder.resolveSelectedDay]) y guarda de vuelta el día resuelto.
    private var requestedDay: LocalDate? = null

    // Último inicio de semana resuelto de verdad (`Known` o `Missing`); `null` mientras la
    // fuente no ha dado NI UNA respuesta. Existe para no DEGRADAR un dato bueno por un tropiezo
    // posterior de Firestore: cada carga sigue releyendo el puerto (así una carga de ruta nueva
    // a mitad de sesión se refleja), pero un `Unavailable` que llega después de un `Known` no
    // borra la semana que ya se conocía.
    private var lastResolvedCycle: CycleStart? = null

    // Reintento en curso del inicio de semana. Es un job APARTE de [loadJob] para que
    // sobreviva a las cancelaciones de carga: si el reintento colgara del `loadJob`, un
    // `setPeriod` lo mataría y la auto-reparación no llegaría nunca.
    private var cycleRetryJob: Job? = null

    init {
        telemetry.screenView(SCREEN)
        // Mantiene `darkTheme` sincronizado con el tema GLOBAL mientras la pantalla vive —
        // no solo en el toggle propio: si el tema cambia desde OTRO lado (Configuración, o el
        // sistema operativo en modo Automático) mientras el reporte está en pantalla, se
        // refleja igual. `viewModelScope` cancela esta colecta sola al destruirse el ViewModel.
        viewModelScope.launch {
            reportThemePort.isDark.collect { dark ->
                mutableState.update { it.copy(darkTheme = dark) }
            }
        }
        load(ReportPeriod.DIA)
    }

    /**
     * Inicio de semana para ESTA carga.
     *
     * Relee el puerto siempre (freshness: una carga de ruta nueva a mitad de sesión debe verse),
     * pero con dos reglas que el `Instant?` anterior no podía expresar:
     *  - un fallo ([CycleStart.Unavailable]) NO pisa un valor bueno anterior;
     *  - un fallo SIN valor bueno previo agenda un reintento, y de ahí sale la auto-reparación:
     *    cuando la fuente por fin responde, se recarga sola el periodo que esté en pantalla.
     *    Antes esto era imposible — el reporte es todo `suspend` one-shot y se quedaba en $0
     *    hasta que el cobrador salía y volvía a entrar.
     */
    private suspend fun resolveCycle(): CycleStart {
        val fresh = readCycleStart()
        if (fresh !is CycleStart.Unavailable) {
            lastResolvedCycle = fresh
            cycleRetryJob?.cancel()
            cycleRetryJob = null
            return fresh
        }
        lastResolvedCycle?.let { return it }
        scheduleCycleRetry()
        return CycleStart.Unavailable
    }

    /**
     * Reintenta leer el inicio de semana con espera creciente y tope de intentos, y RECARGA en
     * cuanto la fuente responde.
     *
     * No se reintenta ante [CycleStart.Missing] (lo corta [resolveCycle]): ésa es una respuesta
     * real y estable — el cobrador no ha iniciado su semana — y machacar Firestore no la va a
     * cambiar; lo que corresponde ahí es decírselo, que es lo que hace [applyNoCycle].
     */
    private fun scheduleCycleRetry() {
        if (cycleRetryJob?.isActive == true) return
        cycleRetryJob = viewModelScope.launch {
            var wait = RETRY_INITIAL_MS
            repeat(RETRY_ATTEMPTS) {
                delay(wait)
                wait = (wait * 2).coerceAtMost(RETRY_MAX_MS)
                if (readCycleStart() !is CycleStart.Unavailable) {
                    load(mutableState.value.period)
                    return@launch
                }
            }
        }
    }

    // Catch genérico deliberado, mismo criterio que `load`: el adapter real habla con Firestore
    // y puede fallar con cualquier excepción. Un fallo NO es "no hay semana" — se clasifica como
    // reintentable, que es la distinción que el `Instant?` anterior perdía.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun readCycleStart(): CycleStart = try {
        withContext(backgroundDispatcher) { userCyclePort.cycleStart() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        telemetry.error(
            code = "collection_report_cycle_unavailable",
            message = failure.message ?: failure::class.simpleName.orEmpty()
        )
        CycleStart.Unavailable
    }

    /** Cambia el periodo (Día/Semana) y dispara una nueva carga con su rango. */
    fun setPeriod(period: ReportPeriod) {
        telemetry.tap(SCREEN, "period_${period.name.lowercase()}")
        load(period)
    }

    /**
     * Elige qué día del ciclo mostrar en el periodo Día y recarga con el rango de ESE día.
     *
     * Cambia el total, la lista de pagos y — porque las tres acciones de salida
     * (Compartir/Imprimir/PDF) se arman desde `state`, no desde la fecha del sistema — también
     * lo que se comparte, se imprime y se exporta. Es literalmente lo que pidió el dueño: poder
     * ver e imprimir cualquier día desde la carga de ruta hasta hoy.
     *
     * [day] se guarda como PETICIÓN: si no pertenece al ciclo vigente (p. ej. el cobrador
     * cargó ruta de nuevo y la tira ya es otra), la carga lo devuelve a hoy — ver
     * [CollectionReportDayStripBuilder.resolveSelectedDay].
     */
    fun selectDay(day: LocalDate) {
        telemetry.tap(SCREEN, "day_select")
        requestedDay = day
        load(ReportPeriod.DIA)
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
     * Alterna el tema GLOBAL de la app vía [ReportThemePort] (persiste, sobrevive a
     * navegación/muerte de proceso) — fix del bug donde el reporte tenía su propio espejo
     * local que se reiniciaba a claro cada vez que se volvía a entrar a la pantalla. El
     * [ReportThemePort.toggle] real es síncrono ([ThemeController.toggle] solo escribe
     * `SharedPreferences`), así que no hace falta lanzar una corrutina aquí: la escritura del
     * `StateFlow` (`darkTheme`) la produce la colecta de [ReportThemePort.isDark] instalada en
     * [init], no esta función — mismo desacople que ya usa `ConfiguracionViewModel.selectThemeMode`
     * con `AppThemePort`. `ThemeRevealRoot` sigue animando sobre el cambio de `darkTheme` igual
     * que antes; lo único que cambia es DE DÓNDE viene ese cambio.
     */
    fun toggleTheme() {
        telemetry.tap(SCREEN, "theme_toggle")
        reportThemePort.toggle()
    }

    /**
     * Reordena el detalle (Hora/Fecha·Nombre) SIN volver a consultar los puertos — el orden es
     * puro dado el último lote ya cargado ([CollectionReportStateBuilder] / [CollectionReportSort]).
     * Día reordena `detail` directo; Semana ([current.period] == SEMANA) reordena
     * `dayPayments` DENTRO de cada día ([CollectionReportSort.dayPaymentRows]) y deja el
     * resumen `DetailUi.Days` intacto — el orden ENTRE días sigue siendo cronológico, solo
     * cambia el orden de los pagos individuales de cada día (consumidos por el sheet
     * `DIA_CICLO`; el ticket impreso aplica su propio orden global sobre TODO el rango, ver
     * `CollectionReportFormatter.addPaymentsBlock`).
     */
    fun setSort(sort: DetailSort) {
        telemetry.tap(SCREEN, "sort_${sort.name.lowercase()}")
        mutableState.update { current ->
            when (current.period) {
                ReportPeriod.DIA -> current.copy(
                    sort = sort,
                    detail = DetailUi.Payments(
                        CollectionReportStateBuilder.sortedPaymentRows(lastPayments, sort)
                    )
                )
                ReportPeriod.SEMANA -> {
                    val range = lastRange
                    val dayPayments = if (range == null) {
                        current.dayPayments
                    } else {
                        CollectionReportSort.dayPaymentRows(lastPayments, range, sort)
                    }
                    current.copy(sort = sort, dayPayments = dayPayments)
                }
            }
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

    // region — impresión térmica (P2) ----------------------------------------------------

    /**
     * Imprime el reporte a la impresora RECORDADA por defecto (auto), abriendo el bottom
     * sheet de impresión para dar feedback. Resuelve la lista de emparejadas y valida/
     * self-healea la recordada contra ella ([PreferredPrinterStore.preferredPrinter]); si hay
     * una recordada válida imprime directo, si no abre el picker ([PrintPhase.SELECTING]).
     * Un fallo del puerto (Bluetooth apagado, permiso, etc.) degrada a [PrintPhase.ERROR] con
     * un mensaje es-MX — nunca crashea.
     */
    fun printReport() {
        telemetry.tap(SCREEN, "print")
        viewModelScope.launch {
            mutableState.update { it.copy(printSheet = PrintSheetUi(PrintPhase.PRINTING)) }
            val printers = printerPort.listPairedPrinters().getOrElse { failure ->
                surfacePrintError(failure, target = null, printers = emptyList())
                return@launch
            }
            val remembered = preferredPrinterStore.preferredPrinter(printers)
            if (remembered == null) {
                mutableState.update {
                    it.copy(printSheet = PrintSheetUi(PrintPhase.SELECTING, printers = printers))
                }
            } else {
                printTo(remembered, printers)
            }
        }
    }

    /**
     * Abre el picker de impresoras SIEMPRE que el usuario lo pida ("Cambiar impresora") — no
     * solo la primera vez. Resuelve la lista de emparejadas y muestra [PrintPhase.SELECTING].
     */
    fun openPrinterPicker() {
        telemetry.tap(SCREEN, "print_change_printer")
        viewModelScope.launch {
            val current = mutableState.value.printSheet
            val printers = printerPort.listPairedPrinters().getOrElse { failure ->
                surfacePrintError(failure, target = current?.target, printers = emptyList())
                return@launch
            }
            mutableState.update {
                it.copy(
                    printSheet = PrintSheetUi(
                        phase = PrintPhase.SELECTING,
                        target = current?.target,
                        printers = printers
                    )
                )
            }
        }
    }

    /** Elige [device] como impresora (la recuerda) e imprime a ella. */
    fun selectPrinter(device: PrinterDevice) {
        telemetry.tap(SCREEN, "print_select_printer")
        preferredPrinterStore.savePreferredAddress(device.address)
        val printers = mutableState.value.printSheet?.printers ?: listOf(device)
        viewModelScope.launch { printTo(device, printers) }
    }

    /** Reintenta imprimir a la última impresora objetivo; si no hay, reinicia el flujo. */
    fun retryPrint() {
        val current = mutableState.value.printSheet
        val target = current?.target
        if (target == null) {
            printReport()
        } else {
            viewModelScope.launch { printTo(target, current.printers) }
        }
    }

    /** Cierra el bottom sheet de impresión. */
    fun dismissPrintSheet() {
        mutableState.update { it.copy(printSheet = null) }
    }

    /**
     * El sistema negó el permiso de Bluetooth requerido para imprimir (API 31+). Lo maneja la
     * UI (el launcher de permisos vive en la pantalla), que llama aquí para reflejarlo como un
     * error del flujo en vez de un no-op silencioso.
     */
    fun onPrintPermissionDenied() {
        mutableState.update {
            it.copy(
                printSheet = PrintSheetUi(
                    phase = PrintPhase.ERROR,
                    target = it.printSheet?.target,
                    printers = it.printSheet?.printers.orEmpty(),
                    message = printErrorMessage(PrintError.PermissionDenied)
                )
            )
        }
    }

    private suspend fun printTo(device: PrinterDevice, printers: List<PrinterDevice>) {
        mutableState.update {
            it.copy(
                printSheet = PrintSheetUi(PrintPhase.PRINTING, target = device, printers = printers)
            )
        }
        val ticket = CollectionReportFormatter.toTicketLines(mutableState.value, clock)
        printerPort.print(device, ticket, PrinterProfile.PROFILE_58MM).fold(
            onSuccess = {
                telemetry.tap(SCREEN, "print_success")
                mutableState.update {
                    it.copy(
                        printSheet = PrintSheetUi(
                            PrintPhase.SUCCESS,
                            target = device,
                            printers = printers
                        )
                    )
                }
            },
            onFailure = { failure ->
                surfacePrintError(
                    failure,
                    target = device,
                    printers = printers
                )
            }
        )
    }

    private fun surfacePrintError(
        failure: Throwable,
        target: PrinterDevice?,
        printers: List<PrinterDevice>
    ) {
        telemetry.error(
            code = "collection_report_print_failed",
            message = failure.message ?: failure::class.simpleName.orEmpty(),
            props = mapOf("printer" to (target?.address ?: ""))
        )
        mutableState.update {
            it.copy(
                printSheet = PrintSheetUi(
                    phase = PrintPhase.ERROR,
                    target = target,
                    printers = printers,
                    message = printErrorMessage(failure)
                )
            )
        }
    }

    private fun printErrorMessage(failure: Throwable): String = when (failure) {
        is PrintError.BluetoothDisabled -> "activa el bluetooth para imprimir"
        is PrintError.NotPaired -> "la impresora ya no está emparejada"
        is PrintError.PermissionDenied -> "concede el permiso de bluetooth para imprimir"
        is PrintError.ConnectionFailed -> "no se pudo conectar con la impresora"
        is PrintError.WriteFailed -> "no se pudo enviar el ticket a la impresora"
        else -> "no se pudo imprimir el reporte"
    }

    // endregion

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
            val start = resolveCycle()
            // Sin semana utilizable NO se consulta nada: un rango inventado produciría el $0.00
            // con la tabla de pagos llena que se vio en campo. Se dice lo que pasa y ya.
            val weekUsable = RangeCalculator.cycleRange(clock, start.instantOrNull) != null
            // Día SÍ sigue siendo un día natural bien definido cuando lo único que falta es la
            // fecha de carga (no hay contra qué recortar, pero "hoy" no depende de la semana).
            // La excepción es una carga en el FUTURO: ahí `dayRange` también sale vacío, y
            // pintar "sin cobros hoy" sobre un rango degenerado sería la misma mentira.
            val dayUsable = start !is CycleStart.Known || weekUsable
            val blocked = when (period) {
                ReportPeriod.DIA -> !dayUsable
                ReportPeriod.SEMANA -> !weekUsable
            }
            if (blocked) {
                mutableState.update { applyNoCycle(it, period, start) }
                return@launch
            }
            try {
                val sort = mutableState.value.sort
                val content = fetchContent(period, sort, start.instantOrNull)
                lastPayments = content.payments
                lastRange = content.range
                // El día que de verdad se cargó (puede NO ser el pedido: ciclo nuevo -> hoy).
                // Guardarlo evita que la petición fantasma sobreviva a la siguiente recarga.
                //
                // SOLO cuando ya había una petición del usuario. Si `requestedDay` es null, el día
                // mostrado lo eligió `resolveSelectedDay` (= hoy) y escribirlo aquí lo CONGELARÍA:
                // un proceso vivo al cruzar la medianoche seguiría marcando el día de ayer como
                // seleccionado, aunque el cobrador nunca lo tocó. Dejándolo en null, el default
                // sigue a "hoy" cada vez que se recarga.
                if (period == ReportPeriod.DIA && requestedDay != null) {
                    requestedDay = content.selectedDay
                }
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

    // Toda la carga corre en [Dispatchers.Default]: además de las queries suspend de Room (que
    // ya despachan a su propio executor), los `.map` entidad->dominio de los adapters y —
    // sobre todo — `CollectionReportStateBuilder.buildContent` (agregación BigDecimal de
    // `Money`, timeline/dailyTrend, `sortedBy { lowercase() }`) se hacían en el hilo Main al
    // reanudar la corrutina, cayendo justo en el frame del slide del toggle. Sacarlos de Main
    // quita ese trabajo pesado de la animación (ver toggle-jank-diagnosis.md, fix 2).
    private suspend fun fetchContent(
        period: ReportPeriod,
        sort: DetailSort,
        fechaCargaInicial: Instant?
    ): CollectionReportStateBuilder.LoadedContent = withContext(backgroundDispatcher) {
        // fechaCargaInicial se usa en AMBOS periodos: el ciclo del cobrador abre en el INSTANTE
        // de la carga y ESE recorte aplica también al día (`inicioEfectivo(día) =
        // max(startOfDay(día), fechaCargaInicial)`, ver el KDoc de `RangeCalculator`) — sin este
        // valor, `RangeCalculator.dayRange` no tiene contra qué recortar y el día de la carga
        // arranca a medianoche, volviendo a contar los pagos del ciclo ANTERIOR (el defecto
        // medido en la ruta 34: $48,200 contra los $43,850 reales). Además es lo que le da a Día
        // la lista de días elegibles del ciclo (el selector de día) — sin fecha de carga no hay
        // ciclo que recorrer. La versión anterior de este comentario afirmaba lo contrario
        // ("Día no depende del ciclo") y el `null` que la acompañaba dejaba el recorte INERTE.
        //
        // Lo que NO cambia: "Meta de la semana" (`CobranzaPorcentaje`) sigue calculándose SOLO en
        // Semana. El gate de la meta es el `period`, no la presencia de este valor — vive en
        // `CollectionReportStateBuilder.buildHero` (DÍA -> `CobranzaSemanal(null, null, 0, 0)`) y
        // en el `salesPort` de abajo, que tampoco se consulta en Día. Pedir la fecha aquí no lo
        // enciende por accidente.
        //
        // Ya NO se consulta el puerto aquí: llega resuelto desde [cycle] (una sola lectura por
        // pantalla + reintentos), así el fallo transitorio de Firestore se maneja en un solo
        // lugar y con reintento, en vez de aplanarse a `null` dentro de cada carga.
        //
        // Días elegibles del ciclo (de la carga a hoy) y día realmente mostrado en Día: si el
        // usuario tenía elegido un día que el ciclo NUEVO ya no contiene, `resolveSelectedDay`
        // lo devuelve a hoy en vez de dejar la pantalla apuntando a un día fantasma.
        val cycleDays = RangeCalculator.cycleDays(clock, fechaCargaInicial)
        val selectedDay = when (period) {
            ReportPeriod.DIA ->
                CollectionReportDayStripBuilder.resolveSelectedDay(cycleDays, requestedDay)
            ReportPeriod.SEMANA -> null
        }
        // Invariante de [load]: aquí solo se llega con una ventana utilizable para el periodo
        // pedido. Si alguien la rompe, revienta con mensaje y `load` lo convierte en banner de
        // error — nunca en un rango inventado.
        val range = checkNotNull(
            CollectionReportStateBuilder.resolveRange(period, clock, fechaCargaInicial, selectedDay)
        ) { "se pidió $period sin una ventana de semana utilizable" }
        val cobrador = userCyclePort.cobradorNombre()
        val payments = paymentsPort.paymentsIn(range)
        val forgiveness = paymentsPort.forgivenessIn(range)
        val visits = visitsPort.visitsIn(range)
        val pending = paymentsPort.pendingCount()
        val priorTotal = ReportAggregator.total(
            paymentsPort.paymentsIn(CollectionReportStateBuilder.priorRange(range))
        )
        val historicalTotals = historicalTotalsPort.dailyTotals(SuggestedGoal.DEFAULT_WINDOW)
        // Ventas activas solo en Semana — evita el costo de la query en Día, donde "Meta de la
        // semana" no se muestra (ver KDoc de HeroUi/CollectionReportStateBuilder.buildHero).
        // Va el MISMO `range` que alimenta `paymentsIn` arriba, para que el denominador (ventas
        // que cuentan en la ventana) y el numerador (las que tuvieron abono) miren el mismo
        // periodo; pasarle otro rango reintroduce el desajuste que el parámetro corrige.
        val sales = if (period == ReportPeriod.SEMANA) {
            salesPort.nonContadoActiveSales(range)
        } else {
            emptyList()
        }
        // Qué días del ciclo tuvieron cobro — SOLO para atenuar (que no esconder) los chips en
        // cero de la tira. Se resuelve con `paymentsGroupedByDaySince`, el puerto que ya existe
        // para justo este resumen por día, y desde el inicio REAL del ciclo (la hora de la
        // carga), así los pagos del ciclo anterior no reviven el día de la carga. Solo en Día y
        // solo cuando hay tira que pintar: Semana no la muestra y un ciclo de un día no tiene
        // nada que elegir, así que ninguno paga esta consulta.
        // `cycleStartIso` es null exactamente cuando no hay semana utilizable — y ahí tampoco hay
        // tira que atenuar (`cycleDays` trae 0 o 1 día), así que la consulta no se paga.
        val cycleStartIso = RangeCalculator.cycleRange(clock, fechaCargaInicial)?.startIso
        val dayGroups = if (period == ReportPeriod.DIA && cycleDays.size > 1 && cycleStartIso != null) {
            paymentsPort.paymentsGroupedByDaySince(cycleStartIso)
        } else {
            emptyMap()
        }

        val context = CollectionReportStateBuilder.LoadContext(
            period,
            range,
            clock,
            sort,
            selectedDay
        )
        val ports = CollectionReportStateBuilder.LoadedPorts(
            cobrador = cobrador,
            payments = payments,
            forgiveness = forgiveness,
            visits = visits,
            pending = pending,
            priorTotal = priorTotal,
            historicalTotals = historicalTotals,
            sales = sales,
            fechaCargaInicial = fechaCargaInicial,
            cycleDays = cycleDays,
            dayGroups = dayGroups
        )
        CollectionReportStateBuilder.buildContent(context, ports)
    }

    private fun applyContent(
        current: CollectionReportUiState,
        period: ReportPeriod,
        content: CollectionReportStateBuilder.LoadedContent
    ): CollectionReportUiState = current.copy(
        period = period,
        // El contenido del nuevo periodo ya está listo: recién ahora se voltea `contentPeriod`
        // (la llave del `AnimatedContent`), así el slide anima sobre datos asentados y no recibe
        // un recompose a mitad de animación (ver toggle-jank-diagnosis.md, fix 1).
        contentPeriod = period,
        loading = false,
        error = null,
        // Se limpia al cargar con éxito: si el aviso se quedara pegado, el reporte diría "sin
        // inicio de semana" encima de las cifras que acaba de reparar.
        cycleNotice = "",
        cobrador = content.cobrador,
        rangeLabel = if (period == ReportPeriod.DIA) content.range.dayLabel() else content.range.cycleLabel(),
        pendingCount = content.pending,
        hero = content.hero,
        efectivo = content.efectivo,
        transferencia = content.transferencia,
        condonado = content.condonado,
        visitas = content.visitas,
        detail = content.detail,
        dayPayments = content.dayPayments,
        condonadoRows = content.condonadoRows,
        visitRows = content.visitRows,
        cycleDays = content.cycleDays,
        selectedDay = content.selectedDay,
        selectedDayNote = content.selectedDayNote
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
     *
     * `cycleDays`/`selectedDay`/`selectedDayNote` TAMPOCO se blanquean, por el mismo criterio:
     * la tira de días es NAVEGACIÓN, no contenido del rango. Borrarla dejaría al cobrador
     * mirando un tablero en blanco sin ningún control para volver a hoy ni reintentar otro día
     * — el mismo error de "control muerto" que ya se pagó una vez en Tier 2.
     */
    private fun applyError(
        current: CollectionReportUiState,
        period: ReportPeriod
    ): CollectionReportUiState = current.copy(
        period = period,
        // El tablero (en blanco) se pinta para el periodo pedido; `contentPeriod` acompaña a
        // `period` para no dejar el `AnimatedContent` mostrando el periodo anterior bajo el banner.
        contentPeriod = period,
        loading = false,
        error = ERROR_MESSAGE,
        // Un banner de error REEMPLAZA al aviso de semana: dos avisos apilados sobre un tablero
        // en blanco no informan más, sólo compiten.
        cycleNotice = "",
        rangeLabel = "",
        pendingCount = 0,
        hero = HeroUi(),
        efectivo = TileUi(label = "Efectivo"),
        transferencia = TileUi(label = "Transferencia"),
        condonado = ChipUi(label = "Condonado"),
        visitas = ChipUi(label = "Visitas"),
        detail = DetailUi.Payments(emptyList()),
        dayPayments = emptyList(),
        condonadoRows = emptyList(),
        visitRows = emptyList()
    )

    /**
     * Estado HONESTO cuando no hay ventana que consultar (defecto D5).
     *
     * Blanquea lo que depende del rango — igual que [applyError] y por la misma razón — pero
     * NO usa `error`: no falló nada, falta un dato. La diferencia importa en pantalla: un $0.00
     * bien maquetado sobre una tabla de pagos llena se lee como una cifra real ("no cobré
     * nada"), y eso es exactamente lo que el cobrador reportó desde campo. El aviso corto de
     * [cycleNoticeFor] dice qué falta.
     *
     * `cobrador`/`cycleDays`/`selectedDay` se conservan por el mismo criterio que en
     * [applyError]: identidad y navegación no dependen del rango.
     */
    private fun applyNoCycle(
        current: CollectionReportUiState,
        period: ReportPeriod,
        start: CycleStart
    ): CollectionReportUiState = current.copy(
        period = period,
        contentPeriod = period,
        loading = false,
        error = null,
        cycleNotice = cycleNoticeFor(start),
        rangeLabel = "",
        pendingCount = 0,
        hero = HeroUi(),
        efectivo = TileUi(label = "Efectivo"),
        transferencia = TileUi(label = "Transferencia"),
        condonado = ChipUi(label = "Condonado"),
        visitas = ChipUi(label = "Visitas"),
        detail = DetailUi.Payments(emptyList()),
        dayPayments = emptyList(),
        condonadoRows = emptyList(),
        visitRows = emptyList()
    )

    /**
     * Aviso es-MX del tablero cuando no hay semana (2-4 palabras, minúsculas, sin punto final;
     * se dice "semana", nunca "ciclo"). Tres causas, tres mensajes: no es lo mismo que la
     * fuente esté caída (se está reintentando) a que el cobrador no haya iniciado su semana
     * (tiene que hacer algo) o a que la fecha guardada sea inservible.
     */
    private fun cycleNoticeFor(start: CycleStart): String = when (start) {
        is CycleStart.Unavailable -> "semana no disponible"
        is CycleStart.Missing -> "sin inicio de semana"
        // Conocida pero inservible: cae en el futuro (reloj corrido o dato sucio).
        is CycleStart.Known -> "fecha de semana inválida"
    }

    private companion object {
        const val SCREEN = "collection_report"
        const val ERROR_MESSAGE = "no se pudo cargar el reporte de cobranza"

        // Reintento del inicio de semana: 5 intentos con espera creciente (0.5s → 8s). Cubre el
        // arranque con red intermitente sin machacar Firestore ni dejar la pantalla girando.
        const val RETRY_ATTEMPTS = 5
        const val RETRY_INITIAL_MS = 500L
        const val RETRY_MAX_MS = 8_000L
    }
}
