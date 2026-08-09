package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
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
 * (`.syncpill`) empujada al extremo derecho (mismo truco visual `margin-left:auto` del CSS).
 *
 * **Fix Task 9 (DS break Tier2@2.0), revisado Task 11 (subrow ya NO se envuelve a 2 líneas):**
 * la primera versión usaba un `Row` con `Spacer(Modifier.weight(1f))` entre ambos pills — a
 * `fontScale = 2.0` el pill de rango por sí solo ya podía ocupar casi todo el ancho disponible,
 * dejando al `Spacer` sin espacio que repartir y a [MspPaymentSyncPill] "hambriento" (medido en
 * una franja casi nula, el texto degradaba a una columna vertical de letras sueltas — ver
 * `task-9-review.md` "Tier2@2.0 DS break"). Una segunda versión cambió a `FlowRow`, que sí evitó
 * el colapso letra-por-letra pero introdujo OTRO defecto (Task 11 fidelity review, finding 2): con
 * el rango largo de Semana ("semana · lun 3 – vie 7 ago · 5 días") el rangepill por sí solo ya
 * llena la línea y `FlowRow` manda "N por subir" a una SEGUNDA línea — el mockup lo mantiene
 * siempre en la misma fila, alineado a la derecha.
 *
 * Fix definitivo: un `Row` (sin wrap) donde el orden de peso es el INVERSO al bug original —
 * [MspPaymentSyncPill] va SIN peso (Compose mide primero los hijos sin peso de un `Row`, así que
 * SIEMPRE recibe su ancho natural completo, nunca una franja artificial: el colapso letra-por-letra
 * queda estructuralmente descartado, no solo evitado por casualidad) y el pill de rango lleva
 * `weight(1f, fill = false)` — el `fill = false` es a propósito: le da como MÁXIMO el ancho que
 * sobra tras medir el sync pill, pero lo deja `wrapContentWidth` a su tamaño natural cuando el
 * texto es corto (Día), en vez de estirar el pill vacío hasta el límite del peso. Si el texto del
 * rango no cabe en ese máximo (Semana), el propio `Text` (sin `maxLines`, `softWrap` por default)
 * reflowea a una 2ª línea DENTRO de su pill — el pill crece en alto, pero la fila entera sigue
 * siendo una sola fila visual y el sync pill nunca se mueve de su lugar. Mismo patrón que el fix
 * de truncado en `SecondaryChip`: el elemento que jamás debe truncarse/moverse va sin peso; el que
 * puede ceder ancho lleva el peso.
 */
@Composable
fun RangeSubRow(rangeLabel: String, pendingCount: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .wrapContentWidth(Alignment.Start)
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
