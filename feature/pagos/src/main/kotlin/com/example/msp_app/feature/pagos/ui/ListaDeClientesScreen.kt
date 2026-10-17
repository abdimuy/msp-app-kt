package com.example.msp_app.feature.pagos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.AvisoSinUbicacion
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import com.example.msp_app.feature.pagos.ui.components.HojaDeOrdenarPor
import com.example.msp_app.feature.pagos.ui.components.PildoraDeBuscar
import com.example.msp_app.feature.pagos.ui.components.RecargaAlVolver
import com.example.msp_app.feature.pagos.ui.components.RenglonDeOrden
import com.example.msp_app.feature.pagos.ui.components.SegmentadoDeCobranza
import com.example.msp_app.feature.pagos.ui.components.VerTodos
import java.time.LocalDate

/** `testTag` del campo de búsqueda grande (cabecera expandida). */
const val BUSCADOR_TAG: String = "pagos_buscador"

/** `testTag` del campo de búsqueda compacto (barra encogida, abierto desde la píldora). */
const val BUSCADOR_COMPACTO_TAG: String = "pagos_buscador_compacto"

/** `testTag` del botón de menú (hamburguesa). */
const val BOTON_MENU_TAG: String = "pagos_boton_menu"

/** `testTag` del título grande "Clientes" (26sp) de la parte de la cabecera que se esconde. */
const val TITULO_GRANDE_TAG: String = "pagos_titulo_grande"

/** `testTag` del título "Clientes" compacto (19sp) de la barra encogida. */
const val TITULO_COMPACTO_TAG: String = "pagos_titulo_compacto"

/** `testTag` de la ✕ que borra el texto del campo compacto. */
const val BORRAR_BUSQUEDA_COMPACTA_TAG: String = "pagos_buscador_compacto_borrar"

/** `testTag` del `LazyColumn` de clientes — lo usan las pruebas para simular scroll. */
const val LISTA_DE_CLIENTES_TAG: String = "pagos_lista_de_clientes"

/** `testTag` del mensaje que se pinta cuando no queda ningún cliente. */
const val LISTA_VACIA_TAG: String = "pagos_lista_vacia"

/**
 * El destino: conecta el ViewModel con el contenido puro **y provee el tema**.
 *
 * ## Por qué `MspTheme` vive aquí
 *
 * **`:app` NUNCA provee `MspTheme`.** Su `MainActivity` monta `MspappTheme` —el
 * Material legado del resto de la app, un sistema de composición DISTINTO— y su
 * `NavHost` no envuelve a ningún destino, así que sin este bloque la primera
 * lectura de `MspTheme.colors` (el `.background` del modificador más externo de
 * [ListaDeClientesContent]) revienta con
 * `IllegalStateException("MspTheme ausente")` apenas el cobrador toca
 * "Clientes" — medido en el emulador, con la base vacía: ni siquiera hace falta
 * que haya datos.
 *
 * Mismo envoltorio, mismo lugar y misma razón que
 * `feature.configuracion.ui.ConfiguracionScreen` y que `ThemeRevealRoot` en el
 * reporte de cobranza: **cada pantalla Msp se envuelve a sí misma**. En el
 * `*Screen` y no en la ruta, para que la pantalla quede correcta desde
 * cualquier host —`NavHost`, un `@Preview`, un bottom sheet futuro—, no solo
 * desde el registro que hoy la monta.
 *
 * **Y no en la raíz de `:app`**, que arreglaría las siete de un plumazo:
 * `MspTheme` monta además un `MaterialTheme` con su propio `colorScheme` y su
 * tipografía, así que ponerlo alrededor de `AppNavigation` repintaría **en
 * silencio** decenas de pantallas legadas que ningún golden cubre. El riesgo
 * dejaría de ser "siete pantallas crashean" —que se ve— para pasar a "la app
 * entera cambia de color" —que no—.
 *
 * ## Y por qué el tema lo envuelve `MspThemeRevealHost` y no `MspTheme` pelado
 *
 * Porque si no, **el mismo botón se sentiría distinto en dos pantallas de esta app**: el
 * `MspThemeToggle` del reporte de cobranza anima una reveal circular (su `ThemeRevealRoot`
 * instala el host) y el de acá haría un crossfade. Kollect instala el host en su raíz, así que
 * todos sus toggles animan igual; acá la raíz está prohibida (Ruling BJ: `MspTheme` alrededor
 * de `AppNavigation` repintaría la app legada), así que cada pantalla Msp instala el host sobre
 * sí misma — igual que ya hace con el tema. El mecanismo es UNO
 * ([MspThemeRevealHost], `:core:designsystem`); lo que cambia por pantalla es qué tema envuelve.
 *
 * La compuerta que impide que la pantalla número ocho vuelva a olvidarlo son
 * `ElTemaLoPoneLaPantallaTest` (monta este composable SIN tema) y
 * `CadaPantallaMspProveeSuTemaTest` de `:app` (escanea las fuentes de todos los
 * módulos). La primera vez que esto pasó quedó una nota en un KDoc, y la nota
 * no impidió la segunda.
 */
