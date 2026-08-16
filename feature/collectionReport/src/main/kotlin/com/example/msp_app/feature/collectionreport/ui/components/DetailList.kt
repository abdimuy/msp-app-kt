package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi
import com.example.msp_app.feature.collectionreport.ui.theme.REPORT_STANDARD_DURATION_MS
import com.example.msp_app.feature.collectionreport.ui.theme.ReportStandardEasing
import com.example.msp_app.feature.collectionreport.ui.theme.rememberReportReducedMotion

private val SYNC_DOT_SIZE = 7.dp
private val DIVIDER_HEIGHT = 1.dp

/**
 * Cuántos pagos se ven con la lista de pagos COLAPSADA.
 *
 * 5, no un número redondo al azar:
 * - Un día real de cobranza trae decenas de pagos (57 el domingo 9 ago 2026 en producción).
 *   Pintados de corrido empujan hacia abajo TODO lo que va después y el tablero deja de leerse
 *   de un vistazo — que es exactamente para lo que existe esta pantalla.
 * - 5 filas enriquecidas (nombre + folio/hora + monto + saldo + pills) ocupan aproximadamente la
 *   altura que le queda a la tarjeta de detalle en un 360×800 sin comerse el resto del scroll:
 *   se ve que HAY una lista y de qué pinta son sus filas, sin que la lista sea la pantalla.
 * - Empata con lo que un ciclo NORMAL enseña en Semana ([DetailUi.Days], ~5 días): los dos
 *   periodos presentan un bloque de detalle de altura comparable y el toggle no da un salto.
 *   Ojo: "~5 días" es lo típico, NO una garantía — el ciclo no tiene tope (ver el KDoc de
 *   `dayDetailItems`), y por eso Semana se pinta perezosa en vez de acotarse con este umbral.
 *
 * El conteo total NUNCA se esconde: [DetailHeader] ya rotula "Pagos del día · N" arriba de la
 * tarjeta, y el control de expansión lleva el número real en su etiqueta.
 */
private const val COLLAPSED_PAYMENT_ROWS = 5

/**
 * Tope del índice que recibe [StaggeredEntrance] al expandir.
 *
 * Sin tope, el escalonado (`30ms + index * 60ms`) haría que el pago #52 entrara tres segundos
 * después del primero: con 57 filas la expansión se sentiría lenta y rota, no "hermosa". Con el
 * tope, los primeros 6 renglones revelados entran escalonados (máximo `30 + 5*60 = 330ms`, dentro
 * de la ventana del propio expand de [REPORT_STANDARD_DURATION_MS]) y el resto comparte ese
 * último paso. No se pierde nada: en un 360×800 solo esos primeros renglones caen dentro del
 * viewport al momento de expandir; los demás ya llegan asentados cuando el usuario baja a ellos.
 */
private const val EXPAND_STAGGER_MAX_INDEX = 5

// Interlineado dentro de cada columna de la fila de pago (nombre/subline, monto/saldo) — más
// apretado que los tokens de `spacing` para que las dos líneas lean como un bloque.
private val ROW_LINE_SPACING = 3.dp

// Separación "Saldo"↔monto y dot↔"Por subir".
private val SALDO_LABEL_SPACING = 4.dp
private const val PENDING_UPLOAD_DESCRIPTION = "por subir"
private const val EMPTY_MESSAGE = "Sin datos aún"

/**
 * Bloque de detalle de DÍA (mockup `.rows`): la lista de pagos ([DetailUi.Payments], una
 * [PaymentRow] por pago). Vacío -> mensaje corto centrado en vez de una [MspCard] en blanco
 * (carga inicial, error, o un rango sin movimientos).
 *
 * `shapes.sectionCard` (18dp) — el radio real de `.rows` en el mockup, no el `shapes.tile`
 * default de [MspCard].
 *
 * **El detalle de SEMANA ([DetailUi.Days]) NO pasa por aquí** — lo emite
 * [dayDetailItems] como ítems de la lista perezosa del tablero. Motivo en su KDoc: el
 * "resumen por día" tiene un renglón por día del ciclo y el ciclo NO tiene tope, así que
 * pintarlos todos de golpe es exactamente el defecto que se corrigió. Este composable se
 * queda con Día porque su lista ya está acotada por el colapsable de abajo.
 *
 * **Colapsable.** Con más de [COLLAPSED_PAYMENT_ROWS] pagos la tarjeta muestra los primeros y
 * añade un control que revela TODOS — no un rótulo muerto tipo "13 pagos más": el dueño
 * rechazó explícitamente informar sin revelar. Con `rows.size <= COLLAPSED_PAYMENT_ROWS` no se
 * pinta control alguno (no hay nada que revelar). El DISEÑO de cada renglón no cambia en ningún
 * caso; lo único nuevo es cuántos se pintan.
 *
 * [expanded]/[onToggleExpand] son estado IZADO (arranca colapsado en ambos call sites, vía
 * `rememberSaveable`): así sobrevive a los swaps Día↔Semana y a los cambios de configuración, y
 * el estado es dirigible desde un test sin depender de un `remember` escondido aquí adentro.
 * Ambos parámetros son OBLIGATORIOS a propósito — un default `{}` en [onToggleExpand] dejaría
 * pintar un control que no hace nada, que es justo el defecto que se está corrigiendo.
 */
