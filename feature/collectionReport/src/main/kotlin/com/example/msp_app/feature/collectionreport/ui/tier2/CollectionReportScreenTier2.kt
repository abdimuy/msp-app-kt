package com.example.msp_app.feature.collectionreport.ui.tier2

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.core.designsystem.component.MspCard
import com.example.msp_app.core.designsystem.component.MspMoneyText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.ChipUi
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.CollectionReportViewModel
import com.example.msp_app.feature.collectionreport.ui.DetailSort
import com.example.msp_app.feature.collectionreport.ui.ErrorBanner
import com.example.msp_app.feature.collectionreport.ui.SheetKind
import com.example.msp_app.feature.collectionreport.ui.TileUi
import com.example.msp_app.feature.collectionreport.ui.actions.ReportActionsController
import com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar
import com.example.msp_app.feature.collectionreport.ui.components.DetailHeader
import com.example.msp_app.feature.collectionreport.ui.components.DetailList
import com.example.msp_app.feature.collectionreport.ui.components.HeroSection
import com.example.msp_app.feature.collectionreport.ui.components.MetaCardTier2
import com.example.msp_app.feature.collectionreport.ui.components.PeriodSelector
import com.example.msp_app.feature.collectionreport.ui.components.RangeSubRow
import com.example.msp_app.feature.collectionreport.ui.components.ReportHeader
import com.example.msp_app.feature.collectionreport.ui.components.ReportSheets
import com.example.msp_app.feature.collectionreport.ui.components.StaggeredEntrance
import com.example.msp_app.feature.collectionreport.ui.components.TabTransition
import com.example.msp_app.feature.collectionreport.ui.theme.ThemeRevealRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Colchón inferior del scroll — cubre la altura de [BlurredActionBar] montada en
 * [ReportTier.TIER_2] (fix bug "Grande/Muy grande rompe el reporte"): a diferencia de Tier 1
 * (fila, `CollectionReportScreen.SCROLL_BOTTOM_CONTENT_PADDING` = 132dp), aquí la barra SIEMPRE
 * se apila en columna (ver KDoc de [BlurredActionBar]) — top 32 + 3 botones de 56dp mín. + 2
 * huecos de [MspTheme.spacing.sm] (8dp) + bottom 16 ≈ 232dp, bastante más alta que la fila de
 * Tier 1. Antes de este fix este valor (96dp, "mismo colchón que Tier 1" según su comentario
 * viejo, que en realidad NUNCA fue el mismo número) ya se quedaba corto incluso contra la fila
 * — quedó sin detectar porque `rememberReportTier()` nunca montaba este composable de verdad
 * (ver KDoc de [rememberReportTier]). Se agrega la misma holgura (~28dp) que Tier 1 sobre su
 * propia estimación de altura de barra. El inset real de la barra de navegación del sistema lo
 * agrega por separado `.navigationBarsPadding()` en el `Column` de abajo (mismo fix que Tier 1).
 */
private val SCROLL_BOTTOM_CONTENT_PADDING = 260.dp

/** Target táctil mínimo curado de Tier 2 (spec §5: "targets mayores") — encima de las 40dp
 * de icon-surface / 48dp de `minimumInteractiveComponentSize` que ya trae el DS. */
private val TIER2_MIN_TOUCH_TARGET = 56.dp

private val TIER2_DOT_SIZE = 11.dp

private const val ENTRANCE_HEADER = 0
private const val ENTRANCE_ERROR_BANNER = 1
private const val ENTRANCE_PERIOD = 2
private const val ENTRANCE_SUBROW = 3
private const val ENTRANCE_HERO = 4
private const val ENTRANCE_META = 5
private const val ENTRANCE_EFECTIVO = 6
private const val ENTRANCE_TRANSFERENCIA = 7
private const val ENTRANCE_CONDONADO = 8
private const val ENTRANCE_VISITAS = 9
private const val ENTRANCE_DETAIL_HEADER = 10
private const val ENTRANCE_DETAIL_LIST = 11