@Composable
fun ListaDeClientesScreen(
    viewModel: ListaDeClientesViewModel,
    onAbrirCliente: (Int) -> Unit,
    onAbrirMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onAbrirVenta: (Int) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Vuelve a leer al reanudarse — no al recibir un pago o una visita nuevos:
    // esta pantalla no los sabe, solo sabe que estuvo pausada. Ver su KDoc.
    RecargaAlVolver(viewModel::recargar)
    MspThemeRevealHost(
        onToggleTheme = viewModel::alternarTema,
        reducedMotion = rememberMspReducedMotion(),
        tema = { animateColors, contenido ->
            // `darkTheme` queda en su default (`appDarkTheme()` → `LocalAppDarkTheme` →
            // `ThemeController.isDarkMode`): el tema lo manda la app, no esta pantalla.
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        ListaDeClientesContent(
            state = state,
            onBuscar = viewModel::buscar,
            onElegirSegmento = viewModel::elegirSegmento,
            onAbrirCliente = onAbrirCliente,
            onAbrirVenta = onAbrirVenta,
            onReintentar = viewModel::cargar,
            onAlternarTema = viewModel::alternarTema,
            onAlternarPrivacidad = viewModel::alternarPrivacidad,
            onElegirOrden = viewModel::elegirOrden,
            onQuitarOrden = viewModel::quitarOrden,
            onAbrirMenu = onAbrirMenu,
            modifier = modifier
        )
    }
}

/**
 * La lista de cobranza **por cliente**: una fila por puerta, con sus ventas
 * dentro.
 *
 * ## Qué reemplaza
 *
 * Las DOS listas que mostraban lo mismo con lógicas distintas: la de
 * `SalesScreen` (tres pestañas sobre el enum legado `ESTADO_COBRANZA`) y la de
 * Home ("VENTAS CERCANAS", ordenada por distancia a los centroides). Ninguna de
 * las dos se borró aquí — los puntos de entrada los recableó la Task 21, que
 * además retiró `SalesScreen`.
 *
 * ## La cercanía queda relegada, y por qué
 *
 * El orden de esta lista es el de cobranza por default. La distancia sólo
 * participa cuando el cobrador elige "Más cerca" en la hoja "Ordenar por"
 * (decisión del dueño, 2026-10-05) — ver [RenglonDeOrden] y
 * [HojaDeOrdenarPor]. Antes de esa decisión el orden de cercanía reemplazaba
 * por completo al de cobranza en Home; ahora es uno más entre siete, elegido
 * a propósito y recordado sólo "durante el día".
 *
 * Composable PURO sobre [ListaDeClientesUiState]: no lee puertos, no deriva
 * estados, no ordena y no emite telemetría. Ordenar aquí sería reordenar la
 * ruta en cada recomposición.
 *
 * ## La cabecera que se encoge al bajar
 *
 * Arriba, fija: la hamburguesa, el título compacto y los dos botones. Debajo, la
 * parte que se esconde ([ZonaQueSeEncoge]): "Clientes" en grande con el orden a su
 * derecha, y el buscador. Debajo, fijos otra vez, los chips de segmento.
 *
 * El colapso es **continuo y ligado al dedo** (decisión del dueño, 2026-10-07): la
 * altura de la parte que se esconde, la escala y la opacidad del título grande, y la
 * aparición del título compacto se interpolan con
 * [androidx.compose.material3.TopAppBarState.collapsedFraction] — nada cambia de
 * rama a medio camino. Subiendo a media lista, se despliega sólo pasado un umbral de
 * recorrido o de velocidad; en el tope, siempre. Ver [rememberCabeceraQueSeEncoge],
 * que además documenta la causa raíz del teclado que se bajaba solo.
 *
 * El estado del colapso es UNO para los tres segmentos: cambiar de chip no abre ni
 * cierra la cabecera (decisión del dueño, 2026-10-07). Cada segmento sí conserva su
 * propio scroll ([ScrollPorSegmento]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListaDeClientesContent(
    state: ListaDeClientesUiState,
    onBuscar: (String) -> Unit,
    onElegirSegmento: (SegmentoDeCobranza) -> Unit,
    onAbrirCliente: (Int) -> Unit,
    onReintentar: () -> Unit,
    onAlternarTema: () -> Unit,
    onAlternarPrivacidad: () -> Unit,
    onElegirOrden: (OrdenDeLaLista) -> Unit,
    onQuitarOrden: () -> Unit,
    onAbrirMenu: () -> Unit,
    modifier: Modifier = Modifier,
    onAbrirVenta: (Int) -> Unit = {},
    /**
     * Hospedado y no fijo adentro: los goldens de la barra compacta
     * (`ListaMatrixScreenshotTest`) necesitan arrancar YA desplegados — sin
     * una interacción de scroll real que disparar bajo Roborazzi— y la única
     * forma de pedir eso es traer el `LazyListState` ya posicionado
     * (`rememberLazyListState(initialFirstVisibleItemIndex = N)`) desde fuera.
     * Una lista que arranca desplazada hace nacer la cabecera compacta.
     */
    listState: LazyListState = rememberLazyListState()
) {
    // **Un `LazyListState` POR SEGMENTO, no uno compartido.** Ver [ScrollPorSegmento]:
    // con uno solo, el scroll de un segmento se pegaba al otro y dos `LazyColumn` (el
    // que sale y el que entra del `AnimatedContent`) compartían estado. [listState]
    // sólo siembra el PRIMER segmento que se muestra; `rememberSaveable` lo conserva
    // al volver de otra pantalla o al recrearse la actividad.
    val scrollPorSegmento = rememberSaveable(saver = ScrollPorSegmento.Saver) {
        ScrollPorSegmento(state.segmento, listState)
    }
    fun estadoDe(segmento: SegmentoDeCobranza): LazyListState = scrollPorSegmento.de(segmento)

    val reducirMovimiento = rememberMspReducedMotion()
    val cabecera = rememberCabeceraQueSeEncoge(
        arrancaColapsada = listState.firstVisibleItemIndex > 0 ||
            listState.firstVisibleItemScrollOffset > 0,
        reducirMovimiento = reducirMovimiento,
        listaVisible = estadoDe(state.segmento)
    )
    val estadoCabecera = cabecera.state
    val expandida by remember { derivedStateOf { estadoCabecera.collapsedFraction <= 0f } }
    val colapsada by remember { derivedStateOf { estadoCabecera.collapsedFraction >= 1f } }
    val algoEncogida by remember { derivedStateOf { estadoCabecera.collapsedFraction > 0f } }
    val pildoraVisible by remember {
        derivedStateOf { estadoCabecera.collapsedFraction > FRACCION_DE_LA_PILDORA }
    }
    var hojaDeOrdenAbierta by remember { mutableStateOf(false) }
    var buscadorCompactoAbierto by remember { mutableStateOf(false) }
    var campoGrandeConFoco by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current

    // Cabecera abierta del todo: ya se ve el buscador grande, con el mismo texto.
    // Sin esto, bajar-subir-bajar podía dejar el campo compacto abierto sin foco.
    LaunchedEffect(expandida) {
        if (expandida) buscadorCompactoAbierto = false
    }
    LaunchedEffect(buscadorCompactoAbierto) {
        if (buscadorCompactoAbierto) {
            focusRequester.requestFocus()
            teclado?.show()
        }
    }

    // Elegir un orden nuevo (o "✕ Quitar", que también cambia `state.orden`
    // de vuelta a COBRANZA) vuelve al principio DEL SEGMENTO VISIBLE —y los
    // demás olvidan su posición, así que arrancan en el tope al visitarlos—: con
    // `key = clienteId` el cliente que estaba primero sólo se MUEVE a su
    // posición nueva, y sin este efecto el `LazyListState` seguía mirando
    // ese mismo índice — medido: elegir "Ventas nuevas" estando en el tope
    // dejaba la lista en el índice 38, el final del orden nuevo.
    //
    // **Qué cuenta como "cambio de orden".** Sólo el paso de un orden YA
    // MOSTRADO (con la carga terminada) a otro distinto. [ordenMostrado] es
    // `rememberSaveable`, y los estados `cargando` no cuentan, por el disparo
    // al restaurar: si Android mata el proceso con un orden elegido hoy, el
    // ViewModel NUEVO arranca `cargando` y en COBRANZA, y al terminar la carga
    // el orden restaurado por
    // [com.example.msp_app.feature.pagos.application.ControladorDeOrden.restaurar]
    // llega como si fuera un cambio. Medido con `StateRestorationTester`. Al
    // entrar en frío [ordenMostrado] es `null`: el primer orden mostrado nunca
    // mueve la lista.
    var ordenMostrado by rememberSaveable { mutableStateOf<OrdenDeLaLista?>(null) }
    LaunchedEffect(state.orden, state.cargando) {
        if (state.cargando) return@LaunchedEffect
        val anterior = ordenMostrado
        ordenMostrado = state.orden
        if (anterior != null && anterior != state.orden) {
            // Los OTROS segmentos también: su posición guardada es del orden
            // viejo y en el nuevo apunta a otros clientes.
            scrollPorSegmento.olvidarTodosMenos(state.segmento)
            estadoDe(state.segmento).scrollToItem(0)
        }
    }

    // **Cada búsqueda empieza en su primer resultado.** Buscar apaga el orden y la
    // lista se reordena; sin esto el `LazyListState` seguía anclado (por `key`) al
    // cliente que estaba arriba, ahora en el índice 24 de la lista nueva — el
    // cobrador veía resultados a media lista. `requestScrollToItem` y no
    // `scrollToItem`: se aplica en la MISMA medida que trae la lista nueva, sin un
    // cuadro intermedio anclado al cliente viejo. En `DisposableEffect` (fase de
    // aplicar, antes de medir) y comparando con la última consulta mostrada —que se
    // guarda— para que restaurar la pantalla con una búsqueda puesta no la regrese
    // al tope.
    val consultaMostrada = rememberSaveable { mutableStateOf(state.query) }
    DisposableEffect(state.query) {
        if (consultaMostrada.value != state.query) {
            consultaMostrada.value = state.query
            scrollPorSegmento.olvidarTodosMenos(state.segmento)
            estadoDe(state.segmento).requestScrollToItem(0)
        }
        onDispose {}
    }

    /**
     * Cierra el campo compacto SÓLO si está vacío (atrás o tocar fuera, mock
     * "Buscador" §1b): con texto, el cobrador sigue viendo su filtro activo y
     * sólo se le retira el foco/teclado — cerrar el campo de un toque
     * perdería de vista qué está filtrando la lista.
     */
    fun cerrarBuscadorCompactoSiVacio() {
        if (!buscadorCompactoAbierto) return
        if (state.query.isBlank()) buscadorCompactoAbierto = false
    }

    BackHandler(enabled = buscadorCompactoAbierto) { cerrarBuscadorCompactoSiVacio() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // DESPUÉS del `background` y no antes: el color se pinta a sangre —también DEBAJO
            // de la barra de estado, que es lo que `enableEdgeToEdge()` pide— y el inset solo
            // baja el CONTENIDO. `systemBars` y no `statusBars`: con `enableEdgeToEdge()` la
            // barra de navegación también queda encima, y el pie de la pantalla se pintaba
            // detrás de los botones de Android (reportado en vidrio, SM-A256E).
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
            // Sin botón de volver: es pantalla de NIVEL SUPERIOR — se llega desde
            // el cajón. La fila es la parte fija de la cabecera.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                BotonDeMenu(
                    onAbrirMenu = {
                        cerrarBuscadorCompactoSiVacio()
                        onAbrirMenu()
                    }
                )
                when {
                    buscadorCompactoAbierto && !expandida -> CampoDeBusquedaCompacto(
                        query = state.query,
                        onBuscar = onBuscar,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f)
                    )
                    // Mismo `weight` que el `Spacer`: aparecer no mueve a nadie.
                    algoEncogida -> TituloCompacto(
                        estado = estadoCabecera,
                        modifier = Modifier.weight(1f)
                    )
                    else -> Spacer(Modifier.weight(1f))
                }
                // Con el campo compacto abierto los dos botones se retiran —encogiéndose
                // de lado y desvaneciéndose— y el campo, con `weight(1f)`, se queda con
                // su ancho. El campo NO cambia de rama por esto: sólo crece, así que no
                // sale de la composición ni pierde el foco (ver ElBuscadorNoPierdeElFocoTest).
                AnimatedVisibility(
                    visible = !(buscadorCompactoAbierto && !expandida),
                    enter = if (reducirMovimiento) {
                        EnterTransition.None
                    } else {
                        fadeIn() + expandHorizontally(expandFrom = Alignment.Start)
                    },
                    exit = if (reducirMovimiento) {
                        ExitTransition.None
                    } else {
                        fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                    },
                    label = "botones_de_la_barra"
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
                    ) {
                        MspPrivacyEyeToggle(
                            masked = state.montosOcultos,
                            onToggle = {
                                cerrarBuscadorCompactoSiVacio()
                                onAlternarPrivacidad()
                            }
                        )
                        MspThemeToggle(
                            darkTheme = state.temaOscuro,
                            onToggle = {
                                cerrarBuscadorCompactoSiVacio()
                                onAlternarTema()
                            }
                        )
                    }
                }
            }
            ZonaQueSeEncoge(
                estado = estadoCabecera,
                // Toda escondida, fuera del árbol de accesibilidad: TalkBack no debe
                // leer un título y un campo que no se ven. Con el campo enfocado no se
                // toca nada — nunca se le quita el nodo a quien está escribiendo.
                modifier = if (colapsada && !campoGrandeConFoco) {
                    Modifier.clearAndSetSemantics {}
                } else {
                    Modifier
                }
            ) {
                Column {
                    FilaDelTitulo(
                        estado = estadoCabecera,
                        orden = state.orden,
                        mostrarOrden = state.query.isBlank(),
                        onAbrirHoja = { hojaDeOrdenAbierta = true },
                        onQuitarOrden = onQuitarOrden
                    )
                    // Con búsqueda activa el orden no se aplica ([CarteraEnPantalla]).
                    if (state.avisoSinUbicacion && state.query.isBlank()) {
                        AvisoSinUbicacion()
                    }
                    CampoDeBusqueda(
                        query = state.query,
                        onBuscar = onBuscar,
                        onFocoCambiado = { campoGrandeConFoco = it },
                        modifier = Modifier.graphicsLayer {
                            alpha = (1f - estadoCabecera.collapsedFraction * DESVANECER_CAMPO)
                                .coerceIn(0f, 1f)
                        }
                    )
                    Spacer(Modifier.height(MspTheme.spacing.sm))
                }
            }
            SegmentadoDeCobranza(
                seleccionado = state.segmento,
                conteos = state.conteos,
                onElegir = onElegirSegmento
            )
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }
        // Deslizar de lado cambia de filtro, sin tener que estirar el pulgar hasta
        // los chips. `detectHorizontalDragGestures` sólo consume el eje
        // horizontal, así que el desplazamiento vertical de la lista no se toca:
        // los dos gestos conviven porque no compiten por el mismo eje. El
        // `nestedScroll` le entrega a la cabecera el desplazamiento VERTICAL que
        // genera el dedo sobre la lista — y sólo ése.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(cabecera.nestedScrollConnection)
                .pointerInput(state.segmento) {
                    var recorrido = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { recorrido = 0f },
                        onDragEnd = {
                            // Un umbral, y no cualquier movimiento: la lista se
                            // recorre con el pulgar y casi ningún deslizamiento
                            // vertical sale perfectamente recto. Sin este mínimo, el
                            // filtro cambiaría solo mientras alguien baja la lista.
                            if (kotlin.math.abs(recorrido) >= UMBRAL_DEL_GESTO_PX) {
                                val paso = if (recorrido < 0) 1 else -1
                                val destino = SegmentoDeCobranza.entries
                                    .indexOf(state.segmento) + paso
                                // Sin dar la vuelta: llegar al último y seguir
                                // deslizando no debe regresar al primero.
                                SegmentoDeCobranza.entries.getOrNull(destino)
                                    ?.let(onElegirSegmento)
                            }
                        }
                    ) { _, arrastre -> recorrido += arrastre }
                }
        ) {
            // La lista entra deslizándose desde el lado hacia el que se fue el
            // dedo, y la anterior sale hacia el contrario: el movimiento dice QUÉ
            // cambió. `reducedMotion` lo apaga a un fundido.
            AnimatedContent(
                targetState = state.segmento,
                transitionSpec = {
                    if (reducirMovimiento) {
                        fadeIn() togetherWith fadeOut()
                    } else {
                        // Hacia adelante en la fila de chips: el contenido nuevo
                        // llega por la derecha. Hacia atrás, por la izquierda.
                        val haciaAdelante = SegmentoDeCobranza.entries.indexOf(targetState) >
                            SegmentoDeCobranza.entries.indexOf(initialState)
                        val signo = if (haciaAdelante) 1 else -1
                        // **Deslizamiento puro, sin fundido**: con `fadeOut` quedaba
                        // un hueco en blanco a media transición. Sin `SizeTransform`:
                        // las dos listas tienen alturas muy distintas y animar el alto
                        // del contenedor es otro tirón.
                        slideInHorizontally { ancho -> signo * ancho } togetherWith
                            slideOutHorizontally { ancho -> -signo * ancho }
                    }.using(sizeTransform = null)
                },
                contentAlignment = Alignment.TopStart,
                label = "contenido_por_segmento"
            ) { segmentoMostrado ->
                when {
                    state.cargando -> Cargando()
                    state.fallo -> MensajeDeFallo(onReintentar)
                    state.clientes.isEmpty() -> ListaVacia()
                    else -> Clientes(
                        clientes = state.clientes,
                        hoy = state.hoy,
                        montosOcultos = state.montosOcultos,
                        listState = estadoDe(segmentoMostrado),
                        onAbrirCliente = onAbrirCliente,
                        onAbrirVenta = onAbrirVenta
                    )
                }
            }
            PildoraDeBuscar(
                visible = pildoraVisible && !buscadorCompactoAbierto,
                reducedMotion = reducirMovimiento,
                onTocar = { buscadorCompactoAbierto = true },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
    HojaDeOrdenarPor(
        abierta = hojaDeOrdenAbierta,
        seleccionado = state.orden,
        onElegir = onElegirOrden,
        onCerrar = { hojaDeOrdenAbierta = false }
    )
}

/**
 * El botón de menú (hamburguesa).
 *
 * `tint = MspTheme.colors.onSurface` explícito — medido: sin él, `Icon` cae al
 * `LocalContentColor` por defecto (negro) y en tema oscuro queda negro sobre
 * negro, invisible. `IconButton` no fuerza un color de contenido propio.
 */
@Composable
private fun BotonDeMenu(onAbrirMenu: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onAbrirMenu,
        modifier = modifier
            .size(BOTON_MENU_SIZE)
            .testTag(BOTON_MENU_TAG)
    ) {
        Icon(
            imageVector = Icons.Default.Menu,
            contentDescription = "Abrir menú",
            tint = MspTheme.colors.onSurface
        )
    }
}

