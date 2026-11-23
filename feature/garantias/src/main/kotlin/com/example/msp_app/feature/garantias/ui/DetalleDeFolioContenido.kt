@file:Suppress("TooManyFunctions") // una pieza por bloque del detalle.

package com.example.msp_app.feature.garantias.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspPrimaryFieldButton
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.garantias.application.ArticuloDelFolio
import com.example.msp_app.feature.garantias.application.EstadoDeLaLectura
import com.example.msp_app.feature.garantias.domain.model.EventoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.AvisoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EncabezadoDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EstadoVacioDeGarantia
import com.example.msp_app.feature.garantias.ui.components.EtiquetaDeSeccion
import com.example.msp_app.feature.garantias.ui.components.FilaDeDato

/**
 * El contenido del detalle: puro, sin ViewModel ni Hilt.
 *
 * Nunca deja la pantalla en blanco. Un folio sin señal, uno pendiente de envío
 * o uno rechazado pintan igual: encabezado, aviso, contadores de pendientes y
 * los artículos que el teléfono conoce. Lo que cambia es qué acciones se
 * ofrecen, no si hay algo que ver (D5).
 *
 * Las imágenes de la línea de tiempo **no se descargan**: el módulo no tiene un
 * cargador autenticado y una URL pelada daría 401. Se pinta el metadato —tipo,
 * descripción y fecha— y se declara la limitación en el reporte.
 */
@Composable
fun DetalleDeFolioContenido(
    state: DetalleDeFolioUiState,
    onRecolectar: (String) -> Unit,
    onEntregar: () -> Unit,
    onRecargar: () -> Unit,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        EncabezadoDeGarantia(titulo = TextosDeGarantias.TITULO_DETALLE, onAtras = onAtras)
        when {
            state.fallo -> AvisoDeGarantiaConReintento(onRecargar = onRecargar)
            state.cargando -> Text(
                text = TextosDeGarantias.CARGANDO,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            state.detalle == null -> EstadoVacioDeGarantia(
                titulo = TextosDeGarantias.FOLIO_DESCONOCIDO,
                pie = TextosDeGarantias.SIN_FOLIOS_PIE
            )
            else -> CuerpoDelDetalle(
                state = state,
                onRecolectar = onRecolectar,
                onEntregar = onEntregar
            )
        }
    }
}

/** El folio con su estado, su línea de tiempo y sus artículos. */
@Composable
private fun ColumnScope.CuerpoDelDetalle(
    state: DetalleDeFolioUiState,
    onRecolectar: (String) -> Unit,
    onEntregar: () -> Unit
) {
    val detalle = state.detalle ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        CabeceraDelFolio(state = state)
        detalle.motivo?.let { AvisoDeGarantia(texto = it, esError = true) }
        LineaDeTiempo(eventos = detalle.eventos)
        Articulos(articulos = detalle.articulos, onRecolectar = onRecolectar)
    }
    if (detalle.folio != null &&
        detalle.estado != EstadoDeLaLectura.RECHAZADO &&
        detalle.detalle?.estaCerrado != true
    ) {
        MspPrimaryFieldButton(
            text = TextosDeGarantias.ENTREGAR,
            onClick = onEntregar,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** La identificación del folio y por qué no está completo, si no lo está. */
@Composable
private fun CabeceraDelFolio(state: DetalleDeFolioUiState) {
    val detalle = state.detalle ?: return
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)) {
        if (detalle.estado != EstadoDeLaLectura.DISPONIBLE) {
            AvisoDeGarantia(texto = TextosDeGarantias.textoDe(detalle.estado))
        }
        val folio = detalle.folio
        if (folio != null) {
            FilaDeDato(
                etiqueta = TextosDeGarantias.ORIGEN,
                valor = TextosDeGarantias.textoDe(folio.origen)
            )
            FilaDeDato(
                etiqueta = TextosDeGarantias.PENDIENTES,
                valor = (detalle.comandosPendientes + detalle.fotosPendientes).toString()
            )
        }
        if (detalle.detalle?.estaCerrado == true) {
            AvisoDeGarantia(texto = TextosDeGarantias.FOLIO_CERRADO)
        }
    }
}

/** La línea de tiempo del folio, con el metadato de cada evento. */
@Composable
private fun LineaDeTiempo(eventos: List<EventoDeGarantia>) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.LINEA_DE_TIEMPO)
        if (eventos.isEmpty()) {
            Text(
                text = TextosDeGarantias.SIN_EVENTOS,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        } else {
            eventos.forEach { evento -> FilaDeEvento(evento = evento) }
        }
    }
}

/** Una entrada de la línea de tiempo. */
@Composable
private fun FilaDeEvento(evento: EventoDeGarantia) {
    MspCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = evento.descripcion,
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurface
            )
            Text(
                text = AppTime.formatForDisplay(evento.creadoEn),
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
            TransicionDelEvento(evento = evento)
            if (evento.imagenes.isNotEmpty()) {
                Text(
                    text = "${TextosDeGarantias.IMAGENES}: ${evento.imagenes.size}",
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
            }
        }
    }
}

/** De qué etapa a qué etapa movió el evento, cuando el servidor lo dice. */
@Composable
private fun TransicionDelEvento(evento: EventoDeGarantia) {
    val desde = evento.etapaDesde
    val hasta = evento.etapaHasta
    if (desde.isNullOrBlank() && hasta.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        FilaDeDato(etiqueta = TextosDeGarantias.ETAPA, valor = "$desde → $hasta")
    }
}

/** Los artículos del folio, cada uno con su acción de recolección. */
@Composable
private fun Articulos(articulos: List<ArticuloDelFolio>, onRecolectar: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)) {
        EtiquetaDeSeccion(texto = TextosDeGarantias.ARTICULOS)
        if (articulos.isEmpty()) {
            Text(
                text = TextosDeGarantias.SIN_ARTICULOS,
                style = MspTheme.type.caption,
                color = MspTheme.colors.onSurfaceMuted
            )
        } else {
            articulos.forEach { articulo ->
                FilaDeArticulo(
                    articulo = articulo,
                    onRecolectar = onRecolectar
                )
            }
        }
    }
}

/** Un artículo con su clave, su descripción, su etapa y su botón de recolección. */
@Composable
private fun FilaDeArticulo(articulo: ArticuloDelFolio, onRecolectar: (String) -> Unit) {
    val referencia = articulo.articuloRef
    MspCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MspTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
        ) {
            Text(
                text = articulo.remoto.descripcion,
                style = MspTheme.type.listTitle,
                color = MspTheme.colors.onSurface
            )
            FilaDeDato(
                etiqueta = TextosDeGarantias.CLAVE_DEL_ARTICULO,
                valor = articulo.remoto.clave
            )
            FilaDeDato(etiqueta = TextosDeGarantias.ETAPA, valor = articulo.remoto.etapa)
            if (referencia != null) {
                MspPrimaryFieldButton(
                    text = TextosDeGarantias.RECOLECTAR,
                    onClick = { onRecolectar(referencia) },
                    modifier = Modifier.fillMaxWidth()
                )
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
