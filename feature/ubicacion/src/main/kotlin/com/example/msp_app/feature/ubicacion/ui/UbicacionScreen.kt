// La pantalla y sus piezas privadas (mapa, forma de cada marcador, cámara) viven
// juntas a propósito: separarlas expondría estado que sólo esta pantalla usa.
@file:Suppress("TooManyFunctions")

package com.example.msp_app.feature.ubicacion.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.core.geo.Punto
import com.example.msp_app.feature.ubicacion.domain.AreaVisible
import com.example.msp_app.feature.ubicacion.domain.Camara
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar
import com.example.msp_app.feature.ubicacion.domain.Encuadre
import com.example.msp_app.feature.ubicacion.domain.FiltroDeLugares
import com.example.msp_app.feature.ubicacion.domain.LugarClasificado
import com.example.msp_app.feature.ubicacion.domain.Textos
import com.example.msp_app.feature.ubicacion.domain.distanciaLegible
import com.example.msp_app.feature.ubicacion.domain.reglaDeEscala
import com.example.msp_app.feature.ubicacion.domain.sueltosFueraDeLaVista
import com.example.msp_app.feature.ubicacion.ui.components.AlturaDelDetalle
import com.example.msp_app.feature.ubicacion.ui.components.AvisoDePuntosLejos
import com.example.msp_app.feature.ubicacion.ui.components.BotonFiltrar
import com.example.msp_app.feature.ubicacion.ui.components.ChipDeOrilla
import com.example.msp_app.feature.ubicacion.ui.components.ControlRedondo
import com.example.msp_app.feature.ubicacion.ui.components.EtiquetaDelMarcador
import com.example.msp_app.feature.ubicacion.ui.components.FLECHA_DE_VOLVER
import com.example.msp_app.feature.ubicacion.ui.components.Figura
import com.example.msp_app.feature.ubicacion.ui.components.FormaDelMarcador
import com.example.msp_app.feature.ubicacion.ui.components.GLIFO_MI_UBICACION
import com.example.msp_app.feature.ubicacion.ui.components.HojaDeLosLugares
import com.example.msp_app.feature.ubicacion.ui.components.PanelDeFiltros
import com.example.msp_app.feature.ubicacion.ui.components.PosicionDeEtiqueta
import com.example.msp_app.feature.ubicacion.ui.components.ReglaDeEscala
import com.example.msp_app.feature.ubicacion.ui.components.grisDelMapa
import com.example.msp_app.feature.ubicacion.ui.components.recordarVariantes
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** `testTag` del botón de volver. */
const val VOLVER_TAG: String = "ubicacion_volver"

/** `testTag` de "Mi ubicación". */
const val MI_UBICACION_TAG: String = "ubicacion_mi_ubicacion"

/** `testTag` de la hoja al pie. */
const val HOJA_TAG: String = "ubicacion_hoja"

/** `testTag` del botón de filtros (compatibilidad con pruebas previas). */
const val FILTROS_TAG: String = "ubicacion_filtros"

/** Los estados de la hoja inferior. */
enum class ModoDeLaHoja { REPOSO, EXPANDIDA }

/**
 * **El mapa completo de lugares del cliente**, como el mock aprobado
 * (`docs/design/mocks/mapa-de-lugares.html`).
 *
 * La app **no dice qué es cada lugar**: sólo dónde se registraron los cobros.
 * La hoja nace en reposo con la primera card entera y el borde de la segunda;
 * arrastrada es la lista de lugares; con un lugar tocado es su historial.
 *
 * [hojaInicial] y [filtrosAbiertos] existen para fotografiar cada estado.
 */
