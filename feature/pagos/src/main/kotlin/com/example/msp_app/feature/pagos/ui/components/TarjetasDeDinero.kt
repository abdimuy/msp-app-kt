package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import java.time.format.DateTimeFormatter

/** `testTag` del botón "usar" de la tarjeta de liquidación. */
const val USAR_LIQUIDACION_TAG: String = "pagos_usar_liquidacion"

private val VIGENCIA: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/**
 * La tarjeta de saldo del mock (`.money`): label, la cifra grande y un pie
 * opcional.
 *
 * **Sin chip de estado a la derecha.** El mock pone ahí "Vencido 12d", que es
 * un dato de días de atraso que el teléfono no tiene hoy; repetir en su lugar
 * la etiqueta del estado —que ya está en grande dos tarjetas arriba— sería
 * decir dos veces lo mismo en la misma pantalla. Se deja el hueco hasta que
 * exista el dato real, en vez de llenarlo con ruido.
 */
@Composable
fun TarjetaDeSaldo(
    label: String,
    monto: Money,
    modifier: Modifier = Modifier,
    pie: @Composable (() -> Unit)? = null
) {
    Tarjeta(modifier = modifier) {
        Column {
            Text(
                text = label.uppercase(BUSINESS_LOCALE),
                style = MspTheme.type.overline,
                color = MspTheme.colors.onSurfaceMuted
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
            MspMoneyText(
                amount = monto.amount,
                style = MspTheme.type.amountHero,
                color = MspTheme.colors.onSurface
            )
            if (pie != null) {
                Spacer(Modifier.height(MspTheme.spacing.md))
                pie()
            }
        }
    }
}

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
    modifier: Modifier = Modifier
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
                    style = MspTheme.type.amountLarge,
                    color = MspTheme.colors.onSurface
                )
                Text(
                    text = liquidacion.vigenteHasta
                        ?.let { "vigente hasta el " + VIGENCIA.format(it) }
                        ?: "sin fecha de vigencia",
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
                    text = "usar",
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

/** `testTag` de una fila de venta dentro del detalle de cliente. */
const val FILA_DE_VENTA_TAG: String = "pagos_fila_venta"

/**
 * Una venta dentro del detalle del cliente (`.sale`): título, **chip de
 * estado**, el saldo, la barra de abonos y el pie con los abonos.
 *
 * **Por qué el chip y no el cuadro.** Kollect arma esta misma fila con
 * `SaleCard`, y `SaleCard.kt:107` pone `StatusChip(status, statusLabel)`: la
 * pastilla con ícono + texto dentro. Aquí había un cuadro de color de 28dp más
 * la etiqueta suelta al lado, en el color del estado — los dos portadores
 * estaban, pero es justo la pieza chica cuya ausencia hacía que la pantalla se
 * viera "suave" al lado de kollect. El chip queda debajo del título en vez de
 * a su izquierda porque a 360dp la fila título + chip + monto no cabe, y el
 * monto es el dato por el que el cobrador abrió la pantalla.
 *
 * El folio se queda, en gris apagado: dejó de compartir línea con el estado
 * (iban los dos en el color del estado, y un identificador no tiene estado).
 */
@Composable
fun FilaDeVenta(venta: VentaDelCliente, onAbrir: () -> Unit, modifier: Modifier = Modifier) {
    Tarjeta(modifier = modifier.testTag(FILA_DE_VENTA_TAG), onClick = onAbrir) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    MspTheme.spacing.sm + MspTheme.spacing.xs
                )
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = venta.descripcion.ifBlank { venta.folio },
                        style = MspTheme.type.saleTitle,
                        color = MspTheme.colors.onSurface,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(MspTheme.spacing.xs))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
                    ) {
                        ChipDeEstado(venta.estado)
                        Text(
                            text = venta.folio,
                            style = MspTheme.type.saleMeta,
                            color = MspTheme.colors.onSurfaceMuted
                        )
                    }
                }
                MspMoneyText(
                    amount = venta.saldo.amount,
                    style = MspTheme.type.amountRow,
                    color = MspTheme.colors.onSurface
                )
            }
            Spacer(Modifier.height(MspTheme.spacing.sm + MspTheme.spacing.xs))
            MspProgressBar(
                progress = venta.avance,
                height = 4.dp,
                fillColor = MspTheme.colors.heroProgressFill,
                trackColor = MspTheme.colors.progressTrack
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${venta.abonosPagados} de ${venta.abonosTotales} abonos",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "ver venta",
                    style = MspTheme.type.captionStrong,
                    color = MspTheme.colors.brand
                )
            }
        }
    }
}
