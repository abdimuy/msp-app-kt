package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import java.math.BigDecimal

/**
 * `testTag` del dot de color del header — localiza el dot en el compose-test
 * que verifica que [MspBentoTile] expone dot + label + valor (task-8-brief.md).
 */
internal const val BENTO_TILE_DOT_TAG = "msp_bento_tile_dot"

private val DOT_SIZE = 8.dp
private val TILE_SHADOW_ELEVATION = 1.dp
private val SUB_LINE_SPACING = 3.dp

/**
 * Tile del "duo" Efectivo/Transferencia (kollect §8.2, task-8-brief.md) —
 * [MspSurface] (vía [MspCard]) shape [MspTheme.shapes.tile] (16dp, el
 * default del wrapper) + `shadowElevation = 1.dp` sobre el hairline (una
 * leve elevación por encima del substrato "casi-plano" de [MspCard]).
 *
 * Header: dot de color ([dotColor], p. ej. `statusPaid` para Efectivo o
 * `brand` para Transferencia — el DS no fija la paleta por tile, la decide el
 * caller) + [label] en `type.tileLabel`, color muted. Valor grande en
 * `MspMoneyText` con `type.amountCard` (tabular). Sub-línea ([subLine], p.
 * ej. "22 pagos") en `type.caption`, muted.
 *
 * [amount] es `BigDecimal` (anti-`Double` del money-path); [masked] enmascara
 * el valor igual que el resto del catálogo de dinero. [onClick] fluye
 * directo a [MspCard]; el `pressScale` de entrada (Task 6, feature/dashboard
 * en kollect) es responsabilidad del `Modifier` que pase el caller, no de
 * este componente.
 */
@Composable
fun MspBentoTile(
    dotColor: Color,
    label: String,
    amount: BigDecimal,
    subLine: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    MspCard(modifier = modifier, shadowElevation = TILE_SHADOW_ELEVATION, onClick = onClick) {
        Column(modifier = Modifier.padding(MspTheme.spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(DOT_SIZE)
                        .clip(CircleShape)
                        .background(dotColor)
                        .testTag(BENTO_TILE_DOT_TAG)
                )
                Spacer(modifier = Modifier.width(MspTheme.spacing.xs))
                Text(
                    text = label,
                    style = MspTheme.type.tileLabel,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            MspMoneyText(
                amount = amount,
                masked = masked,
                style = MspTheme.type.amountCard,
                color = MspTheme.colors.onSurface,
                modifier = Modifier.padding(top = MspTheme.spacing.xs)
            )
            Text(
                text = subLine,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = SUB_LINE_SPACING)
            )
        }
    }
}
