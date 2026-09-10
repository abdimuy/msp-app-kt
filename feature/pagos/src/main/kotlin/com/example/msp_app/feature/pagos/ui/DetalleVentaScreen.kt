package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.component.MspProgressBar
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.ui.components.BarraDeDetalle
import com.example.msp_app.feature.pagos.ui.components.CuadroDeEstado
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.EstadoEnGrande
import com.example.msp_app.feature.pagos.ui.components.FilaClaveValor
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import com.example.msp_app.feature.pagos.ui.components.RielDeMeses
import com.example.msp_app.feature.pagos.ui.components.RitmoDeSemanas
import com.example.msp_app.feature.pagos.ui.components.SIN_DATO
import com.example.msp_app.feature.pagos.ui.components.Tarjeta
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeGarantia
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeLiquidacion
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeSaldo
import com.example.msp_app.feature.pagos.ui.components.TresDatos
import com.example.msp_app.feature.pagos.ui.components.VerTodos
import java.time.format.DateTimeFormatter

/** `testTag` del título de la pantalla de venta — el producto. */
const val TITULO_DE_VENTA_TAG: String = "pagos_titulo_venta"

private val FECHA_DE_VENTA: DateTimeFormatter = DateTimeFormatter.ofPattern(
    "d MMM yyyy",
    BUSINESS_LOCALE
)

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * **Provee el tema.** `:app` nunca provee `MspTheme` —monta `MspappTheme`, el
 * Material legado— y su `NavHost` no envuelve a ningún destino: sin este bloque
 * la primera lectura de `MspTheme.colors` revienta con
 * `IllegalStateException("MspTheme ausente")` al abrir la pantalla. El
 * razonamiento completo —por qué en el `*Screen` y no en la ruta ni en la raíz
 * de `:app`, y cuál es la compuerta— está en el KDoc de [ListaDeClientesScreen].
 */
@Composable
fun DetalleVentaScreen(
    viewModel: DetalleVentaViewModel,
    onAtras: () -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int, Int?) -> Unit,
    onMasAcciones: (Int) -> Unit,
    onVerGarantia: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detalle = state.detalle
    MspTheme {
        DetalleVentaContent(
            state = state,
            onAtras = onAtras,
            onRegistrarAbono = { onRegistrarAbono(viewModel.ventaId) },
            // La visita se registra sobre la PUERTA, con esta cuenta como contexto:
            // el `clienteId` sale del detalle ya cargado, que es el único lugar del
            // módulo que lo conoce sin volver a leer Room.
            onRegistrarVisita = {
                detalle?.let { onRegistrarVisita(it.clienteId, viewModel.ventaId) }
            },
            onMasAcciones = { onMasAcciones(viewModel.ventaId) },
            onUsarLiquidacion = { onRegistrarAbono(viewModel.ventaId) },
            onVerAbonos = { onMasAcciones(viewModel.ventaId) },
            // El flujo de garantías de `:app` está indexado por VENTA
            // (`getGuaranteeSaleById(DOCTO_CC_ID)`), no por el `EXTERNAL_ID` de la
            // garantía: se manda el crédito, que es la llave que ese flujo entiende.
            onVerGarantia = { detalle?.let { onVerGarantia(it.creditoId) } },
            modifier = modifier
        )
    }
}

/**
 * El detalle de una venta: **el estado del catálogo de ocho en grande** con su
 * explicación, y abajo el historial como **ritmo + riel**.
 *
 * Composable PURO sobre [DetalleVentaUiState]. No deriva estados: consume
 * [com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo].
 */
@Composable
fun DetalleVentaContent(
    state: DetalleVentaUiState,
    onAtras: () -> Unit,
    onRegistrarAbono: () -> Unit,
    onRegistrarVisita: () -> Unit,
    onMasAcciones: () -> Unit,
    onUsarLiquidacion: () -> Unit,
    onVerAbonos: () -> Unit,
    onVerGarantia: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
    ) {
        val detalle = state.detalle
        Box(modifier = Modifier.weight(1f)) {
            when {
                state.cargando -> Cargando()
                detalle == null -> MensajeDeError(state.error, onAtras)
                else -> CuerpoDeLaVenta(
                    detalle = detalle,
                    onAtras = onAtras,
                    onUsarLiquidacion = onUsarLiquidacion,
                    onVerAbonos = onVerAbonos,
                    onVerGarantia = onVerGarantia
                )
            }
        }
        if (detalle != null) {
            DockDeAcciones(
                textoPrimario = "abonar " + formatMoneyMxn(detalle.parcialidad.amount),
                onPrimario = onRegistrarAbono,
                onVisita = onRegistrarVisita,
                onMasAcciones = onMasAcciones
            )
        }
    }
}

