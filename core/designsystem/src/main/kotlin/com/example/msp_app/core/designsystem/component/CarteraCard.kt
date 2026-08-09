package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal
import java.math.RoundingMode

private val TILE_SHADOW_ELEVATION = 1.dp
private val SPLIT_DOT_SIZE = 8.dp
private val SPLIT_BAR_HEIGHT = 6.dp
private const val FRACTION_SCALE = 4

/**
 * Tarjeta "cobrado vs pendiente" de cartera (kollect §9, `CarteraCard` del
 * catálogo). **Fase 2 por DATOS** (task-8-brief.md: requiere el backend de
 * saldos por zona; el piloto Plan 5 NO la cablea todavía), pero el
 * componente se entrega ahora — componente puro, 100% dirigido por
 * parámetro, para completar el catálogo del DS.
 *
 * [MspCard] (hairline + `shadowElevation = 1.dp`, mismo substrato que
 * [MspBentoTile]/[MspWeeklyBarsCard]) con: [title] (`type.tileLabel`,
 * muted), [totalAmount] grande (`MspMoneyText` `type.amountCard`), split
 * visual con [MspProgressBar] (`colors.statusPaid` = cobrado sobre
 * `colors.statusOverdueTint` = pendiente/vencido — misma pareja semántica que
 * [ChipStatus.Paid]/[ChipStatus.Overdue]), y dos filas de detalle
 * (dot de color + `MspMoneyText` `type.kvValue` + label `type.caption`) para
 * [collectedAmount]/[collectedLabel] y [pendingAmount]/[pendingLabel]. Un
 * [caption] final opcional (p. ej. "18 clientes activos").
 *
 * Todo monto ([totalAmount], [collectedAmount], [pendingAmount]) es
 * `BigDecimal`, nunca `String` precomputado (anti-`Double`/anti-string-money
 * del brief). La fracción del split se calcula internamente
 * ([collectedAmount] / [totalAmount], `0f` si [totalAmount] es cero — sin
 * división por cero) para que el caller nunca tenga que derivar un `Float` a
 * mano desde dinero.
 */
@Composable
fun MspCarteraCard(
    title: String,
    totalAmount: BigDecimal,
    collectedAmount: BigDecimal,
    collectedLabel: String,
    pendingAmount: BigDecimal,
    pendingLabel: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    masked: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val colors = MspTheme.colors
    val type = MspTheme.type

    MspCard(modifier = modifier, shadowElevation = TILE_SHADOW_ELEVATION, onClick = onClick) {
        Column(modifier = Modifier.padding(MspTheme.spacing.md)) {
            Text(text = title, style = type.tileLabel, color = colors.onSurfaceMuted)
            MspMoneyText(
                amount = totalAmount,
                masked = masked,
                style = type.amountCard,
                color = colors.onSurface,
                modifier = Modifier.padding(top = MspTheme.spacing.xs)
            )
            MspProgressBar(
                progress = collectedFraction(collectedAmount, totalAmount),
                height = SPLIT_BAR_HEIGHT,
                fillColor = colors.statusPaid,
                trackColor = colors.statusOverdueTint,
                modifier = Modifier.padding(top = MspTheme.spacing.sm)
            )
            Column(
                modifier = Modifier.padding(top = MspTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
            ) {
                CarteraSplitRow(
                    dotColor = colors.statusPaid,
                    amount = collectedAmount,
                    label = collectedLabel,
                    masked = masked
                )
                CarteraSplitRow(
                    dotColor = colors.statusOverdue,
                    amount = pendingAmount,
                    label = pendingLabel,
                    masked = masked
                )
            }
            if (caption != null) {
                Text(
                    text = caption,
                    style = type.caption,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = MspTheme.spacing.xs)
                )
            }
        }
    }
}

@Composable
private fun CarteraSplitRow(dotColor: Color, amount: BigDecimal, label: String, masked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(SPLIT_DOT_SIZE)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(MspTheme.spacing.xs))
        MspMoneyText(
            amount = amount,
            masked = masked,
            style = MspTheme.type.kvValue,
            color = MspTheme.colors.onSurface
        )
        Spacer(modifier = Modifier.width(MspTheme.spacing.xs))
        Text(text = label, style = MspTheme.type.caption, color = MspTheme.colors.onSurfaceMuted)
    }
}

/** `collected / total` en `[0,1]`, `0f` si [total] es cero (sin división por cero). */
private fun collectedFraction(collected: BigDecimal, total: BigDecimal): Float {
    if (total.signum() == 0) return 0f
    return collected.divide(total, FRACTION_SCALE, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
}