@Composable
fun UbicacionScreen(
    state: UbicacionUiState,
    onAtras: () -> Unit,
    onComoLlegar: (LugarClasificado) -> Unit,
    onFiltro: (FiltroDeLugares) -> Unit,
    modifier: Modifier = Modifier,
    onTocarLugar: (LugarClasificado?) -> Unit = {},
    onAlternarTema: () -> Unit = {},
    hojaInicial: ModoDeLaHoja = ModoDeLaHoja.REPOSO,
    filtrosAbiertos: Boolean = false,
    detalleInicial: AlturaDelDetalle = AlturaDelDetalle.MEDIA,
    /**
     * `false` sólo en las pruebas: `GoogleMap` necesita Play Services, red y GL,
     * y bajo Robolectric no hay nada de eso. Los goldens fotografían todo lo
     * demás sobre el color de fondo del mapa.
     */
    pintarMapa: Boolean = true
) {
    // **La pantalla se envuelve a sí misma**: `:app` monta el Material legado,
    // nunca `MspTheme`. Lo cobra `CadaPantallaMspProveeSuTemaTest`.
    MspThemeRevealHost(
        onToggleTheme = onAlternarTema,
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        UbicacionContent(
            state,
            onAtras,
            onComoLlegar,
            onFiltro,
            onTocarLugar,
            onAlternarTema,
            hojaInicial,
            filtrosAbiertos,
            detalleInicial,
            pintarMapa,
            modifier
        )
    }
}