/**
 * "Clientes" chico, en la fila fija. Aparece en la segunda mitad del colapso —con un
 * leve ascenso— mientras el grande ya se desvaneció: los dos nunca se ven a la vez.
 * Se lee [TopAppBarState.collapsedFraction] en `graphicsLayer` (fase de dibujo): cada
 * cuadro del colapso no recompone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TituloCompacto(estado: TopAppBarState, modifier: Modifier = Modifier) {
    val subida = with(LocalDensity.current) { MspTheme.spacing.sm.toPx() }
    Text(
        text = "Clientes",
        style = MspTheme.type.screenTitleCompact,
        color = MspTheme.colors.onSurface,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer {
                val visible = (
                    (estado.collapsedFraction - INICIO_DEL_TITULO_COMPACTO) /
                        (1f - INICIO_DEL_TITULO_COMPACTO)
                    ).coerceIn(0f, 1f)
                alpha = visible
                translationY = (1f - visible) * subida
            }
            .testTag(TITULO_COMPACTO_TAG)
    )
}

/**
 * "Clientes" en grande y, a su derecha, el orden de la lista ([RenglonDeOrden]) — en
 * UNA fila: antes eran tres (título, la línea de la semana y el renglón de orden).
 *
 * El título se encoge hacia el tamaño compacto (26 → 19 sp, la razón de las dos
 * tipografías del tema) y se desvanece en la primera mitad del colapso, con el origen
 * en su orilla izquierda: se "va" hacia donde aparece el compacto. Escala de dibujo,
 * no de texto: el renglón no se vuelve a medir en cada cuadro.
 *
 * `FlowRow`: a letra muy grande el orden baja a su propio renglón en vez de apretar
 * al título. El alto mínimo es el del toque y no cambia cuando el orden se esconde
 * (buscando): teclear la primera letra no debe mover la lista.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilaDelTitulo(
    estado: TopAppBarState,
    orden: OrdenDeLaLista,
    mostrarOrden: Boolean,
    onAbrirHoja: () -> Unit,
    onQuitarOrden: () -> Unit
) {
    val escalaCompacta = MspTheme.type.screenTitleCompact.fontSize.value /
        MspTheme.type.screenTitleLarge.fontSize.value
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MspTheme.spacing.touchTarget),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Clientes",
            style = MspTheme.type.screenTitleLarge,
            color = MspTheme.colors.onSurface,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .graphicsLayer {
                    val f = estado.collapsedFraction
                    val escala = 1f + (escalaCompacta - 1f) * f
                    scaleX = escala
                    scaleY = escala
                    alpha = (1f - f * DESVANECER_TITULO).coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .testTag(TITULO_GRANDE_TAG)
        )
        if (mostrarOrden) {
            RenglonDeOrden(
                orden = orden,
                onAbrirHoja = onAbrirHoja,
                onQuitar = onQuitarOrden,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

/**
 * **Una fila por cliente.** `key = { it.clienteId }` es literalmente el arreglo
 * del defecto: la lista vieja usaba `key = { it.DOCTO_CC_ID }` y un cliente con
 * dos ventas salía dos veces, como si fueran dos personas.
 *
 * [listState] se hospeda — no se crea aquí — porque [ListaDeClientesContent]
 * lo necesita para derivar si la cabecera grande sigue vigente.
 */
