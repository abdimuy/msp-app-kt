package com.example.msp_app.core.speech.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * **La pantalla de descarga del dictado, cableada.** Es lo que `:app` monta en
 * el grafo, en la ruta [DictadoRutas.DESCARGA].
 *
 * El reparto: el ViewModel se queda **dentro** del módulo, así que `:app` no
 * tiene que conocer
 * [DescargaDelDictadoViewModel] ni saber que el peso sale de
 * `ModeloDeDictado.tamanoBytes`. Lo único que aporta el llamador es a dónde
 * vuelve la flecha.
 */
@Composable
fun DescargaDelDictadoConectada(
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DescargaDelDictadoViewModel = hiltViewModel()
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    DescargaDelDictadoScreen(
        modelo = viewModel.modelo,
        estado = estado,
        onDescargar = viewModel::descargar,
        onBorrar = viewModel::borrar,
        onAtras = onAtras,
        // Lo pide `MspThemeRevealHost` DESPUÉS de grabar el frame viejo, no un
        // botón de la pantalla: todavía no pinta el glifo sol/luna.
        onAlternarTema = viewModel::alternarTema,
        modifier = modifier
    )
}
