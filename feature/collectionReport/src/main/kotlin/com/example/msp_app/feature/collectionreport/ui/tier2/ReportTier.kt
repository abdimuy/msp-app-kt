package com.example.msp_app.feature.collectionreport.ui.tier2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import kotlin.math.max

/**
 * Los dos layouts del tablero (spec §5): [TIER_1] es el denso/responsivo que
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportContent] ya rinde (Normal);
 * [TIER_2] es el curado de [com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportScreenTier2]
 * (Grande/Muy grande) — una idea por vista, targets mayores, columna única.
 */
enum class ReportTier { TIER_1, TIER_2 }

/**
 * A partir de qué escala se considera "Grande" (spec §5) y por lo tanto [ReportTier.TIER_2] —
 * coincide EXACTAMENTE con
 * [com.example.msp_app.core.designsystem.theme.FontSizeLevel.GRANDE.nominalScale] (`1.5f`, spec
 * `docs/superpowers/specs/2026-08-10-configuracion-tamano-letra-design.md`): tanto GRANDE
 * (`1.5f`) como MUY_GRANDE (`2.0f`) caen en Tier 2, solo NORMAL (`1.0f`) queda en Tier 1 — ver
 * el fix documentado en el KDoc de [rememberReportTier].
 */
private const val FONT_SCALE_TIER2_THRESHOLD = 1.5f

/**
 * Decide [ReportTier] a partir de una escala cruda ya resuelta — `internal` y NO `@Composable`
 * a propósito: función pura testeable sin Compose/Robolectric (unit test JVM puro).
 */
internal fun resolveTier(fontScale: Float): ReportTier =
    if (fontScale >= FONT_SCALE_TIER2_THRESHOLD) ReportTier.TIER_2 else ReportTier.TIER_1

/**
 * Resuelve el [ReportTier] vigente a partir de la preferencia PROPIA de la app
 * ([LocalFontSizeLevel], `:core:designsystem`) — fix del bug "Grande/Muy grande rompe el
 * reporte": esta función ANTES leía `LocalDensity.current.fontScale` (el `fontScale` del SO,
 * proxy documentado en `task-9-brief.md` mientras `LocalFontSizeLevel` no existía). Eso se
 * rompió en cuanto `ReportMspTheme` (`ui/theme/ThemeRevealRoot.kt`) empezó a neutralizar
 * `LocalDensity` a `fontScale = 1f` DENTRO del subárbol del reporte, para evitar double-scaling
 * contra la rampa tipográfica comprimida (ver su KDoc) — cualquier lectura de
 * `LocalDensity.fontScale` hecha desde ese subárbol siempre ve `1.0`, así que Tier 2 nunca
 * montaba aunque el usuario tuviera elegido "Grande"/"Muy grande" en Configuración (Tier 1
 * quedaba forzado, y sus componentes se rompían visualmente bajo el texto comprimido más
 * grande — la barra de acciones envolviendo palabras a la mitad, la última tarjeta tapada).
 * [LocalFontSizeLevel] NO pasa por esa neutralización (solo `LocalDensity` se reinstala), así
 * que leerlo en vez del `fontScale` ambiental es correcto sin importar en qué punto del árbol
 * se invoque esta función.
 *
 * **`máx(nivel, fontScale del SO)`:** la raíz de composición (`MainActivity`) ya aplica la
 * Opción C del spec (`efectivo = máx(nivel elegido, fontScale del SO)`) sobre `LocalDensity`
 * antes de montar `AppNavigation` — en el único call site de hoy (`AppNavigation`, fuera de
 * `ReportMspTheme`) `LocalDensity.current.fontScale` ya trae ese máximo, así que tomarlo de
 * nuevo aquí es redundante EN LOS HECHOS pero no incorrecto (`máx(x, máx(x, y)) == máx(x, y)`).
 * Se repite a propósito para que esta función sea correcta por construcción sin depender de que
 * su caller esté siempre antes del punto de neutralización — así una accesibilidad del SO más
 * agresiva que el nivel elegido en la app sigue empujando a Tier 2, igual que documenta el KDoc
 * de [LocalFontSizeLevel].
 */
@Composable
@ReadOnlyComposable
fun rememberReportTier(): ReportTier {
    val levelScale = LocalFontSizeLevel.current.nominalScale
    val osFontScale = LocalDensity.current.fontScale
    return resolveTier(max(levelScale, osFontScale))
}
