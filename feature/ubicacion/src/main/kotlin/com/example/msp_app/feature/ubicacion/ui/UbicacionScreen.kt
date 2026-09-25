package com.example.msp_app.feature.ubicacion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.core.geo.LugarAgrupado
import com.example.msp_app.feature.ubicacion.domain.FiltroDeLugares
import com.example.msp_app.feature.ubicacion.domain.LugarEnElMapa
import com.example.msp_app.feature.ubicacion.domain.TipoDeLugar
import com.example.msp_app.feature.ubicacion.ui.components.BarraDeFiltros
import com.example.msp_app.feature.ubicacion.ui.components.CRUZ_DE_CENTRAR
import com.example.msp_app.feature.ubicacion.ui.components.ControlRedondo
import com.example.msp_app.feature.ubicacion.ui.components.FLECHA_DE_VOLVER
import com.example.msp_app.feature.ubicacion.ui.components.FormaDelMarcador
import com.example.msp_app.feature.ubicacion.ui.components.HojaDeLosLugares
import com.example.msp_app.feature.ubicacion.ui.components.recordarVariantes
import com.google.android.gms.maps.CameraUpdate
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.launch

/** `testTag` del botón de volver. */
const val VOLVER_TAG: String = "ubicacion_volver"

/** `testTag` del botón que reencuadra sobre todos los lugares. */
const val CENTRAR_TAG: String = "ubicacion_centrar"

/** `testTag` de la hoja al pie. */
const val HOJA_TAG: String = "ubicacion_hoja"

/** `testTag` de la barra de filtros. */
const val FILTROS_TAG: String = "ubicacion_filtros"

/**
 * Los cuatro colores de los lugares, resueltos **una vez** desde el tema.
 *
 * Existen como un valor y no como lecturas sueltas de `MspTheme` porque son la
 * clave del caché de bitmaps: si se leyeran en cada sitio, nada garantizaría que
 * el icono y el círculo del mismo lugar usan el mismo color, ni que el caché se
 * tira al cambiar de tema. Ver el KDoc de `VariantesDelMarcador`.
 */
data class ColoresDeLugar(
    val puerta: Color,
    val compartido: Color,
    val transferencia: Color,
    val otro: Color,
    val sobreElMarcador: Color
) {
    fun de(tipo: TipoDeLugar): Color = when (tipo) {
        TipoDeLugar.LA_PUERTA -> puerta
        TipoDeLugar.COMPARTIDO -> compartido
        TipoDeLugar.SOLO_TRANSFERENCIAS -> transferencia
        TipoDeLugar.OTRO -> otro
    }
}

/** Los colores del tema vigente, que es de donde tienen que salir. */
@Composable
fun coloresDeLugar(): ColoresDeLugar = ColoresDeLugar(
    puerta = MspTheme.colors.brand,
    compartido = MspTheme.colors.statusPending,
    transferencia = MspTheme.colors.statusInfo,
    otro = MspTheme.colors.onSurfaceMuted,
    sobreElMarcador = MspTheme.colors.onBrand
)

/**
 * **Todos los lugares del cliente, a pantalla completa.**
 *
 * Reemplaza a la pantalla de un solo punto que vivía en `:app`. Lo que cambió no
 * es el tamaño del mapa sino la pregunta que contesta: antes decía *"aquí
 * cobraste la última vez"*; ahora dice **dónde se ha cobrado siempre**, que es
 * lo que deja ver si el cliente se mudó.
 *
 * ## El conjunto es el mismo desde las tres entradas
 *
 * Se llega desde el cuadro del detalle de cliente, desde una fila de la bitácora
 * y desde una fila del detalle de venta. Las tres enseñan **todos** los lugares
 * del cliente; las dos últimas además traen su medición **resaltada**. Así la
 * bitácora sigue contestando *"¿dónde fue ESA vez?"* —la decisión que
 * `DetalleCliente` dejó escrita— y de paso enseña si esa vez fue rara.
 *
 * ## Lo que no se esconde
 *
 * Todos los lugares se dibujan, siempre: los compartidos, los de puras
 * transferencias y los sueltos. Lo único que se reparte con criterio es el
 * **título de "la puerta"**, y cuando nadie se lo gana la hoja lo dice en vez de
 * señalar cualquier cosa.
 *
 * El aviso de mudanza sale **sin que el cobrador filtre nada**: no sabe que
 * tiene que buscarla.
 */
@Composable
fun UbicacionScreen(
    state: UbicacionUiState,
    onAtras: () -> Unit,
    onComoLlegar: (LugarAgrupado) -> Unit,
    onFiltro: (FiltroDeLugares) -> Unit,
    onAlternarTema: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // **La pantalla se envuelve a sí misma.** `:app` monta `MspappTheme` —el
    // Material legado—, nunca `MspTheme`, así que una pantalla Msp que no provea
    // el suyo revienta al abrirse desde su `NavHost`. Lo cobra
    // `CadaPantallaMspProveeSuTemaTest`, que lo decide **por función**: no basta
    // con que el envoltorio esté en otro composable del mismo archivo o en el
    // destino — la pantalla misma tiene que estar dentro de un proveedor. Es el
    // mismo mecanismo que usan las ocho de cobranza, y es UNO a propósito: el
    // mismo control no puede sentirse distinto en dos pantallas de la app.
    MspThemeRevealHost(
        onToggleTheme = onAlternarTema,
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        UbicacionContent(state, onAtras, onComoLlegar, onFiltro, modifier)
    }
}

