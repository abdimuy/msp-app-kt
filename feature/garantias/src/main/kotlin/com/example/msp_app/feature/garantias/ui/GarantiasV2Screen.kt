package com.example.msp_app.feature.garantias.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.garantias.ui.components.rememberEstadoDelPadDeFirma
import kotlinx.coroutines.launch

/**
 * La captura de garantías v2: la pantalla que `:app` registra.
 *
 * ## Un solo destino en `:app`, cuatro pantallas adentro
 *
 * `:app` registra únicamente [GarantiasV2Rutas.INICIO]; las otras cuatro
 * pantallas —abrir folio, detalle, recolección y entrega— viven en el `NavHost`
 * **anidado** que arma esta pantalla. `ReglaDelOrigenTest` fija el conjunto
 * exacto de destinos de `:app` y una ruta nueva registrada ahí lo pone rojo.
 *
 * ## Provee el tema, y lo provee animado
 *
 * `:app` monta `MspappTheme`, no `MspTheme`; una pantalla de un módulo `feature`
 * que lea tokens Msp sin envolverlos revienta al abrirse. El envoltorio es
 * [MspThemeRevealHost] y no un `MspTheme` pelado: el sol/luna de la app tiene
 * que sentirse igual en todas las pantallas nuevas, y
 * `CadaPantallaConTemaAnimaElCambioTest` lo exige por escaneo de fuentes. Hoy
 * esta pantalla no pinta el toggle —de ahí el `onToggleTheme` inerte—.
 *
 * ## Se pinta a sangre
 *
 * El fondo va antes del inset para que el color llegue debajo de la barra de
 * estado, y el inset corre sólo el contenido: con `enableEdgeToEdge()` la
 * ventana del sistema queda encima y se come los taps del encabezado.
 */
@Composable
fun GarantiasV2Screen(onAtras: () -> Unit, modifier: Modifier = Modifier) {
    MspThemeRevealHost(
        onToggleTheme = {},
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MspTheme.colors.background)
                .statusBarsPadding()
        ) {
            val nav = rememberNavController()
            NavHost(
                navController = nav,
                startDestination = GarantiasV2Rutas.INICIO,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(GarantiasV2Rutas.INICIO) {
                    RutaDeBandeja(
                        onAtras = onAtras,
                        onAbrirFolio = { nav.navigate(GarantiasV2Rutas.ABRIR) },
                        onFolio = { folioRef -> nav.navigate(GarantiasV2Rutas.folio(folioRef)) }
                    )
                }
                composable(GarantiasV2Rutas.ABRIR) {
                    RutaDeAbrirFolio(
                        onAtras = { nav.popBackStack() },
                        onFolioCreado = { folioRef ->
                            nav.navigate(GarantiasV2Rutas.folio(folioRef)) {
                                popUpTo(GarantiasV2Rutas.ABRIR) { inclusive = true }
                            }
                        }
                    )
                }
                composable(
                    route = GarantiasV2Rutas.FOLIO,
                    arguments = listOf(
                        navArgument(GarantiasV2Rutas.ARG_FOLIO) { type = NavType.StringType }
                    )
                ) {
                    RutaDeDetalleDeFolio(
                        onAtras = { nav.popBackStack() },
                        onRecolectar = { folioRef, articuloRef ->
                            nav.navigate(GarantiasV2Rutas.recolectar(folioRef, articuloRef))
                        },
                        onEntregar = { folioRef ->
                            nav.navigate(GarantiasV2Rutas.entregar(folioRef))
                        }
                    )
                }
                composable(
                    route = GarantiasV2Rutas.RECOLECTAR,
                    arguments = listOf(
                        navArgument(GarantiasV2Rutas.ARG_FOLIO) { type = NavType.StringType },
                        navArgument(GarantiasV2Rutas.ARG_ARTICULO) { type = NavType.StringType }
                    )
                ) {
                    RutaDeRecoleccion(
                        onAtras = { nav.popBackStack() },
                        onGuardado = { nav.popBackStack() }
                    )
                }
                composable(
                    route = GarantiasV2Rutas.ENTREGAR,
                    arguments = listOf(
                        navArgument(GarantiasV2Rutas.ARG_FOLIO) { type = NavType.StringType }
                    )
                ) {
                    RutaDeEntrega(
                        onAtras = { nav.popBackStack() },
                        onGuardado = { nav.popBackStack() }
                    )
                }
            }
        }
    }
}

