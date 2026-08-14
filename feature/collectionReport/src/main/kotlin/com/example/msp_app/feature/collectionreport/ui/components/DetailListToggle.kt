package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme

/** Chevron del control — mismo peso visual que el ícono del `.rangepill` de [RangeSubRow]. */
private val TOGGLE_CHEVRON_SIZE = 18.dp

/** Anillo de foco del control (navegación por teclado/D-pad). */
private val TOGGLE_FOCUS_RING = 2.dp

private const val COLLAPSE_LABEL = "Ver menos"

/**
 * Etiqueta del control colapsado. Lleva el conteo REAL ([total]) porque el control promete
 * revelar la lista COMPLETA — "13 pagos más" (texto muerto que informa y no revela) fue
 * explícitamente rechazado por el dueño. Minimalista, 4 palabras (convención de UI del proyecto).
 *
 * `internal`: [DetailList] la usa para decidir la etiqueta; vive junto al control, no junto a la
 * lista.
 */
internal fun expandLabel(total: Int): String = "Ver los $total pagos"

/**
 * Control de expansión de la lista de pagos ([DetailList], detalle de Día): botón de verdad
 * (`Role.Button`, alto mínimo `spacing.touchTarget`, `onClickLabel` + `contentDescription` con la
 * MISMA etiqueta que se lee en pantalla) y anillo de foco visible para D-pad/teclado — el
 * `indication` por default solo cubre el press, no el foco.
 *
 * Vive en su propio archivo, no dentro de `DetailList.kt`, por el mismo motivo que [MethodPill]:
 * ese archivo ya roza el umbral `TooManyFunctions` de detekt.
 *
 * **Reparto de ancho a `fontScale` grande:** el chevron va SIN peso y el texto lleva
 * `weight(1f, fill = false)`. Es literalmente el fix documentado en [RangeSubRow]: en un `Row`,
 * Compose mide primero los hijos sin peso, así que el ícono SIEMPRE recibe sus
 * [TOGGLE_CHEVRON_SIZE] completos y el texto se queda con el resto — a `fontScale = 2.0` la
 * etiqueta reflowea a dos líneas (`textAlign = Center`) en vez de que alguno de los dos colapse a
 * una columna de letras sueltas. El `fill = false` mantiene el par centrado cuando el texto es
 * corto, en vez de estirarlo hasta el borde.
 */
@Composable
internal fun DetailListToggle(
    expanded: Boolean,
    total: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (expanded) COLLAPSE_LABEL else expandLabel(total)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.control)
            .border(
                width = if (focused) TOGGLE_FOCUS_RING else 0.dp,
                color = if (focused) MspTheme.colors.brand else Color.Transparent,
                shape = MspTheme.shapes.control
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClickLabel = label,
                onClick = onToggle
            )
            .heightIn(min = MspTheme.spacing.touchTarget)
            .padding(horizontal = MspTheme.spacing.sm)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(
            space = MspTheme.spacing.xs,
            alignment = Alignment.CenterHorizontally
        )
    ) {
        Text(
            text = label,
            style = MspTheme.type.buttonSmall,
            color = MspTheme.colors.brand,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false)
        )
        Icon(
            imageVector = if (expanded) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null,
            tint = MspTheme.colors.brand,
            modifier = Modifier.size(TOGGLE_CHEVRON_SIZE)
        )
    }
}
