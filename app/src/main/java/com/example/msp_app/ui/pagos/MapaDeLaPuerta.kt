package com.example.msp_app.ui.pagos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.example.msp_app.core.designsystem.theme.appDarkTheme
import com.example.msp_app.feature.pagos.domain.model.UbicacionDelCobro
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * **El suelo del cuadro de la puerta: el mapa chico, en modo lite.**
 *
 * Es lo que `:app` pone en la ranura `suelo` de
 * [com.example.msp_app.feature.pagos.ui.DetalleClienteScreen].
 *
 * ## Por qué vive en `:app` y no en `:feature:pagos`
 *
 * Principio 17 del brief, literal: *cuando el adaptador necesita algo que solo
 * vive en `:app`, el puerto se queda en el módulo y el adaptador se va a `:app`*.
 * `play-services-maps` y `maps-compose` están declaradas en
 * `app/build.gradle.kts:348-350` y la llave del manifiesto también es de `:app`
 * (`AndroidManifest.xml:67-69`). Meterlas en `:feature:pagos` reabriría la
 * discusión de grafo que `c59bd728` cerró, para nada: la ranura ya existía.
 *
 * Y es lo que mantiene deterministas los goldens de `:feature:pagos`: ellos
 * fotografían `DetalleClienteContent`, que se queda con el dibujo. Un mapa real
 * trae red y bitmaps, y ninguno entra a `captureRoboImage`.
 *
 * ## El mapa se esconde hasta que de verdad pintó — medido, no precavido
 *
 * Instalado en el SM-A256E, este mapa **no pintó ni una tesela**:
 * `Authorization failure … StatusCode=INVALID_ARGUMENT`, porque la llave no
 * autorizaba el paquete de esa build. Lo que se veía era la retícula gris con el
 * logo de Google — *un mapa que no cargó*, que es justo lo que el cuadro existe
 * para no ser. El mismo estado se da en la calle sin señal la primera vez que se
 * abre una puerta nueva.
 *
 * Así que el mapa arranca **transparente** y solo se deja ver cuando el SDK
 * dispara `onMapLoaded`, que es el aviso de que terminó de renderizar. Mientras
 * tanto se ve el dibujo que `CuadroDeLaPuerta` pinta debajo. Sin temporizadores
 * y sin adivinar: si el aviso nunca llega, lo que queda es el dibujo, que es el
 * estado aceptable. El peor caso de este diseño es el estado bueno.
 *
 * Se usa `alpha` y no un `if`: el mapa tiene que estar **compuesto** para poder
 * cargar y avisar. Un `if` que lo saca del árbol nunca le daría la oportunidad.
 *
 * ## `liteMode`, y lo que cuesta — medido, no supuesto
 *
 * **Peso: cero bytes.** El SDK ya viaja en el APK que se reparte hoy: 296 rutas
 * de clase distintas bajo `com/google/android/gms/maps/` sobreviven a R8 en
 * `app-prod-release.apk` (11.59 MB), y los AAR de `play-services-maps` y
 * `maps-compose` no traen **ni un `.so`** — el renderizador vive en la app de
 * Google Play Services del teléfono. Los 58.9 KB de nativo que quedan en el APK
 * son `libandroidx.graphics.path` y `libdatastore_shared_counter`. Es el modelo
 * de distribución opuesto al de MapLibre, que traía el renderizador adentro y
 * por eso pesaba 47.9 MB en cuatro ABIs (`c59bd728`).
 *
 * **Dinero: cero**, según el tarifario publicado de Google Maps Platform — las
 * cargas del SDK nativo de Android (*Mobile Native Dynamic Maps* y *Mobile
 * Native Static Maps*) van a 0 USD, al revés de la Static Maps API por HTTP y de
 * la Geocoding API REST que esta app ya consume desde
 * `SaleLocationMap.kt:110,130`. El tarifario lo fija Google: conviene
 * confirmarlo en la consola antes de darlo por bueno.
 *
 * **Modo lite y no interactivo.** Renderiza un bitmap estático: no mueve cámara,
 * no hace gestos y no compone un `SurfaceView` por cada apertura. Es el modo que
 * Android documenta para exactamente este caso. El zoom vive en
 * [UbicacionDelClienteScreen], que es a donde lleva el toque.
 */
