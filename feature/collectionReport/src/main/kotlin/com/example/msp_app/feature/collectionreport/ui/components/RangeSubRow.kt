package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspPaymentSyncPill
import com.example.msp_app.core.designsystem.theme.MspTheme

private val RANGE_PILL_ICON_SIZE = 14.dp
private val RANGE_PILL_PADDING_HORIZONTAL = 12.dp
private val RANGE_PILL_PADDING_VERTICAL = 8.dp

/**
 * Fila bajo el selector de periodo (mockup `.subrow`): pill de rango (`.rangepill`, ícono
 * calendario + [rangeLabel] en `colors.brand`/`colors.brandTint`) + [MspPaymentSyncPill]
 * (`.syncpill`) empujada al extremo derecho con un `Spacer` de peso — mismo truco
 * `margin-left:auto` del CSS.
 */
@Composable
fun RangeSubRow(rangeLabel: String, pendingCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(MspTheme.shapes.chip)
                .background(MspTheme.colors.brandTint)
                .padding(
                    horizontal = RANGE_PILL_PADDING_HORIZONTAL,
                    vertical = RANGE_PILL_PADDING_VERTICAL
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Icon(
                imageVector = Icons.Filled.DateRange,
                contentDescription = null,
                tint = MspTheme.colors.brand,
                modifier = Modifier.size(RANGE_PILL_ICON_SIZE)
            )
            Text(text = rangeLabel, style = MspTheme.type.chipLabel, color = MspTheme.colors.brand)
        }

        Spacer(modifier = Modifier.weight(1f))

        MspPaymentSyncPill(pendingCount = pendingCount)
    }
}
