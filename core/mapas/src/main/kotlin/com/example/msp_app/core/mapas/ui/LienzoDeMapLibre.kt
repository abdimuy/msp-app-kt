package com.example.msp_app.core.mapas.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.mapas.domain.MapaDeLaRuta
import com.example.msp_app.core.mapas.domain.PuntoDelMapa
import com.example.msp_app.core.mapas.domain.estiloDeLaRuta
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * **El mapa de verdad: MapLibre leyendo el `.pmtiles` del teléfono.**
 *
 * Sin llave, sin servidor y sin proveedor. El estilo no declara `glyphs` ni
 * `sprite` y la única fuente es `pmtiles://file://…`, así que esta vista **no
 * abre un socket**: todo lo que dibuja sale de un archivo local.
 *
 * ## Lo que hace que el pin de Compose sea correcto
 *
 * Tres líneas, y las tres importan juntas: la cámara se centra en [punto], los
 * gestos están **apagados** y el mapa no tiene marcador propio. Así el centro
 * geométrico de esta vista es siempre el punto medido, y el pin que dibuja quien
 * llama cae encima sin que nadie tenga que proyectar coordenadas a píxeles. Si
 * alguien encendiera los gestos, el pin empezaría a mentir en cuanto el dedo
 * arrastrara el mapa.
 *
 * ## El logo y la atribución propios de MapLibre van apagados
 *
 * No para esconder el crédito —al contrario—: [AtribucionDeOpenStreetMap] lo
 * pinta con los tokens del tema y en la esquina que este bloque tiene libre. El
 * widget de MapLibre abre un diálogo del sistema y se coloca donde quiere, que
 * en un cuadro de 130 dp cae encima del botón "cómo llegar".
 *
 * ## Si el motor no arranca
 *
 * La librería nativa puede no cargar (ABI que no viajó en el APK, GL ES que el
 * emulador no expone). Eso **no puede tumbar la pantalla del cliente**: se
 * reporta por [alFallarElMotor] y se degrada al mismo suelo liso de cuando no
 * hay extracto. La `Throwable` se atrapa entera porque lo que se cuida acá no es
 * una excepción concreta sino la propiedad "el detalle del cliente abre".
 */
@Composable
fun LienzoDeMapLibre(
    mapa: MapaDeLaRuta,
    punto: PuntoDelMapa?,
    alFallarElMotor: (Throwable) -> Unit,
    modifier: Modifier = Modifier
) {
    val contexto = LocalContext.current
    val paleta = paletaDelMapa()
    val estilo = remember(mapa, paleta) { estiloDeLaRuta(mapa, paleta) }
    val motor = remember(contexto) { arrancarElMotor(contexto) }

    val fallo = motor.exceptionOrNull()
    if (fallo != null) {
        DisposableEffect(fallo) {
            alFallarElMotor(fallo)
            onDispose { }
        }
        Box(modifier = modifier.fillMaxSize().background(MspTheme.colors.surface2))
        return
    }

    val vista = remember(contexto) { MapView(contexto).also { it.onCreate(null) } }
    CicloDeVidaDelMapa(vista)

    AndroidView(
        factory = { vista },
        modifier = modifier.fillMaxSize(),
        update = { mapView ->
            mapView.getMapAsync { mapLibre ->
                mapLibre.uiSettings.setAllGesturesEnabled(false)
                mapLibre.uiSettings.isAttributionEnabled = false
                mapLibre.uiSettings.isLogoEnabled = false
                mapLibre.uiSettings.isCompassEnabled = false
                mapLibre.setStyle(Style.Builder().fromJson(estilo))
                if (punto != null) {
                    mapLibre.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(punto.lat, punto.lng))
                        .zoom(ZOOM_DE_LA_PUERTA)
                        .build()
                }
            }
        }
    )
}

/**
 * `MapLibre.getInstance` carga la `.so` y prepara el motor. Es lo único que
 * puede reventar por razones del dispositivo, así que se aísla y se envuelve:
 * el resto de la función ya puede asumir que hay motor.
 */
@Suppress("TooGenericExceptionCaught") // un `UnsatisfiedLinkError` no es `Exception`.
private fun arrancarElMotor(contexto: Context): Result<Unit> = try {
    MapLibre.getInstance(contexto)
    Result.success(Unit)
} catch (error: Throwable) {
    Result.failure(error)
}

/**
 * `MapView` es una vista con ciclo de vida propio y **no perdona**: sin
 * `onStart`/`onStop` el renderizador sigue vivo detrás de la app, y sin
 * `onDestroy` se filtra la superficie. `AndroidView` no lo hace solo.
 */
@Composable
private fun CicloDeVidaDelMapa(vista: MapView) {
    val duenoDelCiclo = LocalLifecycleOwner.current
    DisposableEffect(duenoDelCiclo, vista) {
        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> vista.onStart()
                Lifecycle.Event.ON_RESUME -> vista.onResume()
                Lifecycle.Event.ON_PAUSE -> vista.onPause()
                Lifecycle.Event.ON_STOP -> vista.onStop()
                else -> Unit
            }
        }
        duenoDelCiclo.lifecycle.addObserver(observador)
        onDispose {
            duenoDelCiclo.lifecycle.removeObserver(observador)
            vista.onDestroy()
        }
    }
}

/**
 * El zoom del cuadro del detalle: **17**, o sea la manzana.
 *
 * Es el zoom al que se distingue una puerta de la de al lado sin que el cuadro
 * de 130 dp se vuelva una sola calle. El extracto llega a z14 y MapLibre
 * sobre-escala las teselas de z14 hasta acá — se ve la traza, no el detalle de
 * z17, y es la única forma de tener acercamiento sin multiplicar el archivo por
 * cuarenta.
 */
private const val ZOOM_DE_LA_PUERTA = 17.0
