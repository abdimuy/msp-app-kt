package com.example.msp_app.feature.collectionreport.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de la mitad superior del reporte de cobranza
 * ([CollectionReportContent], Task 6): header con el cobrador del estado, el toggle de
 * periodo informando el evento correcto, el hero mostrando monto formateado + barra a la
 * fracción del estado, y el enmascarado de privacidad. El golden visual vive en
 * `screenshot/CollectionReportTopSectionScreenshotTest`.
 *
 * Se ejerce [CollectionReportContent] directo (sin `hiltViewModel()`/`NavController`,
 * reservados a `CollectionReportScreen`/Task 10) — mismo criterio que
 * `MspHeroTodayCardTest`/`SegmentChipsTest` en `:core:designsystem`.
 */
class CollectionReportContentTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(
        state: CollectionReportUiState,
        onPeriodSelect: (ReportPeriod) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                CollectionReportContent(
                    state = state,
                    onMenuClick = {},
                    onPrivacyToggle = {},
                    onThemeToggle = {},
                    onPeriodSelect = onPeriodSelect,
                    onHeroClick = {},
                    onSparkBarClick = {}
                )
            }
        }
    }

    @Test
    fun `el header muestra Cobranza y el subtitulo con el cobrador del estado`() {
        setContent(MockupFixtures.stateDia())

        composeTestRule.onNodeWithText("Cobranza").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reporte · ${MockupFixtures.COBRADOR}").assertIsDisplayed()
    }

    @Test
    fun `tocar Semana en el selector de periodo informa setPeriod con SEMANA`() {
        val selected = mutableListOf<ReportPeriod>()
        setContent(MockupFixtures.stateDia(), onPeriodSelect = { selected += it })

        composeTestRule.onNodeWithText("Semana").performClick()

        assertEquals(listOf(ReportPeriod.SEMANA), selected)
    }

    @Test
    fun `tocar Dia en el selector de periodo informa setPeriod con DIA`() {
        val selected = mutableListOf<ReportPeriod>()
        setContent(MockupFixtures.stateSemana(), onPeriodSelect = { selected += it })

        composeTestRule.onNodeWithText("Día").performClick()

        assertEquals(listOf(ReportPeriod.DIA), selected)
    }

    @Test
    fun `el hero muestra el monto formateado y la barra a la fraccion del estado`() {
        setContent(MockupFixtures.stateDia())

        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("18300"))).assertIsDisplayed()
        composeTestRule
            .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ProgressBarRangeInfo,
                    ProgressBarRangeInfo(HERO_PROGRESS, 0f..1f)
                )
            )
    }

    @Test
    fun `con masked verdadero el monto del hero se oculta con MASKED_MONEY`() {
        setContent(MockupFixtures.stateDia(masked = true))

        composeTestRule.onAllNodesWithText(MASKED_MONEY)[0].assertIsDisplayed()
    }

    @Test
    fun `renderiza sin crash mientras loading esta activo`() {
        setContent(MockupFixtures.stateDia().copy(loading = true))

        composeTestRule.onNodeWithText("Cobranza").assertIsDisplayed()
    }

    @Test
    fun `el banner de error se muestra cuando el estado trae un error`() {
        val mensaje = "no se pudo cargar el reporte de cobranza"
        setContent(MockupFixtures.stateDia(error = mensaje))

        composeTestRule.onNodeWithText(mensaje).assertIsDisplayed()
    }

    private companion object {
        const val HERO_PROGRESS = 0.91f
    }
}
