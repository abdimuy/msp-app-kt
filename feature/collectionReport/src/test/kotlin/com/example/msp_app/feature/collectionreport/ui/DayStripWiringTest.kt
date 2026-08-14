package com.example.msp_app.feature.collectionreport.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.ui.tier2.CollectionReportContentTier2
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Que la tira de días esté CABLEADA en las DOS pantallas, no solo compilada.
 *
 * Es el candado de la lección que ya se pagó una vez en este módulo: dejar Tier 2 a medias
 * (control visible que no hace nada, o funcionalidad que simplemente no existe para quien usa
 * letra muy grande). Tier 2 monta el MISMO [com.example.msp_app.feature.collectionreport.ui.components.DayStrip]
 * que Tier 1 — sus chips ya nacen con el alto mínimo curado de 56dp y la tira se desplaza a lo
 * largo, así que no hacía falta una variante propia; lo que sí hacía falta es que su callback
 * llegue al ViewModel, y eso es lo que se verifica aquí.
 *
 * `setContent` solo admite una llamada por test, de ahí un test por tier y por caso.
 */
class DayStripWiringTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val domingo = LocalDate.of(2026, 8, 9)

    @Test
    fun `Tier 1 pinta la tira del ciclo en Dia`() {
        setTier1(MockupFixtures.stateDiaConCiclo())

        composeTestRule
            .onNodeWithContentDescription("jueves 13 de agosto, hoy, seleccionado")
            .assertIsDisplayed()
    }

    @Test
    fun `Tier 1 informa el dia que se toco`() {
        val elegidos = mutableListOf<LocalDate>()
        setTier1(MockupFixtures.stateDiaConCiclo(), onDaySelect = { elegidos += it })

        composeTestRule.onNodeWithContentDescription("domingo 9 de agosto").performClick()

        assertEquals(listOf(domingo), elegidos)
    }

    @Test
    fun `Tier 2 pinta la MISMA tira, no un control muerto`() {
        setTier2(MockupFixtures.stateDiaConCiclo())

        composeTestRule
            .onNodeWithContentDescription("jueves 13 de agosto, hoy, seleccionado")
            .assertIsDisplayed()
    }

    @Test
    fun `Tier 2 informa el dia que se toco`() {
        val elegidos = mutableListOf<LocalDate>()
        setTier2(MockupFixtures.stateDiaConCiclo(), onDaySelect = { elegidos += it })

        composeTestRule.onNodeWithContentDescription("domingo 9 de agosto").performClick()

        assertEquals(listOf(domingo), elegidos)
    }

    @Test
    fun `en Semana no hay tira - el resumen por dia ya muestra el ciclo entero`() {
        setTier1(MockupFixtures.stateSemana())

        composeTestRule
            .onAllNodesWithContentDescription("agosto", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun `sin ciclo el tablero de Dia queda exactamente como siempre`() {
        setTier1(MockupFixtures.stateDia())

        composeTestRule
            .onAllNodesWithContentDescription("agosto", substring = true)
            .assertCountEquals(0)
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────────

    private fun setTier1(state: CollectionReportUiState, onDaySelect: (LocalDate) -> Unit = {}) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
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
                    onDayRowClick = {},
                    onDaySelect = onDaySelect
                )
            }
        }
    }

    private fun setTier2(state: CollectionReportUiState, onDaySelect: (LocalDate) -> Unit = {}) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
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
                    onDayRowClick = {},
                    onDaySelect = onDaySelect
                )
            }
        }
    }
}
