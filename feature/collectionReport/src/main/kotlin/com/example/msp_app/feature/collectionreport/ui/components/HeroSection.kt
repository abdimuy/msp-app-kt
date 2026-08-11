package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.msp_app.core.designsystem.component.MspHeroTodayCard
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.feature.collectionreport.domain.Insight
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.HeroUi

/** Glifo de privacidad de la frase-insight — mockup `masked?'&bull;&bull;&bull;':d.insight`. */
private const val MASKED_INSIGHT = "•••"

/**
 * `testTag` del hero — ancla determinista para el smoke e2e de dispositivo (Task 11: "el
 * reporte renderiza (hero visible ... un sheet abre)"). Vive en el mismo nodo que el
 * `Modifier.clickable(onClick)` de `MspHeroTodayCard` (el `modifier` de este composable se
 * pasa tal cual a esa tarjeta), así que un test puede tanto confirmar que el hero está en
 * pantalla como tocarlo para abrir su sheet con el MISMO tag. No exportado por import
 * cross-módulo a propósito (mismo criterio que `SEGMENT_CHIP_TAG_PREFIX` en
 * `:core:designsystem`) — el androidTest de `:app` lo referencia por el literal.
 */
const val COLLECTION_REPORT_HERO_TEST_TAG = "collection_report_hero"

/**
 * HERO del tablero (mockup `.hero`): compone [MspHeroTodayCard] del design system con
 * [HeroUi] — el estado trae dinero estructurado ([Money]) y el insight sin formatear
 * ([Insight] sellado); ESTA capa es donde el dinero se formatea a texto (frontera de capas
 * de `CollectionReportUiState`, ver su KDoc) y donde se arma la frase-insight es-MX.
 *
 * [onSparkBarClick] solo se activa en [ReportPeriod.SEMANA] — [Sparkline] ya filtra por
 * periodo, aquí solo se reenvía.
 *
 * **Regla anti-colapso (spec §6):** no se aplica `weight`/`fillMaxHeight` propio — el
 * caller ([com.example.msp_app.feature.collectionreport.ui.CollectionReportScreen]) lo
 * monta dentro de una `Column` con scroll sin `weight`, para que el hero nunca se comprima.
 *
 * **Meta de la semana (reemplaza meta de mediana + wells):** el hero YA NO pasa
 * `progress`/`goalAmount`/wells a [MspHeroTodayCard] (ahora opcionales, ver su KDoc) — esas
 * cifras retiradas viven en `MetaCard`, la tarjeta que el caller monta debajo del hero con
 * [hero]'s [HeroUi.porcentajeCobro]/[HeroUi.porcentajeCuentas].
 */
@Composable
fun HeroSection(
    hero: HeroUi,
    period: ReportPeriod,
    masked: Boolean,
    onClick: () -> Unit,
    onSparkBarClick: ((Int) -> Unit)? = null,
    animateSparkline: Boolean = true,
    modifier: Modifier = Modifier
) {
    MspHeroTodayCard(
        overline = hero.overline,
        delta = hero.delta.text,
        amount = hero.monto.amount,
        insight = if (masked) MASKED_INSIGHT else heroInsightText(hero.insight),
        masked = masked,
        sparkline = {
            Sparkline(
                timeline = hero.sparkline,
                period = period,
                onBarClick = onSparkBarClick,
                animate = animateSparkline
            )
        },
        onClick = onClick,
        modifier = modifier.testTag(COLLECTION_REPORT_HERO_TEST_TAG)
    )
}

/**
 * Arma la frase-insight es-MX a partir del [Insight] sellado del dominio (SIN formatear —
 * ver su KDoc en `ReportAggregator.kt`). `projection` (Día) queda PARKED por diseño en el
 * dominio (Task 5 report: "no hay todavía un oracle de proyección de cierre verificado");
 * cuando es `null` se omite la cláusula "a este ritmo cierras en $Y" en vez de inventarla.
 *
 * **Fix "Meta de la semana":** [Insight.Daily.progressPct] ya NO referencia una meta diaria
 * (`SuggestedGoal` retirado) — DÍA no tiene "meta de la semana" que reportar, así que la
 * cláusula "vas al X% de tu meta" se omite por completo en DÍA (antes mostraba el progreso
 * contra la mediana sugerida). [Insight.Weekly.progressPct] SÍ conserva la cláusula: ahora
 * refleja el "Porcentaje cobro" (ponderado) real capado a `[0,100]` para la frase — el valor
 * SIN capar (puede exceder 100%) se muestra en el ring de `MetaCard`.
 */
private fun heroInsightText(insight: Insight): String = when (insight) {
    is Insight.Daily -> buildString {
        append("${insight.count} pagos")
        insight.projection?.let { projection ->
            append(" · a este ritmo cierras en ${formatMoneyMxn(projection.amount)}")
        }
    }

    is Insight.Weekly ->
        "${insight.count} pagos · vas al ${insight.progressPct}% de la meta · " +
            "día ${insight.cycleDay} de ${insight.cycleDays} de la semana"
}
