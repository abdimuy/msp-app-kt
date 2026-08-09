package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.ui.ChipUi
import java.math.BigDecimal

private val CHIP_DOT_SIZE = 9.dp
private val CHIP_ROW_GAP = 2.dp

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
 * Fix Task 11 (fidelity review, finding 1): `label` y `value` compartiendo una sola `Row`
 * NUNCA garantiza espacio suficiente para "Condonado" completo — el monto en centavos
 * (`formatMoneyMxn` SIEMPRE imprime `.00`, regla intocable, ver `docs/…/task-11-report.md`)
 * es ~50% más ancho que el equivalente sin centavos del mockup, y en un chip `weight(1f)` de
 * dos por fila ese ancho extra se lo robaba a `label`, que truncaba a "Condon…" (`maxLines = 1`
 * + `Ellipsis`) — visto en el golden real.
 *
 * Fix: `dot` + `label` y `value()` pasan de compartir una fila a apilarse en una [Column] —
 * `label` va en su PROPIA fila arriba, con el ANCHO COMPLETO del chip disponible (nunca
 * comparte presupuesto de ancho con el monto), así que YA NO tiene `maxLines`/`Ellipsis`: no
 * puede truncarse por construcción. `value()` va debajo en su propia fila, alineada al final
 * (`Arrangement.End`, ecoa el `margin-left:auto` del mockup) con TODO el ancho del chip para
 * ella sola — nunca se aprieta contra `label`. [MspMoneyText] conserva su regla de no-truncar
 * (`softWrap`, sin `maxLines`) como red de seguridad si algún monto fuera excepcionalmente
 * grande, pero con la fila completa para sí sola ya no debería necesitarla en el caso normal.
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MspTheme.spacing.sm,
                    vertical = MspTheme.spacing.sm
                ),
            verticalArrangement = Arrangement.spacedBy(CHIP_ROW_GAP)
        ) {
            Row(
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
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                value()
            }
        }
    }
}