@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
private fun UbicacionContent(
    state: UbicacionUiState,
    onAtras: () -> Unit,
    onComoLlegar: (LugarClasificado) -> Unit,
    onFiltro: (FiltroDeLugares) -> Unit,
    onTocarLugar: (LugarClasificado?) -> Unit,
    onAlternarTema: () -> Unit,
    hojaInicial: ModoDeLaHoja,
    filtrosIniciales: Boolean,
    detalleInicial: AlturaDelDetalle,
    pintarMapa: Boolean,
    modifier: Modifier
) {
    val p = paletaDelMapa()
    val camara = rememberCameraPositionState()
    val alcance = rememberCoroutineScope()
    val contexto = LocalContext.current
    var filtros by remember { mutableStateOf(filtrosIniciales) }
    var modo by remember { mutableStateOf(hojaInicial) }
    var detalle by remember { mutableStateOf(detalleInicial) }
    // `true` cuando la hoja bajó porque el cobrador movió el mapa: ahí no se recentra.
    var bajoPorElMapa by remember { mutableStateOf(false) }
    // Tocar un lugar (card, marcador, fila) siempre abre el detalle en la media.
    val tocarLugar: (LugarClasificado?) -> Unit = { l ->
        if (l != null) detalle = AlturaDelDetalle.MEDIA
        onTocarLugar(l)
    }
    var conPermiso by remember { mutableStateOf(tienePermiso(contexto)) }
    var centradoEnMi by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(p.c(p.mapBg))) {
        val alto = maxHeight
        val ancho = maxWidth
        val alturaReposo = ALTO_EN_REPOSO
        val alturaExpandida = alto * FRACCION_EXPANDIDA
        val alturaMinima = ALTO_DETALLE_MINIMO
        val alturaMedia = ALTO_DETALLE_MEDIO
        val alturaCompleta = alto * FRACCION_EXPANDIDA
        val alturaDelDetalle = when (detalle) {
            AlturaDelDetalle.MINIMA -> alturaMinima
            AlturaDelDetalle.MEDIA -> alturaMedia
            AlturaDelDetalle.COMPLETA -> alturaCompleta
        }
        val objetivo = when {
            state.lugarTocado != null -> alturaDelDetalle
            modo == ModoDeLaHoja.EXPANDIDA -> alturaExpandida
            else -> alturaReposo
        }
        val altura = remember { Animatable(objetivo.value) }
        val alturaHoja = altura.value.dp
        val visibleAlto = (alto - alturaHoja - ARRIBA_DE_LOS_CONTROLES).value
        val densidad = LocalDensity.current

        // Lleva un punto al centro de lo que SE VE: entre los controles y la hoja
        // a la altura que tenga. Mide dónde quedó con la proyección real, así que
        // no depende de cómo el SDK aplique el `contentPadding` a media animación.
        val centrarEnLoVisible: suspend (Punto, Boolean) -> Unit = { punto, animado ->
            val proyeccion = camara.projection
            if (proyeccion != null) {
                val px = proyeccion.toScreenLocation(LatLng(punto.lat, punto.lon))
                val area = AreaVisible(
                    anchoPx = ancho.value * densidad.density,
                    altoPx = alto.value * densidad.density,
                    arribaPx = ARRIBA_DE_LOS_CONTROLES.value * densidad.density,
                    hojaPx = altura.value * densidad.density
                )
                val (dx, dy) = area.desplazamiento(px.x.toFloat(), px.y.toFloat())
                if (abs(dx) > 1f || abs(dy) > 1f) {
                    val mover = CameraUpdateFactory.scrollBy(dx, dy)
                    runCatching { if (animado) camara.animate(mover) else camara.move(mover) }
                }
            }
        }
        LaunchedEffect(objetivo) {
            altura.animateTo(objetivo.value)
            val l = state.lugarTocado
            val porElMapa = bajoPorElMapa
            bajoPorElMapa = false
            // Cambió la altura (arrastre, "Ver todos", el encabezado): el lugar se
            // queda en el centro de lo visible. Si la bajó el mapa, no se toca.
            if (l != null && !porElMapa && !camara.isMoving) centrarEnLoVisible(l.centro, true)
        }
        // Mover el mapa con el dedo con el detalle en la media lo baja a la mínima.
        LaunchedEffect(camara.isMoving) {
            val conDedo = camara.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
            val enLaMedia = state.lugarTocado != null && detalle == AlturaDelDetalle.MEDIA
            if (camara.isMoving && conDedo && enLaMedia) {
                bajoPorElMapa = true
                detalle = AlturaDelDetalle.MINIMA
            }
        }

        // El encuadre de entrada: principal + otros lugares + último contacto.
        // Nunca los sueltos, salvo que el cobrador encienda "Ver puntos sueltos".
        var encuadrado by remember { mutableStateOf(false) }
        LaunchedEffect(state.mapa, state.filtro.verSueltos) {
            if (state.mapa.todos.isEmpty()) return@LaunchedEffect
            if (encuadrado && !state.filtro.verSueltos) return@LaunchedEffect
            val puntos = state.mapa.encuadreDeEntrada +
                if (state.filtro.verSueltos) state.mapa.sueltos.map { it.centro } else emptyList()
            val c = Encuadre.de(puntos, ancho.value, visibleAlto, MARGEN_DEL_ENCUADRE) ?: return@LaunchedEffect
            camara.position = CameraPosition.fromLatLngZoom(LatLng(c.centro.lat, c.centro.lon), c.zoom)
            encuadrado = true
            // El encuadre de entrada también va al centro de lo visible, no de la pantalla.
            snapshotFlow { camara.projection }.first { it != null }
            withFrameNanos { }
            centrarEnLoVisible(c.centro, false)
        }
        // Tocar un lugar centra el mapa ahí; no reencuadra todo.
        // Y lo deja en el centro de lo visible una vez que la hoja llegó a su altura.
        LaunchedEffect(state.lugarTocado) {
            val l = state.lugarTocado ?: return@LaunchedEffect
            runCatching {
                camara.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(l.centro.lat, l.centro.lon),
                        ZOOM_DEL_LUGAR
                    )
                )
            }
            snapshotFlow { altura.isRunning }.first { !it }
            centrarEnLoVisible(l.centro, true)
        }

        val camaraActual = Camara(
            Punto(camara.position.target.latitude, camara.position.target.longitude),
            camara.position.zoom
        )
        val lejos = if (state.filtro.verSueltos) {
            emptyList()
        } else {
            state.mapa.sueltosFueraDeLaVista(camaraActual, ancho.value, visibleAlto)
        }

        if (pintarMapa) MapaDeLosLugares(state, camara, p, alturaHoja, conPermiso, tocarLugar)

        // ── controles de arriba: volver y tema juntos a la izquierda, Filtrar a la derecha ──
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp)) {
            ControlRedondo(
                icono = FLECHA_DE_VOLVER,
                descripcion = "Volver",
                onClick = onAtras,
                etiqueta = VOLVER_TAG,
                modifier = Modifier.padding(start = 12.dp)
            )
            Box(
                Modifier
                    .padding(start = 70.dp)
                    .size(50.dp)
                    .shadow(4.dp, CircleShape)
                    .clip(CircleShape)
                    .background(p.c(p.ctl))
            ) {
                MspThemeToggle(
                    darkTheme = p == PaletaDelMapa.OSCURO,
                    onToggle = onAlternarTema,
                    modifier = Modifier.fillMaxSize()
                )
            }
            BotonFiltrar(
                filtrado = !state.filtro.estaLimpio,
                onClick = { filtros = true },
                modifier = Modifier.align(
                    Alignment.TopEnd
                ).padding(end = 12.dp).testTag(FILTROS_TAG)
            )
            val tocado = state.lugarTocado
            val principal = state.mapa.principal
            if (tocado != null) {
                val d = tocado.distanciaAlPrincipalM
                if (principal != null && d != null) {
                    ChipDeOrilla(distanciaLegible(d), Modifier.padding(start = 12.dp, top = 62.dp))
                }
            } else if (lejos.isNotEmpty() && modo != ModoDeLaHoja.EXPANDIDA) {
                AvisoDePuntosLejos(
                    cuantos = lejos.size,
                    onVer = {
                        val todos = state.mapa.encuadreDeEntrada + state.mapa.sueltos.map { it.centro }
                        val c = Encuadre.de(todos, ancho.value, visibleAlto, MARGEN_DEL_ENCUADRE)
                        if (c != null) {
                            alcance.launch {
                                runCatching {
                                    camara.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(c.centro.lat, c.centro.lon),
                                            c.zoom
                                        )
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp)
                )
            }
        }

        // ── "Google" lo pinta el SDK; la escala y "Mi ubicación" van sobre la hoja ──
        val mpd = Encuadre.metrosPorDp(camara.position.target.latitude, camara.position.zoom)
        // Con la hoja arrastrada la escala y "Mi ubicación" quedan bajo la hoja, como en el mock.
        val hojaAlta = if (state.lugarTocado == null) {
            modo == ModoDeLaHoja.EXPANDIDA
        } else {
            detalle == AlturaDelDetalle.COMPLETA
        }
        if (camara.position.zoom >= ZOOM_MINIMO_DE_ESCALA && !hojaAlta) {
            val (etiqueta, anchoDp) = reglaDeEscala(mpd)
            ReglaDeEscala(
                etiqueta,
                anchoDp.toFloat().dp,
                Modifier.align(
                    Alignment.BottomEnd
                ).padding(end = 72.dp, bottom = alturaHoja + 10.dp)
            )
        }
        val pedirPermiso = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { res ->
            conPermiso = res.values.any { it }
            if (conPermiso) centrarEnMi(contexto, camara, alcance) { centradoEnMi = true }
        }
        if (!hojaAlta) {
            ControlRedondo(
                icono = GLIFO_MI_UBICACION,
                descripcion = "Mi ubicación",
                onClick = {
                    if (tienePermiso(contexto)) {
                        conPermiso = true
                        centrarEnMi(contexto, camara, alcance) { centradoEnMi = true }
                    } else {
                        runCatching {
                            pedirPermiso.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    }
                },
                etiqueta = MI_UBICACION_TAG,
                tinte = if (centradoEnMi) p.c(p.brand) else null,
                modifier = Modifier.align(
                    Alignment.BottomEnd
                ).padding(end = 12.dp, bottom = alturaHoja + 12.dp)
            )
        }

        // ── la hoja arrastrable ──
        val arrastre = rememberDraggableState { delta ->
            val (piso, techo) = if (state.lugarTocado == null) {
                alturaReposo.value to alturaExpandida.value
            } else {
                alturaMinima.value to alturaCompleta.value
            }
            val nuevo = (altura.value - delta / densidad.density).coerceIn(piso, techo)
            alcance.launch { altura.snapTo(nuevo) }
        }
        val asa = Modifier.draggable(
            state = arrastre,
            orientation = Orientation.Vertical,
            onDragStopped = { velocidad ->
                if (state.lugarTocado != null) {
                    // Tres alturas; soltar nunca cierra el detalle, a lo más lo deja en la mínima.
                    val alturas = listOf(
                        AlturaDelDetalle.MINIMA to alturaMinima.value,
                        AlturaDelDetalle.MEDIA to alturaMedia.value,
                        AlturaDelDetalle.COMPLETA to alturaCompleta.value
                    )
                    val ahora = altura.value
                    val destino = when {
                        velocidad < -UMBRAL_DE_VELOCIDAD -> alturas.firstOrNull { it.second > ahora + 1f } ?: alturas.last()
                        velocidad > UMBRAL_DE_VELOCIDAD -> alturas.lastOrNull { it.second < ahora - 1f } ?: alturas.first()
                        else -> alturas.minBy { abs(it.second - ahora) }
                    }
                    detalle = destino.first
                    altura.animateTo(destino.second)
                    return@draggable
                }
                val mitad = (alturaReposo.value + alturaExpandida.value) / 2
                val sube = velocidad < -UMBRAL_DE_VELOCIDAD || (velocidad <= UMBRAL_DE_VELOCIDAD && altura.value > mitad)
                modo = if (sube) ModoDeLaHoja.EXPANDIDA else ModoDeLaHoja.REPOSO
                altura.animateTo(if (sube) alturaExpandida.value else alturaReposo.value)
            }
        )
        // ── el atrás de Android deshace la última capa, igual que la flecha "<" ──
        // Los tres se registran AQUÍ, donde vive el estado, y en orden de capa:
        // el despachador atiende al último callback habilitado, así que el de
        // Filtros (encima de todo) va después del detalle, y el detalle después
        // de la lista arrastrada. Sin capa abierta ninguno está habilitado y el
        // atrás llega al NavHost. Lo cobra `AtrasDeshaceLaHojaTest`.
        BackHandler(enabled = modo == ModoDeLaHoja.EXPANDIDA && state.lugarTocado == null) {
            modo = ModoDeLaHoja.REPOSO
        }
        BackHandler(enabled = state.lugarTocado != null) { onTocarLugar(null) }
        HojaDeLosLugares(
            state = state,
            expandida = modo == ModoDeLaHoja.EXPANDIDA,
            onTocarLugar = tocarLugar,
            onComoLlegar = onComoLlegar,
            onAbrirFiltros = { filtros = true },
            asa = asa,
            alturaDelDetalle = detalle,
            onAlturaDelDetalle = { detalle = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .height(alturaHoja)
                .sombraDeHoja()
                .testTag(HOJA_TAG)
        )

        // ── filtros: velo y su propia hoja ──
        BackHandler(enabled = filtros) { filtros = false }
        if (filtros) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(p.scrim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { filtros = false }
            )
            PanelDeFiltros(
                state = state,
                onFiltro = onFiltro,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(alto - ALTO_LIBRE_SOBRE_FILTROS)
            )
        }
    }
}

// Las cuatro esquinas iguales a propósito: las de abajo caen fuera de la pantalla
// y no se ven, y una forma de esquinas desiguales hace que la capa de la sombra
// no reciba toques bajo Robolectric (el `Outline` genérico no se puede medir ahí).
private fun Modifier.sombraDeHoja(): Modifier = this.shadow(
    12.dp,
    androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
)

private fun tienePermiso(contexto: android.content.Context): Boolean = listOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
).any {
    ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
}

