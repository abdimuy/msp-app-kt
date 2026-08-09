package com.example.msp_app.feature.collectionreport.screenshot

import androidx.compose.runtime.Composable
import com.example.msp_app.feature.collectionreport.ui.CollectionReportContent
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportContentTier2
import org.junit.Test

/**
 * Task 11 (cierre del piloto): matriz Roborazzi COMPLETA de la pantalla real, Tier 1/Tier 2 ×
 * escala {1.0, 1.3, 2.0} × {light, dark} × {Día, Semana} (task-11-brief.md "Matriz Roborazzi
 * completa"). Nombre determinista `collection_<estado>_<tier>_<tema>_<escala>` — exactamente el
 * patrón que pide el brief, distinto del prefijo `collection_report_*` que usan los goldens de
 * estado puntual (top-section/detail/sheets/masked/action-bar, ya existentes desde Tasks 6-9).
 *
 * **Alcance (gotcha del brief: "el piso obligatorio es Tier 1 × 3 escalas × 2 temas para la
 * pantalla + estados clave; Tier 2 donde el layout curado aplica"):** este archivo es el piso
 * obligatorio Tier 1 completo (12 goldens: Día/Semana × 3 escalas × 2 temas) MÁS la misma
 * cobertura para Tier 2 (12 goldens más) porque el layout curado (`Tier2Tile`/`Tier2Chip`,
 * filas de ancho completo) es precisamente lo que cambia entre tiers y necesita verse a las 3
 * escalas, no solo `2.0` (que ya vivía, con otro nombre, en el extinto
 * `CollectionReportTier2ScreenshotTest` de Task 9 — sus 2 goldens quedan subsumidos aquí como
 * `collection_dia_tier2_{light,dark}_2_0`). Los estados clave (enmascarado, cada sheet) NO
 * repiten esta matriz de 3 escalas — ya tienen su propio golden a escala 1.0
 * (`CollectionReportMaskedScreenshotTest`/`ReportSheetsScreenshotTest`) y la aserción dura de
 * "el dinero no trunca a escala 2.0" vive en `MoneyNoTruncationTest` (layout real, no píxeles) —
 * clonar cada sheet a 3 escalas × 2 temas solo infla el PNG count sin agregar señal nueva
 * (`ReportSheets` es el MISMO overlay para Tier 1 y Tier 2, ver KDoc de `CollectionReportScreen`).
 */
class CollectionReportMatrixScreenshotTest : CollectionReportScreenshotTest() {

    // --- Tier 1, Día -----------------------------------------------------------------------

