package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.feature.collectionreport.domain.DeltaChip
import com.example.msp_app.feature.collectionreport.domain.DeltaDirection
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.Timeline
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.time.Instant

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
    val period: ReportPeriod = ReportPeriod.DIA,
    val loading: Boolean = true,
    val error: String? = null,
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
    val sheet: SheetUi? = null,
    // Listas crudas detrás de los chips agregados (Task 8): `condonado`/`visitas` de arriba
    // ya traen el total/conteo que necesita el tablero; estas dos SOLO existen para que
    // `ReportSheets` (Task 8) pueda derivar el cuerpo real de los sheets Condonado/Visitas
    // (`Forgiveness.motivo`/`CollectionVisit.nota`) sin volver a consultar los puertos — el
    // mismo criterio que ya usa `detail` para Efectivo/Transferencia/Pago (filas ya cargadas,
    // el sheet solo filtra/busca en memoria).
    val condonadoRows: List<ForgivenessRowUi> = emptyList(),
    val visitRows: List<VisitRowUi> = emptyList()
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

/** Bienestar del hero: etiqueta estática (es-MX) + su [Money] — p. ej. "Efectivo en mano". */
data class HeroWell(val label: String, val amount: Money)

/**
 * Tarjeta hero del tablero. [insight] es el sealed [Insight] del dominio (Daily/Weekly) —
 * NO una frase pre-formateada; [sparkline] es el [Timeline] del dominio (buckets + índice
 * resaltado). Formatear ambos a texto/gráfico vive en la UI (Task 6+).
 */
data class HeroUi(
    val overline: String = "",
    val delta: DeltaChip = DeltaChip("—", DeltaDirection.NONE),
    val monto: Money = Money.ZERO,
    val insight: Insight = Insight.Daily(count = 0, progressPct = 0, projection = null),
    val progress: Float = 0f,
    val goalCap: Money = Money.ZERO,
    val sparkline: Timeline = Timeline(buckets = emptyList(), highlightIndex = 0),
    val wells: List<HeroWell> = emptyList()
)

/** Tile protagonista (Efectivo / Transferencia): total cobrado + número de pagos. */
data class TileUi(val label: String, val amount: Money = Money.ZERO, val count: Int = 0)

/**
 * Chip secundario (Condonado / Visitas): SOLO uno de los dos campos de valor va poblado
 * según el chip — [amount] para Condonado, [count] para Visitas — nunca ambos a la vez.
 */
data class ChipUi(val label: String, val amount: Money? = null, val count: Int? = null)

/** Fila de un pago individual (detalle Día, orden vía [DetailSort]). */
data class PaymentRowUi(
    val id: String,
    val cliente: String,
    val ventaLabel: String,
    val paidAt: Instant,
    val amount: Money,
    val method: PaymentMethod,
    val synced: Boolean
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
 * Fila de una visita (sheet `SheetKind.VISITAS`) — 1:1 de `CollectionVisit` del dominio. Sin
 * [Money]: una visita no mueve dinero (mismo criterio que el dominio `CollectionVisit`).
 */
data class VisitRowUi(val cliente: String, val nota: String)
