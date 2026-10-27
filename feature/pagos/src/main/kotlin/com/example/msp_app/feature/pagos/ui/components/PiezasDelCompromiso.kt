package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.ui.CompromisoEnPantalla
import com.example.msp_app.feature.pagos.ui.EstadoCuentaUi

/** `testTag` del texto del compromiso en la tarjeta de la lista. */
const val COMPROMISO_DE_LA_VENTA_TAG: String = "pagos_compromiso_venta"

/** `testTag` del renglón del compromiso en el detalle de la venta. */
const val RENGLON_DE_COMPROMISO_TAG: String = "pagos_renglon_compromiso"

/**
 * El compromiso en la tarjeta de la lista (spec visitas §5.7 B y C; plan P2,
 * Tarea 20; E-APP-115): una línea con ícono y texto en el color de su trato,
 * **debajo** del chip de dinero o de "Primer abono", nunca en su lugar. El
 * dinero sigue mandando en la columna; esto solo dice qué quedó acordado.
 *
 * Ícono + texto + color, como el resto de los estados: nunca color solo.
 */
@Composable
internal fun LineaDeCompromiso(compromiso: CompromisoEnPantalla, modifier: Modifier = Modifier) {
    val color = EstadoCuentaUi.contenidoDe(compromiso.trato, MspTheme.colors)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs + 2.dp)
    ) {
        Icon(
            imageVector = compromiso.icono,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(ICONO_DE_LA_LINEA)
        )
        Text(
            text = compromiso.titulo + SEPARADOR + compromiso.detalle,
            style = MspTheme.type.captionStrong,
            color = color,
            modifier = Modifier.testTag(COMPROMISO_DE_LA_VENTA_TAG)
        )
    }
}

/**
 * El renglón del compromiso en el detalle de la venta (spec visitas §5.7 E):
 * día, hora y monto, en el tinte de su trato, justo debajo del estado.
 */
@Composable
internal fun RenglonDeCompromiso(compromiso: CompromisoEnPantalla, modifier: Modifier = Modifier) {
    val colors = MspTheme.colors
    val contenido = EstadoCuentaUi.contenidoDe(compromiso.trato, colors)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.card)
            .background(EstadoCuentaUi.fondoDe(compromiso.trato, colors))
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(LADO_DEL_CIRCULO)
                .clip(CircleShape)
                .background(contenido.copy(alpha = HALO_DEL_ICONO)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = compromiso.icono,
                contentDescription = null,
                tint = contenido,
                modifier = Modifier.size(ICONO_DEL_RENGLON)
            )
        }
        Column(
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .testTag(RENGLON_DE_COMPROMISO_TAG)
        ) {
            Text(
                text = compromiso.titulo,
                style = MspTheme.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = contenido
            )
            Text(
                text = compromiso.detalle,
                style = MspTheme.type.saleMeta,
                color = colors.onSurface
            )
        }
    }
}

private const val SEPARADOR = " · "
private val ICONO_DE_LA_LINEA = 14.dp
private val LADO_DEL_CIRCULO = 36.dp
private val ICONO_DEL_RENGLON = 18.dp
private const val HALO_DEL_ICONO = 0.16f
