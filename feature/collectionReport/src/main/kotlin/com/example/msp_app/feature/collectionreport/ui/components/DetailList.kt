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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import com.example.msp_app.feature.collectionreport.ui.DayRowUi
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.PaymentRowUi

private val SYNC_DOT_SIZE = 7.dp
private val DAY_AVATAR_SIZE = 34.dp
private val METHOD_PILL_VERTICAL_PADDING = 2.dp
private val DIVIDER_HEIGHT = 1.dp
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
 * Fila de un pago (mockup `.prow`): avatar de iniciales del cliente ([clienteInitials] — el
 * cálculo nombre -> iniciales es del piloto, [MspInitialsAvatar] recibe iniciales YA
 * calculadas) + nombre (`type.name`) / subline "HH:mm · venta" (`type.subtitle`) a la
 * izquierda; monto ([MspMoneyText], enmascarable) + method pill + dot ámbar "por subir"
 * (si `!synced`) a la derecha.
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
        val horaPago = AppTime.formatForDisplay(row.paidAt, AppTime.Formats.TIME_24H)
        MspInitialsAvatar(initials = clienteInitials(row.cliente))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.cliente, style = MspTheme.type.name, color = MspTheme.colors.onSurface)
            Text(
                text = "$horaPago · ${row.ventaLabel}",
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
        Column(horizontalAlignment = Alignment.End) {
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
                MethodPill(method = row.method)
                // Segundo portador de la "por subir" (no solo color): contentDescription
                // explícita — no hay texto visible junto al dot en el mockup para este
                // estado (parked, no está en el mockup estático), así que el canal
                // auditivo/semántico lo carga íntegro el `semantics`.
                if (!row.synced) {
                    Box(
                        modifier = Modifier
                            .size(SYNC_DOT_SIZE)
                            .clip(CircleShape)
                            .background(MspTheme.colors.statusPartial)
                            .semantics { contentDescription = PENDING_UPLOAD_DESCRIPTION }
                    )
                }
            }
        }
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

/**
 * Pill de método de cobro (mockup `.m`): color + texto, NUNCA solo color — "Efectivo" en
 * verde `statusPaid`/`statusPaidTint`, "Transfer." en `brand`/`brandTint` (los únicos dos
 * que trae el mockup). Cheque/Otro no están en el mockup (parked, `PaymentMethod` los
 * clasifica aparte, ver su KDoc) pero SÍ pueden llegar del dominio, así que tienen un
 * matiz propio en vez de dejar el `when` no exhaustivo; Condonado es puramente defensivo —
 * la guarda anti-fuga de `ReportAggregator` ya excluye ese método de la lista de pagos.
 *
 * `MspStatusChip` (`:core:designsystem`) NO se reutiliza aquí a propósito: su paleta de
 * [com.example.msp_app.core.designsystem.component.ChipStatus] no tiene un matiz "brand"
 * (el que necesita Transferencia) y su ícono obligatorio se apartaría del `.m` del mockup
 * (solo color + texto, sin ícono) — mismo criterio que distingue el dot de [DuoTiles] del
 * `MspStatusChip`.
 */
@Composable
private fun MethodPill(method: PaymentMethod, modifier: Modifier = Modifier) {
    Text(
        text = method.pillLabel(),
        style = MspTheme.type.captionStrong,
        color = method.pillContentColor(),
        modifier = modifier
            .clip(MspTheme.shapes.chip)
            .background(method.pillTintColor())
            .padding(horizontal = MspTheme.spacing.sm, vertical = METHOD_PILL_VERTICAL_PADDING)
    )
}

private fun PaymentMethod.pillLabel(): String = when (this) {
    PaymentMethod.EFECTIVO -> "Efectivo"
    PaymentMethod.TRANSFERENCIA -> "Transfer."
    PaymentMethod.CHEQUE -> "Cheque"
    PaymentMethod.CONDONACION -> "Condonado"
    PaymentMethod.OTRO -> "Otro"
}

@Composable
private fun PaymentMethod.pillContentColor(): Color = when (this) {
    PaymentMethod.EFECTIVO -> MspTheme.colors.statusPaid
    PaymentMethod.TRANSFERENCIA -> MspTheme.colors.brand
    PaymentMethod.CHEQUE -> MspTheme.colors.statusInfo
    PaymentMethod.CONDONACION -> MspTheme.colors.promise
    PaymentMethod.OTRO -> MspTheme.colors.onSurfaceMuted
}

@Composable
private fun PaymentMethod.pillTintColor(): Color = when (this) {
    PaymentMethod.EFECTIVO -> MspTheme.colors.statusPaidTint
    PaymentMethod.TRANSFERENCIA -> MspTheme.colors.brandTint
    PaymentMethod.CHEQUE -> MspTheme.colors.statusInfoTint
    PaymentMethod.CONDONACION -> MspTheme.colors.promiseTint
    PaymentMethod.OTRO -> MspTheme.colors.progressTrack
}

/**
 * "María López Hernández" -> "ML" (primera letra de las dos primeras palabras); un nombre
 * de una sola palabra usa su primera letra sola; blanco -> `""`. Cálculo del piloto — el
 * design system ([MspInitialsAvatar]) solo pinta iniciales ya resueltas.
 */
private fun clienteInitials(nombre: String): String {
    val palabras = nombre.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        palabras.isEmpty() -> ""
        palabras.size == 1 -> palabras[0].take(1).uppercase()
        else -> "${palabras[0].first()}${palabras[1].first()}".uppercase()
    }
}
