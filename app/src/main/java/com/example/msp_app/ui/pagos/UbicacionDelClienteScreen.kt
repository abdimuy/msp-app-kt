package com.example.msp_app.ui.pagos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.ui.theme.ThemeController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

/** `testTag` del botón de volver del mapa grande. */
const val VOLVER_DEL_MAPA_TAG: String = "pagos_mapa_volver"

/** `testTag` del botón que recentra la cámara sobre la puerta. */
const val CENTRAR_EL_MAPA_TAG: String = "pagos_mapa_centrar"

/** `testTag` de la hoja de contexto al pie del mapa grande. */
const val HOJA_DEL_MAPA_TAG: String = "pagos_mapa_hoja"

/** `testTag` del botón "cómo llegar" de la hoja. */
const val COMO_LLEGAR_DEL_MAPA_TAG: String = "pagos_mapa_como_llegar"

/**
 * **La puerta, a pantalla completa.** El destino del toque sobre el cuadro del
 * detalle de cliente.
 *
 * ## Por qué existe, y por qué NO es un segundo "cómo llegar"
 *
 * El cuadro del detalle es `liteMode`: un bitmap estático, sin gestos y sin
 * zoom. Eso es lo que lo hace barato y es el modo que Android documenta para un
 * mapita dentro de un detalle — pero deja sin contestar la pregunta que se hace
 * parado en la calle: *"¿es esta puerta?"*. Ésta es la pantalla que la contesta,
 * y se abre **dentro de la app**, no saliendo a Google Maps.
 *
 * El botón de la hoja sí sale, y eso es otro trabajo: mirar la puerta y ponerse
 * a manejar hacia ella no son lo mismo. Lo que no se repite es el camino: el
 * `geo:` lo arma un solo lugar, `IntentAccionesExternasAdapter`, por el mismo
 * puerto que usa el detalle.
 *
 * ## La forma: el mapa es el contenido, así que se lleva la pantalla
 *
 * Nada de barra sólida arriba. Una barra de 56 dp le quita al mapa justo la
 * franja donde suele estar la cuadra de al lado, y no aporta: el rótulo
 * "Ubicación" no dice nada que la pantalla no esté gritando. En su lugar, **dos
 * controles circulares flotando sobre el mapa** —volver y recentrar— y una
 * **hoja al pie** con lo único que el mapa no puede decir: qué dirección es esta
 * y que aquí se cobró.
 *
 * El marcador **no es la chincheta roja de Google**: es un punto de marca con
 * halo, en el azul de la app. Es el único lugar donde esta pantalla levanta la
 * voz, y sirve para algo — la chincheta de Google se lee como "un resultado de
 * búsqueda", y esto no es un resultado de búsqueda, es la puerta donde este
 * cobrador estuvo parado.
 *
 * ## La atribución de Google queda visible, y no es opcional
 *
 * El logo y los créditos van abajo a la izquierda, que es exactamente donde cae
 * la hoja. Taparlos viola los términos del SDK. Por eso el mapa recibe
 * `contentPadding` con el alto de la hoja: el SDK sube su propia atribución por
 * encima de ella.
 *
 * ## Provee el tema, y lo anima
 *
 * `:app` NUNCA provee `MspTheme` —monta `MspappTheme`, el Material legado—, así
 * que esta pantalla se envuelve a sí misma, igual que las ocho de cobranza, y lo
 * hace por [MspThemeRevealHost] porque el mismo control no puede sentirse
 * distinto en dos pantallas de la misma app. La compuerta es
 * `CadaPantallaConTemaAnimaElCambioTest`.
 *
 * Acá el flip del tema **no pasa por un puerto**: esta pantalla ya vive en
 * `:app`, que es donde `ThemeController` vive. Un puerto para cruzar una
 * frontera que no se cruza sería un puerto sin su tercera cláusula (Ruling BF).
 */
@Composable
fun UbicacionDelClienteScreen(
    lat: Double,
    lng: Double,
    direccion: String,
    onAtras: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UbicacionDelClienteViewModel = hiltViewModel()
) {
    MspThemeRevealHost(
        onToggleTheme = ThemeController::toggle,
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        UbicacionDelClienteContent(
            lat = lat,
            lng = lng,
            direccion = direccion,
            onAtras = onAtras,
            onComoLlegar = { viewModel.comoLlegar(direccion, lat, lng) },
            modifier = modifier
        )
    }
}

/**
 * El cuerpo puro: el mapa a sangre, los dos controles y la hoja.
 *
 * Separado del destino por el mismo criterio que el resto de las pantallas
 * nuevas —"stateless, spy vía lambda"—, aunque acá el mapa no se puede
 * fotografiar: necesita red y GL. Lo que sí se mide sobre este composable es la
 * geometría de los controles, que es lo que
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest` barre.
 */
