package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.ui.components.SIN_DATO
import com.example.msp_app.feature.pagos.ui.components.Separador

/** `testTag` del cuerpo de la hoja "Datos de la venta". */
const val HOJA_DE_DATOS_DE_LA_VENTA_TAG: String = "pagos_venta_hoja_datos"

/** `testTag` de cada renglón de la hoja. */
const val RENGLON_DE_DATOS_TAG: String = "pagos_venta_hoja_datos_renglon"

/** `testTag` del rótulo de un renglón de la hoja. */
const val ROTULO_DE_DATOS_TAG: String = "pagos_venta_hoja_datos_rotulo"

/** `testTag` del valor de un renglón de la hoja. */
const val VALOR_DE_DATOS_TAG: String = "pagos_venta_hoja_datos_valor"

/**
 * Un renglón de "Datos de la venta": el rótulo y lo que dice.
 *
 * [monto] va aparte de [valor] para que el ojo pueda taparlo: una cifra de
 * dinero se enmascara, un teléfono o una fecha no.
 */
internal data class DatoDeLaVenta(
    val rotulo: String,
    val valor: String = "",
    val monto: Money? = null
)

/** Los tres bloques de la hoja, en el orden del legado: la puerta, el trato, las personas. */
internal data class GrupoDeDatos(val titulo: String, val datos: List<DatoDeLaVenta>)

/**
 * **Los once renglones** que la pantalla legada enseñaba (`SaleClientDetailsSection`),
 * agrupados de lo más consultado a lo menos: la puerta (teléfono, dirección,
 * zona), el trato (fecha y cifras) y las personas (aval y vendedores).
 *
 * El precio a corto plazo **no abre renglón cuando no aplica**: los dos en cero
 * significan "esta venta no tiene oferta", y un "$0 a 0 meses" es la ausencia
 * del dato disfrazada de cifra. Por eso son once o diez, y el enlace de la
 * tarjeta cuenta los que de verdad hay ([cuantosDatos]).
 */
internal fun gruposDeDatos(detalle: DetalleVenta): List<GrupoDeDatos> = listOf(
    GrupoDeDatos(
        "La puerta",
        listOf(
            DatoDeLaVenta("Teléfono", detalle.telefono),
            DatoDeLaVenta("Dirección", detalle.direccion),
            DatoDeLaVenta("Zona", detalle.zona)
        )
    ),
    GrupoDeDatos(
        "El trato",
        listOfNotNull(
            DatoDeLaVenta(
                "Fecha de venta",
                detalle.fechaVenta?.let { FECHA_DE_VENTA.format(it) } ?: SIN_DATO
            ),
            DatoDeLaVenta("Total venta", monto = detalle.totalVenta),
            DatoDeLaVenta("Precio de contado", monto = detalle.precioContado),
            precioCorto(
                detalle
            )?.let { (plazo, monto) -> DatoDeLaVenta("Precio a $plazo", monto = monto) },
            DatoDeLaVenta("Enganche", monto = detalle.enganche),
            DatoDeLaVenta("Abonado", monto = detalle.abonado)
        )
    ),
    GrupoDeDatos(
        "Las personas",
        listOf(
            DatoDeLaVenta("Aval o responsable", detalle.aval),
            // Uno por renglón, sin vacíos (el adaptador ya los recorta). Sin
            // ninguno la fila sigue con SIN_DATO: que no se sepa quién vendió
            // es información.
            DatoDeLaVenta(
                if (detalle.vendedores.size > 1) "Vendedores" else "Vendedor",
                detalle.vendedores.joinToString("\n")
            )
        )
    )
)

/** Cuántos renglones trae la hoja — lo que dice el enlace "N datos ›". */
internal fun cuantosDatos(detalle: DetalleVenta): Int =
    gruposDeDatos(detalle).sumOf { it.datos.size }

/**
 * El precio a corto plazo con su plazo en palabras ("3 meses", "1 mes"), o
 * `null` cuando la venta no lo tiene.
 */
