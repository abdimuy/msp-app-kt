package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportContentTier2
import org.junit.Test

/**
 * Golden baseline (light+dark @2.0, Tier 2) de [CollectionReportContentTier2] completo — "Muy
 * grande" (task-9-brief.md: "Roborazzi: Tier 2 (light+dark @2.0) del screen completo"). `2.0f`
 * es el mismo punto de escala que la matriz Tier×escala del Plan 3 etiqueta "Muy grande" —
 * confirma visualmente que Efectivo/Transferencia/Condonado/Visitas quedan en filas propias de
 * ancho completo (nunca un grid de 2 apretado) a esa escala.
 */
class CollectionReportTier2ScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `Tier 2 dia light escala 2_0`() {
        capture(name = "collection_report_tier2_dia_light_2_0", dark = false, fontScale = 2.0f) {
            Tier2Content()
        }
    }

    @Test
    fun `Tier 2 dia dark escala 2_0`() {
        capture(name = "collection_report_tier2_dia_dark_2_0", dark = true, fontScale = 2.0f) {
            Tier2Content()
        }
    }
}

@Composable
private fun Tier2Content() {
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
}