@Composable
fun DetailList(
    detail: DetailUi.Payments,
    masked: Boolean,
    onPaymentClick: (String) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    MspCard(modifier = modifier.fillMaxWidth(), shape = MspTheme.shapes.sectionCard) {
        if (detail.rows.isEmpty()) {
            EmptyDetail()
            return@MspCard
        }
        Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
            PaymentRows(
                rows = detail.rows,
                masked = masked,
                expanded = expanded,
                onToggleExpand = onToggleExpand,
                onPaymentClick = onPaymentClick
            )
        }
    }
}

/**
 * Cuerpo de la lista de pagos: cabeza siempre visible ([COLLAPSED_PAYMENT_ROWS] filas) + cola
 * revelable + control. Las filas se pintan con la MISMA [PaymentRow] de siempre, sin envolverla
 * en nada que altere su layout — el colapsable decide CUÁNTAS se pintan, no CÓMO se ven.
 *
 * La cola vive dentro de un `AnimatedVisibility`, no detrás de un `if`: eso da alto + opacidad
 * animados en un solo lugar y, sobre todo, hace que reduce-motion sea un cambio ESTRUCTURAL
 * (`EnterTransition.None`/`ExitTransition.None` monta y desmonta la cola en el acto) en vez de
 * una animación de 0ms que igual dependería del reloj — mismo criterio anti-cuelgue que
 * [TabTransition]. Los renglones revelados entran con [StaggeredEntrance], acotado por
 * [EXPAND_STAGGER_MAX_INDEX] para que 57 pagos no conviertan la expansión en una espera.
 *
 * Divisores: entre filas de la cabeza, antes de cada fila de la cola, y uno más antes del
 * control — así el separador queda igual de parejo esté colapsada o expandida.
 */
@Composable
private fun PaymentRows(
    rows: List<PaymentRowUi>,
    masked: Boolean,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onPaymentClick: (String) -> Unit
) {
    val hasOverflow = rows.size > COLLAPSED_PAYMENT_ROWS
    val head = if (hasOverflow) rows.take(COLLAPSED_PAYMENT_ROWS) else rows
    val reduced = rememberReportReducedMotion()

    head.forEachIndexed { index, row ->
        PaymentRow(row = row, masked = masked, onClick = { onPaymentClick(row.id) })
        if (index != head.lastIndex) DetailDivider()
    }
    if (!hasOverflow) return

    AnimatedVisibility(
        visible = expanded,
        enter = if (reduced) {
            EnterTransition.None
        } else {
            expandVertically(
                animationSpec = tween(REPORT_STANDARD_DURATION_MS, easing = ReportStandardEasing)
            ) + fadeIn(animationSpec = tween(REPORT_STANDARD_DURATION_MS))
        },
        exit = if (reduced) {
            ExitTransition.None
        } else {
            shrinkVertically(
                animationSpec = tween(REPORT_STANDARD_DURATION_MS, easing = ReportStandardEasing)
            ) + fadeOut(animationSpec = tween(REPORT_STANDARD_DURATION_MS))
        }
    ) {
        Column {
            rows.drop(COLLAPSED_PAYMENT_ROWS).forEachIndexed { index, row ->
                DetailDivider()
                StaggeredEntrance(index = index.coerceAtMost(EXPAND_STAGGER_MAX_INDEX)) {
                    PaymentRow(row = row, masked = masked, onClick = { onPaymentClick(row.id) })
                }
            }
        }
    }
    DetailDivider()
    DetailListToggle(expanded = expanded, total = rows.size, onToggle = onToggleExpand)
}

/**
 * Mensaje de "sin datos" dentro de la tarjeta de detalle.
 *
 * `internal` (no `private`): el resumen por día (`dayDetailItems`) sirve exactamente el MISMO
 * vacío, y duplicarlo sería la vía más corta a que los dos periodos digan cosas distintas
 * cuando no hay nada que mostrar.
 */
