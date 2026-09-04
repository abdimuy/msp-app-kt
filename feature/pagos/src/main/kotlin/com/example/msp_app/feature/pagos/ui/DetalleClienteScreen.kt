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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.model.DetalleCliente
import com.example.msp_app.feature.pagos.ui.components.BarraDeDetalle
import com.example.msp_app.feature.pagos.ui.components.CuadroDeEstado
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.FilaClaveValor
import com.example.msp_app.feature.pagos.ui.components.FilaDeContacto
import com.example.msp_app.feature.pagos.ui.components.FilaDeVenta
import com.example.msp_app.feature.pagos.ui.components.LabelDeSeccion
import com.example.msp_app.feature.pagos.ui.components.SIN_DATO
import com.example.msp_app.feature.pagos.ui.components.Tarjeta
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeLiquidacion
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeSaldo
import com.example.msp_app.feature.pagos.ui.components.VerTodos
import java.time.format.DateTimeFormatter

/** `testTag` del título de la pantalla — el nombre del cliente. */
const val TITULO_DE_CLIENTE_TAG: String = "pagos_titulo_cliente"

/** `testTag` del aviso de cuentas pendientes del encabezado. */
const val AVISO_DE_CUENTAS_TAG: String = "pagos_aviso_cuentas"

private val DIA_Y_MES: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", BUSINESS_LOCALE)

/** El destino: conecta el ViewModel con el contenido puro. */
@Composable
fun DetalleClienteScreen(
    viewModel: DetalleClienteViewModel,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: (Int) -> Unit,
    onRegistrarVisita: (Int) -> Unit,
    onMasAcciones: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    DetalleClienteContent(
        state = state,
        onAtras = onAtras,
        onAbrirVenta = onAbrirVenta,
        onRegistrarAbono = { onRegistrarAbono(viewModel.clienteId) },
        onRegistrarVisita = { onRegistrarVisita(viewModel.clienteId) },
        onMasAcciones = onMasAcciones,
        onUsarLiquidacion = { onRegistrarAbono(viewModel.clienteId) },
        onVerContactos = onMasAcciones,
        modifier = modifier
    )
}

/**
 * El detalle de cliente. **El nombre del cliente es el título** y sus ventas
 * van dentro, cada una con su estado — decisiones del `task-16-brief.md`.
 *
 * Composable PURO sobre [DetalleClienteUiState]: no lee puertos, no deriva
 * estados y no emite telemetría. Lo primero lo hace el ViewModel, lo segundo el
 * catálogo de la Task 14, y lo tercero
 * [com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo] una
 * vez por sincronización — emitir desde aquí sería emitir una vez por
 * recomposición.
 */
@Composable
fun DetalleClienteContent(
    state: DetalleClienteUiState,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onRegistrarAbono: () -> Unit,
    onRegistrarVisita: () -> Unit,
    onMasAcciones: () -> Unit,
    onUsarLiquidacion: () -> Unit,
    onVerContactos: () -> Unit,
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
                else -> CuerpoDelCliente(
                    detalle = detalle,
                    onAtras = onAtras,
                    onAbrirVenta = onAbrirVenta,
                    onMasAcciones = onMasAcciones,
                    onUsarLiquidacion = onUsarLiquidacion,
                    onVerContactos = onVerContactos
                )
            }
        }
        if (detalle != null) {
            DockDeAcciones(
                textoPrimario = "registrar abono",
                onPrimario = onRegistrarAbono,
                onVisita = onRegistrarVisita,
                onMasAcciones = onMasAcciones
            )
        }
    }
}

