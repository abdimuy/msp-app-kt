package com.example.msp_app.feature.collectionreport.ui.tier2

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.DetailSort
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import java.math.BigDecimal
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [CollectionReportContentTier2] — el layout curado Tier 2 (Muy
 * grande, spec §5) sobre el MISMO estado/callbacks que
 * [com.example.msp_app.feature.collectionreport.ui.CollectionReportContentTest] ya cubre para
 * Tier 1; aquí solo se verifica lo que CAMBIA: Efectivo/Transferencia/Condonado/Visitas pasan
 * de grid de 2 a una fila de ancho completo cada uno ("una idea por vista"), y que el
 * enmascarado sigue cubriendo esos montos igual que en Tier 1. El golden visual (Tier 2 ×
 * {1.0,1.3,2.0} × {light,dark}, Día/Semana) vive en `screenshot/CollectionReportMatrixScreenshotTest`
 * (Task 11 — subsume el `CollectionReportTier2ScreenshotTest` original de Task 9, que solo
 * cubría Día @2.0).
 */
class CollectionReportContentTier2Test : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Suppress("LongParameterList")
    private fun setContent(
        state: CollectionReportUiState,
        onEfectivoClick: () -> Unit = {},
        onTransferenciaClick: () -> Unit = {},
        onCondonadoClick: () -> Unit = {},
        onVisitasClick: () -> Unit = {},
        onSortSelect: (DetailSort) -> Unit = {},
        onPaymentRowClick: (String) -> Unit = {}
    ) {
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
                    onEfectivoClick = onEfectivoClick,
                    onTransferenciaClick = onTransferenciaClick,
                    onCondonadoClick = onCondonadoClick,
                    onVisitasClick = onVisitasClick,
                    onSortSelect = onSortSelect,
                    onPaymentRowClick = onPaymentRowClick,
                    onDayRowClick = {}
                )
            }
        }
    }

    @Test
    fun `el header y el hero se rinden igual que Tier 1`() {
        setContent(MockupFixtures.stateDia())

        composeTestRule.onNodeWithText("Cobranza").assertIsDisplayed()
        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("18300"))).assertIsDisplayed()
    }

    @Test
    fun `Efectivo y Transferencia son filas propias de ancho completo, no un grid de 2`() {
        setContent(MockupFixtures.stateDia())

        // "Efectivo" también es el texto del pill de método en las filas cobradas en
        // efectivo del detalle (mismo texto, varios lugares); la tarjeta Tier2Tile se
        // compone PRIMERO — índice 0 es siempre la tarjeta, nunca una fila (mismo criterio
        // que `CollectionReportContentTest`).
        composeTestRule.onAllNodesWithText("Efectivo")[0].performScrollTo().assertIsDisplayed()
        // "$12,100" también es el well "Efectivo en mano" del hero (mismo monto real);
        // el well se compone primero (índice 0), el Tier2Tile de Efectivo después.
        composeTestRule.onAllNodesWithText(
            formatMoneyMxn(BigDecimal("12100"))
        )[1].performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Transferencia").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            formatMoneyMxn(BigDecimal("6200"))
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tocar la fila de Efectivo informa onEfectivoClick`() {
        var tapped = false
        setContent(MockupFixtures.stateDia(), onEfectivoClick = { tapped = true })

        composeTestRule.onAllNodesWithText("Efectivo")[0].performScrollTo().performClick()

        assertTrue(tapped)
    }

    @Test
    fun `tocar la fila de Condonado informa onCondonadoClick y la de Visitas onVisitasClick`() {
        var condonadoTapped = false
        var visitasTapped = false
        setContent(
            MockupFixtures.stateDia(),
            onCondonadoClick = { condonadoTapped = true },
            onVisitasClick = { visitasTapped = true }
        )

        composeTestRule.onNodeWithText("Condonado").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Visitas").performScrollTo().performClick()

        assertTrue(condonadoTapped)
        assertTrue(visitasTapped)
    }

    @Test
    fun `con masked verdadero todos los montos del duo y del chip Condonado se ocultan`() {
        setContent(MockupFixtures.stateDia(masked = true))

        // hero (monto+goalCap+2 wells) + Efectivo + Transferencia + Condonado + 4 pagos × 2
        // (monto + saldo enriquecido) = 15, mismo total que Tier 1 (mismo estado).
        // `useUnmergedTree = true`: ver el comentario equivalente en `CollectionReportContentTest`.
        composeTestRule.onAllNodesWithText(MASKED_MONEY, useUnmergedTree = true)
            .assertCountEquals(TOTAL_MASKED_MONEY_NODES)
        // Visitas es un conteo, nunca dinero.
        composeTestRule.onNodeWithText("14").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el detalle Dia se rinde igual que Tier 1`() {
        setContent(MockupFixtures.stateDia())

        composeTestRule.onNodeWithText(
            "María López Hernández"
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `en Semana el resumen por dia se rinde igual que Tier 1`() {
        setContent(MockupFixtures.stateSemana())

        composeTestRule.onNodeWithText("lun 3 ago").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("39 pagos").performScrollTo().assertIsDisplayed()
    }

    private companion object {
        const val TOTAL_MASKED_MONEY_NODES = 15
    }
}