/**
 * El cuerpo, sin el tema.
 *
 * Separado para que el envoltorio de arriba sea una sola línea y no haya dudas
 * de qué está dentro del tema y qué no.
 */
@Composable
private fun UbicacionContent(
    state: UbicacionUiState,
    onAtras: () -> Unit,
    onComoLlegar: (LugarAgrupado) -> Unit,
    onFiltro: (FiltroDeLugares) -> Unit,
    modifier: Modifier = Modifier
) {
    val camara = rememberCameraPositionState()
    val alcance = rememberCoroutineScope()
    var encuadrado by remember { mutableStateOf(false) }

    // El primer encuadre mete TODOS los lugares en pantalla. Abrir centrado en
    // la puerta escondería el segundo grupo justo cuando más importa —una
    // mudanza son dos grupos lejos uno del otro— y el cobrador no tendría por
    // qué sospechar que hay algo fuera de cuadro.
    LaunchedEffect(state.lugares) {
        if (state.lugares.isEmpty() || encuadrado) return@LaunchedEffect
        encuadrado = true
        camara.mover(state.lugares)
    }

    Box(modifier = modifier.fillMaxSize().background(MspTheme.colors.background)) {
        MapaDeLosLugares(state, camara)
        ControlRedondo(
            icono = FLECHA_DE_VOLVER,
            descripcion = "Volver",
            onClick = onAtras,
            etiqueta = VOLVER_TAG,
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(ORILLA)
        )
        BarraDeFiltros(
            filtro = state.filtro,
            ventas = state.ventasDisponibles,
            cobradores = state.cobradoresDisponibles,
            onFiltro = onFiltro,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .systemBarsPadding()
                .padding(top = ORILLA, end = ORILLA)
                .testTag(FILTROS_TAG)
        )
        ControlRedondo(
            icono = CRUZ_DE_CENTRAR,
            descripcion = "Ver todos los puntos",
            onClick = {
                alcance.launch {
                    if (state.lugares.isNotEmpty()) camara.animar(state.lugares)
                }
            },
            etiqueta = CENTRAR_TAG,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = ORILLA, bottom = ALTO_DE_LA_HOJA + ORILLA)
        )
        HojaDeLosLugares(
            state = state,
            onComoLlegar = onComoLlegar,
            modifier = Modifier.align(Alignment.BottomCenter).testTag(HOJA_TAG)
        )
    }
}

/**
 * El mapa con todos los lugares.
 *
 * **Los marcadores los pinta el mapa**, anclados a su coordenada — ver el KDoc
 * de `VariantesDelMarcador` para por qué eso respeta la decisión de los 21 dp y
 * por qué el icono sale de un caché por forma en vez de un `ComposeView` por
 * marcador.
 */
@Composable
private fun MapaDeLosLugares(
    state: UbicacionUiState,
    camara: com.google.maps.android.compose.CameraPositionState
) {
    val colores = coloresDeLugar()
    val variantes = recordarVariantes(
        puerta = colores.puerta,
        compartido = colores.compartido,
        transferencia = colores.transferencia,
        texto = colores.sobreElMarcador
    )
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camara,
        // La atribución de Google sube por encima de la hoja. Taparla viola los
        // términos del SDK — misma razón que en la pantalla que ésta reemplaza.
        contentPadding = PaddingValues(bottom = ALTO_DE_LA_HOJA),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false
        )
    ) {
        state.lugares.forEach { enElMapa ->
            val color = colores.de(enElMapa.tipo)
            val alfa = alfaDe(enElMapa, state)
            CirculoDeConfianza(enElMapa, color, alfa)
            Marker(
                state = rememberMarkerState(
                    position = LatLng(enElMapa.lugar.centro.lat, enElMapa.lugar.centro.lon)
                ),
                icon = variantes.de(
                    FormaDelMarcador(
                        relleno = enElMapa.tipo != TipoDeLugar.SOLO_TRANSFERENCIAS,
                        resaltado = enElMapa.resaltado,
                        conteo = enElMapa.lugar.conteo
                    ),
                    color.toArgb()
                ),
                alpha = alfa,
                title = enElMapa.rotulo,
                snippet = enElMapa.detalle,
                zIndex = if (enElMapa.resaltado) Z_DEL_RESALTADO else alfa
            )
        }
    }
}

