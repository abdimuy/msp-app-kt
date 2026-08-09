package com.example.msp_app.feature.collectionreport.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.actions.ReportActionsController
import com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar
import com.example.msp_app.feature.collectionreport.ui.components.DetailHeader
import com.example.msp_app.feature.collectionreport.ui.components.DetailList
import com.example.msp_app.feature.collectionreport.ui.components.DuoTiles
import com.example.msp_app.feature.collectionreport.ui.components.HeroSection
import com.example.msp_app.feature.collectionreport.ui.components.PeriodSelector
import com.example.msp_app.feature.collectionreport.ui.components.RangeSubRow
import com.example.msp_app.feature.collectionreport.ui.components.ReportHeader
import com.example.msp_app.feature.collectionreport.ui.components.ReportSheets
import com.example.msp_app.feature.collectionreport.ui.components.SecondaryChips
import com.example.msp_app.feature.collectionreport.ui.components.StaggeredEntrance
import com.example.msp_app.feature.collectionreport.ui.components.TabTransition
import com.example.msp_app.feature.collectionreport.ui.theme.ThemeRevealRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Padding inferior del scroll — deja espacio para que el contenido suba sobre la barra de
 * acciones difuminada del pie (mockup `.actions`, Task 8), que se monta encima como overlay
 * de este mismo `Box`. No es un token de [MspTheme.spacing] (`xs`/`sm`/`md`/`lg`): es un
 * valor propio de este scaffold, igual que `HERO_PADDING` en `MspHeroTodayCard`.
 */
private val SCROLL_BOTTOM_CONTENT_PADDING = 96.dp

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
private const val ENTRANCE_HERO = 4
private const val ENTRANCE_DUO = 5
private const val ENTRANCE_CHIPS = 6
private const val ENTRANCE_DETAIL_HEADER = 7
private const val ENTRANCE_DETAIL_LIST = 8

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
 */
@Suppress("UnusedParameter") // navController: firma reservada (el reporte no navega saliente).
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet (ReportSheets).
@Composable
fun CollectionReportScreen(
    navController: NavController,
    viewModel: CollectionReportViewModel = hiltViewModel(),
    onMenuClick: () -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
                onDayRowClick = onDiaCicloClick
            )
            BlurredActionBar(
                onCompartirClick = {
                    context.startActivity(
                        Intent.createChooser(ReportActionsController.buildShareIntent(state), null)
                    )
                },
                // PARKED FOR USER (ver KDoc de ReportActionsController): sin conexión Bluetooth
                // real todavía, comparte el mismo ticket dinero-seguro como puente temporal.
                onImprimirClick = {
                    context.startActivity(
                        Intent.createChooser(
                            ReportActionsController.buildTicketShareIntent(state),
                            null
                        )
                    )
                },
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
                        val intent =
                            ReportActionsController.buildPdfShareIntent(context, state, file)
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
            ReportSheets(state = state, onDismiss = viewModel::closeSheet)
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
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MspTheme.colors.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MspTheme.spacing.md)
                .padding(top = MspTheme.spacing.sm, bottom = SCROLL_BOTTOM_CONTENT_PADDING),
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

            TabTransition(period = state.period) { period ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.md)
                ) {
                    StaggeredEntrance(index = ENTRANCE_HERO) {
                        HeroSection(
                            hero = state.hero,
                            period = period,
                            masked = state.masked,
                            onClick = onHeroClick,
                            onSparkBarClick = onSparkBarClick
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_DUO) {
                        DuoTiles(
                            efectivo = state.efectivo,
                            transferencia = state.transferencia,
                            masked = state.masked,
                            onEfectivoClick = onEfectivoClick,
                            onTransferenciaClick = onTransferenciaClick
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_CHIPS) {
                        SecondaryChips(
                            condonado = state.condonado,
                            visitas = state.visitas,
                            masked = state.masked,
                            onCondonadoClick = onCondonadoClick,
                            onVisitasClick = onVisitasClick
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_DETAIL_HEADER) {
                        DetailHeader(
                            detail = state.detail,
                            sort = state.sort,
                            onSortSelect = onSortSelect
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_DETAIL_LIST) {
                        DetailList(
                            detail = state.detail,
                            masked = state.masked,
                            onPaymentClick = onPaymentRowClick,
                            onDayClick = onDayRowClick
                        )
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