/**
 * Centra en el cobrador con la última ubicación conocida. Sin ubicación (GPS
 * apagado) no hace nada: el punto azul del SDK sigue diciendo la verdad.
 */
@SuppressLint("MissingPermission")
private fun centrarEnMi(
    contexto: android.content.Context,
    camara: CameraPositionState,
    alcance: kotlinx.coroutines.CoroutineScope,
    listo: () -> Unit
) {
    if (!tienePermiso(contexto)) return
    runCatching {
        LocationServices.getFusedLocationProviderClient(
            contexto
        ).lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                alcance.launch {
                    runCatching {
                        camara.animate(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude),
                                ZOOM_DEL_LUGAR
                            )
                        )
                    }
                    listo()
                }
            }
        }
    }
}

/**
 * El mapa con todos los lugares.
 *
 * **Los marcadores los pinta el mapa**, anclados a su coordenada — ver el KDoc
 * de `VariantesDelMarcador` para por qué eso respeta la decisión de los 21 dp.
 */
@Composable
@Suppress("LongMethod", "LongParameterList")
private fun MapaDeLosLugares(
    state: UbicacionUiState,
    camara: CameraPositionState,
    p: PaletaDelMapa,
    alturaHoja: Dp,
    conPermiso: Boolean,
    onTocarLugar: (LugarClasificado?) -> Unit
) {
    val variantes = recordarVariantes(p)
    val mapa = state.mapa
    val ultimo = mapa.lugarDelUltimo
    val tocado = state.lugarTocado
    val propiedades = propiedadesDelMapa().copy(isMyLocationEnabled = conPermiso)
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camara,
        // La atribución de Google sube por encima de la hoja: taparla viola los
        // términos del SDK.
        contentPadding = PaddingValues(bottom = alturaHoja),
        properties = propiedades,
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false
        ),
        onMapClick = { if (tocado != null) onTocarLugar(null) }
    ) {
        val principal = mapa.principal
        principal?.lugar?.radioDeConfianzaM?.let { radio ->
            Circle(
                center = LatLng(principal.centro.lat, principal.centro.lon),
                radius = radio,
                fillColor = Color(p.mkCasa).copy(alpha = ALFA_DEL_RELLENO),
                strokeColor = Color(p.mkCasa).copy(alpha = ALFA_DEL_BORDE),
                strokeWidth = 1f
            )
        }
        if (tocado != null && principal != null && tocado !== principal) {
            Polyline(
                points = listOf(
                    LatLng(tocado.centro.lat, tocado.centro.lon),
                    LatLng(principal.centro.lat, principal.centro.lon)
                ),
                color = Color(p.ink).copy(alpha = ALFA_DE_LA_REGLA),
                width = GROSOR_DE_LA_REGLA,
                pattern = listOf(Dash(GUION), Gap(GUION))
            )
        }
        mapa.todos.forEach { l ->
            val esTocado = tocado != null && l.centro == tocado.centro && l.conteo == tocado.conteo
            val forma = formaEnElMapa(l, p, esTocado, ultimo, state)
            val alfa = when {
                esTocado -> 1f
                l.clase == ClaseDeLugar.SUELTO && !state.filtro.verSueltos -> ALFA_SUELTO
                l.clase == ClaseDeLugar.PAGABA_ANTES -> ALFA_ANTES
                else -> 1f
            }
            val puntos = if (state.filtro.sinAgrupar) {
                l.lugar.mediciones.map { it.punto }
            } else {
                listOf(
                    l.centro
                )
            }
            puntos.forEachIndexed { i, punto ->
                key(l.centro, i) {
                    val f = if (state.filtro.sinAgrupar) {
                        forma.copy(
                            conteo = null,
                            diametroDp = DIAMETRO_DE_PUNTO,
                            etiqueta = null,
                            figura = figuraDePunto(forma)
                        )
                    } else {
                        forma
                    }
                    val dibujo = variantes.de(f)
                    val posicion = LatLng(punto.lat, punto.lon)
                    Marker(
                        state = remember(posicion) { MarkerState(posicion) },
                        icon = dibujo.icono,
                        anchor = Offset(dibujo.anclaX, dibujo.anclaY),
                        alpha = alfa,
                        zIndex = if (esTocado) Z_TOCADO else zDe(l.clase),
                        onClick = {
                            onTocarLugar(l)
                            true
                        }
                    )
                }
            }
        }
        state.visitas.forEach { v ->
            key("v-${v.id}") {
                val dibujo = variantes.de(FormaDelMarcador(Figura.ROMBO, p.mkVis))
                val posicion = LatLng(v.punto.lat, v.punto.lon)
                Marker(
                    state = remember(posicion) { MarkerState(posicion) },
                    icon = dibujo.icono,
                    anchor = Offset(dibujo.anclaX, dibujo.anclaY),
                    title = v.tipo,
                    zIndex = Z_VISITA
                )
            }
        }
    }
}