    @Test
    fun `dia tier1 light escala 1_0`() = captureMatrix(
        "dia",
        "tier1",
        dark = false,
        scale = 1.0f,
        "1_0"
    ) {
        Tier1(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier1 light escala 1_3`() = captureMatrix(
        "dia",
        "tier1",
        dark = false,
        scale = 1.3f,
        "1_3"
    ) {
        Tier1(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier1 light escala 2_0`() = captureMatrix(
        "dia",
        "tier1",
        dark = false,
        scale = 2.0f,
        "2_0"
    ) {
        Tier1(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier1 dark escala 1_0`() = captureMatrix(
        "dia",
        "tier1",
        dark = true,
        scale = 1.0f,
        "1_0"
    ) {
        Tier1(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier1 dark escala 1_3`() = captureMatrix(
        "dia",
        "tier1",
        dark = true,
        scale = 1.3f,
        "1_3"
    ) {
        Tier1(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier1 dark escala 2_0`() = captureMatrix(
        "dia",
        "tier1",
        dark = true,
        scale = 2.0f,
        "2_0"
    ) {
        Tier1(MockupFixtures.stateDia())
    }

    // --- Tier 1, Semana ----------------------------------------------------------------------

    @Test
    fun `semana tier1 light escala 1_0`() = captureMatrix(
        "semana",
        "tier1",
        dark = false,
        scale = 1.0f,
        "1_0"
    ) {
        Tier1(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier1 light escala 1_3`() = captureMatrix(
        "semana",
        "tier1",
        dark = false,
        scale = 1.3f,
        "1_3"
    ) {
        Tier1(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier1 light escala 2_0`() = captureMatrix(
        "semana",
        "tier1",
        dark = false,
        scale = 2.0f,
        "2_0"
    ) {
        Tier1(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier1 dark escala 1_0`() = captureMatrix(
        "semana",
        "tier1",
        dark = true,
        scale = 1.0f,
        "1_0"
    ) {
        Tier1(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier1 dark escala 1_3`() = captureMatrix(
        "semana",
        "tier1",
        dark = true,
        scale = 1.3f,
        "1_3"
    ) {
        Tier1(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier1 dark escala 2_0`() = captureMatrix(
        "semana",
        "tier1",
        dark = true,
        scale = 2.0f,
        "2_0"
    ) {
        Tier1(MockupFixtures.stateSemana())
    }

    // --- Tier 2, Día -------------------------------------------------------------------------

    @Test
    fun `dia tier2 light escala 1_0`() = captureMatrix(
        "dia",
        "tier2",
        dark = false,
        scale = 1.0f,
        "1_0"
    ) {
        Tier2(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier2 light escala 1_3`() = captureMatrix(
        "dia",
        "tier2",
        dark = false,
        scale = 1.3f,
        "1_3"
    ) {
        Tier2(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier2 light escala 2_0`() = captureMatrix(
        "dia",
        "tier2",
        dark = false,
        scale = 2.0f,
        "2_0"
    ) {
        Tier2(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier2 dark escala 1_0`() = captureMatrix(
        "dia",
        "tier2",
        dark = true,
        scale = 1.0f,
        "1_0"
    ) {
        Tier2(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier2 dark escala 1_3`() = captureMatrix(
        "dia",
        "tier2",
        dark = true,
        scale = 1.3f,
        "1_3"
    ) {
        Tier2(MockupFixtures.stateDia())
    }

    @Test
    fun `dia tier2 dark escala 2_0`() = captureMatrix(
        "dia",
        "tier2",
        dark = true,
        scale = 2.0f,
        "2_0"
    ) {
        Tier2(MockupFixtures.stateDia())
    }

    // --- Tier 2, Semana ----------------------------------------------------------------------

    @Test
    fun `semana tier2 light escala 1_0`() = captureMatrix(
        "semana",
        "tier2",
        dark = false,
        scale = 1.0f,
        "1_0"
    ) {
        Tier2(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier2 light escala 1_3`() = captureMatrix(
        "semana",
        "tier2",
        dark = false,
        scale = 1.3f,
        "1_3"
    ) {
        Tier2(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier2 light escala 2_0`() = captureMatrix(
        "semana",
        "tier2",
        dark = false,
        scale = 2.0f,
        "2_0"
    ) {
        Tier2(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier2 dark escala 1_0`() = captureMatrix(
        "semana",
        "tier2",
        dark = true,
        scale = 1.0f,
        "1_0"
    ) {
        Tier2(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier2 dark escala 1_3`() = captureMatrix(
        "semana",
        "tier2",
        dark = true,
        scale = 1.3f,
        "1_3"
    ) {
        Tier2(MockupFixtures.stateSemana())
    }

    @Test
    fun `semana tier2 dark escala 2_0`() = captureMatrix(
        "semana",
        "tier2",
        dark = true,
        scale = 2.0f,
        "2_0"
    ) {
        Tier2(MockupFixtures.stateSemana())
    }

    private fun captureMatrix(
        estado: String,
        tier: String,
        dark: Boolean,
        scale: Float,
        escalaSuffix: String,
        content: @Composable () -> Unit
    ) {
        val tema = if (dark) "dark" else "light"
        capture(
            name = "collection_${estado}_${tier}_${tema}_$escalaSuffix",
            dark = dark,
            fontScale = scale,
            content = content
        )
    }
}

@Composable
private fun Tier1(state: CollectionReportUiState) {
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
private fun Tier2(state: CollectionReportUiState) {
    CollectionReportContentTier2(
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
