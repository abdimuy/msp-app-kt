package com.example.msp_app.feature.collectionreport.ui.components

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
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.ui.DayRowUi
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi

private val SYNC_DOT_SIZE = 7.dp
private val DAY_AVATAR_SIZE = 34.dp
private val DIVIDER_HEIGHT = 1.dp

// Interlineado dentro de cada columna de la fila de pago (nombre/subline, monto/saldo) — más
// apretado que los tokens de `spacing` para que las dos líneas lean como un bloque.
private val ROW_LINE_SPACING = 3.dp

// Separación "Saldo"↔monto y dot↔"Por subir".
private val SALDO_LABEL_SPACING = 4.dp
private const val PENDING_UPLOAD_DESCRIPTION = "por subir"
private const val EMPTY_MESSAGE = "Sin datos aún"

/**
 * Bloque de detalle (mockup `.rows`): lista de pagos en Día ([DetailUi.Payments], una
 * [PaymentRow] por pago) o resumen por día en Semana ([DetailUi.Days], una [DayRow] por día
 * del ciclo) — nunca ambos a la vez, mismo contrato exhaustivo que [DetailUi]. Vacío ->
 * mensaje corto centrado en vez de una [MspCard] en blanco (carga inicial, error, o un
 * rango sin movimientos).
 *
 * `shapes.sectionCard` (18dp) — el radio real de `.rows` en el mockup, no el `shapes.tile`
 * default de [MspCard].
 */
@Composable
fun DetailList(
    detail: DetailUi,
    masked: Boolean,
    onPaymentClick: (String) -> Unit,
    onDayClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEmpty = when (detail) {
        is DetailUi.Payments -> detail.rows.isEmpty()
        is DetailUi.Days -> detail.rows.isEmpty()
    }
    MspCard(modifier = modifier.fillMaxWidth(), shape = MspTheme.shapes.sectionCard) {
        if (isEmpty) {
            EmptyDetail()
            return@MspCard
        }
        Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
            when (detail) {
                is DetailUi.Payments -> detail.rows.forEachIndexed { index, row ->
                    PaymentRow(row = row, masked = masked, onClick = { onPaymentClick(row.id) })
                    if (index != detail.rows.lastIndex) DetailDivider()
                }

                is DetailUi.Days -> detail.rows.forEachIndexed { index, row ->
                    DayRow(row = row, masked = masked, onClick = { onDayClick(index) })
                    if (index != detail.rows.lastIndex) DetailDivider()
                }
            }
        }
    }
}

@Composable
private fun EmptyDetail(modifier: Modifier = Modifier) {
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

@Composable
private fun DetailDivider(modifier: Modifier = Modifier) {
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

/**
 * Fila de un día del ciclo (mockup `.srow`): avatar de iniciales del día (ya calculadas por
 * `ReportAggregator.dailyTrend`, p. ej. "L3") + nombre del día + subline "N pagos" a la
 * izquierda; monto a la derecha. Avatar más chico ([DAY_AVATAR_SIZE], 34dp) que el de
 * [PaymentRow] (38dp default) — 1:1 `.srow .sa` vs `.prow .ava` del mockup.
 */
@Composable
private fun DayRow(
    row: DayRowUi,
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
        MspInitialsAvatar(initials = row.initials, size = DAY_AVATAR_SIZE)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.label, style = MspTheme.type.name, color = MspTheme.colors.onSurface)
            Text(
                text = "${row.count} pagos",
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
        MspMoneyText(
            amount = row.amount.amount,
            masked = masked,
            style = MspTheme.type.amountRow,
            color = MspTheme.colors.onSurface
        )
    }
}
