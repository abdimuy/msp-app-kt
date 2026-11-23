package com.example.msp_app.feature.garantias.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.ChipStatus
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.component.MspStatusChip
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.garantias.application.FolioEnBandeja
import com.example.msp_app.feature.garantias.domain.model.EstadoLocalDeFolio
import com.example.msp_app.feature.garantias.ui.components.AvisoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EncabezadoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EstadoVacioDeGarantia

/** Mínimo tocable del repo; cada fila de la bandeja lo respeta. */
private val ALTO_DE_FILA = 50.dp

/** El rótulo corto de un folio: su id del servidor, o los primeros del local. */
private const val LARGO_DEL_ID_LOCAL = 8

/**
 * El contenido de la bandeja: puro, sin ViewModel ni Hilt.
 *
 * Recibe [state] y los callbacks, así una captura Roborazzi la pinta con datos
 * de mentira y sin el grafo de la app. Se llama `*Contenido` y no `*Screen` a
 * propósito: la compuerta `CadaPantallaSeAlcanzaDesdeElGrafoTest` sólo exige
 * alcanzabilidad de los `*Screen`, y la única pantalla del módulo es
 * `GarantiasV2Screen`.
 */
@Composable
fun BandejaDeGarantiasContenido(
    state: BandejaUiState,
    onAtras: () -> Unit,
    onAbrirFolio: () -> Unit,
    onFolio: (String) -> Unit,
    onRecargar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        EncabezadoDeGarantia(titulo = TextosDeGarantias.TITULO_BANDEJA, onAtras = onAtras)
        MspPrimaryFieldButton(
            text = TextosDeGarantias.NUEVO_FOLIO,
            onClick = onAbrirFolio,
            modifier = Modifier.fillMaxWidth()
        )
        when {
            state.fallo -> AvisoDeGarantiaConReintento(onRecargar = onRecargar)
            state.cargando -> Text(
                text = TextosDeGarantias.CARGANDO,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            state.folios.isEmpty() -> EstadoVacioDeGarantia(
                titulo = TextosDeGarantias.SIN_FOLIOS,
                pie = TextosDeGarantias.SIN_FOLIOS_PIE
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                items(items = state.folios, key = { it.folio.id }) { folio ->
                    FilaDeFolio(folio = folio, onClic = { onFolio(folio.folio.id) })
                }
            }
        }
    }
}

/** El aviso de la lectura que falló, con su botón de reintento. */
@Composable
private fun AvisoDeGarantiaConReintento(onRecargar: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        AvisoDeGarantia(texto = TextosDeGarantias.NO_SE_PUDO_LEER, esError = true)
        MspPrimaryFieldButton(
            text = TextosDeGarantias.RECARGAR,
            onClick = onRecargar,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Un folio de la bandeja: a qué folio, de dónde salió y cuánto falta. */
@Composable
private fun FilaDeFolio(folio: FolioEnBandeja, onClic: () -> Unit) {
    MspCard(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ALTO_DE_FILA),
        onClick = onClic
    ) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = folio.folio.folioId ?: folio.folio.id.take(LARGO_DEL_ID_LOCAL),
                style = MspTheme.type.listTitle,
                color = MspTheme.colors.onSurface
            )
            Text(
                text = "${TextosDeGarantias.textoDe(folio.folio.origen)} · " +
                    AppTime.formatForDisplay(folio.folio.abiertoEn),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            MspStatusChip(
                status = folio.folio.estadoLocal.aChip(),
                text = TextosDeGarantias.textoDe(folio.folio.estadoLocal)
            )
            if (folio.tienePendientes) {
                Text(
                    text = "${TextosDeGarantias.PENDIENTES}: " +
                        "${folio.comandosPendientes + folio.fotosPendientes}",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.statusPartial
                )
            }
            folio.ultimoError?.let { error ->
                Text(
                    text = error,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.danger
                )
            }
        }
    }
}

/** El chip de un estado local del folio. */
private fun EstadoLocalDeFolio.aChip(): ChipStatus = when (this) {
    EstadoLocalDeFolio.PENDIENTE_ENVIO -> ChipStatus.Pending
    EstadoLocalDeFolio.ABIERTO -> ChipStatus.Promise
    EstadoLocalDeFolio.RECHAZADO -> ChipStatus.Overdue
}
