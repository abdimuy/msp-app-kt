package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspInitialsAvatar
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.EstadoCuentaUi
import com.example.msp_app.feature.pagos.ui.SegmentoDeCobranza
import com.example.msp_app.feature.pagos.ui.estadoVisualDe

/** Prefijo de `testTag` de cada chip: `"${CHIP_DE_SEGMENTO_TAG}todos"`, etc. */
const val CHIP_DE_SEGMENTO_TAG: String = "pagos_chip_"

/** `testTag` de la tarjeta de un cliente en la lista. */
const val FILA_DE_CLIENTE_TAG: String = "pagos_fila_cliente"

/**
 * Los chips de segmento, con el conteo de clientes de cada uno.
 *
 * ## Por qué no se usa `MspSegmentChips` del design system
 *
 * Se midió: sus segmentos son `10.dp` de padding vertical sobre un
 * `segmentLabel` de 13sp, o sea ~38dp de alto tocable — por debajo de los 50px
 * que exige el plan (la Task 16 shipeó un control de 49.5dp y tuvo que
 * corregirlo). Además es un **selector segmentado** de 2-3 opciones que
 * reparten el ancho por igual; aquí hay cuatro filtros con conteo que a
 * `MUY_GRANDE` (2.0) no caben en 360dp y tienen que poder desplazarse.
 *
 * Cada chip se fija en [MspTheme.spacing.touchTarget] (56dp), que es el token
 * que el design system ya define para esto y va holgado sobre el mínimo.
 */
@Composable
fun ChipsDeSegmento(
    seleccionado: SegmentoDeCobranza,
    conteos: Map<SegmentoDeCobranza, Int>,
    onElegir: (SegmentoDeCobranza) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SegmentoDeCobranza.entries.forEach { segmento ->
            ChipDeSegmento(
                segmento = segmento,
                cuantos = conteos[segmento] ?: 0,
                activo = segmento == seleccionado,
                onElegir = { onElegir(segmento) }
            )
        }
    }
}

