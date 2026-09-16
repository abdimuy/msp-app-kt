package com.example.msp_app.core.mapas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * **La pantalla de descarga del mapa, cableada.** Es lo que `:app` monta en el
 * grafo, en la ruta [MapasRutas.DESCARGA].
 *
 * Reusa [MapaViewModel] —el mismo que lee el suelo del mapa— a propósito: son
 * la misma pregunta ("¿hay mapa en este teléfono?") hecha desde dos lugares, y
 * su KDoc ya explica por qué dos ViewModels sobre el mismo puerto podrían
 * contestar distinto durante una descarga.
 */
@Composable
fun DescargaDelMapaConectada(
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapaViewModel = hiltViewModel()
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    DescargaDelMapaScreen(
        paquete = viewModel.paquete,
        estado = estado,
        onDescargar = viewModel::descargar,
        onBorrar = viewModel::borrar,
        onAtras = onAtras,
        modifier = modifier
    )
}
