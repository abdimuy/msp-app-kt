package com.example.msp_app.core.mapas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.mapas.domain.EstadoDelExtracto
import com.example.msp_app.core.mapas.domain.PuntoDelMapa

/**
 * **El suelo del mapa, cableado.** Es lo que una feature pone en su slot.
 *
 * Se lleva el ViewModel adentro para que la pantalla que lo usa —hoy el detalle
 * del cliente— **no tenga que saber que el mapa existe**: su propio ViewModel no
 * gana una dependencia, sus tests no ganan un fake y sus goldens siguen pintando
 * el suelo liso porque ellos llaman al contenido, no a esta función.
 *
 * ## El punto viaja como parámetro y el mapa no
 *
 * El punto lo sabe la feature (sale del último abono con coordenadas) y el mapa
 * lo sabe el módulo. Cada uno aporta lo suyo y ninguno le pide al otro algo que
 * no le toca.
 *
 * ## Si el punto cae fuera del extracto
 *
 * Se dibuja el mapa igual, centrado donde toque: MapLibre pinta el fondo del
 * estilo donde no hay teselas, así que lo que se ve es el suelo del tema y el
 * pin. Es exactamente la degradación correcta —nadie inventa calles— y no hace
 * falta un caso aparte. Las 65 visitas de Mountain View que
 * `huella-geografica-medida.md` encontró son justamente eso: puntos reales,
 * fuera de la caja.
 */
@Composable
fun SueloDeLaRutaConectado(
    punto: PuntoDelMapa?,
    modifier: Modifier = Modifier,
    viewModel: MapaViewModel = hiltViewModel()
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    SueloDeLaRuta(
        mapa = (estado as? EstadoDelExtracto.Listo)?.mapa,
        punto = punto,
        lienzo = { mapa, donde ->
            LienzoDeMapLibre(
                mapa = mapa,
                punto = donde,
                alFallarElMotor = viewModel::motorNoArranco
            )
        },
        modifier = modifier
    )
}

/**
 * **¿Este teléfono tiene el mapa?**
 *
 * Existe para una sola pregunta, y la pregunta es del pin: quien dibuja el pin
 * tiene que ser el que manda la cámara (ver [PinDelMapa]), así que la pantalla
 * que usa el bloque necesita saber si el suelo va a traer el suyo. Sin esto, o
 * se pintan dos pines, o no se pinta ninguno cuando no hay mapa — y las dos
 * cosas se vieron antes de que este par de funciones existiera.
 *
 * Comparte el mismo [MapaViewModel] que [SueloDeLaRutaConectado]: `hiltViewModel()`
 * devuelve la misma instancia dentro del mismo `ViewModelStoreOwner`, así que las
 * dos funciones no pueden contestar distinto.
 */
@Composable
fun hayMapaDeLaRuta(viewModel: MapaViewModel = hiltViewModel()): Boolean {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    return estado is EstadoDelExtracto.Listo
}
