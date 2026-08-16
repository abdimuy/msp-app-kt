package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.feature.collectionreport.domain.DeltaChip
import com.example.msp_app.feature.collectionreport.domain.DeltaDirection
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.Timeline
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.time.Instant
import java.time.LocalDate

/**
 * Estado observable único del reporte de cobranza (`StateFlow`), consumido por la UI
 * (Tasks 6-9) vía [CollectionReportViewModel]. Contrato loading-content-error PLANO (no
 * sealed): [loading] es `true` mientras se resuelve una carga (inicial o por cambio de
 * [ReportPeriod]); [error] no-nulo cuando el último intento de carga falló (mensaje es-MX
 * listo para UI). El resto de los campos SIEMPRE conservan el último contenido cargado con
 * éxito (o los defaults vacíos si aún no cargó nada) — así la UI puede mostrar un banner de
 * error sin perder la última cifra buena en pantalla.
 *
 * **Todo dinero es [Money], NUNCA `Double` ni `String` pre-formateada** (ver
 * `docs/superpowers/plans/DISPATCH-CONVENTIONS.md`): formatear a pesos (`formatMoneyMxn`,
 * `:core:designsystem`) es responsabilidad de la UI, no de este estado ni del ViewModel
 * que lo produce — misma frontera de capas que ya defendió `ReportAggregator` (Task 3).
 */