@Composable
fun SueloDelUltimoCobro(punto: UbicacionDelCobro?, modifier: Modifier = Modifier) {
    if (punto == null) return
    val destino = remember(punto) { LatLng(punto.lat, punto.lng) }
    val camara = rememberCameraPositionState(key = "${punto.lat},${punto.lng}") {
        position = CameraPosition.fromLatLngZoom(destino, ZOOM_DE_LA_PUERTA)
    }
    var pinto by remember(destino) { mutableStateOf(false) }
    GoogleMap(
        modifier = modifier.fillMaxSize().alpha(if (pinto) 1f else 0f),
        googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
        cameraPositionState = camara,
        properties = propiedadesDelMapa(),
        uiSettings = SIN_CONTROLES,
        onMapLoaded = { pinto = true }
    ) {
        Marker(state = MarkerState(position = destino))
    }
}

/**
 * Las propiedades comunes al mapa chico y al grande: el estilo de noche cuando
 * el tema es oscuro, y nada más.
 *
 * El `remember` va **fuera** de cualquier rama condicional: un `remember` dentro
 * de un `if` cambia de posición en la slot table cuando la rama cambia, y
 * Compose lo trata como otro `remember`.
 */
@Composable
internal fun propiedadesDelMapa(): MapProperties {
    val estiloDeNoche = remember { MapStyleOptions(ESTILO_OSCURO) }
    return MapProperties(mapStyleOptions = if (appDarkTheme()) estiloDeNoche else null)
}

/**
 * Zoom 17: la cuadra, no la ciudad.
 *
 * Es el mismo con el que se midió el corrimiento de 21 dp del pin cuando lo
 * pintaba la feature en vez del mapa (~24 m a este zoom). Acá el marcador lo
 * pinta el mapa, centrado en el objetivo de la cámara, que es el único lugar
 * donde "es aquí" es cierto.
 */
internal const val ZOOM_DE_LA_PUERTA = 17f

/**
 * El cuadro chico no se toca: sin gestos, sin controles, sin botón de mi
 * ubicación. El toque lo recoge `CuadroDeLaPuerta` y abre el mapa grande.
 *
 * `liteMode` ya ignora los gestos, pero declararlo acá es lo que hace que el
 * comportamiento no dependa de un flag de otra capa.
 */
private val SIN_CONTROLES = MapUiSettings(
    compassEnabled = false,
    indoorLevelPickerEnabled = false,
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    rotationGesturesEnabled = false,
    scrollGesturesEnabled = false,
    scrollGesturesEnabledDuringRotateOrZoom = false,
    tiltGesturesEnabled = false,
    zoomControlsEnabled = false,
    zoomGesturesEnabled = false
)

/**
 * El estilo de noche, recortado a lo que se ve en un cuadro chico: geometría,
 * agua, calles y etiquetas. Sin puntos de interés ni tránsito, que a ese tamaño
 * son ruido de color sobre la única cosa que importa, el marcador.
 *
 * Va como constante de texto y no como `res/raw`: es un archivo menos que
 * mantener por doce reglas de color. Sin él, el cuadro queda como un rectángulo
 * blanco dentro de una hoja oscura.
 */
internal const val ESTILO_OSCURO = """
[
  {"elementType":"geometry","stylers":[{"color":"#1f2421"}]},
  {"elementType":"labels.icon","stylers":[{"visibility":"off"}]},
  {"elementType":"labels.text.fill","stylers":[{"color":"#8d968f"}]},
  {"elementType":"labels.text.stroke","stylers":[{"color":"#1f2421"}]},
  {"featureType":"poi","stylers":[{"visibility":"off"}]},
  {"featureType":"transit","stylers":[{"visibility":"off"}]},
  {"featureType":"road","elementType":"geometry","stylers":[{"color":"#2b322e"}]},
  {"featureType":"road","elementType":"labels.text.fill","stylers":[{"color":"#9aa39c"}]},
  {"featureType":"road.arterial","elementType":"geometry","stylers":[{"color":"#343c37"}]},
  {"featureType":"road.highway","elementType":"geometry","stylers":[{"color":"#3d4741"}]},
  {"featureType":"water","elementType":"geometry","stylers":[{"color":"#16211e"}]},
  {"featureType":"water","elementType":"labels.text.fill","stylers":[{"color":"#5a655e"}]}
]
"""
