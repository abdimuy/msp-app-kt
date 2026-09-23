package com.example.msp_app.feature.pagos.ui

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.designsystem.component.MspPrivacyEyeToggle
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberMspReducedMotion
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import com.example.msp_app.feature.pagos.ui.components.RecargaAlVolver
import com.example.msp_app.feature.pagos.ui.components.SegmentadoDeCobranza
import com.example.msp_app.feature.pagos.ui.components.VerTodos

/** `testTag` del campo de búsqueda. */
const val BUSCADOR_TAG: String = "pagos_buscador"

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
    modifier: Modifier = Modifier
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
            onReintentar = viewModel::cargar,
            onAlternarTema = viewModel::alternarTema,
            onAlternarPrivacidad = viewModel::alternarPrivacidad,
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
 * El orden de esta lista es el de cobranza: primero quien no ha abonado nada,
 * después las ventas más viejas. La distancia **no participa**. Ordenar por
 * cercanía optimiza gasolina, no cobranza: pone al principio al vecino que ya
 * pagó y manda al final la puerta donde no ha entrado un peso desde que se
 * entregó el mueble. La cercanía tiene su lugar —"estoy aquí, muéstrame su
 * venta"— y ese lugar es entrar por el mapa o por el bloque de cercanas, no
 * reordenar el día.
 *
 * Composable PURO sobre [ListaDeClientesUiState]: no lee puertos, no deriva
 * estados, no ordena y no emite telemetría. Ordenar aquí sería reordenar la
 * ruta en cada recomposición.
 *
 * ## El botón de modo oscuro, y por qué en ESTA pantalla
 *
 * El encabezado cuelga [MspThemeToggle] del hueco de la derecha que
 * `BarraDeDetalle` ya tenía. kollect pone su cluster de toggles en el
 * encabezado de sus **5 pantallas de nivel superior** y en ninguna de las
 * empujadas, donde el encabezado es del botón de atrás y del contexto. De las
 * siete pantallas de cobranza, **ésta es la única de nivel superior**: es la que
 * el cajón abre (`DrawerContainer` → `PagosRutas.LISTA_CLIENTES`); a las otras
 * seis se llega empujadas desde otra pantalla, y las dos de ticket ni eso —
 * aparecen después de cobrar. Ponerlo en las siete sería copiarle a kollect algo
 * que kollect no hace.
 *
 * **Sin `HeaderToggles`.** El envoltorio de kollect existe para juntar DOS
 * controles (tema + ojo de privacidad) y darlos "de forma consistente en todas
 * partes". Acá el ojo no se construye —enmascarar montos es funcionalidad
 * nueva que nadie pidió—, así que un `Row` de un solo hijo, en un solo sitio,
 * sería una abstracción sin nada que abstraer (YAGNI). El día que exista el
 * ojo, `HeaderToggles` es el molde y este `accion` es donde entra.
 *
 * [onAlternarTema] **no tiene default**. Un `= {}` habría dejado compilar a
 * cualquier host que se olvidara de cablearlo, y el síntoma sería un botón que
 * se ve, se puede tocar y no hace nada — un defecto que ningún golden
 * fotografía.
 *
 * ## Y pasó igual, por la otra puerta: `statusBarsPadding()`
 *
 * El cableado estaba bien y el botón estaba muerto de todos modos. La app corre
 * `enableEdgeToEdge()`, así que el contenido arranca en `y = 0` y la ventana
 * `StatusBar` del sistema —156 px en el emulador donde se midió— queda ENCIMA
 * del encabezado: se come todo tap por arriba de su borde, el centro del toggle
 * incluido. Medido: taps a `y ≤ 155` perdidos, taps a `y ≥ 157` llegados, sobre
 * un botón cuya caja tocable va de 36 a 180. Nada en logcat, porque el evento
 * nunca entró al proceso.
 *
 * Ninguna pantalla de este módulo ni de `:feature:visitas` consumía el inset, y
 * **todas las demás del repo sí** (las siete legadas, el reporte de cobranza,
 * Configuración). Ruling BR las arregló las siete. La compuerta es
 * `CadaPantallaDeCobranzaRespetaLaBarraDeEstadoTest`, que despacha un inset de
 * barra de estado al árbol de vistas —lo único que una composición de test nunca
 * recibe— y exige que ningún control tocable arranque por encima de él.
 * `performClick()` no podía verlo: despacha sobre el nodo de semántica, sin
 * pasar por el sistema de ventanas.
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
    modifier: Modifier = Modifier
) {
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
            //
            // Los dos botones comparten el renglón del título en vez de tener el
            // suyo, que era el otro reclamo del dueño sobre esta cabecera.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = MspTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
            ) {
                Text(
                    text = "Clientes",
                    style = MspTheme.type.screenTitle,
                    color = MspTheme.colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                MspPrivacyEyeToggle(
                    masked = state.montosOcultos,
                    onToggle = onAlternarPrivacidad
                )
                MspThemeToggle(
                    darkTheme = state.temaOscuro,
                    onToggle = onAlternarTema
                )
            }
            Spacer(Modifier.height(MspTheme.spacing.sm))
            CampoDeBusqueda(query = state.query, onBuscar = onBuscar)
            Spacer(Modifier.height(MspTheme.spacing.sm))
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
        // los dos gestos conviven porque no compiten por el mismo eje.
        Box(
            modifier = Modifier.pointerInput(state.segmento) {
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
            ) { _ ->
                when {
                    state.cargando -> Cargando()
                    state.fallo -> MensajeDeFallo(onReintentar)
                    state.clientes.isEmpty() -> ListaVacia()
                    else -> Clientes(
                        clientes = state.clientes,
                        montosOcultos = state.montosOcultos,
                        onAbrirCliente = onAbrirCliente
                    )
                }
            }
        }
    }
}

/**
 * **Una fila por cliente.** `key = { it.clienteId }` es literalmente el arreglo
 * del defecto: la lista vieja usaba `key = { it.DOCTO_CC_ID }` y un cliente con
 * dos ventas salía dos veces, como si fueran dos personas.
 */
@Composable
private fun Clientes(
    clientes: List<ClienteEnLista>,
    montosOcultos: Boolean,
    onAbrirCliente: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = MspTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
    ) {
        items(clientes, key = { it.clienteId }) { cliente ->
            FilaDeCliente(
                cliente = cliente,
                montosOcultos = montosOcultos,
                onAbrirCliente = { onAbrirCliente(cliente.clienteId) }
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
