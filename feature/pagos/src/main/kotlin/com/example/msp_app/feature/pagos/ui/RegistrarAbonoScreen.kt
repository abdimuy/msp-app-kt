package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.pagos.domain.BloqueoDelAbono
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.ui.components.BandaDeBloqueo
import com.example.msp_app.feature.pagos.ui.components.BandaDeRegistrado
import com.example.msp_app.feature.pagos.ui.components.ChipsSugeridos
import com.example.msp_app.feature.pagos.ui.components.EncabezadoDelAbono
import com.example.msp_app.feature.pagos.ui.components.HojaDeConfirmacion
import com.example.msp_app.feature.pagos.ui.components.SelectorDeMetodo
import com.example.msp_app.feature.pagos.ui.components.TarjetaDeCaptura
import com.example.msp_app.feature.pagos.ui.components.TecladoDeMontos
import com.example.msp_app.feature.pagos.ui.components.TiraDeContexto

/** `testTag` del CTA que abre el paso uno de la confirmación. */
const val CTA_ABONO_TAG: String = "pagos_abono_cta"

/** `testTag` de la banda que dice por qué el abono no quedó. */
const val FALLO_DEL_ABONO_TAG: String = "pagos_abono_fallo"

/**
 * El destino: conecta el ViewModel con el contenido puro.
 *
 * [onRegistrado] se dispara UNA vez, con el id del abono — la Task 20 lo lleva
 * al ticket. Va en un `LaunchedEffect` con clave el propio id para que una
 * recomposición no lo vuelva a disparar.
 */
@Composable
fun RegistrarAbonoScreen(
    viewModel: RegistrarAbonoViewModel,
    onAtras: () -> Unit,
    onRegistrado: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RegistrarAbonoContent(
        state = state,
        onAtras = onAtras,
        onDigito = viewModel::onDigito,
        onPunto = viewModel::onPunto,
        onBorrar = viewModel::onBorrar,
        onMetodo = viewModel::onMetodo,
        onSugerido = viewModel::onSugerido,
        onRegistrar = viewModel::pedirConfirmacion,
        onConfirmar = viewModel::confirmar,
        onEditar = viewModel::descartarConfirmacion,
        modifier = modifier
    )
    val registrado = state.registrado
    if (registrado != null) {
        LaunchedEffect(registrado) { onRegistrado(registrado) }
    }
}

/**
 * Registrar abono, en sus cuatro estados del mock: **captura**, **bloqueo
 * duro**, **confirmar** y **monto raro**.
 *
 * Composable PURO sobre [RegistrarAbonoUiState]. No decide nada de dinero: el
 * veredicto llega hecho y aquí solo se pinta — el borde rojo de la captura, la
 * banda del bloqueo, el CTA apagado y el color de la hoja salen todos del mismo
 * [com.example.msp_app.feature.pagos.domain.VeredictoDelAbono].
 *
 * **Hueco para la Task 22:** la foto entra debajo del teclado, dentro de la
 * columna que hace scroll, y en la hoja entre la cifra y el flujo de saldos.
 * Ninguna de las tres piezas de seguridad —bloqueo, dos pasos, alerta roja—
 * necesita moverse para que quepa.
 */
@Composable
fun RegistrarAbonoContent(
    state: RegistrarAbonoUiState,
    onAtras: () -> Unit,
    onDigito: (Int) -> Unit,
    onPunto: () -> Unit,
    onBorrar: () -> Unit,
    onMetodo: (MetodoDeCobro) -> Unit,
    onSugerido: (Money) -> Unit,
    onRegistrar: () -> Unit,
    onConfirmar: () -> Unit,
    onEditar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            val venta = state.venta
            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.cargando -> CargandoElAbono()
                    venta == null -> MensajeDeErrorDelAbono(state.error, onAtras)
                    else -> CuerpoDelAbono(
                        state = state,
                        venta = venta,
                        onAtras = onAtras,
                        onDigito = onDigito,
                        onPunto = onPunto,
                        onBorrar = onBorrar,
                        onMetodo = onMetodo,
                        onSugerido = onSugerido
                    )
                }
            }
            if (venta != null) {
                DockDeRegistro(state = state, onRegistrar = onRegistrar)
            }
        }
        val confirmacion = state.confirmacion
        if (confirmacion != null && state.venta != null) {
            HojaDeConfirmacion(
                cliente = state.venta.clienteNombre,
                producto = state.venta.titulo,
                folio = state.venta.folio,
                importe = confirmacion.importe,
                metodo = confirmacion.metodo,
                veredicto = confirmacion.veredicto,
                esperadoHoy = MontosSugeridos.esperadoHoy(state.venta),
                onConfirmar = onConfirmar,
                onEditar = onEditar
            )
        }
    }
}

