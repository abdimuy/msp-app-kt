package com.example.msp_app.feature.collectionreport.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
    fun `el hero muestra el monto formateado y ya no trae barra de progreso (retirada por Meta de la semana)`() {
        setContent(MockupFixtures.stateDia())

        composeTestRule.onNodeWithText(formatMoneyMxn(BigDecimal("18300"))).assertIsDisplayed()
        // La barra de progreso del hero (meta de mediana) fue retirada — "Meta de la semana"
        // (MetaCard) reemplaza esa cifra con anillos reales, ver KDoc de HeroUi/MspHeroTodayCard.
        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
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

        // El well "Efectivo en mano" del hero fue retirado (ver KDoc de HeroUi/HeroSection) —
        // "$12,100" ahora aparece UNA sola vez, en el tile del duo.
        composeTestRule.onNodeWithText(
            formatMoneyMxn(BigDecimal("12100"))
        ).performScrollTo().assertIsDisplayed()
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

    // Task 4: antes Semana NO mostraba el segment de orden ("siempre cronológico, nada que
    // reordenar"); ahora también reordena los pagos individuales dentro de cada día del ciclo
    // (`CollectionReportViewModel.setSort`), así que el segment aparece con labels
    // period-aware: "Fecha" en vez de "Hora" (el primer chip sigue siendo cronológico).
    @Test
    fun `el encabezado de detalle en Semana muestra el resumen y el segment Fecha Nombre`() {
        setContent(MockupFixtures.stateSemana())

        composeTestRule.onNodeWithText(
            "Resumen por día · 5 días"
        ).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Fecha").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Nombre").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Hora").assertDoesNotExist()
    }

    @Test
    fun `tocar Nombre en el segment de orden en Semana informa onSortSelect con NOMBRE`() {
        val selected = mutableListOf<DetailSort>()
        setContent(MockupFixtures.stateSemana(), onSortSelect = { selected += it })

        composeTestRule.onNodeWithText("Nombre").performScrollTo().performClick()

        assertEquals(listOf(DetailSort.NOMBRE), selected)
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

    // Fix de dispositivo (Task 1): el avatar de iniciales decorativo se reemplazó por un tile
    // tintado por método de cobro (`MethodTile`) — su `contentDescription` es el nombre del
    // método (mismo texto que el pill), no ya las iniciales del cliente.
    @Test
    fun `la fila de pago muestra el tile de metodo y el pill de metodo`() {
        setContent(MockupFixtures.stateDia())

        composeTestRule.onAllNodesWithContentDescription("Efectivo")[0]
            .performScrollTo()
            .assertIsDisplayed()
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

    @Test
    fun `con masked verdadero TODOS los montos del tablero muestran MASKED_MONEY, incluido el well del hero`() {
        setContent(MockupFixtures.stateDia(masked = true))

        // Auditoría de completitud (task-9-brief.md, actualizado por "Meta de la semana"):
        // monto del hero = 1 (la barra de progreso/goal cap/wells del hero fueron retirados,
        // ver KDoc de HeroUi — sus cifras ahora viven en `MetaCard`, SOLO en SEMANA, fuera de
        // este fixture Día), + duo (Efectivo/Transferencia) = 2, + chip Condonado = 1, + 4
        // filas de pago del detalle Día × 2 (monto del pago + saldo de la venta enriquecido)
        // = 8 -> 12 ocurrencias de `MASKED_MONEY` — ningún monto crudo se cuela sin `masked`.
        // `useUnmergedTree = true`:
        // el hero/tiles/chip/filas son contenedores clickables que MERGEAN sus descendientes
        // en un solo nodo de semántica (varias ocurrencias de texto colapsan a UN nodo); el
        // árbol sin mergear cuenta cada `Text` real, uno por monto.
        val maskedNodes = composeTestRule.onAllNodesWithText(MASKED_MONEY, useUnmergedTree = true)
        maskedNodes.assertCountEquals(TOTAL_MASKED_MONEY_NODES)
    }

    @Test
    fun `con masked verdadero el insight del hero se oculta con el glifo de puntos, no el texto real`() {
        setContent(MockupFixtures.stateDia(masked = true))

        composeTestRule.onNodeWithText("32 pagos", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText(MASKED_INSIGHT_GLYPH).assertIsDisplayed()
    }

    @Test
    fun `con masked falso el insight del hero muestra la frase real, no el glifo`() {
        setContent(MockupFixtures.stateDia(masked = false))

        // DÍA ya no reporta "% de tu meta" (retirado junto con la meta de mediana, ver KDoc de
        // `HeroSection.heroInsightText`) — solo el conteo de pagos + la proyección a cierre.
        composeTestRule.onNodeWithText(
            "32 pagos · a este ritmo cierras en ${formatMoneyMxn(BigDecimal("19800"))}"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithText(MASKED_INSIGHT_GLYPH).assertDoesNotExist()
    }

    // endregion

    private companion object {
        // Glifo de privacidad de la frase-insight del hero — 1:1 `HeroSection.MASKED_INSIGHT`
        // (`private`, no importable desde el test); mockup `masked?'&bull;&bull;&bull;':d.insight`.
        const val MASKED_INSIGHT_GLYPH = "•••"

        // Ver el desglose completo en el test que lo consume.
        const val TOTAL_MASKED_MONEY_NODES = 12
    }
}
