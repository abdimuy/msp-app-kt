package com.example.msp_app.feature.collectionreport.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.components.DetailHeader
import com.example.msp_app.feature.collectionreport.ui.components.DetailList
import com.example.msp_app.feature.collectionreport.ui.components.DuoTiles
import com.example.msp_app.feature.collectionreport.ui.components.HeroSection
import com.example.msp_app.feature.collectionreport.ui.components.PeriodSelector
import com.example.msp_app.feature.collectionreport.ui.components.RangeSubRow
import com.example.msp_app.feature.collectionreport.ui.components.ReportHeader
import com.example.msp_app.feature.collectionreport.ui.components.SecondaryChips
import com.example.msp_app.feature.collectionreport.ui.components.StaggeredEntrance
import com.example.msp_app.feature.collectionreport.ui.components.TabTransition

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
 * [CollectionReportContent]. [navController] queda reservado — el cableado real de
 * navegación (drawer, back stack) llega en Task 10; Task 6 solo construye la UI de la
 * mitad superior de la pantalla.
 */
@Suppress("UnusedParameter") // navController: firma reservada para el cableado de Task 10.
@Composable
fun CollectionReportScreen(
    navController: NavController,
    viewModel: CollectionReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Mismo sheet (DIA_CICLO) para la barra de la sparkline (Task 6) y la fila de día del
    // resumen Semana (Task 7) — el mockup los une bajo `openSheet('day', i)`, mismo índice.
    val onDiaCicloClick: (Int) -> Unit = { index ->
        viewModel.openSheet(SheetKind.DIA_CICLO, index.toString())
    }
    CollectionReportContent(
        state = state,
        // El drawer real (y el resto de navegación por `navController`) se cablea en Task 10.
        onMenuClick = {},
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
}

/**
 * Render puro (sin `hiltViewModel()`/`NavController`) del tablero completo: header +
 * selector de periodo + subrow + hero con sparkline (Task 6) + duo Efectivo/Transferencia +
 * chips Condonado/Visitas + detalle (lista Día / resumen Semana, Task 7). `Box`/`Column` sin
 * `TopAppBar` M3 — el header es propio ([ReportHeader]). La barra de acciones del pie
 * (Task 8) y el sheet modal (Task 9) llegan como overlays de este mismo `Box`.
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
 */
@Composable
private fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
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