internal fun precioCorto(detalle: DetalleVenta): Pair<String, Money>? {
    if (detalle.mesesACortoPlazo <= 0 || detalle.montoACortoPlazo <= Money.ZERO) return null
    val meses = detalle.mesesACortoPlazo
    return (if (meses == 1) "1 mes" else "$meses meses") to detalle.montoACortoPlazo
}

/**
 * La hoja "Datos de la venta". **No renderiza nada con [detalle] en `null`**:
 * la abre el estado, igual que `HojaDeLaFicha` y `HojaDeAbono`, y el cuerpo vive
 * aparte ([CuerpoDeLosDatosDeLaVenta]) para que Roborazzi lo capture — el
 * `Popup` del `ModalBottomSheet` no entra a `captureRoboImage`.
 *
 * Se cierra deslizando, tocando el velo o con atrás: todo eso lo da M3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDeDatosDeLaVenta(
    detalle: DetalleVenta?,
    ocultos: Boolean,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (detalle == null) return
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        modifier = modifier,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MspTheme.colors.surface,
        contentColor = MspTheme.colors.onSurface
    ) {
        CuerpoDeLosDatosDeLaVenta(detalle = detalle, ocultos = ocultos)
    }
}

/**
 * El cuerpo de la hoja, sin el `ModalBottomSheet`.
 *
 * **A letra grande pasa a UNA columna**: el rótulo arriba y el valor debajo, a
 * todo lo ancho (sección "h" del mock). A 2.0× dos cosas en la misma fila se
 * pelean 150 dp y la dirección se parte en cinco renglones angostos. El título
 * se queda fijo y el resto se desplaza por dentro.
 */
@Composable
fun CuerpoDeLosDatosDeLaVenta(
    detalle: DetalleVenta,
    ocultos: Boolean,
    modifier: Modifier = Modifier
) {
    val unaColumna = LocalFontSizeLevel.current != FontSizeLevel.NORMAL
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MspTheme.spacing.md)
            .testTag(HOJA_DE_DATOS_DE_LA_VENTA_TAG)
    ) {
        Text(
            text = "Datos de la venta",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            gruposDeDatos(detalle).forEachIndexed { g, grupo ->
                Text(
                    text = grupo.titulo.uppercase(BUSINESS_LOCALE),
                    style = MspTheme.type.navLabel.copy(letterSpacing = 0.12.em),
                    color = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = MspTheme.spacing.sm + MspTheme.spacing.xs)
                )
                grupo.datos.forEachIndexed { i, dato ->
                    val ultimo = g == 2 && i == grupo.datos.lastIndex
                    val valor = when {
                        dato.monto != null && ocultos -> MASKED_MONEY
                        dato.monto != null -> formatMoneyMxn(dato.monto.amount)
                        else -> dato.valor.ifBlank { SIN_DATO }
                    }
                    RenglonDeDatos(dato.rotulo, valor, unaColumna)
                    if (!ultimo) Separador()
                }
            }
            Spacer(Modifier.height(MspTheme.spacing.md))
        }
    }
}

@Composable
private fun RenglonDeDatos(rotulo: String, valor: String, unaColumna: Boolean) {
    val estiloRotulo = MspTheme.type.body.copy(fontSize = 13.5.sp, lineHeight = 17.sp)
    if (unaColumna) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 9.dp)
                .testTag(RENGLON_DE_DATOS_TAG)
        ) {
            Text(
                rotulo,
                style = estiloRotulo,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.testTag(ROTULO_DE_DATOS_TAG)
            )
            Text(
                valor,
                style = MspTheme.type.name.copy(lineHeight = 19.sp),
                color = MspTheme.colors.onSurface,
                modifier = Modifier.testTag(VALOR_DE_DATOS_TAG)
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = MspTheme.spacing.sm)
                .testTag(RENGLON_DE_DATOS_TAG),
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            Text(
                rotulo,
                style = estiloRotulo,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.testTag(ROTULO_DE_DATOS_TAG)
            )
            Text(
                valor,
                style = MspTheme.type.bodyStrong.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                color = MspTheme.colors.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .weight(1f)
                    .testTag(VALOR_DE_DATOS_TAG)
            )
        }
    }
}
