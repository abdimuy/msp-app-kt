package com.example.msp_app.feature.pagos.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
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

/** `testTag` del título grande "Clientes" (26sp) de la cabecera expandida. */
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
 * ## La cabecera que se encoge al bajar (mock "Título" §1, decisión del dueño 2026-10-05)
 *
 * El título grande "Clientes" (26sp) y su línea de contexto —"semana del D al
 * D mes", [ListaDeClientesUiState.contextoSemana]— viven ARRIBA de la lista,
 * no dentro de ella. Al bajar lo suficiente desaparecen y "Clientes" pasa a
 * 19sp junto a la hamburguesa, en la misma fila que ya tenían los dos botones
 * del encabezado. Los chips de segmento y [RenglonDeOrden] NO se mueven con
 * este cambio: viven, igual que antes, en el bloque estático sobre la lista —
 * cuando el título grande se retira, simplemente quedan más arriba. Es lo que
 * el mock llama "quedan pegados arriba (sticky)", pero no hace falta un
 * mecanismo de *sticky header* dentro de un `LazyColumn`: nunca estuvieron
 * dentro de uno.
 *
 * [rememberCabeceraGrande] deriva el encogido de [LazyListState] —el mismo que
 * usa [Clientes]— en vez de `TopAppBarScrollBehavior`/`NestedScroll` de
 * Material 3: ese mecanismo está pensado para alimentar un `TopAppBar`/
 * `LargeTopAppBar` reales dentro de un `Scaffold`, y esta pantalla no usa
 * ninguno de los dos — tendría que fijar a mano un `heightOffsetLimit` sin
 * ninguna `TopAppBar` real que lo consuma. Derivar de `(index, offset)` del
 * propio `LazyListState` da exactamente las dos propiedades que el mock pide
 * —colapsa al bajar, "regreso rápido" con cualquier scroll hacia arriba— sin
 * ese rodeo, y es trivial de probar: un test de Compose sólo necesita mover el
 * `LazyListState` y leer si el título grande sigue en el árbol semántico.
 */
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
     */
    listState: LazyListState = rememberLazyListState()
) {
    // **Un `LazyListState` POR SEGMENTO, no uno compartido.** Antes había un
    // único `listState` hospedado aquí y pasado tal cual a [Clientes] sin
    // importar qué segmento se pintara — eso dejaba el scroll de "Volver"
    // pegado al volver de "Sin visitar" (medido: índice 19→19) y, peor, hace
    // que DOS `LazyColumn` (el que sale y el que entra del `AnimatedContent`)
    // compartan el mismo estado mientras se deslizan, algo que Compose no
    // soporta. [listState] (el parámetro, con su default o el que trae un
    // golden) sólo siembra el estado del PRIMER segmento que esta pantalla
    // muestra — los demás nacen con uno propio la primera vez que se visitan
    // y, a partir de ahí, lo conservan: cada segmento recuerda su propia
    // posición, igual que antes de que todos compartieran uno solo.
    val estadosPorSegmento = remember {
        mutableMapOf(state.segmento to listState)
    }
    fun estadoDe(segmento: SegmentoDeCobranza): LazyListState =
        estadosPorSegmento.getOrPut(segmento) { LazyListState() }

    val cabeceraGrande by rememberCabeceraGrande(estadoDe(state.segmento))
    var hojaDeOrdenAbierta by remember { mutableStateOf(false) }
    var buscadorCompactoAbierto by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val teclado = LocalSoftwareKeyboardController.current

    // Vuelta al título grande: ya no hace falta el campo pegado arriba. Sin
    // esto, bajar-subir-bajar podía dejar el campo compacto abierto sin foco
    // y sin forma de saber por qué seguía ahí.
    LaunchedEffect(cabeceraGrande) {
        if (cabeceraGrande) buscadorCompactoAbierto = false
    }
    LaunchedEffect(buscadorCompactoAbierto) {
        if (buscadorCompactoAbierto) {
            focusRequester.requestFocus()
            teclado?.show()
        }
    }

    // Elegir un orden nuevo (o "✕ Quitar", que también cambia `state.orden`
    // de vuelta a COBRANZA) vuelve al principio DEL SEGMENTO VISIBLE: con
    // `key = clienteId` el cliente que estaba primero sólo se MUEVE a su
    // posición nueva, y sin este efecto el `LazyListState` seguía mirando
    // ese mismo índice — medido: elegir "Ventas nuevas" estando en el tope
    // dejaba la lista en el índice 38, el final del orden nuevo. Se salta la
    // primera composición (el flag) para no pelear con el orden restaurado
    // de [com.example.msp_app.feature.pagos.application.ControladorDeOrden.restaurar]
    // al entrar a la pantalla.
    var yaComposoUnaVezElOrden by remember { mutableStateOf(false) }
    LaunchedEffect(state.orden) {
        if (yaComposoUnaVezElOrden) {
            estadoDe(state.segmento).scrollToItem(0)
        } else {
            yaComposoUnaVezElOrden = true
        }
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
            // baja el CONTENIDO. Al revés, la franja de la barra quedaría con el fondo del
            // tema legado de `MainActivity` y se vería clara en modo oscuro.
            // `systemBars` y no `statusBars`: el mismo argumento vale ABAJO. Con
            // `enableEdgeToEdge()` la barra de navegación también queda encima, y el pie de
            // la pantalla se pintaba detrás de los botones de Android (reportado en vidrio,
            // SM-A256E). El fondo sigue a sangre porque este padding va después del
            // `background`; lo único que se corre es el CONTENIDO.
            .systemBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = MspTheme.spacing.md)) {
            // Sin botón de volver: es pantalla de NIVEL SUPERIOR — se llega desde
            // el cajón, verificado en el aparato, y kollect tampoco lo pone en
            // ninguna de sus cuatro pestañas. La flecha no llevaba a ningún lado
            // útil y se comía una franja entera para ella sola.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MspTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                BotonDeMenu(
                    onAbrirMenu = {
                        cerrarBuscadorCompactoSiVacio()
                        onAbrirMenu()
                    }
                )
                // Con la cabecera grande, la fila sólo trae la hamburguesa y los
                // dos toggles — "Clientes" vive abajo, en [TituloGrande]. Encogida,
                // el título (o el campo compacto) ocupa el hueco de en medio.
                if (cabeceraGrande) {
                    Spacer(Modifier.weight(1f))
                } else if (buscadorCompactoAbierto) {
                    CampoDeBusquedaCompacto(
                        query = state.query,
                        onBuscar = onBuscar,
                        focusRequester = focusRequester,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Text(
                        text = "Clientes",
                        style = MspTheme.type.screenTitleCompact,
                        color = MspTheme.colors.onSurface,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TITULO_COMPACTO_TAG)
                    )
                }
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
            if (cabeceraGrande) {
                Spacer(Modifier.height(MspTheme.spacing.sm))
                TituloGrande(contextoSemana = state.contextoSemana)
                Spacer(Modifier.height(MspTheme.spacing.sm))
                CampoDeBusqueda(query = state.query, onBuscar = onBuscar)
            }
            Spacer(Modifier.height(MspTheme.spacing.sm))
            SegmentadoDeCobranza(
                seleccionado = state.segmento,
                conteos = state.conteos,
                onElegir = onElegirSegmento
            )
            // Con búsqueda activa el orden no se aplica ([CarteraEnPantalla] ya lo
            // hace) y el renglón que lo cambiaría no tiene nada que cambiar.
            if (state.query.isBlank()) {
                Spacer(Modifier.height(MspTheme.spacing.sm))
                RenglonDeOrden(
                    orden = state.orden,
                    conteo = state.clientes.size,
                    avisoSinUbicacion = state.avisoSinUbicacion,
                    onAbrirHoja = { hojaDeOrdenAbierta = true },
                    onQuitar = onQuitarOrden
                )
            }
            Spacer(Modifier.height(MspTheme.spacing.sm))
        }
        // Deslizar de lado cambia de filtro, sin tener que estirar el pulgar hasta
        // los chips. `detectHorizontalDragGestures` sólo consume el eje
        // horizontal, así que el desplazamiento vertical de la lista no se toca:
        // los dos gestos conviven porque no compiten por el mismo eje.
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                // deslizando no debe regresar al primero. En una fila
                                // de cuatro, saltar de "Pagados" a "Sin visitar" se
                                // lee como un error, no como una vuelta.
                                SegmentoDeCobranza.entries.getOrNull(destino)
                                    ?.let(onElegirSegmento)
                            }
                        }
                    ) { _, arrastre -> recorrido += arrastre }
                }
        ) {
            // La lista entra deslizándose desde el lado hacia el que se fue el
            // dedo, y la anterior sale hacia el contrario. El movimiento no es
            // adorno: es lo que dice QUÉ cambió. Un cambio seco entre dos listas
            // de nombres parecidos se lee como un parpadeo, y el cobrador no
            // alcanza a ver que está mirando otro filtro.
            //
            // `reducedMotion` lo apaga a un fundido: quien pidió menos animación
            // en el sistema no debe recibir desplazamientos laterales, y aun así
            // necesita ver que la lista cambió.
            val reducirMovimiento = rememberMspReducedMotion()
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
                        // **Deslizamiento puro, sin fundido.** Con `fadeOut` la
                        // lista vieja se desvanecía antes de que la nueva
                        // terminara de entrar, y en medio quedaba un hueco en
                        // blanco: se veía como un tirón, no como un movimiento.
                        // Las dos listas se mueven juntas y en sentidos
                        // contrarios, como las páginas de un carrusel — una
                        // empuja a la otra y nunca hay vacío entre ellas.
                        slideInHorizontally { ancho -> signo * ancho } togetherWith
                            slideOutHorizontally { ancho -> -signo * ancho }
                        // Sin `SizeTransform`: las dos listas tienen alturas muy
                        // distintas (268 clientes contra 1) y animar el alto del
                        // contenedor lo hace crecer o encogerse a media entrada,
                        // que es el otro tirón. El contenedor se queda quieto y
                        // sólo se mueve lo que está adentro.
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
                visible = !cabeceraGrande && !buscadorCompactoAbierto,
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
 * Deriva si la cabecera grande debe mostrarse, a partir del scroll de
 * [listState] — ver el KDoc de [ListaDeClientesContent], sección "La cabecera
 * que se encoge al bajar", para por qué es esto y no
 * `TopAppBarScrollBehavior`.
 *
 * Tres reglas, en este orden:
 * 1. En el tope (`index == 0` y `offset` por debajo de [UMBRAL_INICIO]):
 *    siempre grande. Cubre el caso "la lista se repobló y el scroll volvió a
 *    cero".
 * 2. Cualquier movimiento HACIA el principio (`index` o `offset` bajando):
 *    siempre grande. Es el "regreso rápido" del mock — no hace falta llegar
 *    al tope, un solo gesto hacia arriba basta.
 * 3. Movimiento hacia ABAJO más allá de [UMBRAL_COLAPSO] (o que ya salió
 *    del primer elemento): encoge. El umbral evita que el primer pixel de un
 *    scroll a medias encoja la cabecera por un temblor del dedo.
 */
@Composable
private fun rememberCabeceraGrande(listState: LazyListState): State<Boolean> {
    val umbralColapsoPx = with(LocalDensity.current) { UMBRAL_COLAPSO.toPx() }
    val umbralInicioPx = with(LocalDensity.current) { UMBRAL_INICIO.toPx() }
    // `derivedStateOf` y no `LaunchedEffect` + `snapshotFlow`: la primera versión
    // media el (index, offset) dentro de una corrutina lanzada por el efecto, y
    // bajo Robolectric esa corrutina no se alcanzaba a ejecutar dentro de la misma
    // pasada de "idle" que `performScrollToIndex` espera — medido:
    // `ListaDeClientesHeaderTest` quedaba con el título grande puesto después de
    // scrollear al último índice, en las cuatro pruebas que dependían de bajar.
    // `derivedStateOf` recalcula de forma síncrona, como parte del propio sistema
    // de snapshots que ya invalida `firstVisibleItemIndex`/`Offset` — sin pasar
    // por un dispatcher de corrutinas intermedio.
    return remember(listState, umbralColapsoPx, umbralInicioPx) {
        var indicePrevio = listState.firstVisibleItemIndex
        var offsetPrevio = listState.firstVisibleItemScrollOffset
        var grande = indicePrevio == 0
        derivedStateOf {
            val indice = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (indice != indicePrevio || offset != offsetPrevio) {
                val haciaElPrincipio = indice < indicePrevio ||
                    (indice == indicePrevio && offset < offsetPrevio)
                val haciaAbajo = indice > indicePrevio ||
                    (indice == indicePrevio && offset > offsetPrevio)
                grande = when {
                    indice == 0 && offset <= umbralInicioPx -> true
                    haciaElPrincipio -> true
                    haciaAbajo && (indice > 0 || offset > umbralColapsoPx) -> false
                    else -> grande
                }
                indicePrevio = indice
                offsetPrevio = offset
            }
            grande
        }
    }
}

/**
 * El botón de menú (hamburguesa).
 *
 * `tint = MspTheme.colors.onSurface` explícito — medido: sin él, `Icon` cae al
 * `LocalContentColor` por defecto (negro) y en tema oscuro queda negro sobre
 * negro, invisible (`pagos_lista_dark_1_0.png`/`pagos_barra_compacta_dark_1_0.png`,
 * antes de este arreglo). `IconButton` no fuerza un color de contenido
 * propio, sólo hereda el ambiente — a diferencia de `OutlinedTextField`, que
 * sí resuelve el color de sus iconos contra `colorScheme` y por eso la lupa y
 * la ✕ del buscador nunca tuvieron este problema (verificado en el mismo
 * golden: se ven bien en oscuro).
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

/** El título grande y su línea de contexto — cabecera expandida. */
@Composable
private fun TituloGrande(contextoSemana: String) {
    Column {
        Text(
            text = "Clientes",
            style = MspTheme.type.screenTitleLarge,
            color = MspTheme.colors.onSurface,
            modifier = Modifier.testTag(TITULO_GRANDE_TAG)
        )
        if (contextoSemana.isNotBlank()) {
            Spacer(Modifier.height(MspTheme.spacing.xs))
            Text(
                text = contextoSemana,
                style = MspTheme.type.contextNote,
                color = MspTheme.colors.onSurfaceMuted
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
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
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
private fun CampoDeBusqueda(query: String, onBuscar: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onBuscar,
        modifier = Modifier
            .fillMaxWidth()
            // El mínimo del plan es 50px; el default de Material ya está encima,
            // pero se fija para que no dependa de un default que puede cambiar.
            .heightIn(min = MspTheme.spacing.touchTarget)
            .testTag(BUSCADOR_TAG),
        placeholder = { Text("Buscar cliente", style = MspTheme.type.body) },
        singleLine = true,
        shape = MspTheme.shapes.field,
        textStyle = MspTheme.type.body,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onBuscar("") },
                    modifier = Modifier.heightIn(min = MspTheme.spacing.touchTarget)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                }
            }
        }
    )
}