@Composable
fun UbicacionDelClienteContent(
    lat: Double,
    lng: Double,
    direccion: String,
    onAtras: () -> Unit,
    onComoLlegar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val destino = remember(lat, lng) { LatLng(lat, lng) }
    val camara = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(destino, ZOOM_DE_LA_PUERTA)
    }
    val alcance = rememberCoroutineScope()
    Box(modifier = modifier.fillMaxSize().background(MspTheme.colors.background)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camara,
            properties = propiedadesDelMapa(),
            // La atribución de Google sube por encima de la hoja. Taparla viola
            // los términos del SDK, y la hoja cae justo donde el logo se dibuja.
            contentPadding = PaddingValues(bottom = ALTO_DE_LA_HOJA),
            // Zoom y arrastre: es la pantalla que existe para eso. Fuera la
            // brújula (recentrar ya tiene su botón), la barra de Google (manda a
            // otra app sin avisar) y los controles +/- (el gesto basta y los
            // botones envejecen la pantalla).
            uiSettings = MapUiSettings(
                compassEnabled = false,
                mapToolbarEnabled = false,
                myLocationButtonEnabled = false,
                zoomControlsEnabled = false
            )
        ) {
            PuntoDeLaPuerta(destino)
        }
        ControlRedondo(
            icono = Icons.AutoMirrored.Filled.ArrowBack,
            descripcion = "Volver",
            onClick = onAtras,
            etiqueta = VOLVER_DEL_MAPA_TAG,
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(ORILLA)
        )
        ControlRedondo(
            icono = CRUZ_DE_CENTRAR,
            descripcion = "Centrar en la puerta",
            onClick = {
                alcance.launch {
                    camara.animate(
                        CameraUpdateFactory.newLatLngZoom(destino, ZOOM_DE_LA_PUERTA)
                    )
                }
            },
            etiqueta = CENTRAR_EL_MAPA_TAG,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ORILLA, bottom = ALTO_DE_LA_HOJA + ORILLA)
        )
        HojaDeLaUbicacion(
            direccion = direccion,
            onComoLlegar = onComoLlegar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * El punto de marca de la puerta.
 *
 * Un **disco con halo**, no la chincheta de Google. La chincheta se lee como "un
 * resultado de búsqueda"; esto es la coordenada donde el cobrador de verdad
 * estuvo parado, y el disco es la forma que el resto de la app ya usa para
 * decir "un punto medido" (ver `CuadroDeLaPuerta`).
 *
 * Se dibuja con [MarkerComposable] para que sea un Composable del tema y no un
 * `BitmapDescriptor` recoloreado: así el halo respeta la paleta —clara y
 * oscura— y no hay que mantener un PNG por densidad.
 *
 * [MapsComposeExperimentalApi] es la única API experimental de esta pantalla, y
 * el `@OptIn` va acotado a esta función y no al archivo: si mañana la API cambia,
 * el compilador señala exactamente el composable que hay que rehacer, no la
 * pantalla entera. La degradación si se cayera es conocida y aceptable — el
 * marcador de siempre—, no una pantalla rota.
 */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun PuntoDeLaPuerta(destino: LatLng) {
    MarkerComposable(state = rememberMarkerState(position = destino)) {
        Box(
            modifier = Modifier
                .size(HALO_DEL_PUNTO)
                .clip(RoundedCornerShape(percent = 50))
                .background(MspTheme.colors.brandTint),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(DISCO_DEL_PUNTO)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MspTheme.colors.brand)
            )
        }
    }
}

/**
 * La hoja al pie: la dirección y la salida a navegar.
 *
 * **No es arrastrable y no tiene asa.** Un asa promete que se puede subir para
 * ver más, y aquí no hay más: son dos renglones y un botón. Prometer un gesto
 * que no existe es la clase de forma que miente (principio 2).
 *
 * Entra deslizándose una sola vez. Es el único movimiento de la pantalla —el
 * mapa ya se mueve bajo el dedo— y respeta movimiento reducido: con él puesto,
 * la hoja simplemente está.
 */
