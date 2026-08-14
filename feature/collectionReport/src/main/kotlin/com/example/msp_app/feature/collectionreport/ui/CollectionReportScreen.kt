package com.example.msp_app.feature.collectionreport.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.actions.ReportActionsController
import com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar
import com.example.msp_app.feature.collectionreport.ui.components.DayStrip
import com.example.msp_app.feature.collectionreport.ui.components.DaySwap
import com.example.msp_app.feature.collectionreport.ui.components.DetailHeader
import com.example.msp_app.feature.collectionreport.ui.components.DetailList
import com.example.msp_app.feature.collectionreport.ui.components.DuoTiles
import com.example.msp_app.feature.collectionreport.ui.components.HeroSection
import com.example.msp_app.feature.collectionreport.ui.components.MetaCard
import com.example.msp_app.feature.collectionreport.ui.components.PeriodSelector
import com.example.msp_app.feature.collectionreport.ui.components.PrintSheet
import com.example.msp_app.feature.collectionreport.ui.components.RangeSubRow
import com.example.msp_app.feature.collectionreport.ui.components.ReportHeader
import com.example.msp_app.feature.collectionreport.ui.components.ReportSheets
import com.example.msp_app.feature.collectionreport.ui.components.SecondaryChips
import com.example.msp_app.feature.collectionreport.ui.components.StaggeredEntrance
import com.example.msp_app.feature.collectionreport.ui.components.TabTransition
import com.example.msp_app.feature.collectionreport.ui.theme.ThemeRevealRoot
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Padding inferior del scroll — deja espacio para que el contenido suba sobre la barra de
 * acciones difuminada del pie (mockup `.actions`, Task 8), que se monta encima como overlay
 * de este mismo `Box`. No es un token de [MspTheme.spacing] (`xs`/`sm`/`md`/`lg`): es un
 * valor propio de este scaffold, igual que `HERO_PADDING` en `MspHeroTodayCard`.
 *
 * Cubre la altura FIJA de [com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar]
 * (top 32 + botón 56 + bottom 16 ≈ 104dp) con holgura, para que la ÚLTIMA fila de pago libre
 * la barra de acciones (fix de dispositivo: con muchos pagos, el valor previo de 96dp — menor
 * que la barra — dejaba las últimas filas tapadas, dando la sensación de que "la lista no
 * baja"). El inset real de la barra de navegación del sistema (variable por dispositivo) lo
 * agrega por separado `.navigationBarsPadding()` en el `Column` de abajo — mismo componente,
 * mismo inset, sin duplicar el número a mano.
 */
private val SCROLL_BOTTOM_CONTENT_PADDING = 132.dp

/**
 * Índices de [StaggeredEntrance] de todo el tablero (header, banner de error opcional,
 * selector de periodo, subrow, hero, duo de tiles, chips secundarios, encabezado + lista de
 * detalle) — el escalonado sigue el orden visual de arriba a abajo, mismo criterio que el
 * mockup `.an:nth-child(n)`.
 */
private const val ENTRANCE_HEADER = 0
private const val ENTRANCE_ERROR_BANNER = 1
private const val ENTRANCE_PERIOD = 2
private const val ENTRANCE_SUBROW = 3
private const val ENTRANCE_DAY_STRIP = 4
private const val ENTRANCE_HERO = 5
private const val ENTRANCE_META = 6
private const val ENTRANCE_DUO = 7
private const val ENTRANCE_CHIPS = 8
private const val ENTRANCE_DETAIL_HEADER = 9
private const val ENTRANCE_DETAIL_LIST = 10

/**
 * Punto de entrada del reporte de cobranza (Plan 5, piloto `:feature:collectionReport`).
 * Recolecta el `StateFlow` de [CollectionReportViewModel] y delega el render puro a
 * [CollectionReportContent]. [navController] queda reservado (el reporte no navega a otras
 * pantallas por sí mismo). [onMenuClick] lo cablea el composition root de `:app` (Task 10)
 * al `openDrawer` del `DrawerContainer` — así el botón de menú del [ReportHeader] abre el
 * drawer real de la app.
 *
 * **Envuelto en [ThemeRevealRoot] (fix Task 11):** `:app` NUNCA provee [MspTheme] — su
 * `MainActivity`/`AppNavigation` solo montan `MspappTheme` (el tema legado de Material del
 * resto de la app, un sistema de composición DISTINTO). [ThemeRevealRoot] es, por diseño
 * (ver su KDoc, "el caller NO debe volver a envolver en MspTheme por su cuenta"), el ÚNICO
 * punto que provee [MspTheme] a este piloto — pero Task 10 nunca lo cableó aquí: quedó
 * definido y testeado (`ThemeRevealRootTest`) pero sin un solo consumidor real, así que
 * `MspTheme.colors` reventaba con `IllegalStateException("MspTheme ausente")` en cuanto
 * `:app` navegaba de verdad a `"daily_reports"` — invisible en los tests del módulo porque
 * CADA test envuelve su propio contenido en un `MspTheme{}` de scaffolding (nunca ejercitan
 * este composable de entrada). Detectado por el smoke e2e de dispositivo (Task 11,
 * `CollectionReportDeviceSmokeTest`), el único test que monta la `MainActivity` real.
 *
 * **[onThemeChanged] (fix defecto visual, íconos de barra de estado):** `state.darkTheme` ahora
 * ESPEJA el tema GLOBAL de la app (`ReportThemePort`, ver KDoc de
 * `CollectionReportViewModel.toggleTheme` — antes era un espejo local desacoplado que se
 * reiniciaba a claro al volver a entrar a la pantalla, ya corregido). Este callback sigue
 * existiendo igual: sin él, la barra de sistema (`MainActivity`, `enableEdgeToEdge`) se queda
 * con la apariencia de íconos de lo que sea que mostraba ANTES de que este composable montara
 * (hay una ventana entre "la Activity arranca" y "este `LaunchedEffect` corre por primera vez").
 * Default no-op: nada cambia para callers/tests que no lo cablean; `:app` (composition root,
 * `AppNavigation`) lo conecta a `ThemeController.reportStatusBarAppearanceDark` — redundante en
 * valor con `ThemeController.isDarkMode` ahora que ambos coinciden, pero se conserva por
 * simetría con el resto de pantallas y porque sigue siendo la vía más directa desde este
 * composable hacia la barra de sistema.
 */
@Suppress("UnusedParameter") // navController: firma reservada (el reporte no navega saliente).
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet (ReportSheets).
@Composable
fun CollectionReportScreen(
    navController: NavController,
    viewModel: CollectionReportViewModel = hiltViewModel(),
    onMenuClick: () -> Unit = {},
    onThemeChanged: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.darkTheme) { onThemeChanged(state.darkTheme) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Permiso de Bluetooth para imprimir (P2). En API 31+ imprimir necesita CONNECT + SCAN
    // (SCAN lo exige el `cancelDiscovery()` interno de DantSu al conectar) — mismo conjunto que
    // `BluetoothPrinterDiscovery.requiredRuntimePermissions()` de `:core:printing`; por debajo
    // de 31 es manifest-only (arreglo vacío -> se salta el request). El launcher vive aquí (el
    // request de permisos es territorio de Activity/UI); el ViewModel se mantiene sin Android y
    // testeable con fakes — solo recibe `onPrintPermissionDenied()` cuando el usuario lo niega.
    val btPrintPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            emptyArray()
        }
    }
    var pendingPrintAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val printPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val action = pendingPrintAction
        pendingPrintAction = null
        if (grants.values.all { it }) action?.invoke() else viewModel.onPrintPermissionDenied()
    }
    val withBtPermission: (() -> Unit) -> Unit = { action ->
        val granted = btPrintPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (granted) {
            action()
        } else {
            pendingPrintAction = action
            printPermissionLauncher.launch(btPrintPermissions)
        }
    }
    // Mismo sheet (DIA_CICLO) para la barra de la sparkline (Task 6) y la fila de día del
    // resumen Semana (Task 7) — el mockup los une bajo `openSheet('day', i)`, mismo índice.
    val onDiaCicloClick: (Int) -> Unit = { index ->
        viewModel.openSheet(SheetKind.DIA_CICLO, index.toString())
    }
    ThemeRevealRoot(darkTheme = state.darkTheme, onToggleTheme = viewModel::toggleTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            CollectionReportContent(
                state = state,
                // El drawer real lo provee `:app` vía [onMenuClick] (Task 10); por defecto no-op.
                onMenuClick = onMenuClick,
                onPrivacyToggle = viewModel::toggleMask,
                onThemeToggle = viewModel::toggleTheme,
                onPeriodSelect = viewModel::setPeriod,
                onHeroClick = { viewModel.openSheet(SheetKind.HERO) },
                onSparkBarClick = onDiaCicloClick,
                onEfectivoClick = { viewModel.openSheet(SheetKind.EFECTIVO) },
                onTransferenciaClick = { viewModel.openSheet(SheetKind.TRANSFERENCIA) },
                onCondonadoClick = { viewModel.openSheet(SheetKind.CONDONADO) },
                onVisitasClick = { viewModel.openSheet(SheetKind.VISITAS) },
                onSortSelect = viewModel::setSort,
                onPaymentRowClick = { id -> viewModel.openSheet(SheetKind.PAGO, id) },
                onDayRowClick = onDiaCicloClick,
                onDaySelect = viewModel::selectDay
            )
            BlurredActionBar(
                // Compartir SIEMPRE el mismo PDF que abre el botón "PDF" (misma
                // `generatePdf`/`pdfFileName`, una sola fuente de verdad del archivo) — antes
                // mandaba un resumen de texto plano (`buildShareText`), ahora manda el ticket
                // completo como PDF, vía `ACTION_SEND` dentro de un chooser (no lo abre).
                onCompartirClick = {
                    coroutineScope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ReportActionsController.generatePdf(
                                context = context,
                                state = state,
                                fileName = ReportActionsController.pdfFileName(state),
                                clock = AppClock.System
                            )
                        }
                        val intent =
                            ReportActionsController.buildPdfShareIntent(context, state, file)
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                // P2: imprime de verdad a la impresora recordada por defecto (auto), pidiendo
                // primero el permiso de Bluetooth si hace falta. El flujo/feedback vive en el
                // `PrintSheet` de abajo (incl. "Cambiar impresora" siempre disponible).
                onImprimirClick = { withBtPermission { viewModel.printReport() } },
                // PDF ABRE el archivo en un visor (`ACTION_VIEW`), a diferencia de Compartir
                // (`ACTION_SEND`) — mismo archivo (misma `generatePdf`/`pdfFileName`), acción
                // distinta. `startActivitySafely` evita el crash si el dispositivo no trae un
                // visor de PDF instalado.
                onPdfClick = {
                    coroutineScope.launch {
                        val file = withContext(Dispatchers.IO) {
                            ReportActionsController.generatePdf(
                                context = context,
                                state = state,
                                fileName = ReportActionsController.pdfFileName(state),
                                clock = AppClock.System
                            )
                        }
                        val intent = ReportActionsController.buildPdfViewIntent(context, file)
                        ReportActionsController.startActivitySafely(context, intent)
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            ReportSheets(state = state, onDismiss = viewModel::closeSheet)
            PrintSheet(
                state = state,
                onDismiss = viewModel::dismissPrintSheet,
                onPrint = viewModel::retryPrint,
                onSelectPrinter = viewModel::selectPrinter,
                onChangePrinter = { withBtPermission { viewModel.openPrinterPicker() } }
            )
        }
    }
}

/**
 * Render puro (sin `hiltViewModel()`/`NavController`) del tablero completo: header +
 * selector de periodo + subrow + hero con sparkline (Task 6) + duo Efectivo/Transferencia +
 * chips Condonado/Visitas + detalle (lista Día / resumen Semana, Task 7). `Box`/`Column` sin
 * `TopAppBar` M3 — el header es propio ([ReportHeader]). La barra de acciones del pie
 * ([com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar]) y el sheet
 * modal ([com.example.msp_app.feature.collectionreport.ui.components.ReportSheets]) son
 * Task 8 (no Task 9, que es el reveal de tema) — se montan como overlays de
 * [CollectionReportScreen], NO dentro de este composable puro: así los goldens de Task 6/7
 * de [CollectionReportContent] no cambian (ver `ReportSheetsScreenshotTest`/
 * `BlurredActionBarScreenshotTest` para los goldens propios de Task 8).
 *
 * Hero + duo + chips + detalle viven DENTRO del mismo [TabTransition] (mockup `.pc`: todo
 * el bloque desliza junto al cambiar de periodo, no solo el hero) — usan `state.*`
 * directamente (no el `period` que recibe el lambda de [TabTransition]) porque ya vienen
 * resueltos para el periodo actual desde `CollectionReportViewModel`; el `period` del lambda
 * solo lo necesita [HeroSection] para su copy dependiente de periodo.
 *
 * **Regla anti-colapso (spec §6):** ningún hijo de la `Column` con scroll recibe `weight` —
 * cada tarjeta se dimensiona por contenido (`wrapContentHeight` implícito); el
 * `verticalScroll` maneja el overflow, nunca comprime.
 *
 * `internal`: punto de entrada testeable desde este módulo sin pasar por Hilt/NavController
 * — [CollectionReportScreen] es el único wrapper público.
 */
@Composable
internal fun CollectionReportContent(
    state: CollectionReportUiState,
    onMenuClick: () -> Unit,
    onPrivacyToggle: () -> Unit,
    onThemeToggle: () -> Unit,
    onPeriodSelect: (ReportPeriod) -> Unit,
    onHeroClick: () -> Unit,
    onSparkBarClick: (Int) -> Unit,
    onEfectivoClick: () -> Unit,
    onTransferenciaClick: () -> Unit,
    onCondonadoClick: () -> Unit,
    onVisitasClick: () -> Unit,
    onSortSelect: (DetailSort) -> Unit,
    onPaymentRowClick: (String) -> Unit,
    onDayRowClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    // Día elegido en la tira del ciclo. Default no-op para no romper a los tests/goldens que
    // montan el tablero sin ciclo — con `state.cycleDays` vacía la tira ni siquiera se pinta,
    // así que un default aquí no deja ningún control muerto en pantalla (a diferencia del
    // `onToggleExpand` de `DetailList`, que sí es obligatorio porque su control SIEMPRE se ve
    // cuando hay overflow).
    onDaySelect: (LocalDate) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
            // Inset del status bar (fix defecto visual, ver KDoc de [CollectionReportScreen]):
            // el fondo sigue edge-to-edge (pintado ANTES de este padding), solo el contenido
            // (header incl.) se corre debajo del status bar del sistema. Mismo patrón que
            // `Scaffold(modifier = Modifier.statusBarsPadding())` de `HomeScreen`/`SalesScreen`
            // — este piloto no usa `Scaffold` (KDoc de [CollectionReportContent]), así que el
            // inset se aplica directo al contenedor raíz del contenido.
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MspTheme.spacing.md)
                .padding(top = MspTheme.spacing.sm, bottom = SCROLL_BOTTOM_CONTENT_PADDING)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
        ) {
            StaggeredEntrance(index = ENTRANCE_HEADER) {
                ReportHeader(
                    cobrador = state.cobrador,
                    masked = state.masked,
                    darkTheme = state.darkTheme,
                    onMenuClick = onMenuClick,
                    onPrivacyToggle = onPrivacyToggle,
                    onThemeToggle = onThemeToggle
                )
            }

            state.error?.let { message ->
                StaggeredEntrance(index = ENTRANCE_ERROR_BANNER) {
                    ErrorBanner(message = message)
                }
            }

            StaggeredEntrance(index = ENTRANCE_PERIOD) {
                PeriodSelector(period = state.period, onSelect = onPeriodSelect)
            }

            StaggeredEntrance(index = ENTRANCE_SUBROW) {
                RangeSubRow(rangeLabel = state.rangeLabel, pendingCount = state.pendingCount)
            }

            // La entrada escalonada + el crecimiento de la sparkline corren SOLO la primera vez
            // que se pinta el tablero; en los cambios Día↔Semana cada slot del `AnimatedContent`
            // es un subárbol nuevo que las re-dispararía, apilándolas sobre el slide de 300ms y
            // haciéndolo saltar (toggle-jank-diagnosis.md, fix 4). El flag sobrevive a los swaps
            // por estar hoisteado ARRIBA del `TabTransition`.
            var hasEntered by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { hasEntered = true }
            val animateEntrance = !hasEntered

            // Colapsable de la lista de pagos (Día) — estado izado ARRIBA del `TabTransition`
            // por el mismo motivo que `hasEntered`: cada slot del `AnimatedContent` es un
            // subárbol nuevo, así que un `rememberSaveable` de adentro se reiniciaría en cada
            // swap Día↔Semana y la lista se volvería a colapsar sola. Arranca colapsado; el
            // umbral y el porqué viven en `DetailList`.
            var paymentsExpanded by rememberSaveable { mutableStateOf(false) }

            // `TabTransition` se llavea con `contentPeriod` (no `period`): el slide arranca solo
            // cuando los datos del nuevo periodo ya están asentados (toggle-jank-diagnosis.md,
            // fix 1), nunca sobre datos viejos que recompondrían a mitad de animación.
            TabTransition(period = state.contentPeriod) { period ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
                ) {
                    // Tira de días del ciclo — solo en Día y solo cuando hay más de un día que
                    // elegir (ver `CollectionReportDayStripBuilder`). Va FUERA del `DaySwap` de
                    // abajo a propósito: el control que se toca no debe parpadear con el
                    // contenido que él mismo acaba de cambiar.
                    if (period == ReportPeriod.DIA && state.cycleDays.isNotEmpty()) {
                        StaggeredEntrance(index = ENTRANCE_DAY_STRIP, animate = animateEntrance) {
                            DayStrip(
                                days = state.cycleDays,
                                onSelect = onDaySelect,
                                emptyDay = state.selectedDayEmpty,
                                note = state.selectedDayNote
                            )
                        }
                    }
                    // Al cambiar de día, SOLO este bloque transiciona: la tira de arriba (y todo
                    // lo que está fuera del `TabTransition`) se queda quieto. `null` en Semana ->
                    // no monta nada, el árbol queda idéntico al de siempre.
                    DaySwap(day = state.selectedDay.takeIf { period == ReportPeriod.DIA }) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
                        ) {
                            StaggeredEntrance(index = ENTRANCE_HERO, animate = animateEntrance) {
                                HeroSection(
                                    hero = state.hero,
                                    period = period,
                                    masked = state.masked,
                                    onClick = onHeroClick,
                                    onSparkBarClick = onSparkBarClick,
                                    animateSparkline = animateEntrance
                                )
                            }
                            // "Meta de la semana": solo en SEMANA — ver KDoc de HeroUi/MetaCard.
                            if (period == ReportPeriod.SEMANA) {
                                StaggeredEntrance(
                                    index = ENTRANCE_META,
                                    animate = animateEntrance
                                ) {
                                    MetaCard(
                                        porcentajeCobro = state.hero.porcentajeCobro,
                                        porcentajeCuentas = state.hero.porcentajeCuentas,
                                        clientesPagaron = state.hero.clientesPagaron,
                                        clientesTotal = state.hero.clientesTotal
                                    )
                                }
                            }
                            StaggeredEntrance(index = ENTRANCE_DUO, animate = animateEntrance) {
                                DuoTiles(
                                    efectivo = state.efectivo,
                                    transferencia = state.transferencia,
                                    masked = state.masked,
                                    onEfectivoClick = onEfectivoClick,
                                    onTransferenciaClick = onTransferenciaClick
                                )
                            }
                            StaggeredEntrance(index = ENTRANCE_CHIPS, animate = animateEntrance) {
                                SecondaryChips(
                                    condonado = state.condonado,
                                    visitas = state.visitas,
                                    masked = state.masked,
                                    onCondonadoClick = onCondonadoClick,
                                    onVisitasClick = onVisitasClick
                                )
                            }
                            StaggeredEntrance(
                                index = ENTRANCE_DETAIL_HEADER,
                                animate = animateEntrance
                            ) {
                                DetailHeader(
                                    detail = state.detail,
                                    sort = state.sort,
                                    onSortSelect = onSortSelect
                                )
                            }
                            StaggeredEntrance(
                                index = ENTRANCE_DETAIL_LIST,
                                animate = animateEntrance
                            ) {
                                DetailList(
                                    detail = state.detail,
                                    masked = state.masked,
                                    onPaymentClick = onPaymentRowClick,
                                    onDayClick = onDayRowClick,
                                    expanded = paymentsExpanded,
                                    onToggleExpand = { paymentsExpanded = !paymentsExpanded }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Banner de error mínimo (es-MX, minimalista) — [CollectionReportUiState.error] no-nulo se
 * pinta sobre un tablero en blanco para el periodo pedido (ver KDoc de
 * `CollectionReportViewModel.applyError`); esta capa solo lo hace visible, no inventa un
 * componente nuevo del design system para un solo texto de una línea.
 *
 * `internal` (no `private`): [com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportScreenTier2]
 * (Task 9) reutiliza el mismo banner — mismo criterio anti-duplicación que el resto de los
 * componentes de `ui/components` compartidos entre Tier 1 y Tier 2.
 */
@Composable
internal fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        style = MspTheme.type.body,
        color = MspTheme.colors.danger,
        modifier = modifier
            .fillMaxWidth()
            .clip(MspTheme.shapes.field)
            .background(MspTheme.colors.dangerTint)
            .padding(MspTheme.spacing.sm)
    )
}