/** La bandeja de "mis folios": la primera lectura la dispara su ruta. */
@Composable
private fun RutaDeBandeja(
    onAtras: () -> Unit,
    onAbrirFolio: () -> Unit,
    onFolio: (String) -> Unit,
    viewModel: BandejaDeGarantiasViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.cargar() }
    BandejaDeGarantiasContenido(
        state = state,
        onAtras = onAtras,
        onAbrirFolio = onAbrirFolio,
        onFolio = onFolio,
        onRecargar = viewModel::cargar
    )
}

/** El alta de un folio; al encolarse navega a su detalle. */
@Composable
private fun RutaDeAbrirFolio(
    onAtras: () -> Unit,
    onFolioCreado: (String) -> Unit,
    viewModel: AbrirFolioViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val creado = state.folioCreado
    if (creado != null) {
        LaunchedEffect(creado) {
            onFolioCreado(creado)
            viewModel.folioNavegado()
        }
    }
    AbrirFolioContenido(
        state = state,
        onOrigen = viewModel::elegirOrigen,
        onBusqueda = viewModel::cambiarBusqueda,
        onCliente = viewModel::elegirCliente,
        onDomicilio = viewModel::cambiarDomicilio,
        onDescripcion = viewModel::cambiarDescripcion,
        onClave = viewModel::cambiarClaveDelArticulo,
        onDescripcionDelArticulo = viewModel::cambiarDescripcionDelArticulo,
        onVigencia = viewModel::cambiarVigencia,
        onGuardar = viewModel::guardar,
        onAtras = onAtras
    )
}

/** El detalle del folio; se relee al volver de una recolección o entrega. */
@Composable
private fun RutaDeDetalleDeFolio(
    onAtras: () -> Unit,
    onRecolectar: (folioRef: String, articuloRef: String) -> Unit,
    onEntregar: (folioRef: String) -> Unit,
    viewModel: DetalleDeFolioViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.cargar() }
    DetalleDeFolioContenido(
        state = state,
        onRecolectar = { articuloRef -> onRecolectar(viewModel.folioRef, articuloRef) },
        onEntregar = { onEntregar(viewModel.folioRef) },
        onRecargar = viewModel::cargar,
        onAtras = onAtras
    )
}

/**
 * La recolección de un artículo.
 *
 * La cámara vive acá y no en el ViewModel: el contrato `TakePicture()` necesita
 * `android.net.Uri` y el ViewModel sólo publica el `content://` en su estado. Al
 * terminar, vuelve al detalle.
 */
@Composable
private fun RutaDeRecoleccion(
    onAtras: () -> Unit,
    onGuardado: () -> Unit,
    viewModel: RecoleccionViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) viewModel.fotoTomada() else viewModel.fotoCancelada()
    }
    LaunchedEffect(Unit) { viewModel.cargar() }
    val destino = state.destinoDeFoto
    if (destino != null) {
        LaunchedEffect(destino) { camara.launch(Uri.parse(destino)) }
    }
    val guardado = state.guardado
    if (guardado) {
        LaunchedEffect(guardado) {
            onGuardado()
            viewModel.guardadoNavegado()
        }
    }
    RecoleccionContenido(
        state = state,
        onAccion = viewModel::elegirAccion,
        onTomarFoto = viewModel::tomarFoto,
        onQuitarFoto = viewModel::quitarFoto,
        onPedirUbicacion = viewModel::pedirUbicacion,
        onGuardar = viewModel::guardar,
        onAtras = onAtras
    )
}

/**
 * La entrega con firma.
 *
 * El pad de la firma lo posee **esta** capa, no el ViewModel: el PNG se pide al
 * guardar y sólo entonces viaja. Al terminar, vuelve al detalle.
 */
@Composable
private fun RutaDeEntrega(
    onAtras: () -> Unit,
    onGuardado: () -> Unit,
    viewModel: EntregaViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pad = rememberEstadoDelPadDeFirma()
    val alcance = rememberCoroutineScope()
    val guardado = state.guardado
    if (guardado) {
        LaunchedEffect(guardado) {
            onGuardado()
            viewModel.guardadoNavegado()
        }
    }
    EntregaContenido(
        state = state,
        estadoDelPad = pad,
        onLimpiarFirma = pad::limpiar,
        onPedirUbicacion = viewModel::pedirUbicacion,
        onGuardar = { alcance.launch { viewModel.guardar(pad.aPng()) } },
        onAtras = onAtras
    )
}