@Composable
internal fun EmptyDetail(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(MspTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = EMPTY_MESSAGE,
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

/**
 * Hairline separador entre renglones de la tarjeta de detalle. `internal` por el mismo motivo
 * que [EmptyDetail]: el resumen por día lo pinta entre sus ítems y debe ser EL MISMO trazo.
 */
@Composable
internal fun DetailDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(DIVIDER_HEIGHT)
            .background(MspTheme.colors.outline)
    )
}

/**
 * Fila de un pago (mockup `.prow`), enriquecida (fix de dispositivo 2026-08): a la izquierda,
 * tile tintado por método de cobro ([MethodTile], reemplaza el avatar de iniciales decorativo
 * — Task 1: el nombre ya va en texto al lado, el MÉTODO sí es dato nuevo) + nombre
 * (`type.name`) y una subline con el contexto de la venta — "Folio {folio} · HH:mm" cuando la
 * venta está en local, si no solo "HH:mm" (el folio nunca se inventa, ver [PaymentRowUi]). A
 * la derecha, jerarquía de dinero: monto del pago ([MspMoneyText], enmascarable) arriba, saldo
 * restante de la venta ("Saldo $X", tabular, enmascarable) debajo cuando existe, y el chip
 * ámbar "Por subir" (texto + color, nunca color solo) si el pago aún no sube — junto con el
 * pill de método (texto), que se conserva para quien busca el nombre del método en texto.
 */
@Composable
private fun PaymentRow(
    row: PaymentRowUi,
    masked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MethodTile(method = row.method)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ROW_LINE_SPACING)
        ) {
            Text(
                text = row.cliente,
                style = MspTheme.type.name,
                color = MspTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ventaSubline(row),
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(ROW_LINE_SPACING)
        ) {
            MspMoneyText(
                amount = row.amount.amount,
                masked = masked,
                style = MspTheme.type.amountRow,
                color = MspTheme.colors.onSurface
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
            ) {
                if (!row.synced) PendingUploadChip()
                MethodPill(method = row.method)
            }
            row.saldo?.let { SaldoLine(saldo = it, masked = masked) }
        }
    }
}

/**
 * Subline de contexto de la venta: "Folio {folio} · {hora}" cuando la venta está en local
 * ([PaymentRowUi.folio] no vacío), si no solo la hora del pago. El `DOCTO_CC_ACR_ID` crudo
 * ([PaymentRowUi.ventaLabel]) NO se muestra: es un id interno sin valor para el cobrador; el
 * folio comercial sí lo es (fix de dispositivo — la fila mostraba el número en bruto).
 */
private fun ventaSubline(row: PaymentRowUi): String {
    val horaPago = AppTime.formatForDisplay(row.paidAt, AppTime.Formats.TIME_24H)
    return if (row.folio.isNotBlank()) "Folio ${row.folio} · $horaPago" else horaPago
}

/**
 * "Saldo $X" — saldo restante actual de la venta, tabular y enmascarable (privacidad).
 * `internal` (no `private`): [ReportSheets] (Task 1) reusa esta MISMA fila para la tercera
 * línea de sus filas de pago, en vez de duplicar el layout "Saldo" + [MspMoneyText].
 */
@Composable
internal fun SaldoLine(saldo: Money, masked: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SALDO_LABEL_SPACING)
    ) {
        Text(
            text = "Saldo",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
        MspMoneyText(
            amount = saldo.amount,
            masked = masked,
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

/**
 * Chip "por subir" (mockup: dot ámbar): texto + color, NUNCA color solo (accesibilidad) —
 * la `contentDescription` sigue siendo el segundo portador redundante del estado. Reemplaza
 * el dot pelón por un chip legible ahora que la fila tiene más contexto.
 *
 * `internal` (no `private`): [ReportSheets] (Task 1) reusa este MISMO chip para sus filas de
 * pago, en vez de duplicar el par dot+texto ámbar.
 */
@Composable
internal fun PendingUploadChip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.semantics { contentDescription = PENDING_UPLOAD_DESCRIPTION },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SALDO_LABEL_SPACING)
    ) {
        Box(
            modifier = Modifier
                .size(SYNC_DOT_SIZE)
                .clip(CircleShape)
                .background(MspTheme.colors.statusPartial)
        )
        Text(
            text = "Por subir",
            style = MspTheme.type.captionStrong,
            color = MspTheme.colors.statusPartial
        )
    }
}