data class CollectionReportUiState(
    // [period] = periodo SELECCIONADO (resalta el chip Día/Semana al instante al tocar, para
    // respuesta inmediata). [contentPeriod] = periodo cuyos datos están realmente en pantalla;
    // sólo se voltea en `applyContent` cuando la carga del nuevo periodo ya resolvió, y es el
    // que llavea el `AnimatedContent`/`TabTransition`. Separarlos evita que el slide de 300ms
    // arranque sobre datos viejos y reciba un recompose a mitad de animación (jank del toggle).
    val period: ReportPeriod = ReportPeriod.DIA,
    val contentPeriod: ReportPeriod = ReportPeriod.DIA,
    val loading: Boolean = true,
    val error: String? = null,
    // Aviso corto es-MX cuando NO hay semana que reportar y el tablero se sirve en blanco a
    // propósito (`CollectionReportViewModel.applyNoCycle`): "semana no disponible" / "sin inicio
    // de semana" / "fecha de semana inválida". Vacío = hay ventana y las cifras son reales.
    //
    // Es un campo APARTE de [error] porque no son lo mismo: `error` dice "falló la carga",
    // esto dice "falta el dato con el que se calcula la ventana". Antes esa ausencia no se
    // modelaba en ningún lado — el rango caía al día de hoy y el cobrador veía $0.00 en la
    // semana con la tabla de pagos llena, sin una sola pista de por qué (defecto D5).
    val cycleNotice: String = "",
    val cobrador: String = "",
    // "viernes 7 ago 2026" (Día) / "semana · lun 3 – vie 7 ago · 5 días" (Semana).
    val rangeLabel: String = "",
    val pendingCount: Int = 0,
    val masked: Boolean = false,
    val darkTheme: Boolean = false,
    val sort: DetailSort = DetailSort.HORA,
    val hero: HeroUi = HeroUi(),
    val efectivo: TileUi = TileUi(label = "Efectivo"),
    val transferencia: TileUi = TileUi(label = "Transferencia"),
    val condonado: ChipUi = ChipUi(label = "Condonado"),
    val visitas: ChipUi = ChipUi(label = "Visitas"),
    val detail: DetailUi = DetailUi.Payments(emptyList()),
    // Pagos individuales por día del ciclo (Semana), alineados 1:1 por índice con
    // `(detail as DetailUi.Days).rows` (ver `ReportAggregator.paymentsByDay`): la lista en el
    // índice i son los pagos del día i, en orden cronológico. Vacío en Día (el detalle ya ES
    // la lista de pagos del día). SOLO existe para que el sheet `SheetKind.DIA_CICLO` liste los
    // pagos individuales del día tocado sin re-consultar los puertos — mismo criterio que
    // `condonadoRows`/`visitRows` alimentan sus sheets desde datos ya cargados.
    val dayPayments: List<List<PaymentRowUi>> = emptyList(),
    val sheet: SheetUi? = null,
    // Listas crudas detrás de los chips agregados (Task 8): `condonado`/`visitas` de arriba
    // ya traen el total/conteo que necesita el tablero; estas dos SOLO existen para que
    // `ReportSheets` (Task 8) pueda derivar el cuerpo real de los sheets Condonado/Visitas
    // (`Forgiveness.motivo`/`CollectionVisit.nota`) sin volver a consultar los puertos — el
    // mismo criterio que ya usa `detail` para Efectivo/Transferencia/Pago (filas ya cargadas,
    // el sheet solo filtra/busca en memoria).
    val condonadoRows: List<ForgivenessRowUi> = emptyList(),
    val visitRows: List<VisitRowUi> = emptyList(),
    // Tira de días del ciclo (SOLO en Día): de la carga de ruta a hoy, inclusive. Vacía en
    // Semana y también en Día cuando no hay nada que elegir (ciclo de un solo día / cobrador sin
    // `FECHA_CARGA_INICIAL`) — ver `CollectionReportDayStripBuilder.chips`. Que arranque vacía es
    // lo que mantiene idéntica la pantalla de siempre para quien no tiene ciclo.
    val cycleDays: List<DayChipUi> = emptyList(),
    // Día realmente cargado en Día (`null` en Semana, y en Día hasta la primera carga). NO es la
    // fecha del sistema: es el día que eligió el cobrador, y es el que manda sobre el total, la
    // lista de pagos y las tres acciones de salida (Compartir/Imprimir/PDF leen este estado).
    val selectedDay: LocalDate? = null,
    // "desde las 7:33 p.m. · inicio de semana" cuando el día mostrado es el de la carga de ruta;
    // vacía el resto de los días. Ver `CollectionReportDayStripBuilder.startNote`.
    val selectedDayNote: String = "",
    // Flujo de impresión térmica (P2): null cuando el bottom sheet de impresión está cerrado.
    // Es un sheet APARTE de [sheet] (los sheets de detalle del tablero) — mismo patrón
    // (`ModalBottomSheet` manejado por estado), otra responsabilidad.
    val printSheet: PrintSheetUi? = null
) {

    /**
     * ¿El día mostrado (Día) cerró sin un solo cobro? Derivado de [detail], no un campo aparte:
     * un booleano duplicado podría desincronizarse de la lista que la pantalla realmente pinta,
     * y entonces el tablero diría "Sin cobros" con filas debajo (o al revés). En Semana es
     * siempre `false` — el detalle es el resumen por día, no una lista de pagos.
     *
     * Con [error] presente es `false` aunque el detalle esté vacío: ahí la lista está en blanco
     * porque la carga FALLÓ (ver `CollectionReportViewModel.applyError`), no porque el cobrador
     * no haya cobrado. Decir "Sin cobros" sobre un banner de error sería exactamente la mentira
     * que el estado vacío honesto viene a evitar.
     */
    val selectedDayEmpty: Boolean
        get() = error == null && detail is DetailUi.Payments && detail.rows.isEmpty()
}

/**
 * Un chip de la tira de días del ciclo (periodo Día). Semántico, no pre-formateado: el nombre
 * corto del día y el número los arma la propia tira
 * ([com.example.msp_app.feature.collectionreport.ui.components.DayStrip]) desde [date] en
 * es-MX — misma frontera que ya defiende [HeroUi] con `Money`/`Insight` (formatear es de la UI).
 *
 * [isToday] e [isSelected] son estados DISTINTOS y pueden darse por separado: al poder ver días
 * pasados aparece el caso "estoy viendo el miércoles y hoy sigue siendo jueves", donde el chip de
 * hoy debe seguir marcado aunque no sea el que se está mirando. Por eso no se colapsan en un
 * solo enum ni comparten color — ver `dayChipPalette` en `DayStrip.kt`.
 *
 * [hasCollections] `false` = día sin un solo cobro: se pinta ATENUADO, nunca ausente (decisión
 * de transparencia del dueño, ver [CollectionReportDayStripBuilder.chips]).
 */