private fun figuraDePunto(f: FormaDelMarcador): Figura =
    if (f.figura == Figura.PIN) Figura.DISCO else f.figura

/** La forma de cada lugar en el mapa: exactamente el panel "Propuesta" del mock. */
internal fun formaEnElMapa(
    l: LugarClasificado,
    p: PaletaDelMapa,
    tocado: Boolean,
    ultimo: LugarClasificado?,
    state: UbicacionUiState
): FormaDelMarcador {
    val color = p.deClase(l.clase)
    if (l.clase == ClaseDeLugar.DONDE_MAS_PAGA) {
        return FormaDelMarcador(
            figura = Figura.PIN,
            color = color,
            etiqueta = EtiquetaDelMarcador(
                "Principal",
                "${l.conteo}",
                null,
                PosicionDeEtiqueta.DERECHA_DEL_PIN
            )
        )
    }
    val figura = if (l.clase == ClaseDeLugar.TRANSFERENCIAS) Figura.HUECO else Figura.DISCO
    val etiqueta = when {
        tocado -> EtiquetaDelMarcador(
            nombreCorto(l.clase),
            "${l.conteo}",
            color,
            PosicionDeEtiqueta.ARRIBA_DERECHA
        )
        ultimo != null && l === ultimo && l.clase != ClaseDeLugar.SUELTO -> EtiquetaDelMarcador(
            "Último",
            Textos.dia(
                l.lugar.masReciente,
                com.example.msp_app.core.common.time.BUSINESS_ZONE,
                state.ahora
            ),
            color,
            PosicionDeEtiqueta.ARRIBA
        )
        else -> null
    }
    return FormaDelMarcador(
        figura = figura,
        color = color,
        conteo = l.conteo,
        resaltado = tocado,
        etiqueta = etiqueta
    )
}