@Composable
private fun CuerpoDeLaVenta(
    detalle: DetalleVenta,
    onAtras: () -> Unit,
    onUsarLiquidacion: () -> Unit,
    onVerAbonos: () -> Unit,
    onVerGarantia: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        BarraDeDetalle(onAtras = onAtras)
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            CuadroDeEstado(estadoVisualDe(detalle.estado), lado = 22.dp)
            Text(
                text = detalle.clienteNombre,
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
        }
        Spacer(Modifier.height(MspTheme.spacing.sm))
        Text(
            text = detalle.titulo,
            style = MspTheme.type.detailTitle,
            color = MspTheme.colors.onSurface,
            modifier = Modifier.testTag(TITULO_DE_VENTA_TAG)
        )
        Text(
            text = listOfNotNull(
                detalle.folio,
                "crédito ${detalle.creditoId}",
                detalle.fechaVenta?.let { FECHA_DE_VENTA.format(it) }
            ).joinToString(" · "),
            style = MspTheme.type.subtitle,
            color = MspTheme.colors.onSurfaceMuted
        )

        Spacer(Modifier.height(MspTheme.spacing.md))
        EstadoEnGrande(detalle.estado)

        Spacer(Modifier.height(MspTheme.spacing.md))
        TarjetaDeSaldo(
            label = "saldo de esta venta",
            monto = detalle.saldo,
            pie = { PieDeLaVenta(detalle) }
        )

        detalle.liquidacion?.let { liquidacion ->
            LabelDeSeccion("liquidación de esta venta")
            TarjetaDeLiquidacion(
                label = "hoy liquida con",
                liquidacion = liquidacion,
                onUsar = onUsarLiquidacion
            )
        }

        LabelDeSeccion("ritmo · últimas 12 semanas")
        RitmoDeSemanas(detalle.historial)

        LabelDeSeccion("pagos")
        if (detalle.historial.meses.isEmpty()) {
            Tarjeta {
                Text(
                    text = "sin abonos todavía",
                    style = MspTheme.type.body,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        } else {
            RielDeMeses(detalle.historial.meses)
            Spacer(Modifier.height(MspTheme.spacing.sm))
            VerTodos("ver los ${detalle.historial.totalPagos} abonos", onVerAbonos)
        }

        if (detalle.productos.isNotEmpty()) {
            LabelDeSeccion("productos")
            detalle.productos.forEach { producto ->
                FilaClaveValor(
                    clave = producto.nombre,
                    valor = producto.importe?.let { formatMoneyMxn(it.amount) } ?: SIN_DATO
                )
            }
        }

        detalle.garantia?.let { garantia ->
            LabelDeSeccion("garantía")
            TarjetaDeGarantia(garantia = garantia, onVerGarantia = onVerGarantia)
        }

        LabelDeSeccion("datos de la venta")
        FilaClaveValor(
            "fecha de venta",
            detalle.fechaVenta?.let { FECHA_DE_VENTA.format(it) } ?: SIN_DATO
        )
        FilaClaveValor("total venta", formatMoneyMxn(detalle.totalVenta.amount))
        FilaClaveValor("precio de contado", formatMoneyMxn(detalle.precioContado.amount))
        FilaClaveValor("enganche", formatMoneyMxn(detalle.enganche.amount))
        FilaClaveValor("abonado", formatMoneyMxn(detalle.abonado.amount))
        FilaClaveValor("vendedor", detalle.vendedor)
        Spacer(Modifier.height(MspTheme.spacing.lg))
    }
}

@Composable
private fun PieDeLaVenta(detalle: DetalleVenta) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MspProgressBar(
            progress = detalle.avance,
            height = 6.dp,
            fillColor = MspTheme.colors.heroProgressFill,
            trackColor = MspTheme.colors.progressTrack
        )
        Spacer(Modifier.height(MspTheme.spacing.md))
        TresDatos(
            primero = { celda ->
                DatoDelPie("abonos", "${detalle.abonosPagados} / ${detalle.abonosTotales}", celda)
            },
            segundo = { celda ->
                DatoDelPie("parcialidad", formatMoneyMxn(detalle.parcialidad.amount), celda)
            },
            tercero = { celda ->
                DatoDelPie("frecuencia", detalle.frecuencia.ifBlank { SIN_DATO }, celda)
            }
        )
    }
}

@Composable
private fun DatoDelPie(clave: String, valor: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            // `.pgrid .k` del mock: `10px/700`, `.11em`, `uppercase`.
            text = clave.uppercase(BUSINESS_LOCALE),
            style = MspTheme.type.overline,
            color = MspTheme.colors.onSurfaceMuted
        )
        Spacer(Modifier.height(MspTheme.spacing.xs))
        Text(
            text = valor,
            style = MspTheme.type.metricSmall,
            color = MspTheme.colors.onSurface
        )
    }
}
