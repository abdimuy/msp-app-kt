package com.example.msp_app.feature.visitas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.MspPrinterRow
import com.example.msp_app.core.designsystem.component.MspTicketBanner
import com.example.msp_app.core.designsystem.component.MspTicketFacsimile
import com.example.msp_app.core.designsystem.component.MspTicketSummary
import com.example.msp_app.core.designsystem.component.MspTicketTopBar
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.feature.visitas.ui.components.ATRAS_DEL_TICKET_TAG
import com.example.msp_app.feature.visitas.ui.components.BANDA_DEL_TICKET_TAG
import com.example.msp_app.feature.visitas.ui.components.CAMBIAR_IMPRESORA_TAG
import com.example.msp_app.feature.visitas.ui.components.CERRAR_IMPRESION_TAG
import com.example.msp_app.feature.visitas.ui.components.IMPRESORA_TAG
import com.example.msp_app.feature.visitas.ui.components.IMPRIMIR_TAG
import com.example.msp_app.feature.visitas.ui.components.RESUMEN_DEL_TICKET_TAG
import com.example.msp_app.feature.visitas.ui.components.VISTA_PREVIA_TAG

private const val TITULO = "ticket de visita"
private const val CTA_IMPRIMIR = "imprimir ticket"
private const val CTA_CAMBIAR = "cambiar impresora"
private const val CTA_REINTENTAR = "reintentar"
private const val CTA_CERRAR = "cerrar"
private const val CARGANDO = "cargando"
private const val SIN_IMPRESORAS = "empareja una impresora"
private const val ELIGE_IMPRESORA = "elige impresora"
private const val FUERA_TITULO = "fuera del día"
private const val FUERA_DETALLE = "solo se imprime hoy"
private const val COPIA_TITULO = "copia ya impresa"
private const val ENVIANDO_TITULO = "enviando el ticket"
private const val ENVIANDO_DETALLE = "no apagues la impresora"
private const val IMPRESO_TITULO = "ticket impreso"
private const val IMPRESO_DETALLE = "ya quedó registrado"
private const val FALLO_TITULO = "no se imprimió"
private const val ETIQUETA_SALDO = "saldo total"
private const val ETIQUETA_PROMETIO = "prometió"
private const val ETIQUETA_CUENTAS = "cuentas"
private const val SIN_MONTO = "sin monto"

/** Pantalla del ticket de visita, cableada a su ViewModel. */
@Composable
fun TicketDeVisitaScreen(
    viewModel: TicketDeVisitaViewModel,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TicketDeVisitaContent(
        state = state,
        onAtras = onAtras,
        onImprimir = viewModel::imprimir,
        onCambiarImpresora = viewModel::cambiarImpresora,
        onElegirImpresora = viewModel::elegirImpresora,
        onCerrarImpresion = viewModel::cerrarImpresion,
        onReintentar = viewModel::cargar,
        modifier = modifier
    )
}

/**
 * El contenido, puro. Misma estructura que el ticket de pago: barra, banda de
 * estado arriba (la regla del día manda), vista previa del papel y dock abajo.
 *
 * **El CTA apagado se ve apagado:** `enabled=false` en [MspPrimaryFieldButton]
 * pinta relleno `outline` + texto `onSurfaceMuted` y apaga la sombra.
 */
@Composable
@Suppress("LongParameterList") // una lambda por accion de la pantalla; agruparlas las esconde.
fun TicketDeVisitaContent(
    state: TicketDeVisitaUiState,
    onAtras: () -> Unit,
    onImprimir: () -> Unit,
    onCambiarImpresora: () -> Unit,
    onElegirImpresora: (PrinterDevice) -> Unit,
    onCerrarImpresion: () -> Unit,
    onReintentar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            .padding(horizontal = MspTheme.spacing.md)
    ) {
        MspTicketTopBar(
            titulo = TITULO,
            onAtras = onAtras,
            atrasModifier = Modifier.testTag(ATRAS_DEL_TICKET_TAG)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            when {
                state.cargando -> Nota(CARGANDO)
                state.error != null -> BloqueDeError(state.error, onReintentar)
                else -> CuerpoDelTicket(state, onElegirImpresora)
            }
        }
        if (state.error == null && !state.cargando) {
            DockDelTicket(state, onImprimir, onCambiarImpresora, onCerrarImpresion)
        }
    }
}

@Composable
private fun CuerpoDelTicket(
    state: TicketDeVisitaUiState,
    onElegirImpresora: (PrinterDevice) -> Unit
) {
    BandaDeEstado(state)
    // Las cifras primero, en la tipografía que SÍ crece con la preferencia del
    // usuario; el facsímil del papel debajo. Ver el KDoc de `ResumenDelTicket`.
    state.ticket?.let { ticket ->
        val promesa = ticket.promesa
        MspTicketSummary(
            cliente = ticket.cliente,
            etiquetaPrincipal = ETIQUETA_SALDO,
            montoPrincipal = formatMoneyMxn(ticket.saldoTotal.amount),
            etiquetaSecundaria = if (promesa == null) ETIQUETA_CUENTAS else ETIQUETA_PROMETIO,
            montoSecundario = when {
                promesa == null -> ticket.cuentas.size.toString()
                // "dijo cuándo pero no cuánto" es un caso real de campo; un cero
                // ahí significaría "prometió no pagar", que es otro desenlace.
                promesa.monto == null -> SIN_MONTO
                else -> formatMoneyMxn(promesa.monto.amount)
            },
            modifier = Modifier.testTag(RESUMEN_DEL_TICKET_TAG)
        )
    }
    MspTicketFacsimile(state.vistaPrevia, Modifier.testTag(VISTA_PREVIA_TAG))
    if (state.impresion.fase == FaseDeImpresionDeVisita.ELIGIENDO) {
        PickerDeImpresoras(state, onElegirImpresora)
    }
}

