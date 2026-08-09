package com.example.msp_app.feature.collectionreport.ui.tier2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity

/**
 * Los dos layouts del tablero (spec §5): [TIER_1] es el denso/responsivo que
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportContent] ya rinde (Normal/Grande);
 * [TIER_2] es el curado de [com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportScreenTier2]
 * (Muy grande) — una idea por vista, targets mayores, columna única.
 */
enum class ReportTier { TIER_1, TIER_2 }

/**
 * A partir de qué `fontScale` del SO se considera "Muy grande" (spec §5) y por lo tanto
 * [ReportTier.TIER_2] — a medio camino entre el `1.3f` que la matriz de escalas del Plan 3
 * etiqueta "Grande" (sigue siendo Tier 1) y el `2.0f` que etiqueta "Muy grande" (Tier 2); ver
 * `docs/superpowers/plans/2026-08-09-plan3-designsystem.md` Task 10 para esa matriz.
 */
private const val FONT_SCALE_TIER2_THRESHOLD = 1.5f

/**
 * Decide [ReportTier] a partir de un `fontScale` crudo — `internal` y NO `@Composable` a
 * propósito: función pura testeable sin Compose/Robolectric (unit test JVM puro).
 *
 * **Parked for user (task-9-brief.md):** el spec §5 define esta selección por la preferencia
 * propia "Tamaño de texto" (Normal/Grande/Muy grande) de `:core:common`/settings, que TODAVÍA
 * no existe como módulo transversal. Mientras tanto se usa `fontScale` del SO (accesibilidad
 * "Tamaño de fuente") como proxy fiel — cablear la preferencia real cuando exista es trabajo
 * transversal a toda la app, no solo de este piloto; no bloquea la fidelidad Tier 1/2 de hoy.
 */
internal fun resolveTier(fontScale: Float): ReportTier =
    if (fontScale >= FONT_SCALE_TIER2_THRESHOLD) ReportTier.TIER_2 else ReportTier.TIER_1

/**
 * Lee el `fontScale` activo (vía [LocalDensity], mismo canal que usa el resto del design
 * system para escalar tipografía) y resuelve el [ReportTier] correspondiente — el punto que
 * Task 10 (cableado de ruta) consulta para decidir entre
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportScreen] (Tier 1) y
 * [com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportScreenTier2] (Tier 2).
 */
@Composable
@ReadOnlyComposable
fun rememberReportTier(): ReportTier {
    val fontScale = LocalDensity.current.fontScale
    return resolveTier(fontScale)
}