@Composable
private fun CuerpoDelCliente(
    detalle: DetalleCliente,
    onAtras: () -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onMasAcciones: () -> Unit,
    onUsarLiquidacion: () -> Unit,
    onVerContactos: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        BarraDeDetalle(onAtras = onAtras, onMasAcciones = onMasAcciones)
        EncabezadoDelCliente(detalle)
        Spacer(Modifier.height(MspTheme.spacing.md))
        TarjetaDeSaldo(
            label = "saldo total",
            monto = detalle.saldoTotal,
            pie = { PieDelSaldo(detalle) }
        )
        EstadoCuentaUi.avisoDeCuentas(detalle.ventas.map { it.estado })?.let { aviso ->
            Spacer(Modifier.height(MspTheme.spacing.sm))
            Aviso(aviso)
        }

        LabelDeSeccion("sus ventas")
        detalle.ventas.forEach { venta ->
            FilaDeVenta(venta = venta, onAbrir = { onAbrirVenta(venta.ventaId) })
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }

        detalle.liquidacion?.let { liquidacion ->
            LabelDeSeccion("liquidación")
            TarjetaDeLiquidacion(
                label = "hoy liquida todo con",
                liquidacion = liquidacion,
                onUsar = onUsarLiquidacion
            )
        }

        if (detalle.contactos.isNotEmpty()) {
            LabelDeSeccion("últimos contactos")
            detalle.contactos.forEach { FilaDeContacto(it) }
            Spacer(Modifier.height(MspTheme.spacing.sm))
            VerTodos("ver los ${detalle.totalContactos} contactos", onVerContactos)
        }

        detalle.ficha?.let { ficha ->
            LabelDeSeccion("lo que hay que saber")
            Tarjeta {
                Text(
                    text = ficha,
                    style = MspTheme.type.body,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }

        LabelDeSeccion("datos del cliente")
        FilaClaveValor("zona", detalle.zona)
        FilaClaveValor("aval o responsable", detalle.aval)
        FilaClaveValor("teléfono", detalle.telefono)
        FilaClaveValor("dirección", detalle.direccion)
        Spacer(Modifier.height(MspTheme.spacing.lg))
    }
}

@Composable
private fun EncabezadoDelCliente(detalle: DetalleCliente) {
    Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm + MspTheme.spacing.xs)) {
        Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
            detalle.ventas.take(CUADROS_EN_EL_CLUSTER).forEach { venta ->
                CuadroDeEstado(estadoVisualDe(venta.estado), lado = 22.dp)
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = detalle.nombre,
                style = MspTheme.type.detailTitle,
                color = MspTheme.colors.onSurface,
                modifier = Modifier.testTag(TITULO_DE_CLIENTE_TAG)
            )
            Text(
                text = listOf(detalle.telefono, detalle.zona).filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifBlank { SIN_DATO },
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
            Text(
                text = detalle.direccion.ifBlank { SIN_DATO },
                style = MspTheme.type.subtitle,
                color = MspTheme.colors.onSurfaceMuted
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
            Text(
                text = "${detalle.cuentas} cuentas",
                style = MspTheme.type.chipLabel,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier
                    .clip(MspTheme.shapes.control)
                    .background(MspTheme.colors.surface2)
                    .padding(horizontal = MspTheme.spacing.sm, vertical = MspTheme.spacing.xs)
            )
        }
    }
}

@Composable
private fun PieDelSaldo(detalle: DetalleCliente) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${detalle.cuentas} cuentas activas",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = detalle.ultimaVisita
                ?.let { "última visita " + DIA_Y_MES.format(AppTime.toBusinessDate(it)) }
                ?: "sin visitas registradas",
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
    }
}

/** La banda del encabezado (`.tip`): ámbar de "vuelvo", nunca rojo de "no cae". */
@Composable
private fun Aviso(texto: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.field)
            .background(MspTheme.colors.statusPartialTint)
            .padding(MspTheme.spacing.md)
    ) {
        Text(
            text = texto,
            style = MspTheme.type.bodyStrong,
            color = MspTheme.colors.statusPartial,
            modifier = Modifier.testTag(AVISO_DE_CUENTAS_TAG)
        )
    }
}

@Composable
internal fun Cargando() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MspTheme.colors.brand)
    }
}

@Composable
internal fun MensajeDeError(error: ErrorDeDetalle?, onAtras: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when (error) {
                ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO -> "no está en el teléfono"
                else -> "no se pudo abrir"
            },
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        VerTodos("volver", onAtras)
    }
}

private const val CUADROS_EN_EL_CLUSTER = 4
