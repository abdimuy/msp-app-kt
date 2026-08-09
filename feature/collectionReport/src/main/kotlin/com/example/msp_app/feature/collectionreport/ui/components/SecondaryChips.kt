package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.ChipUi
import java.math.BigDecimal

private val CHIP_DOT_SIZE = 9.dp

/**
 * Chips secundarios Condonado/Visitas (mockup `.chips`): dos [MspCard] "pill-ish" —
 * `shapes.field` (14dp, el radio real de `.chip` en el mockup, no el `shapes.tile` default
 * de [MspCard]) — con un dot de color + etiqueta muted + valor a la derecha.
 *
 * Condonado muestra [MspMoneyText] en ámbar (`statusPartial`, mockup `.cv.amb`); Visitas
 * muestra un conteo plano ([ChipUi.count], NO es dinero) — [masked] solo enmascara montos,
 * así que el conteo de visitas nunca se oculta.
 */
@Composable
fun SecondaryChips(
    condonado: ChipUi,
    visitas: ChipUi,
    masked: Boolean,
    onCondonadoClick: () -> Unit,
    onVisitasClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        SecondaryChip(
            dotColor = MspTheme.colors.statusPartial,
            label = condonado.label,
            onClick = onCondonadoClick,
            modifier = Modifier.weight(1f)
        ) {
            MspMoneyText(
                amount = condonado.amount?.amount ?: BigDecimal.ZERO,
                masked = masked,
                style = MspTheme.type.amountInline,
                color = MspTheme.colors.statusPartial
            )
        }
        SecondaryChip(
            dotColor = MspTheme.colors.statusPending,
            label = visitas.label,
            onClick = onVisitasClick,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "${visitas.count ?: 0}",
                style = MspTheme.type.amountInline,
                color = MspTheme.colors.onSurface
            )
        }
    }
}

/**
 * `dot` + `label` viven en su PROPIA `Row` con `weight(1f)` — no un `Spacer(weight(1f))`
 * suelto entre `label` y `value` — a propósito: en un `Row`, los hijos SIN peso se miden en
 * orden de código con el ancho que va quedando; con el `Spacer` como único hijo con peso,
 * `value()` (el monto) quedaba MEDIDO DESPUÉS de `dot`+`label`, con lo que le tocaban las
 * sobras — en un chip angosto (`weight(1f)` de dos por fila) eso partía el monto a dos
 * líneas (bug real, visto en el golden). Con `value()` como único hijo SIN peso de la `Row`
 * externa, se mide primero con el ancho completo (nunca se parte — regla anti-truncado de
 * dinero); lo que sobra se lo reparte el grupo `dot`+`label`, y `label` (`maxLines = 1` +
 * `Ellipsis`, NO es dinero) es quien cede si el chip es angosto.
 */
@Composable
private fun SecondaryChip(
    dotColor: Color,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit
) {
    MspCard(modifier = modifier, shape = MspTheme.shapes.field, onClick = onClick) {
        Row(
            modifier = Modifier.padding(
                horizontal = MspTheme.spacing.sm,
                vertical = MspTheme.spacing.sm
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(CHIP_DOT_SIZE)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = label,
                    style = MspTheme.type.chipLabel,
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            value()
        }
    }
}