/**
 * Prioridad deliberada: primero lo que IMPIDE imprimir (fuera del día), luego lo
 * que está pasando y al final lo que advierte (copia).
 */
@Composable
private fun BandaDeEstado(state: TicketDeVisitaUiState) {
    val colors = MspTheme.colors
    when {
        state.fueraDelDia -> MspTicketBanner(
            titulo = FUERA_TITULO,
            detalle = FUERA_DETALLE,
            fondo = colors.statusOverdueTint,
            contenido = colors.statusOverdue,
            modifier = Modifier.testTag(BANDA_DEL_TICKET_TAG)
        )

        state.impresion.fase == FaseDeImpresionDeVisita.IMPRIMIENDO -> MspTicketBanner(
            titulo = ENVIANDO_TITULO,
            detalle = ENVIANDO_DETALLE,
            fondo = colors.statusInfoTint,
            contenido = colors.statusInfo,
            modifier = Modifier.testTag(BANDA_DEL_TICKET_TAG)
        )

        state.impresion.fase == FaseDeImpresionDeVisita.FALLO -> MspTicketBanner(
            titulo = FALLO_TITULO,
            detalle = state.impresion.mensaje.orEmpty(),
            fondo = colors.dangerTint,
            contenido = colors.danger,
            modifier = Modifier.testTag(BANDA_DEL_TICKET_TAG)
        )

        state.impresion.fase == FaseDeImpresionDeVisita.IMPRESO -> MspTicketBanner(
            titulo = IMPRESO_TITULO,
            detalle = IMPRESO_DETALLE,
            fondo = colors.statusPaidTint,
            contenido = colors.statusPaid,
            modifier = Modifier.testTag(BANDA_DEL_TICKET_TAG)
        )

        state.esReimpresion -> MspTicketBanner(
            titulo = COPIA_TITULO,
            detalle = state.detalleDeCopia,
            fondo = colors.statusPendingTint,
            contenido = colors.statusPending,
            modifier = Modifier.testTag(BANDA_DEL_TICKET_TAG)
        )

        else -> Unit
    }
}

@Composable
private fun PickerDeImpresoras(
    state: TicketDeVisitaUiState,
    onElegirImpresora: (PrinterDevice) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
    ) {
        Nota(if (state.impresion.disponibles.isEmpty()) SIN_IMPRESORAS else ELIGE_IMPRESORA)
        state.impresion.disponibles.forEach { dispositivo ->
            MspPrinterRow(
                nombre = dispositivo.name,
                direccion = dispositivo.address,
                elegida = dispositivo.address == state.impresion.impresora?.address,
                onClick = { onElegirImpresora(dispositivo) },
                modifier = Modifier.testTag(IMPRESORA_TAG + dispositivo.address)
            )
        }
    }
}

@Composable
private fun DockDelTicket(
    state: TicketDeVisitaUiState,
    onImprimir: () -> Unit,
    onCambiarImpresora: () -> Unit,
    onCerrarImpresion: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        MspPrimaryFieldButton(
            text = if (state.impresion.fase == FaseDeImpresionDeVisita.FALLO) {
                CTA_REINTENTAR
            } else {
                CTA_IMPRIMIR
            },
            variant = PrimaryFieldButtonVariant.Primary,
            enabled = state.sePuedeImprimir,
            onClick = onImprimir,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(IMPRIMIR_TAG)
        )
        MspPrimaryFieldButton(
            text = CTA_CAMBIAR,
            variant = PrimaryFieldButtonVariant.Ghost,
            enabled = state.sePuedeImprimir,
            onClick = onCambiarImpresora,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CAMBIAR_IMPRESORA_TAG)
        )
        // Salida del picker y del aviso de fallo. Sin esto, abrir la lista de
        // impresoras —o fallar una impresión— dejaba al cobrador sin forma de
        // volver a la pantalla sin salir del ticket: una banda roja que no se
        // puede cerrar es una trampa chica pero real.
        if (state.impresion.fase == FaseDeImpresionDeVisita.ELIGIENDO ||
            state.impresion.fase == FaseDeImpresionDeVisita.FALLO
        ) {
            MspPrimaryFieldButton(
                text = CTA_CERRAR,
                variant = PrimaryFieldButtonVariant.Ghost,
                onClick = onCerrarImpresion,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CERRAR_IMPRESION_TAG)
            )
        }
    }
}

@Composable
private fun BloqueDeError(error: ErrorDelTicketDeVisita, onReintentar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        MspTicketBanner(
            titulo = FALLO_TITULO,
            detalle = error.mensaje,
            fondo = MspTheme.colors.dangerTint,
            contenido = MspTheme.colors.danger,
            modifier = Modifier.testTag(BANDA_DEL_TICKET_TAG)
        )
        if (error == ErrorDelTicketDeVisita.NO_SE_PUDO_LEER) {
            MspPrimaryFieldButton(
                text = CTA_REINTENTAR,
                variant = PrimaryFieldButtonVariant.Ghost,
                onClick = onReintentar,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Nota(texto: String) {
    Text(
        text = texto,
        style = MspTheme.type.caption,
        color = MspTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(vertical = MspTheme.spacing.xs)
    )
}