/** El nombre de la píldora del lugar tocado ("Otro lugar · 6"). */
internal fun nombreCorto(c: ClaseDeLugar): String = when (c) {
    ClaseDeLugar.DONDE_MAS_PAGA -> "Principal"
    ClaseDeLugar.OTRO_LUGAR -> "Otro lugar"
    ClaseDeLugar.PAGABA_ANTES -> "Antes"
    ClaseDeLugar.COMPARTIDO -> "Compartido"
    ClaseDeLugar.TRANSFERENCIAS -> "Transferencias"
    ClaseDeLugar.SUELTO -> "Suelto"
}

private fun zDe(c: ClaseDeLugar): Float = when (c) {
    ClaseDeLugar.DONDE_MAS_PAGA -> Z_PRINCIPAL
    ClaseDeLugar.OTRO_LUGAR -> Z_OTRO
    ClaseDeLugar.COMPARTIDO -> Z_COMPARTIDO
    ClaseDeLugar.TRANSFERENCIAS -> Z_TRANSFERENCIAS
    ClaseDeLugar.PAGABA_ANTES -> 1f
    ClaseDeLugar.SUELTO -> 0f
}

@Suppress("unused")
private fun grisDeEtiquetas(p: PaletaDelMapa) = grisDelMapa(p)

