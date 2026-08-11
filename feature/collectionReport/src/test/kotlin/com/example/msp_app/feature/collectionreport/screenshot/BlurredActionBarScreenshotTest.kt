package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.msp_app.feature.collectionreport.ui.CollectionReportContent
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar
import com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportContentTier2
import com.example.msp_app.feature.collectionreport.ui.tier2.ReportTier
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0, Tier 1) de [BlurredActionBar] — el degradado
 * transparente→fondo (mockup `.actions`) y sus tres botones, sola y "sobre el scroll"
 * (compuesta con [CollectionReportContent], como la monta `CollectionReportScreen`) para
 * verificar el degradado contra contenido real detrás, no un fondo en blanco.
 *
 * **Goldens `tier2` (fix bug "Grande/Muy grande rompe el reporte"):** a `fontScale = 2.0f` —
 * mismo mecanismo de escala que usa [CollectionReportMatrixScreenshotTest] (vía `LocalDensity`,
 * sp escala con `fontScale` incluso sin `ReportMspTheme`/la rampa comprimida) — verifican que la
 * barra apilada de [ReportTier.TIER_2] no envuelve "Compartir"/"Imprimir" a la mitad de palabra
 * ni dentro de [BlurredActionBar] sola ni contra el contenido real detrás.
 */
class BlurredActionBarScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `barra difuminada sola light`() {
        capture(name = "collection_report_action_bar_light", dark = false) {
            BlurredActionBar(onCompartirClick = {}, onImprimirClick = {}, onPdfClick = {})
        }
    }

    @Test
    fun `barra difuminada sola dark`() {
        capture(name = "collection_report_action_bar_dark", dark = true) {
            BlurredActionBar(onCompartirClick = {}, onImprimirClick = {}, onPdfClick = {})
        }
    }

    @Test
    fun `barra difuminada sobre el scroll light`() {
        capture(name = "collection_report_action_bar_over_scroll_light", dark = false) {
            ContentWithBar()
        }
    }

    @Test
    fun `barra difuminada sobre el scroll dark`() {
        capture(name = "collection_report_action_bar_over_scroll_dark", dark = true) {
            ContentWithBar()
        }
    }

    @Test
    fun `barra difuminada tier2 apilada sola light escala 2_0`() {
        capture(
            name = "collection_report_action_bar_tier2_light_2_0",
            dark = false,
            fontScale = 2.0f
        ) {
            BlurredActionBar(
                onCompartirClick = {},
                onImprimirClick = {},
                onPdfClick = {},
                tier = ReportTier.TIER_2
            )
        }
    }

    @Test
    fun `barra difuminada tier2 apilada sola dark escala 2_0`() {
        capture(
            name = "collection_report_action_bar_tier2_dark_2_0",
            dark = true,
            fontScale = 2.0f
        ) {
            BlurredActionBar(
                onCompartirClick = {},
                onImprimirClick = {},
                onPdfClick = {},
                tier = ReportTier.TIER_2
            )
        }
    }

    @Test
    fun `barra difuminada tier2 apilada sobre el scroll light escala 2_0`() {
        capture(
            name = "collection_report_action_bar_tier2_over_scroll_light_2_0",
            dark = false,
            fontScale = 2.0f
        ) {
            ContentWithBarTier2()
        }
    }

    @Test
    fun `barra difuminada tier2 apilada sobre el scroll dark escala 2_0`() {
        capture(
            name = "collection_report_action_bar_tier2_over_scroll_dark_2_0",
            dark = true,
            fontScale = 2.0f
        ) {
            ContentWithBarTier2()
        }
    }
}

@Composable
private fun ContentWithBar() {
    Box(modifier = Modifier.fillMaxSize()) {
        CollectionReportContent(
            state = MockupFixtures.stateDia(),
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
        BlurredActionBar(
            onCompartirClick = {},
            onImprimirClick = {},
            onPdfClick = {},
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ContentWithBarTier2() {
    Box(modifier = Modifier.fillMaxSize()) {
        CollectionReportContentTier2(
            state = MockupFixtures.stateDia(),
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
        BlurredActionBar(
            onCompartirClick = {},
            onImprimirClick = {},
            onPdfClick = {},
            modifier = Modifier.align(Alignment.BottomCenter),
            tier = ReportTier.TIER_2
        )
    }
}