/**
 * Punto de entrada Tier 2 (Muy grande, spec §5) del reporte de cobranza — hermano de
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportScreen] (Tier 1) sobre el
 * MISMO [CollectionReportViewModel]/estado; la diferencia es puramente de `ui/`: columna única
 * curada, "una idea por vista" (nunca dos tarjetas compitiendo lado a lado), targets táctiles
 * ≥[TIER2_MIN_TOUCH_TARGET]. Task 10 decide CUÁNDO montar este composable en vez del Tier 1
 * (vía [rememberReportTier]) — este archivo solo lo deja listo para esa selección.
 *
 * **Envuelto en [ThemeRevealRoot] (fix Task 11)** — mismo motivo que
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportScreen] (Tier 1): sin esto
 * `MspTheme.colors` revienta en cuanto `:app` monta este composable de verdad (nunca detectado
 * antes porque los tests del módulo siempre aportan su propio `MspTheme{}`).
 *
 * **[onThemeChanged] (fix defecto visual, íconos de barra de estado)** — mismo motivo que
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportScreen] (Tier 1, ver su
 * KDoc): este callback (default no-op) deja que `:app` mantenga los íconos de la barra de
 * sistema correctos apenas este composable monta.
 */
@Suppress(
    "UnusedParameter"
) // navController: firma reservada (igual que Tier 1); el drawer entra por [onMenuClick].
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet (ReportSheets).
@Composable
fun CollectionReportScreenTier2(
    navController: NavController,
    viewModel: CollectionReportViewModel = hiltViewModel(),
    onMenuClick: () -> Unit = {},
    onThemeChanged: (Boolean) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.darkTheme) { onThemeChanged(state.darkTheme) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val onDiaCicloClick: (Int) -> Unit = { index ->
        viewModel.openSheet(SheetKind.DIA_CICLO, index.toString())
    }
    ThemeRevealRoot(darkTheme = state.darkTheme, onToggleTheme = viewModel::toggleTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            CollectionReportContentTier2(
                state = state,
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
                modifier = Modifier.align(Alignment.BottomCenter),
                tier = ReportTier.TIER_2
            )
            ReportSheets(state = state, onDismiss = viewModel::closeSheet)
        }
    }
}

/**
 * Render puro Tier 2 (sin `hiltViewModel()`/`NavController`) — mismo contrato de callbacks que
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportContent] (Tier 1), reutiliza
 * TAL CUAL [ReportHeader]/[PeriodSelector]/[RangeSubRow]/[HeroSection]/[DetailHeader]/
 * [DetailList] (ya son de columna única, full-width — no necesitan una variante Tier 2 propia)
 * y solo reemplaza el duo Efectivo/Transferencia + los chips Condonado/Visitas: en Tier 1 son
 * grids de 2 (mockup `.duo`/`.chips`, dos ideas compitiendo lado a lado); en Tier 2 cada uno es
 * su PROPIA fila de ancho completo ([Tier2Tile]/[Tier2Chip]) — "una idea por vista" (spec §5).
 *
 * **Regla anti-colapso (spec §6):** igual que Tier 1, ningún hijo de la `Column` con scroll
 * lleva `weight` propio.
 */
