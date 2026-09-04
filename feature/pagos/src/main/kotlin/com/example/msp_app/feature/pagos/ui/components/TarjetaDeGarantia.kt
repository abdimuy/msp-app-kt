package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.MspColors
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.EstadoDeGarantia
import com.example.msp_app.feature.pagos.domain.model.GarantiaDeLaVenta
import java.time.format.DateTimeFormatter

/** `testTag` de la tarjeta de garantía. */
const val TARJETA_DE_GARANTIA_TAG: String = "pagos_tarjeta_garantia"

/** `testTag` del chip de estado de la garantía. */
const val ESTADO_DE_GARANTIA_TAG: String = "pagos_estado_garantia"

private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * La tarjeta de garantía del mock (`.gar`): ícono, producto, fecha de reporte,
 * chip de estado, y la falla debajo.
 *
 * **Las dos acciones del mock no se reimplementan aquí.** "Recolectar producto"
 * e "imprimir aviso" son flujos que ya viven en `:app` (`features/guarantees`);
 * la tarjeta lleva a ellos por navegación en vez de tener una segunda
 * implementación de un flujo que este plan declaró intacto — el mismo criterio
 * que con la liquidación y la condonación.
 *
 * Color por la tabla del Task 2: el chip del mock (`.gc`) usa `vioDeep`/`vio`,
 * que mapea a `promiseTint`/`promise`, y ahí queda "notificada". Los otros dos
 * estados no están en el mock, así que toman el token que ya significa lo mismo
 * en el design system (`statusInfo` recolectada, `statusPaid` entregada). El
 * color nunca va solo: la etiqueta dice el estado en palabras.
 */
@Composable
fun TarjetaDeGarantia(
    garantia: GarantiaDeLaVenta,
    onVerGarantia: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Tarjeta(modifier = modifier.testTag(TARJETA_DE_GARANTIA_TAG)) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    MspTheme.spacing.sm + MspTheme.spacing.xs
                )
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(fondoDe(garantia.estado, MspTheme.colors)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Build,
                        contentDescription = null,
                        tint = contenidoDe(garantia.estado, MspTheme.colors),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = garantia.producto,
                        style = MspTheme.type.cardTitle,
                        color = MspTheme.colors.onSurface,
                        maxLines = 2
                    )
                    Text(
                        text = garantia.reportadaEl
                            ?.let { "reportada el " + DIA_Y_MES.format(it) }
                            ?: "sin fecha de reporte",
                        style = MspTheme.type.caption,
                        color = MspTheme.colors.onSurfaceMuted
                    )
                }
                Text(
                    text = garantia.estado.etiqueta,
                    style = MspTheme.type.chipLabel,
                    color = contenidoDe(garantia.estado, MspTheme.colors),
                    modifier = Modifier
                        .clip(MspTheme.shapes.control)
                        .background(fondoDe(garantia.estado, MspTheme.colors))
                        .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
                        .testTag(ESTADO_DE_GARANTIA_TAG)
                )
            }
            Spacer(Modifier.height(MspTheme.spacing.sm))
            Text(
                text = garantia.falla,
                style = MspTheme.type.body,
                color = MspTheme.colors.onSurfaceMuted
            )
            Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
            VerTodos(
                texto = "ver la garantía",
                onClick = { onVerGarantia(garantia.garantiaId) },
                color = MspTheme.colors.surface2
            )
        }
    }
}

private fun contenidoDe(estado: EstadoDeGarantia, colors: MspColors): Color = when (estado) {
    EstadoDeGarantia.NOTIFICADA -> colors.promise
    EstadoDeGarantia.RECOLECTADA -> colors.statusInfo
    EstadoDeGarantia.ENTREGADA -> colors.statusPaid
    EstadoDeGarantia.DESCONOCIDO -> colors.statusPending
}

private fun fondoDe(estado: EstadoDeGarantia, colors: MspColors): Color = when (estado) {
    EstadoDeGarantia.NOTIFICADA -> colors.promiseTint
    EstadoDeGarantia.RECOLECTADA -> colors.statusInfoTint
    EstadoDeGarantia.ENTREGADA -> colors.statusPaidTint
    EstadoDeGarantia.DESCONOCIDO -> colors.statusPendingTint
}
