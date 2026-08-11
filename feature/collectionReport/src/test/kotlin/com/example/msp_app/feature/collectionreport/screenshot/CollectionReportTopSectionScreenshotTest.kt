package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import com.example.msp_app.feature.collectionreport.ui.CollectionReportContent
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.components.HeroSection
import java.math.BigDecimal
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0, Tier 1) de [CollectionReportContent] completo — header +
 * selector de periodo + subrow + hero con sparkline (Task 6) + duo/chips/detalle (Task 7) —
 * en Día y en Semana, con los datos EXACTOS del mockup (task-6-brief.md/task-7-brief.md, ver
 * [MockupFixtures]). El nombre "top section" es histórico (Task 6, cuando `TopSection` SÍ
 * capturaba solo header→hero); [CollectionReportDetailScreenshotTest] agrega un golden
 * ACOTADO solo a duo+chips+detalle. La matriz Tier×escala completa llega en Task 11
 * (fidelity gate).
 *
 * El tercer par (`hero large amount`) es la evidencia visual de que el monto del hero NO
 * trunca a escala de millones — mismo truco que
 * `MspHeroTodayCardScreenshotTest` (`:core:designsystem`, Task 8): se fuerza un ancho
 * angosto y el número reflowea a varias líneas en vez de perder dígitos.
 */
class CollectionReportTopSectionScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `seccion superior dia light`() {
        capture(name = "collection_report_top_section_dia_light", dark = false) {
            TopSection(MockupFixtures.stateDia())
        }
    }

    @Test
    fun `seccion superior dia dark`() {
        capture(name = "collection_report_top_section_dia_dark", dark = true) {
            TopSection(MockupFixtures.stateDia())
        }
    }

    @Test
    fun `seccion superior semana light`() {
        capture(name = "collection_report_top_section_semana_light", dark = false) {
            TopSection(MockupFixtures.stateSemana())
        }
    }

    @Test
    fun `seccion superior semana dark`() {
        capture(name = "collection_report_top_section_semana_dark", dark = true) {
            TopSection(MockupFixtures.stateSemana())
        }
    }

    @Test
    fun `hero monto grande no trunca light`() {
        capture(
            name = "collection_report_hero_large_amount_light",
            dark = false
        ) { LargeAmountHero() }
    }

    @Test
    fun `hero monto grande no trunca dark`() {
        capture(
            name = "collection_report_hero_large_amount_dark",
            dark = true
        ) { LargeAmountHero() }
    }
}

@Composable
private fun TopSection(state: CollectionReportUiState) {
    CollectionReportContent(
        state = state,
        onMenuClick = {},
        onPrivacyToggle = {},
        onThemeToggle = {},
        onPeriodSelect = {},
        onHeroClick = {},
        onSparkBarClick = {},
        onEfectivoClick = {},
        onTransferenciaClick = {},
        onCondonadoClick = {},
        onVisitasClick = {},
        onSortSelect = {},
        onPaymentRowClick = {},
        onDayRowClick = {}
    )
}

@Composable
private fun LargeAmountHero() {
    Box(modifier = Modifier.width(NARROW_WIDTH).padding(MspTheme.spacing.md)) {
        HeroSection(
            hero = MockupFixtures.heroDia().copy(
                monto = Money.of(BigDecimal("12345678.90"))
            ),
            period = ReportPeriod.DIA,
            masked = false,
            onClick = {}
        )
    }
}

private val NARROW_WIDTH = 200.dp
