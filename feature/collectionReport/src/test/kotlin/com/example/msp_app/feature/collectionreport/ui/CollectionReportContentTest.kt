package com.example.msp_app.feature.collectionreport.ui

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.domain.model.ReportPeriod
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // Refleja 1:1 la superficie de callbacks de `CollectionReportContent` (Task 6 + Task 7)
    // para poder interceptar cualquiera de ellos en un test — no hay forma de reducir esto
    // sin perder cobertura de alguno de los eventos del tablero.
    @Suppress("LongParameterList")
    private fun setContent(
        state: CollectionReportUiState,
        onPeriodSelect: (ReportPeriod) -> Unit = {},
        onEfectivoClick: () -> Unit = {},
        onTransferenciaClick: () -> Unit = {},
        onCondonadoClick: () -> Unit = {},
        onVisitasClick: () -> Unit = {},
        onSortSelect: (DetailSort) -> Unit = {},
        onPaymentRowClick: (String) -> Unit = {},
        onDayRowClick: (Int) -> Unit = {}
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
                    onSparkBarClick = {},
                    onEfectivoClick = onEfectivoClick,
                    onTransferenciaClick = onTransferenciaClick,
                    onCondonadoClick = onCondonadoClick,
                    onVisitasClick = onVisitasClick,
                    onSortSelect = onSortSelect,
                    onPaymentRowClick = onPaymentRowClick,
                    onDayRowClick = onDayRowClick
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

    // region — Task 7: duo de tiles, chips secundarios, detalle (lista Día / resumen Semana)

    @Test
    fun `el duo muestra los montos formateados y los conteos de efectivo y transferencia`() {
        setContent(MockupFixtures.stateDia())

        // "$12,100.00" también es el well "Efectivo en mano" del hero (mismo monto real,
        // ambos vienen de `ReportAggregator.efectivoEnMano`/`.efectivo`) — dos nodos con el
        // mismo texto; el well se compone primero (índice 0), el tile del duo después.
        composeTestRule.onAllNodesWithText(
            formatMoneyMxn(BigDecimal("12100"))
        )[1].performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("22 pagos").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            formatMoneyMxn(BigDecimal("6200"))
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("10 pagos").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tocar el tile de Efectivo informa onEfectivoClick`() {
        var tapped = false
        setContent(MockupFixtures.stateDia(), onEfectivoClick = { tapped = true })

        // "Efectivo" también aparece en el pill de método de las filas cobradas en efectivo
        // (mismo texto, varios lugares); el tile del duo se compone PRIMERO en el árbol —
        // índice 0 es siempre el tile, nunca una fila.
        composeTestRule.onAllNodesWithText("Efectivo")[0].performScrollTo().performClick()

        assertTrue(tapped)
    }

    @Test
    fun `tocar el tile de Transferencia informa onTransferenciaClick`() {
        var tapped = false
        setContent(MockupFixtures.stateDia(), onTransferenciaClick = { tapped = true })

        composeTestRule.onNodeWithText("Transferencia").performScrollTo().performClick()

        assertTrue(tapped)
    }

    @Test
    fun `el chip de Condonado muestra el monto ambar y tocarlo informa onCondonadoClick`() {
        var tapped = false
        setContent(MockupFixtures.stateDia(), onCondonadoClick = { tapped = true })

        composeTestRule.onNodeWithText(
            formatMoneyMxn(BigDecimal("1400"))
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Condonado").performScrollTo().performClick()

        assertTrue(tapped)
    }

    @Test
    fun `el chip de Visitas muestra el conteo y tocarlo informa onVisitasClick`() {
        var tapped = false
        setContent(MockupFixtures.stateDia(), onVisitasClick = { tapped = true })

        composeTestRule.onNodeWithText("14").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Visitas").performScrollTo().performClick()

        assertTrue(tapped)
    }

    @Test
    fun `el encabezado de detalle en Dia muestra el conteo de pagos y el segment Hora Nombre`() {
        setContent(MockupFixtures.stateDia())

        composeTestRule.onNodeWithText("Pagos del día · 4").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Hora").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Nombre").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `el encabezado de detalle en Semana muestra el resumen sin el segment de orden`() {
        setContent(MockupFixtures.stateSemana())

        composeTestRule.onNodeWithText(
            "Resumen por día · 5 días"
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Hora").assertDoesNotExist()
    }

    @Test
    fun `tocar Nombre en el segment de orden informa onSortSelect con NOMBRE`() {
        val selected = mutableListOf<DetailSort>()
        setContent(MockupFixtures.stateDia(), onSortSelect = { selected += it })

        composeTestRule.onNodeWithText("Nombre").performScrollTo().performClick()

        assertEquals(listOf(DetailSort.NOMBRE), selected)
    }

    @Test
    fun `tocar Hora en el segment de orden informa onSortSelect con HORA`() {
        val selected = mutableListOf<DetailSort>()
        setContent(MockupFixtures.stateDia().copy(sort = DetailSort.NOMBRE), onSortSelect = {
            selected += it
        })

        composeTestRule.onNodeWithText("Hora").performScrollTo().performClick()

        assertEquals(listOf(DetailSort.HORA), selected)
    }

    @Test
    fun `en Dia la lista de pagos respeta el orden que trae detail_rows`() {
        val reversed = MockupFixtures.paymentsDia().reversed()
        setContent(MockupFixtures.stateDia().copy(detail = DetailUi.Payments(reversed)))

        val firstTop = composeTestRule.onNodeWithText(
            reversed.first().cliente
        ).getUnclippedBoundsInRoot().top
        val lastTop = composeTestRule.onNodeWithText(
            reversed.last().cliente
        ).getUnclippedBoundsInRoot().top

        assertTrue(firstTop < lastTop)
    }

    @Test
    fun `la fila de pago muestra las iniciales del cliente y el pill de metodo`() {
        setContent(MockupFixtures.stateDia())

        composeTestRule.onNodeWithText("ML").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Transfer.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tocar una fila de pago en Dia informa onPaymentRowClick con su id`() {
        val clicked = mutableListOf<String>()
        setContent(MockupFixtures.stateDia(), onPaymentRowClick = { clicked += it })

        composeTestRule.onNodeWithText("María López Hernández").performScrollTo().performClick()

        assertEquals(listOf("p-ml"), clicked)
    }

    @Test
    fun `una fila de pago no sincronizada muestra el dot por subir con su descripcion`() {
        val unsynced = MockupFixtures.paymentsDia().first().copy(synced = false)
        setContent(MockupFixtures.stateDia().copy(detail = DetailUi.Payments(listOf(unsynced))))

        composeTestRule.onNodeWithContentDescription(
            "por subir"
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `en Semana el detalle muestra una fila por dia con nombre, conteo y monto`() {
        setContent(MockupFixtures.stateSemana())

        composeTestRule.onNodeWithText("lun 3 ago").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("39 pagos").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(
            formatMoneyMxn(BigDecimal("21300"))
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("vie 7 ago (hoy)").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `tocar una fila de dia en Semana informa onDayRowClick con su indice`() {
        val clicked = mutableListOf<Int>()
        setContent(MockupFixtures.stateSemana(), onDayRowClick = { clicked += it })

        composeTestRule.onNodeWithText("mar 4 ago").performScrollTo().performClick()

        assertEquals(listOf(1), clicked)
    }

    @Test
    fun `el detalle vacio muestra un mensaje en vez de una tarjeta en blanco`() {
        setContent(MockupFixtures.stateDia().copy(detail = DetailUi.Payments(emptyList())))

        composeTestRule.onNodeWithText("Sin datos aún").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `con masked verdadero se ocultan tambien los montos del duo, chips y detalle`() {
        setContent(MockupFixtures.stateDia(masked = true))

        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("12100"))).assertDoesNotExist()
        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("1400"))).assertDoesNotExist()
        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("1200"))).assertDoesNotExist()
        // Visitas es un conteo, NO dinero — nunca se enmascara.
        composeTestRule.onNodeWithText("14").performScrollTo().assertIsDisplayed()
    }

    // endregion

    private companion object {
        const val HERO_PROGRESS = 0.91f
    }
}
