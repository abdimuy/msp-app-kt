@file:Suppress("TooManyFunctions") // una pieza por bloque de la entrega.

package com.example.msp_app.feature.garantias.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.PrimaryFieldButtonVariant
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.garantias.domain.ReglasDeEntrega
import com.example.msp_app.feature.garantias.domain.model.FirmaDeConformidad
import com.example.msp_app.feature.garantias.domain.model.Ubicacion
import com.example.msp_app.feature.garantias.ui.components.AvisoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EncabezadoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EstadoDelPadDeFirma
import com.example.msp_app.feature.garantias.ui.components.EtiquetaDeSeccion
import com.example.msp_app.feature.garantias.ui.components.PadDeFirma

/** El alto del recuadro de la firma: cómodo para el dedo y para el ojo. */
private val ALTO_DEL_PAD = 200.dp

/**
 * El contenido de la entrega: puro, sin ViewModel ni Hilt.
 *
 * El botón de guardar se apaga con [ReglasDeEntrega] y explica el primer
 * bloqueo —sin GPS o sin firma—, la misma regla que el caso de uso vuelve a
 * evaluar antes de escribir. La firma llega por [estadoDelPad]: el wrapper de la
 * ruta la posee y pide el PNG al guardar.
 */
@Composable
fun EntregaContenido(
    state: EntregaUiState,
    estadoDelPad: EstadoDelPadDeFirma,
    onLimpiarFirma: () -> Unit,
    onPedirUbicacion: () -> Unit,
    onGuardar: () -> Unit,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bloqueos = ReglasDeEntrega.bloqueosDe(
        ubicacion = state.ubicacion,
        firma = if (estadoDelPad.hayTrazos) {
            FirmaDeConformidad(
                rutaArchivo = "",
                tamanoBytes = 1
            )
        } else {
            null
        }
    )
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        EncabezadoDeGarantia(titulo = TextosDeGarantias.TITULO_ENTREGA, onAtras = onAtras)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
        ) {
            SeccionDeUbicacion(
                ubicacion = state.ubicacion,
                pidiendoUbicacion = state.pidiendoUbicacion,
                onPedirUbicacion = onPedirUbicacion
            )
            SeccionDeFirma(estadoDelPad = estadoDelPad, onLimpiarFirma = onLimpiarFirma)
            state.fallo?.let { fallo ->
                AvisoDeGarantia(texto = TextosDeGarantias.textoDe(fallo), esError = true)
            }
            if (state.falloTecnico) {
                AvisoDeGarantia(
                    texto = TextosDeGarantias.NO_SE_PUDO_GUARDAR,
                    esError = true
                )
            }
            bloqueos.firstOrNull()?.let { bloqueo ->
                AvisoDeGarantia(texto = TextosDeGarantias.textoDe(bloqueo))
            }
        }
        MspPrimaryFieldButton(
            text = if (state.guardando) {
                TextosDeGarantias.GUARDANDO_ENTREGA
            } else {
                TextosDeGarantias.GUARDAR_ENTREGA
            },
            onClick = onGuardar,
            enabled = bloqueos.isEmpty() && !state.guardando,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * El GPS de la entrega, que sí es obligatorio.
 *
 * Mientras no haya coordenadas la pantalla lo dice con "Esperando ubicación" y
 * ofrece reintentar: el proveedor puede tardar o haberse negado, y el cobrador
 * no puede quedarse sin forma de entregar.
 */
@Composable
private fun SeccionDeUbicacion(
    ubicacion: Ubicacion?,
    pidiendoUbicacion: Boolean,
    onPedirUbicacion: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.UBICACION_DE_LA_ENTREGA)
        Text(
            text = when {
                pidiendoUbicacion -> TextosDeGarantias.ESPERANDO_UBICACION
                ubicacion != null ->
                    "${TextosDeGarantias.UBICACION_REGISTRADA}: " +
                        "${ubicacion.latitud}, ${ubicacion.longitud}"
                else -> TextosDeGarantias.SIN_UBICACION
            },
            style = MspTheme.type.caption,
            color = MspTheme.colors.onSurfaceMuted
        )
        MspPrimaryFieldButton(
            text = TextosDeGarantias.PEDIR_UBICACION,
            onClick = onPedirUbicacion,
            enabled = !pidiendoUbicacion,
            modifier = Modifier.fillMaxWidth(),
            variant = PrimaryFieldButtonVariant.Ghost
        )
    }
}

/** El recuadro donde el cliente firma, con su botón de borrar. */
@Composable
private fun SeccionDeFirma(estadoDelPad: EstadoDelPadDeFirma, onLimpiarFirma: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.FIRMA)
        PadDeFirma(
            estado = estadoDelPad,
            modifier = Modifier
                .fillMaxWidth()
                .height(ALTO_DEL_PAD)
        )
        MspPrimaryFieldButton(
            text = TextosDeGarantias.LIMPIAR_FIRMA,
            onClick = onLimpiarFirma,
            enabled = estadoDelPad.hayTrazos,
            modifier = Modifier.fillMaxWidth(),
            variant = PrimaryFieldButtonVariant.Ghost
        )
    }
}