@Composable
private fun Clientes(
    clientes: List<ClienteEnLista>,
    hoy: LocalDate,
    montosOcultos: Boolean,
    listState: LazyListState,
    onAbrirCliente: (Int) -> Unit,
    onAbrirVenta: (Int) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MspTheme.spacing.md)
            .testTag(LISTA_DE_CLIENTES_TAG),
        // Abajo, el alto de la píldora "Buscar cliente" y su margen: el último cliente
        // se puede subir por encima de ella en vez de quedar tapado.
        contentPadding = PaddingValues(bottom = MspTheme.spacing.touchTarget + MspTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
    ) {
        items(clientes, key = { it.clienteId }) { cliente ->
            FilaDeCliente(
                cliente = cliente,
                hoy = hoy,
                montosOcultos = montosOcultos,
                onAbrirCliente = { onAbrirCliente(cliente.clienteId) },
                onAbrirVenta = onAbrirVenta
            )
        }
    }
}

@Composable
private fun CampoDeBusqueda(
    query: String,
    onBuscar: (String) -> Unit,
    onFocoCambiado: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CampoDeBuscarCliente(
        query = query,
        onBuscar = onBuscar,
        conLupa = true,
        tag = BUSCADOR_TAG,
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { onFocoCambiado(it.isFocused) }
    )
}