@Composable
internal fun CollectionReportContentTier2(
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
            // Inset del status bar (mismo fix que Tier 1, ver KDoc de
            // [com.example.msp_app.feature.collectionreport.ui.CollectionReportScreen]): fondo
            // edge-to-edge, contenido corrido debajo del status bar.
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MspTheme.spacing.lg)
                .padding(top = MspTheme.spacing.md, bottom = SCROLL_BOTTOM_CONTENT_PADDING)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.lg)
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

            // Entrada escalonada + crecimiento de sparkline SOLO en la primera pintada; no se
            // replayan en cada toggle Día↔Semana (mismo criterio que Tier 1, ver
            // toggle-jank-diagnosis.md, fix 4). `contentPeriod` llavea el slide para que arranque
            // sobre datos ya asentados (fix 1).
            var hasEntered by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) { hasEntered = true }
            val animateEntrance = !hasEntered

            TabTransition(period = state.contentPeriod) { period ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(MspTheme.spacing.lg)
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
                        StaggeredEntrance(index = ENTRANCE_META, animate = animateEntrance) {
                            MetaCardTier2(
                                porcentajeCobro = state.hero.porcentajeCobro,
                                porcentajeCuentas = state.hero.porcentajeCuentas,
                                clientesPagaron = state.hero.clientesPagaron,
                                clientesTotal = state.hero.clientesTotal
                            )
                        }
                    }
                    StaggeredEntrance(index = ENTRANCE_EFECTIVO, animate = animateEntrance) {
                        Tier2Tile(
                            dotColor = MspTheme.colors.statusPaid,
                            tile = state.efectivo,
                            masked = state.masked,
                            onClick = onEfectivoClick
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_TRANSFERENCIA, animate = animateEntrance) {
                        Tier2Tile(
                            dotColor = MspTheme.colors.brand,
                            tile = state.transferencia,
                            masked = state.masked,
                            onClick = onTransferenciaClick
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_CONDONADO, animate = animateEntrance) {
                        Tier2Chip(
                            dotColor = MspTheme.colors.statusPartial,
                            chip = state.condonado,
                            masked = state.masked,
                            valueColor = MspTheme.colors.statusPartial,
                            onClick = onCondonadoClick
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_VISITAS, animate = animateEntrance) {
                        Tier2Chip(
                            dotColor = MspTheme.colors.statusPending,
                            chip = state.visitas,
                            masked = state.masked,
                            valueColor = MspTheme.colors.onSurface,
                            onClick = onVisitasClick
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_DETAIL_HEADER, animate = animateEntrance) {
                        DetailHeader(
                            detail = state.detail,
                            sort = state.sort,
                            onSortSelect = onSortSelect
                        )
                    }
                    StaggeredEntrance(index = ENTRANCE_DETAIL_LIST, animate = animateEntrance) {
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
 * Fila Tier 2 de ancho completo para Efectivo/Transferencia (mockup `.duo` en Tier 1 grid de
 * 2; aquí una tarjeta por fila — "una idea por vista"). Tipografía escalada respecto al
 * [MspBentoTile] de Tier 1 (`amountCard`, 21sp): usa `type.amountLarge` (34sp) para el monto,
 * el mismo peso visual que el hero le da a su propio protagonista.
 */
@Composable
private fun Tier2Tile(
    dotColor: Color,
    tile: TileUi,
    masked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MspCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TIER2_MIN_TOUCH_TARGET),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(MspTheme.spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(TIER2_DOT_SIZE)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = tile.label,
                    style = MspTheme.type.cardTitle,
                    color = MspTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(start = MspTheme.spacing.xs)
                )
            }
            MspMoneyText(
                amount = tile.amount.amount,
                masked = masked,
                style = MspTheme.type.amountLarge,
                color = MspTheme.colors.onSurface,
                modifier = Modifier.padding(top = MspTheme.spacing.xs)
            )
            Text(
                text = "${tile.count} pagos",
                style = MspTheme.type.body,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = MspTheme.spacing.xs)
            )
        }
    }
}

/**
 * Fila Tier 2 de ancho completo para Condonado/Visitas — mismo criterio que [Tier2Tile]:
 * grid de 2 en Tier 1 ([com.example.msp_app.feature.collectionreport.ui.components.SecondaryChips])
 * se vuelve una tarjeta por fila aquí. [ChipUi.amount] enmascarable (Condonado);
 * [ChipUi.count] (Visitas) nunca se enmascara — mismo contrato que Tier 1.
 */
@Composable
private fun Tier2Chip(
    dotColor: Color,
    chip: ChipUi,
    masked: Boolean,
    valueColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    MspCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TIER2_MIN_TOUCH_TARGET),
        shape = MspTheme.shapes.field,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MspTheme.spacing.lg, vertical = MspTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MspTheme.spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(TIER2_DOT_SIZE)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = chip.label,
                style = MspTheme.type.cardTitle,
                color = MspTheme.colors.onSurfaceMuted,
                modifier = Modifier.weight(1f)
            )
            if (chip.amount != null) {
                MspMoneyText(
                    amount = chip.amount.amount,
                    masked = masked,
                    style = MspTheme.type.amountRow,
                    color = valueColor
                )
            } else {
                Text(
                    text = "${chip.count ?: 0}",
                    style = MspTheme.type.amountRow,
                    color = valueColor
                )
            }
        }
    }
}