data class DayChipUi(
    val date: LocalDate,
    val isToday: Boolean,
    val isSelected: Boolean,
    val hasCollections: Boolean
)

/**
 * Fase del bottom sheet de impresión (P2). El sheet es SIEMPRE la vía para imprimir, así la
 * opción "Cambiar impresora" está disponible en todo momento (requisito clave del usuario,
 * ausente en kollect), no solo la primera vez.
 *
 * - [PRINTING] preparando/enviando a la impresora (recordada por defecto, auto).
 * - [SELECTING] eligiendo impresora — el picker Bluetooth (`PrinterPort.listPairedPrinters`);
 *   se abre solo si no hay recordada, o cuando el usuario toca "Cambiar impresora".
 * - [SUCCESS] impreso; ofrece "Imprimir de nuevo" y "Cambiar impresora".
 * - [ERROR] falló con un [PrintError] tipado -> mensaje es-MX + "Reintentar" + "Cambiar impresora".
 */
enum class PrintPhase { PRINTING, SELECTING, SUCCESS, ERROR }

/**
 * Estado del bottom sheet de impresión: la [phase], la impresora [target] resuelta/elegida (o
 * null mientras se resuelve o si no hay ninguna), la lista de impresoras emparejadas
 * [printers] para el picker, y un [message] es-MX en [PrintPhase.ERROR].
 */
data class PrintSheetUi(
    val phase: PrintPhase,
    val target: PrinterDevice? = null,
    val printers: List<PrinterDevice> = emptyList(),
    val message: String? = null
)

/** Orden del detalle Día: por hora del pago o por nombre del cliente. Semana lo ignora. */
enum class DetailSort { HORA, NOMBRE }

/** Qué tarjeta abrió el sheet — ver el mockup `docs/design/reporte-cobranza-mockup.html`. */
enum class SheetKind { HERO, EFECTIVO, TRANSFERENCIA, CONDONADO, VISITAS, DIA_CICLO, PAGO }

/**
 * Sheet abierto: [kind] + un [argument] opcional (p. ej. el id de [PaymentRowUi] para
 * [SheetKind.PAGO], o la posición del día tocado en `detail.rows` para
 * [SheetKind.DIA_CICLO]). El contenido detallado del sheet lo arma la UI (Task 6+) a partir
 * de [kind]/[argument] y el resto de [CollectionReportUiState] — este estado solo marca
 * CUÁL está abierto, no duplica su contenido.
 */
data class SheetUi(val kind: SheetKind, val argument: String? = null)

/**
 * Tarjeta hero del tablero. [insight] es el sealed [Insight] del dominio (Daily/Weekly) —
 * NO una frase pre-formateada; [sparkline] es el [Timeline] del dominio (buckets + índice
 * resaltado). Formatear ambos a texto/gráfico vive en la UI (Task 6+).
 *
 * **Meta de la semana (reemplaza la meta de mediana):** [porcentajeCobro] (ponderado) y
 * [porcentajeCuentas] (cobertura) son las dos métricas REALES de cobranza calculadas offline
 * desde Room (`CobranzaPorcentaje`, puerto fiel del backend Go
 * `internal/rutas/domain`/`internal/rutas/app` de `msp-api`) — reemplazan la meta sugerida por
 * mediana (`SuggestedGoal`, retirada) y los dos wells "Efectivo en mano"/"Ticket prom."
 * (retirados). Solo se calculan en [com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod.SEMANA]
 * (ver `CollectionReportStateBuilder`); en DÍA quedan en `0f`/`0` y la tarjeta "Meta de la
 * semana" (`MetaCard`) no se monta. [clientesPagaron]/[clientesTotal] alimentan el subtítulo
 * "N de M clientes" de la cobertura (en rigor cuentan VENTAS activas, no clientes únicos —
 * mismo criterio que `CoberturaNum`/`CoberturaDen` en el Go).
 */
