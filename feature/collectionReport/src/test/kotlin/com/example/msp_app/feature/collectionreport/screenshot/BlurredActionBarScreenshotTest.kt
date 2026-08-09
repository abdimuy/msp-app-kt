package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.msp_app.feature.collectionreport.ui.CollectionReportContent
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.components.BlurredActionBar
import org.junit.Test

/**
 * Golden baseline (light+dark @1.0, Tier 1) de [BlurredActionBar] — el degradado
 * transparente→fondo (mockup `.actions`) y sus tres botones, sola y "sobre el scroll"
 * (compuesta con [CollectionReportContent], como la monta `CollectionReportScreen`) para
 * verificar el degradado contra contenido real detrás, no un fondo en blanco.
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
