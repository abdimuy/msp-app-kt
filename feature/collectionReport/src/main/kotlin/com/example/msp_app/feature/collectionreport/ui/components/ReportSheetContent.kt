package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Gauge
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Target
import com.composables.icons.lucide.Wallet
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.ReportAggregator
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi
import com.example.msp_app.feature.collectionreport.ui.SheetKind
import com.example.msp_app.feature.collectionreport.ui.SheetUi
import com.example.msp_app.feature.collectionreport.ui.TileUi

/**
 * Derivación PURA (sin Compose) del cuerpo de cada [SheetKind] a partir de
 * [CollectionReportUiState] — separada de `ReportSheets.kt` (el `ModalBottomSheet`/render)
 * solo para no cruzar el umbral `TooManyFunctions` de detekt en un solo archivo; mismo
 * criterio que separó `CollectionReportStateBuilder` del `CollectionReportViewModel`.
 */

/**
 * Fila derivada de un sheet (mockup `.srow`): a lo más UNO de [amount]/[text] va poblado —
 * [amount] es dinero real (enmascarable), [text] es un valor no-dinero (forma de pago, folio,
 * estatus) que NUNCA se enmascara. Tres formas de leading, mutuamente excluyentes (Task 1/2,
 * fix de dispositivo — reemplazo de los emojis/iniciales sueltas):
 * - [method] no nulo -> fila de PAGO: tile tintado por método ([MethodTile]) + [saldo]/
 *   [synced] disponibles para la tercera línea/chip "Por subir" (mismo criterio que
 *   `DetailList.PaymentRow`).
 * - [avatar] true con [leading] no nulo -> avatar de iniciales de cliente (condonación), sin
 *   tile de método (no es un pago con forma de cobro propia en este contexto).
 * - [leadingIcon] no nulo -> glifo Lucide SUELTO del hero (Cobrado/Ritmo/Mejor momento/Falta
 *   para meta), sin fondo tintado — reemplaza los emojis 📊/⚡/🕘/🎯.
 */
internal data class SheetRowUi(
    val leading: String? = null,
    val leadingIcon: ImageVector? = null,
    val title: String,
    val subtitle: String? = null,
    val amount: Money? = null,
    val text: String? = null,
    val avatar: Boolean = false,
    // Fila de pago (Task 1): método de cobro (tile tintado), saldo restante de la venta
    // (tercera línea muted, "Saldo $X") y si ya subió (chip ámbar "Por subir" cuando false).
    // `synced` por defecto `true` -> filas que NO son de pago (hero/condonación/visitas) nunca
    // disparan el chip aunque alguien olvide poblarlo.
    val method: PaymentMethod? = null,
    val saldo: Money? = null,
    val synced: Boolean = true
)

/** Cuerpo derivado de un [SheetUi]: título + subtítulo (mockup `h3`/`.ssub`) + [rows]. */
internal data class SheetContentUi(
    val title: String,
    val subtitle: String,
    val rows: List<SheetRowUi>
)

/**
 * Deriva el cuerpo de CUALQUIER sheet a partir de [SheetUi.kind] + el resto de
 * [CollectionReportUiState] — el estado (Task 5) solo marca CUÁL sheet está abierto, esta
 * función arma su contenido en memoria, sin I/O ni recarga (mismo criterio que
 * `CollectionReportStateBuilder.sortedPaymentRows`).
 *
 * **Completo > inventado (parked del brief, Task 8):** el mockup usa datos de ejemplo fijos
 * (folio "A-10482", "ref 4821", "mejor hora $4,200") que NO tienen un campo de dominio real
 * detrás. Cuando el campo real existe se usa tal cual (`Forgiveness.motivo`,
 * `CollectionVisit.nota`, `ReportAggregator.mejorMomento`); cuando no existe, la fila se
 * OMITE (nunca se inventa un valor). Efectivo/Transferencia y "día del ciclo" tienen desglose
 * fila-a-fila en AMBOS periodos ([allPayments], Task 3): Día lo lee de [DetailUi.Payments]
 * directo, Semana ([DetailUi.Days]) lo aplana de `state.dayPayments` — antes esa segunda
 * fuente no se consultaba y el sheet salía vacío en Semana aunque el estado YA trajera los
 * pagos individuales cargados.
 */
