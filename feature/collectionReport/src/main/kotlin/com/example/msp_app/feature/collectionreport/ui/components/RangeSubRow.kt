package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
 * (`.syncpill`) empujada al extremo derecho (mismo truco visual `margin-left:auto` del CSS
 * cuando ambas caben en una línea).
 *
 * **Fix Task 11 (carry-forward de Task 9, DS break Tier2@2.0):** la versión original usaba un
 * `Row` con un `Spacer(Modifier.weight(1f))` entre ambos pills — a `fontScale = 2.0` el pill de
 * rango por sí solo ya podía ocupar casi todo el ancho disponible, dejando al `Spacer` sin
 * espacio que repartir y a [MspPaymentSyncPill] "hambriento" (medido en una franja casi nula, el
 * texto degradaba a una columna vertical de letras sueltas — ver `task-9-review.md` "Tier2@2.0 DS
 * break"). Reemplazado por [FlowRow]: ambos pills se miden SIEMPRE a su tamaño natural (nunca se
 * aprietan a un ancho artificial) y, si no caben juntos en una línea, el sync pill simplemente
 * baja de línea — nunca se solapan ni se recortan, a NINGUNA escala de fuente.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RangeSubRow(rangeLabel: String, pendingCount: Int, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
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

        MspPaymentSyncPill(pendingCount = pendingCount)
    }
}