@Composable
private fun ChipDeSegmento(
    segmento: SegmentoDeCobranza,
    cuantos: Int,
    activo: Boolean,
    onElegir: () -> Unit
) {
    Surface(
        onClick = onElegir,
        // El chip activo va en `brand` — es el control protagónico de la
        // pantalla. Nunca en `statusPaid`: el verde es solo estado.
        color = if (activo) MspTheme.colors.brand else MspTheme.colors.surface,
        shape = MspTheme.shapes.chip,
        modifier = Modifier
            .heightIn(min = MspTheme.spacing.touchTarget)
            .testTag(CHIP_DE_SEGMENTO_TAG + segmento.name.lowercase())
    ) {
        Row(
            // Padding justo: con `md` los cuatro chips no caben en 360dp ni
            // siquiera a escala NORMAL y el último quedaba cortado. Con `sm`
            // caben; el alto tocable no depende de esto y sigue en 56dp.
            modifier = Modifier.padding(horizontal = MspTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = segmento.etiqueta,
                style = MspTheme.type.chipLabel,
                color = if (activo) MspTheme.colors.onBrand else MspTheme.colors.onSurfaceMuted,
                maxLines = 1
            )
            Text(
                text = cuantos.toString(),
                style = MspTheme.type.captionStrong,
                color = if (activo) MspTheme.colors.onBrand else MspTheme.colors.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Un CLIENTE en la lista: su encabezado y **sus ventas dentro**, cada una con
 * el mismo `FilaDeVenta` que ya pinta el detalle de cliente.
 *
 * Se reusa ese componente a propósito: las dos pantallas hablan del mismo
 * estado del catálogo de ocho y una segunda presentación del mismo estado es
 * exactamente cómo dos pantallas empiezan a decir cosas distintas del mismo
 * dato.
 */
@Composable
fun FilaDeCliente(
    cliente: ClienteEnLista,
    onAbrirCliente: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Tarjeta(modifier = Modifier.testTag(FILA_DE_CLIENTE_TAG), onClick = onAbrirCliente) {
            EncabezadoDeCliente(cliente)
        }
        Spacer(Modifier.height(MspTheme.spacing.sm))
        cliente.ventas.forEach { enLista ->
            FilaDeVenta(
                venta = enLista.venta,
                onAbrir = { onAbrirVenta(enLista.venta.ventaId) },
                modifier = Modifier.padding(start = SANGRIA_DE_LA_VENTA)
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }
    }
}

@Composable
private fun EncabezadoDeCliente(cliente: ClienteEnLista) {
    // A escala grande la cifra no cabe junto al nombre en 360dp y se baja a su
    // propio renglón — mismo criterio que `TresDatos`, y por la misma razón: un
    // dato que se sale de la pantalla es información perdida.
    val enUnaFila = LocalFontSizeLevel.current == FontSizeLevel.NORMAL
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)
        ) {
            MspInitialsAvatar(initials = cliente.iniciales)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cliente.nombre,
                    style = MspTheme.type.listTitle,
                    color = MspTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(cliente.telefono, cliente.zona)
                        .filter { it.isNotBlank() }
                        .joinToString(SEPARADOR_DE_META)
                        .ifBlank { SIN_DATO },
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (enUnaFila) SaldoDelCliente(cliente)
        }
        if (!enUnaFila) {
            Spacer(Modifier.height(MspTheme.spacing.sm))
            SaldoDelCliente(cliente)
        }
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Text(
            text = cliente.direccion.ifBlank { SIN_DATO },
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            // El racimo de cuadros: un estado por cuenta, con su ícono. Es lo
            // que hace visible de un vistazo que esta puerta tiene dos deudas
            // en situaciones distintas — el caso que la lista por venta partía
            // en dos personas.
            cliente.ventas.take(CUADROS_EN_EL_RACIMO).forEach { enLista ->
                CuadroDeEstado(estadoVisualDe(enLista.venta.estado), lado = LADO_DEL_CUADRO)
            }
            Spacer(Modifier.weight(1f))
            Aviso(cliente)
        }
    }
}

@Composable
private fun SaldoDelCliente(cliente: ClienteEnLista) {
    MspMoneyText(
        amount = cliente.saldoTotal.amount,
        style = MspTheme.type.amountRow,
        color = MspTheme.colors.onSurface
    )
}

/**
 * "faltan 2 de 3" cuando quedan cuentas por trabajar, "N cuentas" cuando no.
 * El primer texto lo calcula [EstadoCuentaUi.avisoDeCuentas], el mismo que usa
 * el encabezado del detalle: el número que dice cuántas puertas quedan abiertas
 * tiene un solo dueño.
 */
@Composable
private fun Aviso(cliente: ClienteEnLista) {
    val pendientes = EstadoCuentaUi.avisoDeCuentas(cliente.ventas.map { it.venta.estado })
    Box(
        modifier = Modifier
            .background(
                color = if (pendientes == null) {
                    MspTheme.colors.surface2
                } else {
                    MspTheme.colors.statusPartialTint
                },
                shape = MspTheme.shapes.chip
            )
            .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
    ) {
        Text(
            text = pendientes ?: "${cliente.cuentas} cuentas",
            style = MspTheme.type.chipLabel,
            color = if (pendientes == null) {
                MspTheme.colors.onSurfaceMuted
            } else {
                MspTheme.colors.statusPartial
            },
            maxLines = 1
        )
    }
}

/** Las ventas van sangradas: la puerta manda, sus deudas cuelgan de ella. */
private val SANGRIA_DE_LA_VENTA = 12.dp

private val LADO_DEL_CUADRO = 22.dp

private const val CUADROS_EN_EL_RACIMO = 4

private const val SEPARADOR_DE_META = " · "