internal fun deriveSheetContent(sheet: SheetUi, state: CollectionReportUiState): SheetContentUi =
    when (sheet.kind) {
        SheetKind.HERO -> heroSheet(state)
        SheetKind.EFECTIVO -> methodSheet(state, PaymentMethod.EFECTIVO, "Efectivo", state.efectivo)
        SheetKind.TRANSFERENCIA ->
            methodSheet(state, PaymentMethod.TRANSFERENCIA, "Transferencia", state.transferencia)
        SheetKind.CONDONADO -> condonadoSheet(state)
        SheetKind.VISITAS -> visitasSheet(state)
        SheetKind.DIA_CICLO -> diaCicloSheet(state, sheet.argument)
        SheetKind.PAGO -> pagoSheet(state, sheet.argument)
    }

private fun heroSheet(state: CollectionReportUiState): SheetContentUi {
    val hero = state.hero
    val title = if (state.period == ReportPeriod.DIA) "Resumen del día" else "Resumen de la semana"
    val subtitle = state.rangeLabel
    val rows = buildList {
        add(
            SheetRowUi(
                leadingIcon = Lucide.Wallet,
                title = "Cobrado",
                amount = hero.monto
            )
        )
        val projection = (hero.insight as? Insight.Daily)?.projection
        add(
            SheetRowUi(
                leadingIcon = Lucide.Gauge,
                title = "Ritmo",
                subtitle = "proyección a cierre",
                amount = projection,
                text = if (projection == null) "—" else null
            )
        )
        ReportAggregator.mejorMomento(hero.sparkline, state.period)?.let { best ->
            add(
                SheetRowUi(
                    leadingIcon = Lucide.Clock,
                    title = "Mejor momento",
                    subtitle = best.label,
                    amount = best.total
                )
            )
        }
        // "Meta de la semana" (Step C): reemplaza la fila "Falta para meta" (mediana retirada,
        // ver KDoc de HeroUi) por las dos métricas reales — solo hay algo que mostrar en SEMANA
        // (mismo criterio que la tarjeta `MetaCard`, que tampoco se monta en DÍA).
        if (state.period == ReportPeriod.SEMANA) {
            add(
                SheetRowUi(
                    leadingIcon = Lucide.Target,
                    title = "Porcentaje cobro",
                    text = "${"%.0f".format(hero.porcentajeCobro)}% · meta " +
                        "${CobranzaPorcentaje.META_COBRO_PCT}%"
                )
            )
            add(
                SheetRowUi(
                    leadingIcon = Lucide.Target,
                    title = "Porcentaje cuentas",
                    text = "${"%.0f".format(hero.porcentajeCuentas)}% · " +
                        "${hero.clientesPagaron} de ${hero.clientesTotal}"
                )
            )
        }
    }
    return SheetContentUi(title, subtitle, rows)
}

/**
 * Cuerpo de Efectivo/Transferencia: el duo ya tiene el total/conteo ([tile]); las filas
 * individuales salen de [allPayments] — Día ([DetailUi.Payments]) o Semana aplanando
 * `state.dayPayments` (fix de dispositivo, Task 3: antes esta sheet solo leía
 * `state.detail as? DetailUi.Payments`, que en Semana ES `DetailUi.Days` -> `null` ->
 * `orEmpty()`, así que el sheet salía SIEMPRE vacío en ese periodo aunque hubiera pagos del
 * método cargados). Cada fila es un [paymentSheetRow] (Task 1: tile de método + folio/hora +
 * saldo + chip "Por subir").
 */
private fun methodSheet(
    state: CollectionReportUiState,
    method: PaymentMethod,
    label: String,
    tile: TileUi
): SheetContentUi {
    val subtitle = "${moneyText(tile.amount, state.masked)} · ${tile.count} pagos"
    val rows = state.allPayments()
        .filter { it.method == method }
        .map(::paymentSheetRow)
    return SheetContentUi(label, subtitle, rows)
}

/**
 * Todos los pagos individuales cargados en el estado, sin importar el periodo (fix de
 * dispositivo, Task 3): Día los trae directo en `detail` ([DetailUi.Payments]); Semana
 * ([DetailUi.Days]) no los conserva ahí, así que se aplana `state.dayPayments` — MISMA fuente
 * que ya usa `diaCicloSheet` y `CollectionReportFormatter.addPaymentsBlock` para no volver a
 * consultar los puertos. Reusada por [methodSheet] y [pagoSheet].
 */
private fun CollectionReportUiState.allPayments(): List<PaymentRowUi> =
    (detail as? DetailUi.Payments)?.rows ?: dayPayments.flatten()