/**
 * **El círculo de precisión — y sólo cuando se lo puede sostener.**
 *
 * `radioDeConfianzaM` devuelve `null` por debajo de cinco mediciones, y aquí eso
 * se respeta al pie de la letra: **no se dibuja nada**. Un círculo apretado
 * calculado sobre tres puntos que cayeron juntos por casualidad diría "confíe en
 * esto" sin tener con qué, y el cobrador tocaría una puerta equivocada. No hay
 * radio por omisión que rellene ese hueco: el hueco **es** la respuesta.
 *
 * Tampoco se dibuja sobre un lugar compartido ni sobre uno de puras
 * transferencias: ahí la dispersión mide lo apretado que estaba el mostrador, no
 * la puerta de nadie.
 */
@Composable
private fun CirculoDeConfianza(enElMapa: LugarEnElMapa, color: Color, alfa: Float) {
    if (enElMapa.tipo != TipoDeLugar.LA_PUERTA) return
    val radio = enElMapa.lugar.radioDeConfianzaM ?: return
    Circle(
        center = LatLng(enElMapa.lugar.centro.lat, enElMapa.lugar.centro.lon),
        radius = radio,
        fillColor = color.copy(alpha = ALFA_DEL_RELLENO * alfa),
        strokeColor = color.copy(alpha = ALFA_DEL_BORDE * alfa),
        strokeWidth = GROSOR_DEL_CIRCULO
    )
}

/**
 * La opacidad por antigüedad: **lo reciente sólido, lo viejo desvaído**.
 *
 * Nunca baja de [ALFA_MINIMO]. Un punto viejo tiene que seguir siendo visible
 * —es justo el que delata la mudanza—, así que "desvaído" quiere decir tenue, no
 * invisible. Lo resaltado va siempre entero: lo pidió el cobrador.
 */
internal fun alfaDe(enElMapa: LugarEnElMapa, state: UbicacionUiState): Float {
    if (enElMapa.resaltado) return 1f
    val viejo = state.masViejo ?: return 1f
    val nuevo = state.masNuevo ?: return 1f
    return ALFA_MINIMO + (1f - ALFA_MINIMO) * enElMapa.frescura(viejo, nuevo)
}

/**
 * El encuadre que mete todos los lugares en pantalla.
 *
 * Con **un solo lugar** `LatLngBounds` degenera en un punto y el SDK aplica un
 * zoom absurdo, así que ese caso va por `newLatLngZoom` con el 17 de "la cuadra,
 * no la ciudad" que ya usaba el mapa chico.
 */
internal fun encuadreDe(lugares: List<LugarEnElMapa>): CameraUpdate {
    val puntos = lugares.map { LatLng(it.lugar.centro.lat, it.lugar.centro.lon) }
    if (puntos.size == 1) {
        return CameraUpdateFactory.newLatLngZoom(puntos.single(), ZOOM_DE_LA_PUERTA)
    }
    val limites = LatLngBounds.builder().apply { puntos.forEach { include(it) } }.build()
    return CameraUpdateFactory.newLatLngBounds(limites, MARGEN_DEL_ENCUADRE)
}

/**
 * El respaldo del encuadre: el centro de todos los lugares, con zoom fijo.
 *
 * Existe porque `newLatLngBounds(bounds, padding)` **exige que el mapa ya esté
 * medido** y lanza `IllegalStateException` si no lo está. El primer encuadre
 * corre desde un `LaunchedEffect` que puede ganarle al layout, así que el fallo
 * es **real y alcanzable**, no una precaución de adorno.
 *
 * El respaldo no es un `Unit` disimulado: encuadra de verdad, sólo que peor —
 * puede dejar un lugar lejano fuera de cuadro. Es un mal encuadre contra una
 * pantalla caída.
 */
internal fun respaldoDelEncuadre(lugares: List<LugarEnElMapa>): CameraUpdate {
    val centro = LatLng(
        lugares.sumOf { it.lugar.centro.lat } / lugares.size,
        lugares.sumOf { it.lugar.centro.lon } / lugares.size
    )
    return CameraUpdateFactory.newLatLngZoom(centro, ZOOM_DE_LA_PUERTA)
}

/** Mueve la cámara sin animar, tolerando que el mapa aún no tenga tamaño. */
private fun com.google.maps.android.compose.CameraPositionState.mover(
    lugares: List<LugarEnElMapa>
) = try {
    move(encuadreDe(lugares))
} catch (_: IllegalStateException) {
    move(respaldoDelEncuadre(lugares))
}

/** Lo mismo, animando: es el botón de "ver todos los puntos". */
private suspend fun com.google.maps.android.compose.CameraPositionState.animar(
    lugares: List<LugarEnElMapa>
) = try {
    animate(encuadreDe(lugares))
} catch (_: IllegalStateException) {
    animate(respaldoDelEncuadre(lugares))
}

private val ORILLA = 16.dp
private val ALTO_DE_LA_HOJA = 176.dp
private const val ZOOM_DE_LA_PUERTA = 17f
private const val MARGEN_DEL_ENCUADRE = 120
private const val ALFA_MINIMO = 0.35f
private const val ALFA_DEL_RELLENO = 0.12f
private const val ALFA_DEL_BORDE = 0.5f
private const val GROSOR_DEL_CIRCULO = 2f
private const val Z_DEL_RESALTADO = 10f