@Composable
private fun CuerpoDelAbono(
    state: RegistrarAbonoUiState,
    venta: DetalleVenta,
    onAtras: () -> Unit,
    onDigito: (Int) -> Unit,
    onPunto: () -> Unit,
    onBorrar: () -> Unit,
    onMetodo: (MetodoDeCobro) -> Unit,
    onSugerido: (Money) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MspTheme.spacing.md)
            .padding(bottom = MspTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        EncabezadoDelAbono(cliente = venta.clienteNombre, onAtras = onAtras)
        TiraDeContexto(folio = venta.folio, producto = venta.titulo, saldo = venta.saldo)
        TarjetaDeCaptura(
            monto = state.monto,
            metodo = state.metodo,
            conError = state.monto.esPositivo &&
                BloqueoDelAbono.EXCEDE_EL_SALDO in state.veredicto.bloqueos
        )
        MensajeDeBloqueo(state = state, venta = venta)
        if (state.registrado != null) BandaDeRegistrado()
        MensajeDeFallo(state.fallo)
        ChipsSugeridos(sugeridos = state.sugeridos, onSugerido = onSugerido)
        SelectorDeMetodo(seleccionado = state.metodo, onMetodo = onMetodo)
        TecladoDeMontos(onDigito = onDigito, onPunto = onPunto, onBorrar = onBorrar)
    }
}

/**
 * La banda del bloqueo duro. Silenciosa con el teclado en blanco: el CTA
 * apagado ya cubre "no hay nada que registrar".
 */
@Composable
private fun MensajeDeBloqueo(state: RegistrarAbonoUiState, venta: DetalleVenta) {
    val bloqueos = state.veredicto.bloqueos
    val mensaje = when {
        !state.monto.esPositivo -> null
        BloqueoDelAbono.VENTA_SIN_SALDO in bloqueos -> "esta venta ya no debe nada"
        BloqueoDelAbono.EXCEDE_EL_SALDO in bloqueos ->
            "el abono excede el saldo · máximo " + formatMoneyMxn(venta.saldo.amount)

        else -> null
    } ?: return
    BandaDeBloqueo(mensaje = mensaje)
}

@Composable
private fun MensajeDeFallo(fallo: FalloDelAbono?) {
    if (fallo == null) return
    val texto = when (fallo) {
        FalloDelAbono.VENTA_NO_ESTA -> "la venta ya no está en el teléfono"
        FalloDelAbono.SIN_COBRADOR -> "falta el cobrador, vuelve a entrar"
        FalloDelAbono.NO_SE_PUDO_GUARDAR -> "no se pudo guardar, intenta de nuevo"
        FalloDelAbono.BLOQUEADO -> "el monto no se puede registrar"
        FalloDelAbono.NO_SE_PUDO_VERIFICAR -> "no se pudo confirmar, vuelve a abrir"
    }
    BandaDeBloqueo(mensaje = texto, modifier = Modifier.testTag(FALLO_DEL_ABONO_TAG))
}

/**
 * El dock del mock (`.acts`): un solo CTA. **Apagado es apagado** — el
 * `enabled` sale del veredicto, y con él la pinta plana que el design system
 * usa para un botón deshabilitado.
 */
@Composable
private fun DockDeRegistro(state: RegistrarAbonoUiState, onRegistrar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MspTheme.colors.background)
            .padding(horizontal = MspTheme.spacing.md, vertical = MspTheme.spacing.sm)
    ) {
        MspPrimaryFieldButton(
            text = "registrar abono " + formatMoneyMxn(state.monto.importe.amount),
            onClick = onRegistrar,
            enabled = state.sePuedeRegistrar,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CTA_ABONO_TAG)
        )
    }
}

@Composable
private fun CargandoElAbono() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MspTheme.colors.brand, strokeWidth = 2.dp)
    }
}

@Composable
private fun MensajeDeErrorDelAbono(error: ErrorDeDetalle?, onAtras: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.md),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when (error) {
                ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO -> "esta venta no está en el teléfono"
                else -> "no se pudo cargar la venta"
            },
            style = MspTheme.type.body,
            color = MspTheme.colors.onSurfaceMuted
        )
        MspPrimaryFieldButton(
            text = "volver",
            onClick = onAtras,
            modifier = Modifier.padding(top = MspTheme.spacing.md)
        )
    }
}