@Composable
private fun BoxScope.HojaDeLaUbicacion(
    direccion: String,
    onComoLlegar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var puesta by remember { mutableStateOf(false) }
    val sinMovimiento = rememberMspReducedMotion()
    LaunchedEffect(Unit) { puesta = true }
    AnimatedVisibility(
        visible = puesta || sinMovimiento,
        enter = if (sinMovimiento) {
            androidx.compose.animation.EnterTransition.None
        } else {
            slideInVertically(initialOffsetY = { it })
        },
        modifier = modifier
    ) {
        Surface(
            color = MspTheme.colors.surface,
            shape = RoundedCornerShape(topStart = RADIO_DE_LA_HOJA, topEnd = RADIO_DE_LA_HOJA),
            modifier = Modifier.fillMaxWidth().testTag(HOJA_DEL_MAPA_TAG)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .systemBarsPadding()
                    .padding(MspTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.xs)
            ) {
                Text(
                    text = direccion.ifBlank { SIN_DIRECCION },
                    style = MspTheme.type.cardTitle,
                    color = MspTheme.colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = AQUI_COBRASTE,
                    style = MspTheme.type.caption,
                    color = MspTheme.colors.onSurfaceMuted
                )
                Surface(
                    onClick = onComoLlegar,
                    color = MspTheme.colors.brand,
                    shape = MspTheme.shapes.control,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MspTheme.spacing.xs)
                        .testTag(COMO_LLEGAR_DEL_MAPA_TAG)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(MspTheme.spacing.sm + 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = COMO_LLEGAR,
                            style = MspTheme.type.buttonSmall,
                            color = MspTheme.colors.onBrand
                        )
                    }
                }
            }
        }
    }
}

/**
 * Un control circular flotando sobre el mapa.
 *
 * [TOQUE_MINIMO] son los 50 dp del repo, más estrictos que los 48 de Material, y
 * acá importan el doble: sobre un mapa, un toque que falla no hace nada visible
 * y el cobrador no sabe si tocó mal o si la app se colgó.
 */
@Composable
private fun ControlRedondo(
    icono: ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    etiqueta: String,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = MspTheme.colors.surface,
        shape = RoundedCornerShape(percent = 50),
        shadowElevation = SOMBRA_DEL_CONTROL,
        modifier = modifier.size(TOQUE_MINIMO).testTag(etiqueta)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icono,
                contentDescription = descripcion,
                tint = MspTheme.colors.onSurface,
                modifier = Modifier.size(GLIFO_DEL_CONTROL)
            )
        }
    }
}

/** El área tocable mínima del repo. */
private val TOQUE_MINIMO = 50.dp

/** El glifo dentro de un control circular. */
private val GLIFO_DEL_CONTROL = 22.dp

/** Lo que despega el control del mapa sin convertirlo en una tarjeta. */
private val SOMBRA_DEL_CONTROL = 3.dp

/** El aire entre un control y la orilla de la pantalla. */
private val ORILLA = 16.dp

/**
 * El alto reservado a la hoja.
 *
 * Es el número que el mapa usa como `contentPadding` para subir la atribución de
 * Google, así que **no es decorativo**: si la hoja creciera por encima de esto,
 * el logo quedaría tapado y eso viola los términos del SDK. Cubre los dos
 * renglones, el botón y el inset de la barra de gestos.
 */
private val ALTO_DE_LA_HOJA = 168.dp

/** La esquina superior de la hoja. */
private val RADIO_DE_LA_HOJA = 28.dp

/** El halo del punto de marca. */
private val HALO_DEL_PUNTO = 34.dp

/** El disco de marca dentro del halo. */
private val DISCO_DEL_PUNTO = 14.dp

/** Lo que dice la hoja cuando el cliente no trae dirección escrita. */
private const val SIN_DIRECCION = "Sin dirección registrada"

/** La línea de apoyo: por qué este punto está aquí. */
private const val AQUI_COBRASTE = "Aquí cobraste la última vez"

/** El botón que sale a navegar. Mismo nombre que la acción del detalle. */
private const val COMO_LLEGAR = "Cómo llegar"

/**
 * El glifo de "centrar en la puerta": una mira.
 *
 * Se dibuja a mano, como el resto de los glifos de la app, y no se toma de los
 * iconos extendidos de Material: ésos son una dependencia entera (miles de
 * vectores) por un solo dibujo, y el catálogo básico no trae ninguna mira. Dos
 * constantes `String` cuestan cero bytes de asset.
 *
 * Una mira y no un pin: el pin ya significa "la puerta" en esta pantalla, y el
 * mismo símbolo para dos cosas distintas es lo que esta app evita en todos lados.
 */
private val CRUZ_DE_CENTRAR: ImageVector = ImageVector.Builder(
    name = "centrar_en_la_puerta",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).apply {
    listOf(ANILLO_DE_LA_MIRA, MARCAS_DE_LA_MIRA).forEach { trazo ->
        addPath(
            pathData = PathParser().parsePathString(trazo).toNodes(),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
}.build()

/** El anillo de la mira, en dos arcos: es lo que `PathParser` acepta sin trucos. */
private const val ANILLO_DE_LA_MIRA =
    "M6,12 A6,6 0 1,1 18,12 A6,6 0 1,1 6,12"

/** Las cuatro marcas que salen del anillo. */
private const val MARCAS_DE_LA_MIRA =
    "M12,2 L12,5 M12,19 L12,22 M2,12 L5,12 M19,12 L22,12"
