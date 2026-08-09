package com.example.msp_app.feature.collectionreport.screenshot

import com.example.msp_app.feature.collectionreport.ui.CollectionReportContent
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import org.junit.Test

/**
 * Golden baseline (light+dark, Tier 1) del tablero completo con `masked = true` (task-9-brief.md
 * — "goldens: masked state light/dark"): confirma visualmente la auditoría de completitud de
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportContentTest]
 * ("con masked verdadero TODOS los montos... muestran MASKED_MONEY") — el `$••••` en hero,
 * duo, chip Condonado y cada fila del detalle, nunca una cifra cruda.
 */
class CollectionReportMaskedScreenshotTest : CollectionReportScreenshotTest() {

    @Test
    fun `tablero completo enmascarado dia light`() {
        capture(name = "collection_report_masked_dia_light", dark = false) {
            CollectionReportContent(
                state = MockupFixtures.stateDia(masked = true),
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
    }

    @Test
    fun `tablero completo enmascarado dia dark`() {
        capture(name = "collection_report_masked_dia_dark", dark = true) {
            CollectionReportContent(
                state = MockupFixtures.stateDia(masked = true),
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
    }
}
