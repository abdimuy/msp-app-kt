package com.example.msp_app.feature.collectionreport.ui.components

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.ReportAggregator
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.DetailUi
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
 * Fila derivada de un sheet (mockup `.srow`): [leading] es un emoji/inicial suelta (o `null`
 * si el mockup no le pone avatar, p. ej. las visitas); a lo más UNO de [amount]/[text] va
 * poblado — [amount] es dinero real (enmascarable), [text] es un valor no-dinero (forma de
 * pago, folio, estatus) que NUNCA se enmascara.
 */
internal data class SheetRowUi(
    val leading: String? = null,
    val title: String,
    val subtitle: String? = null,
    val amount: Money? = null,
    val text: String? = null
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
 * OMITE (nunca se inventa un valor). Efectivo/Transferencia y "día del ciclo" solo tienen
 * desglose fila-a-fila cuando el estado YA trae la lista de pagos individual del rango
 * abierto ([DetailUi.Payments], periodo Día); en Semana ([DetailUi.Days]) el estado no
 * conserva pagos individuales (Task 4/5 solo agregan por día), así que esas dos sheets
 * muestran el total/conteo sin filas — un vacío honesto, no una lista fabricada.
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
    val title = if (state.period == ReportPeriod.DIA) "Resumen del día" else "Resumen del ciclo"
    val subtitle = "${state.rangeLabel} · meta ${moneyText(hero.goalCap, state.masked)}"
    val rows = buildList {
        add(
            SheetRowUi(
                leading = "📊",
                title = "Cobrado",
                subtitle = "${hero.insight.progressPct}% de la meta",
                amount = hero.monto
            )
        )
        val projection = (hero.insight as? Insight.Daily)?.projection
        add(
            SheetRowUi(
                leading = "⚡",
                title = "Ritmo",
                subtitle = "proyección a cierre",
                amount = projection,
                text = if (projection == null) "—" else null
            )
        )
        ReportAggregator.mejorMomento(hero.sparkline, state.period)?.let { best ->
            add(
                SheetRowUi(
                    leading = "🕘",
                    title = "Mejor momento",
                    subtitle = best.label,
                    amount = best.total
                )
            )
        }
        if (hero.goalCap.amount.signum() > 0) {
            val falta = hero.goalCap - hero.monto
            add(
                SheetRowUi(
                    leading = "🎯",
                    title = "Falta para meta",
                    amount = if (falta.amount.signum() > 0) falta else Money.ZERO
                )
            )
        }
    }
    return SheetContentUi(title, subtitle, rows)
}

/**
 * Cuerpo de Efectivo/Transferencia: el duo ya tiene el total/conteo ([tile]); las filas
 * individuales solo existen cuando `state.detail` es [DetailUi.Payments] (periodo Día) — ver
 * KDoc de [deriveSheetContent].
 */
private fun methodSheet(
    state: CollectionReportUiState,
    method: PaymentMethod,
    label: String,
    tile: TileUi
): SheetContentUi {
    val subtitle = "${moneyText(tile.amount, state.masked)} · ${tile.count} pagos"
    val rows = (state.detail as? DetailUi.Payments)?.rows
        ?.filter { it.method == method }
        ?.map { row ->
            SheetRowUi(
                leading = clienteInitials(row.cliente),
                title = row.cliente,
                subtitle = "${AppTime.formatForDisplay(
                    row.paidAt,
                    AppTime.Formats.TIME_24H
                )} · ${row.ventaLabel}",
                amount = row.amount
            )
        }
        .orEmpty()
    return SheetContentUi(label, subtitle, rows)
}

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
            amount = row.amount
        )
    }
    return SheetContentUi("Condonado", subtitle, rows)
}

private fun visitasSheet(state: CollectionReportUiState): SheetContentUi {
    val subtitle = "${state.visitas.count ?: 0} visitas"
    val rows = state.visitRows.map { row -> SheetRowUi(title = row.cliente, subtitle = row.nota) }
    return SheetContentUi("Visitas", subtitle, rows)
}

/**
 * Cuerpo del día del ciclo (Semana): [argument] es el índice de `state.detail.rows`
 * ([DetailUi.Days]) — mismo índice que `CollectionReportScreen.onDiaCicloClick` ya usa para
 * abrir este sheet (barra de la sparkline o fila del resumen). Sin desglose por pago (el
 * estado no lo conserva por día); título/subtítulo caen a un texto neutro si el índice no
 * resuelve (defensivo, no debería ocurrir en uso normal).
 */
private fun diaCicloSheet(state: CollectionReportUiState, argument: String?): SheetContentUi {
    val day = (state.detail as? DetailUi.Days)?.rows?.getOrNull(argument?.toIntOrNull() ?: -1)
    val title = day?.label ?: "Día"
    val subtitle = day?.let {
        "${moneyText(
            it.amount,
            state.masked
        )} · ${it.count} pagos"
    }.orEmpty()
    return SheetContentUi(title, subtitle, emptyList())
}

/**
 * Detalle de un pago: [argument] es `PaymentRowUi.id`, buscado en `state.detail.rows`
 * (periodo Día, el único que lista pagos individuales). Folio del mockup ("A-10482") se omite
 * — no hay un campo de folio en `PaymentRowUi`/el dominio de pagos del piloto (parked del
 * brief: "completo > inventado").
 */
private fun pagoSheet(state: CollectionReportUiState, argument: String?): SheetContentUi {
    val payment = (state.detail as? DetailUi.Payments)?.rows?.firstOrNull { it.id == argument }
    val subtitle = payment
        ?.let { "${it.cliente} · ${AppTime.formatForDisplay(it.paidAt, AppTime.Formats.TIME_24H)}" }
        .orEmpty()
    val rows = payment?.let {
        listOf(
            SheetRowUi(title = "Importe", amount = it.amount),
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

/** "María López Hernández" -> "ML" — mismo cálculo que `DetailList.clienteInitials`. */
private fun clienteInitials(nombre: String): String {
    val palabras = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        palabras.isEmpty() -> ""
        palabras.size == 1 -> palabras[0].take(1).uppercase()
        else -> "${palabras[0].first()}${palabras[1].first()}".uppercase()
    }
}