data class HeroUi(
    val overline: String = "",
    val delta: DeltaChip = DeltaChip("—", DeltaDirection.NONE),
    val monto: Money = Money.ZERO,
    val insight: Insight = Insight.Daily(count = 0, progressPct = 0, projection = null),
    val sparkline: Timeline = Timeline(buckets = emptyList(), highlightIndex = 0),
    val porcentajeCobro: Float = 0f,
    val porcentajeCuentas: Float = 0f,
    val clientesPagaron: Int = 0,
    val clientesTotal: Int = 0
)

/** Tile protagonista (Efectivo / Transferencia): total cobrado + número de pagos. */
data class TileUi(val label: String, val amount: Money = Money.ZERO, val count: Int = 0)

/**
 * Chip secundario (Condonado / Visitas): SOLO uno de los dos campos de valor va poblado
 * según el chip — [amount] para Condonado, [count] para Visitas — nunca ambos a la vez.
 */
data class ChipUi(val label: String, val amount: Money? = null, val count: Int? = null)

/**
 * Fila de un pago individual (detalle Día, orden vía [DetailSort]).
 *
 * [folio] y [saldo] enriquecen la fila con la venta asociada (join a `sales` en el borde de
 * datos, ver `CollectionPayment`): [folio] es el folio comercial ("A-10482", vacío si la venta
 * ya no está en local) y [saldo] el saldo restante actual de esa venta (`null` si no está en
 * local). Se muestran como contexto secundario de la fila, nunca inventados.
 */
data class PaymentRowUi(
    val id: String,
    val cliente: String,
    val ventaLabel: String,
    val paidAt: Instant,
    val amount: Money,
    val method: PaymentMethod,
    val synced: Boolean,
    val folio: String = "",
    val saldo: Money? = null
)

/** Fila de un día del ciclo (detalle Semana) — mapeo 1:1 de `ReportAggregator.DayTrend`. */
data class DayRowUi(
    val label: String,
    val amount: Money,
    val count: Int,
    val initials: String,
    val isToday: Boolean
)

/** Detalle del tablero: lista de pagos (Día) o resumen por día (Semana) — nunca ambos. */
sealed interface DetailUi {
    data class Payments(val rows: List<PaymentRowUi>) : DetailUi
    data class Days(val rows: List<DayRowUi>) : DetailUi
}

/**
 * Fila de una condonación (sheet `SheetKind.CONDONADO`) — 1:1 de `Forgiveness` del dominio.
 * [motivo] llega vacío en producción hoy (sin fuente real en v27 ni en el backend Go — ver
 * KDoc de `Forgiveness.motivo`); el sheet omite la línea de subtítulo cuando está vacío, en
 * vez de mostrar un motivo fabricado.
 */
data class ForgivenessRowUi(val cliente: String, val motivo: String, val amount: Money)

/**
 * Fila de una visita (sheet `SheetKind.VISITAS`, ticket impreso) — 1:1 de `CollectionVisit`
 * del dominio. Sin [Money]: una visita no mueve dinero (mismo criterio que el dominio
 * `CollectionVisit`).
 *
 * [tipo] es el motivo/resultado elegido al capturar la visita (`CollectionVisit.tipo`, p. ej.
 * "No se encontraba") — Task 2: antes se perdía al mapear a esta fila, ahora viaja hasta el
 * sheet y el ticket impreso. [visitedAt] viaja también (antes se descartaba en
 * `CollectionReportStateBuilder`) porque el ticket impreso necesita el prefijo hora/fecha por
 * visita, el mismo lenguaje visual que [PaymentRowUi.paidAt] en las filas de pago.
 */
data class VisitRowUi(
    val cliente: String,
    val nota: String,
    val tipo: String,
    val visitedAt: Instant
)
