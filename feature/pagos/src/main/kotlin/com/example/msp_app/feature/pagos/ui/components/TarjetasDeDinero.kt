package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import java.time.format.DateTimeFormatter

/** `testTag` del botón "usar" de la tarjeta de liquidación. */
const val USAR_LIQUIDACION_TAG: String = "pagos_usar_liquidacion"

private val VIGENCIA: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * "Hoy liquida con" (`.liq`). Existe a nivel cliente y a nivel venta y **no es
 * redundancia**: en el cliente cierra la conversación completa, en la venta
 * cierra ese mueble.
 *
 * El botón "usar" va en `brand`, no en verde: es una ACCIÓN. El verde
 * `statusPaid` es solo para estado (regla dura del plan). El mock lo pinta en
 * su esmeralda `acDeep`/`acInk` y es el caso donde más fácil es equivocarse —
 * la tabla del Task 2 §3(a) lo nombra explícitamente.
 */
@Composable
fun TarjetaDeLiquidacion(
    label: String,
    liquidacion: Liquidacion,
    onUsar: () -> Unit,
    modifier: Modifier = Modifier,
    ocultos: Boolean = false
) {
    Tarjeta(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(BUSINESS_LOCALE),
                    style = MspTheme.type.overline,
                    color = MspTheme.colors.onSurfaceMuted
                )
                Spacer(Modifier.height(MspTheme.spacing.xs))
                MspMoneyText(
                    amount = liquidacion.monto.amount,
                    masked = ocultos,
                    style = MspTheme.type.amountLarge,
                    color = MspTheme.colors.onSurface
                )
                Text(
                    text = liquidacion.vigenteHasta
                        ?.let { "Vigente hasta el " + VIGENCIA.format(it) }
                        ?: "Sin fecha de vigencia",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
            androidx.compose.material3.Surface(
                onClick = onUsar,
                shape = MspTheme.shapes.control,
                color = MspTheme.colors.brand,
                modifier = Modifier
                    .heightIn(min = 50.dp)
                    .testTag(USAR_LIQUIDACION_TAG)
            ) {
                Text(
                    text = "Usar",
                    style = MspTheme.type.buttonSmall,
                    color = MspTheme.colors.onBrand,
                    modifier = Modifier.padding(
                        horizontal = MspTheme.spacing.md,
                        vertical = MspTheme.spacing.md
                    )
                )
            }
        }
    }
}