/**
 * El campo compacto que aparece pegado arriba, en la barra encogida, al tocar
 * [PildoraDeBuscar] (mock "Buscador" §1b, decisión del dueño 2026-10-05).
 *
 * La ✕ borra el texto y **pide el foco de vuelta** explícitamente
 * (`focusRequester.requestFocus()`): tocar el `IconButton` le pasa el foco a
 * él por un instante, y sin este empujón el teclado se cerraría justo cuando
 * el cobrador querría seguir escribiendo — lo contrario de lo que pide el mock
 * ("deja el foco/teclado para seguir escribiendo").
 */
@Composable
private fun CampoDeBusquedaCompacto(
    query: String,
    onBuscar: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onBuscar,
        modifier = modifier
            .heightIn(min = MspTheme.spacing.touchTarget)
            .focusRequester(focusRequester)
            .testTag(BUSCADOR_COMPACTO_TAG),
        placeholder = { Text("Buscar cliente", style = MspTheme.type.body) },
        singleLine = true,
        shape = MspTheme.shapes.field,
        textStyle = MspTheme.type.body,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = ImeAction.Search
        ),
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = {
                        onBuscar("")
                        focusRequester.requestFocus()
                    },
                    modifier = Modifier
                        .heightIn(min = MspTheme.spacing.touchTarget)
                        .testTag(BORRAR_BUSQUEDA_COMPACTA_TAG)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda")
                }
            }
        }
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
private val BOTON_MENU_SIZE = 50.dp

/** Debajo de este offset, en el primer elemento, la cabecera sigue grande sin importar nada más. */
private val UMBRAL_INICIO = 8.dp

/** Más allá de este offset hacia abajo (todavía en el primer elemento) la cabecera encoge. */
private val UMBRAL_COLAPSO = 24.dp