/**
 * El campo compacto que aparece pegado arriba, en la barra encogida, al tocar
 * [PildoraDeBuscar] (mock "Buscador" §1b, decisión del dueño 2026-10-05). Mientras
 * está abierto, los botones de tema y de montos se esconden y el campo toma su ancho.
 *
 * La ✕ borra el texto y **pide el foco de vuelta** explícitamente
 * (`focusRequester.requestFocus()`): sin ese empujón el teclado se cerraría justo
 * cuando el cobrador querría seguir escribiendo.
 */
@Composable
private fun CampoDeBusquedaCompacto(
    query: String,
    onBuscar: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    CampoDeBuscarCliente(
        query = query,
        onBuscar = onBuscar,
        conLupa = false,
        tag = BUSCADOR_COMPACTO_TAG,
        tagDeBorrar = BORRAR_BUSQUEDA_COMPACTA_TAG,
        alBorrar = { focusRequester.requestFocus() },
        modifier = modifier.focusRequester(focusRequester)
    )
}

@Composable
private fun ListaVacia() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No hay clientes",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurfaceMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(LISTA_VACIA_TAG)
        )
    }
}

@Composable
private fun MensajeDeFallo(onReintentar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MspTheme.spacing.lg),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No se pudo cargar",
            style = MspTheme.type.cardTitle,
            color = MspTheme.colors.onSurface
        )
        Spacer(Modifier.height(MspTheme.spacing.sm))
        VerTodos("Reintentar", onReintentar)
    }
}