/**
 * Fix round 1 (Important 2, honestidad): [ForgivenessRowUi.motivo] llega vacío en producción
 * — `RoomPaymentsAdapter.toForgiveness` lo documenta y lo audita: el schema v27 de `Payment`
 * (`:core:database`) NO tiene columna de razón de condonación, y el backend Go (msp-api,
 * `internal/cobranza/domain/saldo.go`/`venta.go`) tampoco modela una — la condonación (forma
 * 137026) es solo un monto, sin campo de texto libre en ningún punto del pipeline hoy. `.
 * ifBlank { null }` — si algún día SÍ llega un motivo real (columna nueva, enriquecimiento),
 * esta fila lo muestra tal cual; mientras tanto, blank -> sin línea de subtítulo, NUNCA un
 * placeholder inventado. El golden de este sheet usa `motivo = ""` (fixture fiel a
 * producción, no una muestra bonita) — ver `MockupFixtures.condonadoRows`.
 */
private fun condonadoSheet(state: CollectionReportUiState): SheetContentUi {
    val subtitle = moneyText(state.condonado.amount ?: Money.ZERO, state.masked)
    val rows = state.condonadoRows.map { row ->
        SheetRowUi(
            leading = clienteInitials(row.cliente),
            title = row.cliente,
            subtitle = row.motivo.ifBlank { null },
            amount = row.amount,
            avatar = true
        )
    }
    return SheetContentUi("Condonado", subtitle, rows)
}

/**
 * Cuerpo del día del ciclo (Semana): [argument] es el índice de `state.detail.rows`
 * ([DetailUi.Days]) — mismo índice que `CollectionReportScreen.onDiaCicloClick` ya usa para
 * abrir este sheet (barra de la sparkline o fila del resumen). Lista TODOS los pagos
 * individuales de ese día desde `state.dayPayments` (alineado 1:1 por índice con las filas del
 * resumen — ver `CollectionReportStateBuilder.buildDayPayments` / `ReportAggregator.
 * paymentsByDay`), cada uno como un [paymentSheetRow] (mismo estilo que Efectivo/
 * Transferencia). Un día sin pagos -> lista vacía (el `SheetBody` pinta su estado vacío
 * honesto). Título/subtítulo caen a un texto neutro si el índice no resuelve (defensivo, no
 * debería ocurrir en uso normal).
 */
private fun diaCicloSheet(state: CollectionReportUiState, argument: String?): SheetContentUi {
    val index = argument?.toIntOrNull() ?: -1
    val day = (state.detail as? DetailUi.Days)?.rows?.getOrNull(index)
    val title = day?.label ?: "Día"
    val subtitle = day?.let {
        "${moneyText(
            it.amount,
            state.masked
        )} · ${it.count} pagos"
    }.orEmpty()
    val rows = state.dayPayments.getOrNull(index).orEmpty().map(::paymentSheetRow)
    return SheetContentUi(title, subtitle, rows)
}

/**
 * Detalle de un pago: [argument] es `PaymentRowUi.id`, buscado en [allPayments] (Día vía
 * `detail.rows`, Semana vía `dayPayments` aplanado — fix de dispositivo, Task 3: antes solo
 * buscaba en `state.detail.rows`, que en Semana es `DetailUi.Days` y nunca resolvía nada). La
 * primera fila es un [paymentSheetRow] completo (Task 1: tile de método + "Folio {folio} ·
 * HH:mm" + saldo + chip "Por subir" si no sincronizado); las siguientes conservan el desglose
 * textual que ya traía el sheet (Forma/Venta/Estado) para quien busca esos valores como texto
 * plano.
 */
private fun pagoSheet(state: CollectionReportUiState, argument: String?): SheetContentUi {
    val payment = state.allPayments().firstOrNull { it.id == argument }
    val subtitle = payment
        ?.let { "${it.cliente} · ${AppTime.formatForDisplay(it.paidAt, AppTime.Formats.TIME_24H)}" }
        .orEmpty()
    val rows = payment?.let {
        listOf(
            paymentSheetRow(it),
            SheetRowUi(title = "Forma", text = it.method.sheetLabel()),
            SheetRowUi(title = "Venta", text = it.ventaLabel),
            SheetRowUi(title = "Estado", text = if (it.synced) "Sincronizado" else "Por subir")
        )
    }.orEmpty()
    return SheetContentUi("Detalle de pago", subtitle, rows)
}

private fun moneyText(amount: Money, masked: Boolean): String =
    if (masked) MASKED_MONEY else formatMoneyMxn(amount.amount)

private fun PaymentMethod.sheetLabel(): String = when (this) {
    PaymentMethod.EFECTIVO -> "Efectivo"
    PaymentMethod.TRANSFERENCIA -> "Transfer."
    PaymentMethod.CHEQUE -> "Cheque"
    PaymentMethod.CONDONACION -> "Condonado"
    PaymentMethod.OTRO -> "Otro"
}