private val ALTO_EN_REPOSO = 230.dp
private const val FRACCION_EXPANDIDA = 0.86f
private val ALTO_DETALLE_MINIMO = 124.dp
private val ALTO_DETALLE_MEDIO = 300.dp
private val ALTO_LIBRE_SOBRE_FILTROS = 36.dp
private val ARRIBA_DE_LOS_CONTROLES = 72.dp
private const val MARGEN_DEL_ENCUADRE = 40f
private const val ZOOM_DEL_LUGAR = 17f
private const val ZOOM_MINIMO_DE_ESCALA = 10f
private const val UMBRAL_DE_VELOCIDAD = 600f
private const val ALFA_SUELTO = 0.45f
private const val ALFA_ANTES = 0.6f
private const val ALFA_DEL_RELLENO = 0.12f
private const val ALFA_DEL_BORDE = 0.35f
private const val ALFA_DE_LA_REGLA = 0.45f
private const val GROSOR_DE_LA_REGLA = 5f
private const val GUION = 12f
private const val DIAMETRO_DE_PUNTO = 14f
private const val Z_TOCADO = 10f
private const val Z_PRINCIPAL = 5f
private const val Z_OTRO = 4f
private const val Z_COMPARTIDO = 3f
private const val Z_TRANSFERENCIAS = 2f
private const val Z_VISITA = 6f