/**
 * Cuánto hay que deslizar de lado para que el filtro cambie.
 *
 * No es un número bonito: la lista se recorre con el pulgar y casi ningún
 * deslizamiento vertical sale recto, así que sin un mínimo el filtro cambiaría
 * solo mientras alguien baja la lista. 80 píxeles son un gesto que se hizo a
 * propósito y no el temblor de bajar el dedo.
 */
private const val UMBRAL_DEL_GESTO_PX = 80f

/**
 * Tamaño del botón de menú — 50dp, el extremo alto del rango 48-50dp que pide
 * el mock. Medido: a 48dp, `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`
 * lo marcaba en 96px contra el mínimo de 100px (≥50dp) que exige todo control
 * tocable del repo — el mock cede ante la compuerta, no al revés.
 */
internal val BOTON_MENU_SIZE = 50.dp

/** Más allá de esta fracción de colapso aparece la píldora "Buscar cliente". */
private const val FRACCION_DE_LA_PILDORA = 0.5f

/** El título compacto empieza a aparecer pasada esta fracción del colapso. */
private const val INICIO_DEL_TITULO_COMPACTO = 0.5f

/** El título grande termina de desvanecerse a 1/[DESVANECER_TITULO] del colapso. */
private const val DESVANECER_TITULO = 2f

/** El buscador grande termina de desvanecerse a 1/[DESVANECER_CAMPO] del colapso. */
private const val DESVANECER_CAMPO = 1.25f
