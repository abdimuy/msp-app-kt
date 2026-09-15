package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.msp_app.core.designsystem.component.MspThemeRevealHost
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.rememberReducedMotionEnabled
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.BarraDeDetalle
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
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
    onAtras: () -> Unit,
    onAbrirCliente: (Int) -> Unit,
    onAbrirVenta: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Las DOS señales de reduce-motion, combinadas acá y no en un helper: es el mismo criterio
    // que `rememberReportReducedMotion()` (accesibilidad del SO **o** "Deshabilitar
    // animaciones" de Configuración) pero ese vive en `:feature:collectionReport`, que este
    // módulo no importa. Una línea en el único call site, en vez de una abstracción nueva —
    // cuando aparezca el tercer caller, esto sí gana su lugar en `:core:designsystem`.
    val reduceMotion = rememberReducedMotionEnabled() || LocalReduceMotion.current
    MspThemeRevealHost(
        onToggleTheme = viewModel::alternarTema,
        reducedMotion = reduceMotion,
        tema = { animateColors, contenido ->
            // `darkTheme` queda en su default (`appDarkTheme()` → `LocalAppDarkTheme` →
            // `ThemeController.isDarkMode`): el tema lo manda la app, no esta pantalla.
            MspTheme(animateColors = animateColors, content = contenido)
        }
    ) {
        ListaDeClientesContent(
            state = state,
            onAtras = onAtras,
            onBuscar = viewModel::buscar,
            onElegirSegmento = viewModel::elegirSegmento,
            onAbrirCliente = onAbrirCliente,
            onAbrirVenta = onAbrirVenta,
            onReintentar = viewModel::cargar,
            onAlternarTema = viewModel::alternarTema,
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
    onAtras: () -> Unit,
    onBuscar: (String) -> Unit,
    onElegirSegmento: (SegmentoDeCobranza) -> Unit,
    onAbrirCliente: (Int) -> Unit,
    onAbrirVenta: (Int) -> Unit,
    onReintentar: () -> Unit,
    onAlternarTema: () -> Unit,
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
            BarraDeDetalle(
                onAtras = onAtras,
                accion = {
                    MspThemeToggle(
                        darkTheme = state.temaOscuro,
                        onToggle = onAlternarTema
                    )
                }
            )
            Text(
                text = "Clientes",
                style = MspTheme.type.screenTitle,
                color = MspTheme.colors.onSurface
            )
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
        when {
            state.cargando -> Cargando()
            state.fallo -> MensajeDeFallo(onReintentar)
            state.clientes.isEmpty() -> ListaVacia()
            else -> Clientes(
                clientes = state.clientes,
                onAbrirCliente = onAbrirCliente,
                onAbrirVenta = onAbrirVenta
            )
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
    onAbrirCliente: (Int) -> Unit,
    onAbrirVenta: (Int) -> Unit
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
