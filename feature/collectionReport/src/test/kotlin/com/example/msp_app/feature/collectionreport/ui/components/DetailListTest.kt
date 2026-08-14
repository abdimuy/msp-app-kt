package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.ui.DayRowUi
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Cuántos pagos se ven con la lista colapsada — replicado a propósito, NO importado de
 * `DetailList.kt` (la constante es `private` allá y así debe quedarse). Si alguien mueve el
 * umbral de producción sin pensarlo, esta suite truena: es el candado, no un espejo.
 */
private const val EXPECTED_COLLAPSED_ROWS = 5

/**
 * Volumen REAL de producción: 57 pagos en un solo día (domingo 9 ago 2026) — el dato que
 * originó el encargo. Con este tamaño se verifica que ni el escalonado de entrada ni el conteo
 * de la etiqueta se rompen fuera de la fixture bonita de 4 filas del mockup.
 */
private const val PRODUCTION_DAY_PAYMENTS = 57

private const val LARGE_FONT_SCALE = 2.0f

/**
 * Frames que se dejan correr tras mover el estado con el reloj congelado. Tres alcanzan de sobra
 * para que Compose recomponga y DESMONTE la cola cuando no hay animación (`ExitTransition.None`),
 * y son solo ~48ms de los 300ms del `shrinkVertically` cuando sí la hay — margen amplio en ambos
 * sentidos, sin depender de reloj de pared.
 */
private const val FRAMES_TO_SETTLE_STRUCTURE = 3

/**
 * Compose-test (no golden) del colapsable de la lista de pagos de [DetailList] — el encargo del
 * dueño: "con un colapsable para ver todos los pagos, eso de '13 pagos más' y que no los muestre
 * no sirve, y deja la lista de pagos como ya está por item".
 *
 * Cubre las promesas del contrato: (1) colapsada se ven [EXPECTED_COLLAPSED_ROWS] filas y
 * expandida se ven TODAS; (2) el control solo aparece cuando hay algo que revelar (frontera del
 * umbral) y siempre lleva el conteo real; (3) [DetailUi.Days] (Semana) NO cambia de
 * comportamiento; (4) el estado es izado — si el padre no lo mueve, la lista no se mueve sola;
 * (5) con reduce-motion la expansión/colapso es estructuralmente instantánea.
 *
 * Las filas se cuentan por la etiqueta "Saldo", que la fila de pago pinta una vez por pago con
 * saldo (todos los de [MockupFixtures.manyPaymentsDia] lo traen). Sobre el árbol de semántica
 * MERGEADO cada fila es `clickable` y colapsa en UN nodo, así que el conteo de nodos con ese
 * texto ES el conteo de filas pintadas — sin depender de píxeles ni de nombres de cliente (que
 * en la fixture larga se ciclan y se repiten).
 *
 * `@GraphicsMode(NATIVE)` + qualifiers fijos: el caso `fontScale = 2.0` mide `TextLayoutResult`
 * real, mismo motivo que `MoneyNoTruncationTest`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class DetailListTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ---------------------------------------------------------------- colapsado / expandido

    @Test
    fun `colapsada pinta exactamente el umbral de filas`() {
        setContent(payments(PRODUCTION_DAY_PAYMENTS), expanded = false)

        assertPaintedRows(EXPECTED_COLLAPSED_ROWS)
    }

    @Test
    fun `expandida pinta TODOS los pagos, sin excepcion`() {
        setContent(payments(PRODUCTION_DAY_PAYMENTS), expanded = true)

        assertPaintedRows(PRODUCTION_DAY_PAYMENTS)
    }

    @Test
    fun `colapsada esconde el ultimo pago del dia`() {
        val rows = MockupFixtures.manyPaymentsDia(PRODUCTION_DAY_PAYMENTS)

        setContent(DetailUi.Payments(rows), expanded = false)

        assertFolioCount(rows.first().folio, expected = 1)
        assertFolioCount(rows.last().folio, expected = 0)
    }

    @Test
    fun `expandida revela el ultimo pago del dia`() {
        val rows = MockupFixtures.manyPaymentsDia(PRODUCTION_DAY_PAYMENTS)

        setContent(DetailUi.Payments(rows), expanded = true)

        assertFolioCount(rows.first().folio, expected = 1)
        assertFolioCount(rows.last().folio, expected = 1)
    }

    // ------------------------------------------------------------------- frontera del umbral

    @Test
    fun `con exactamente el umbral de filas NO hay control`() {
        setContent(payments(EXPECTED_COLLAPSED_ROWS), expanded = false)

        assertPaintedRows(EXPECTED_COLLAPSED_ROWS)
        assertNoToggle()
    }

    @Test
    fun `con una fila mas que el umbral SI hay control`() {
        setContent(payments(EXPECTED_COLLAPSED_ROWS + 1), expanded = false)

        assertPaintedRows(EXPECTED_COLLAPSED_ROWS)
        composeTestRule.onNodeWithText(expandLabel(EXPECTED_COLLAPSED_ROWS + 1)).assertIsDisplayed()
    }

    @Test
    fun `el control lleva el conteo REAL de pagos, no el remanente`() {
        setContent(payments(PRODUCTION_DAY_PAYMENTS), expanded = false)

        // "Ver los 57 pagos" — NO "52 pagos más": el control promete la lista completa.
        composeTestRule.onNodeWithText("Ver los $PRODUCTION_DAY_PAYMENTS pagos").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("pagos más", substring = true).assertCountEquals(0)
    }

    @Test
    fun `expandida el control invita a colapsar`() {
        // Lista CORTA a propósito: con 57 pagos expandidos el control cae fuera del canvas de
        // 800dp del test (en la pantalla real lo alcanza el `verticalScroll`) y `assertIsDisplayed`
        // fallaría por geometría del harness, no por el componente.
        setContent(payments(SHORT_OVERFLOW_PAYMENTS), expanded = true)

        composeTestRule.onNodeWithText("Ver menos").assertIsDisplayed()
        composeTestRule
            .onAllNodesWithText(expandLabel(SHORT_OVERFLOW_PAYMENTS))
            .assertCountEquals(0)
    }

    @Test
    fun `el control expone contentDescription con la misma etiqueta visible`() {
        setContent(payments(PRODUCTION_DAY_PAYMENTS), expanded = false)

        composeTestRule
            .onNodeWithContentDescription(expandLabel(PRODUCTION_DAY_PAYMENTS))
            .assertIsDisplayed()
    }

    // ------------------------------------------------------------------------- lista vacía

    @Test
    fun `lista vacia sigue mostrando el estado vacio y ningun control`() {
        setContent(DetailUi.Payments(emptyList()), expanded = false)

        composeTestRule.onNodeWithText("Sin datos aún").assertIsDisplayed()
        assertPaintedRows(0)
        assertNoToggle()
    }

    @Test
    fun `lista vacia expandida tampoco inventa un control`() {
        setContent(DetailUi.Payments(emptyList()), expanded = true)

        composeTestRule.onNodeWithText("Sin datos aún").assertIsDisplayed()
        assertNoToggle()
    }

    // --------------------------------------------------------------- Semana no cambia de piel

    @Test
    fun `DetailUi Days pinta TODOS los dias y nunca un control, aunque pase el umbral`() {
        val dias = (1..DIAS_FUERA_DE_RANGO).map { i ->
            DayRowUi(
                label = "día $i",
                amount = Money.of(BigDecimal("1000")),
                count = i,
                initials = "D$i",
                isToday = false
            )
        }

        setContent(DetailUi.Days(dias), expanded = false)

        dias.forEach { dia -> composeTestRule.onNodeWithText(dia.label).assertIsDisplayed() }
        assertNoToggle()
    }

    @Test
    fun `DetailUi Days del ciclo real se sigue pintando completo`() {
        val dias = MockupFixtures.daysSemana()

        setContent(DetailUi.Days(dias), expanded = false)

        dias.forEach { dia -> composeTestRule.onNodeWithText(dia.label).assertIsDisplayed() }
        assertNoToggle()
    }

    // ------------------------------------------------------------------------ estado izado

    @Test
    fun `tocar el control invoca el callback una sola vez`() {
        var toques = 0
        setContent(
            detail = payments(PRODUCTION_DAY_PAYMENTS),
            expanded = false,
            onToggleExpand = { toques++ }
        )

        composeTestRule.onNodeWithText(expandLabel(PRODUCTION_DAY_PAYMENTS)).performClick()

        assertEquals(1, toques)
    }

    @Test
    fun `el estado es izado, no interno - sin el padre la lista NO se expande sola`() {
        // El callback se traga el evento a propósito. Si [DetailList] escondiera su propio
        // `remember`, la lista se abriría igual y este test lo delataría.
        setContent(payments(PRODUCTION_DAY_PAYMENTS), expanded = false, onToggleExpand = {})

        composeTestRule.onNodeWithText(expandLabel(PRODUCTION_DAY_PAYMENTS)).performClick()

        assertPaintedRows(EXPECTED_COLLAPSED_ROWS)
        composeTestRule.onNodeWithText(expandLabel(PRODUCTION_DAY_PAYMENTS)).assertIsDisplayed()
    }

    @Test
    fun `con el estado izado cableado, el control abre y cierra la lista completa`() {
        // Ida y vuelta con lista corta: el control queda dentro del canvas también expandido,
        // así que el segundo `performClick` toca de verdad (ver nota en el test de la etiqueta).
        setHoistedContent(payments(SHORT_OVERFLOW_PAYMENTS))

        composeTestRule.onNodeWithText(expandLabel(SHORT_OVERFLOW_PAYMENTS)).performClick()
        assertPaintedRows(SHORT_OVERFLOW_PAYMENTS)

        composeTestRule.onNodeWithText("Ver menos").performClick()
        assertPaintedRows(EXPECTED_COLLAPSED_ROWS)
    }

    // -------------------------------------------------------------------------- reduce-motion

    @Test
    fun `con reduce-motion colapsar es instantaneo - la cola se desmonta en el acto`() {
        val expanded = mutableStateOf(true)
        setHoistedContent(payments(PRODUCTION_DAY_PAYMENTS), state = expanded, reduceMotion = true)
        assertPaintedRows(PRODUCTION_DAY_PAYMENTS)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { expanded.value = false }
        advanceFrames(FRAMES_TO_SETTLE_STRUCTURE)

        // Sin animación de salida no queda NADA de la cola en el árbol, ni por un frame.
        assertPaintedRows(EXPECTED_COLLAPSED_ROWS)
    }

    @Test
    fun `sin reduce-motion colapsar SI anima - la cola sigue montada mientras encoge`() {
        val expanded = mutableStateOf(true)
        setHoistedContent(payments(PRODUCTION_DAY_PAYMENTS), state = expanded, reduceMotion = false)
        assertPaintedRows(PRODUCTION_DAY_PAYMENTS)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { expanded.value = false }
        advanceFrames(FRAMES_TO_SETTLE_STRUCTURE)

        // Contraste con el test de arriba: aquí la cola sobrevive esos frames porque el
        // `shrinkVertically` va a la mitad — es lo que prueba que reduce-motion NO es cosmético.
        assertPaintedRows(PRODUCTION_DAY_PAYMENTS)

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        assertPaintedRows(EXPECTED_COLLAPSED_ROWS)
    }

    /**
     * En la EXPANSIÓN la cola se monta en el mismo frame con o sin animación (el
     * `expandVertically` solo recorta lo ya compuesto), así que contar filas no distingue nada.
     * Lo que sí distingue es la GEOMETRÍA: el control va debajo de la cola, o sea que su posición
     * es el alto real de la lista. Con reduce-motion esa posición ya es la definitiva al tercer
     * frame; con animación todavía va trepada. Se compara contra la posición asentada del MISMO
     * render, sin números mágicos de píxeles.
     */
    @Test
    fun `con reduce-motion expandir es instantaneo - el control ya esta en su sitio final`() {
        val expanded = mutableStateOf(false)
        setHoistedContent(payments(PRODUCTION_DAY_PAYMENTS), state = expanded, reduceMotion = true)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { expanded.value = true }
        advanceFrames(FRAMES_TO_SETTLE_STRUCTURE)
        val enTresFrames = toggleTop()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertPaintedRows(PRODUCTION_DAY_PAYMENTS)
        assertEquals(toggleTop(), enTresFrames)
    }

    @Test
    fun `sin reduce-motion expandir SI anima - el control todavia va subiendo`() {
        val expanded = mutableStateOf(false)
        setHoistedContent(payments(PRODUCTION_DAY_PAYMENTS), state = expanded, reduceMotion = false)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { expanded.value = true }
        advanceFrames(FRAMES_TO_SETTLE_STRUCTURE)
        val enTresFrames = toggleTop()

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertPaintedRows(PRODUCTION_DAY_PAYMENTS)
        assertTrue(
            "el control debería seguir subiendo a mitad del expand ($enTresFrames vs ${toggleTop()})",
            enTresFrames < toggleTop()
        )
    }

    // ------------------------------------------------------------------------- fontScale 2.0

    @Test
    fun `la etiqueta del control no se trunca a fontScale 2`() {
        setContent(
            detail = payments(PRODUCTION_DAY_PAYMENTS),
            expanded = false,
            fontScale = LARGE_FONT_SCALE
        )

        val layouts = mutableListOf<TextLayoutResult>()
        // `useUnmergedTree = true`: el control es `clickable` y fusiona su Text con el chevron;
        // sin desmergear, `GetTextLayoutResult` resolvería sobre el nodo compuesto (misma trampa
        // documentada en `MoneyNoTruncationTest`).
        composeTestRule
            .onNode(hasText(expandLabel(PRODUCTION_DAY_PAYMENTS)), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }

        assertTrue("no se obtuvo TextLayoutResult del control", layouts.isNotEmpty())
        val layout = layouts.first()
        (0 until layout.lineCount).forEach { line ->
            assertFalse(
                "la etiqueta del colapsable se truncó en la línea $line a fontScale 2.0",
                layout.isLineEllipsized(line)
            )
        }
    }

    @Test
    fun `el control sigue siendo tocable a fontScale 2`() {
        var toques = 0
        setContent(
            detail = payments(PRODUCTION_DAY_PAYMENTS),
            expanded = false,
            fontScale = LARGE_FONT_SCALE,
            onToggleExpand = { toques++ }
        )

        composeTestRule.onNodeWithText(expandLabel(PRODUCTION_DAY_PAYMENTS)).performClick()

        assertEquals(1, toques)
    }

    // ------------------------------------------------------------------------------- helpers

    private fun payments(count: Int): DetailUi.Payments =
        DetailUi.Payments(MockupFixtures.manyPaymentsDia(count))

    private fun expandLabel(total: Int): String = "Ver los $total pagos"

    /** Render de una sola pasada: [expanded] fijo, el toggle solo informa. */
    private fun setContent(
        detail: DetailUi,
        expanded: Boolean,
        fontScale: Float = 1f,
        onToggleExpand: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            Harness(fontScale = fontScale, reduceMotion = false) {
                DetailList(
                    detail = detail,
                    masked = false,
                    onPaymentClick = {},
                    onDayClick = {},
                    expanded = expanded,
                    onToggleExpand = onToggleExpand
                )
            }
        }
    }

    /** Render con el estado izado REALMENTE cableado, como lo hace la pantalla. */
    private fun setHoistedContent(
        detail: DetailUi,
        state: MutableState<Boolean> = mutableStateOf(false),
        reduceMotion: Boolean = false
    ) {
        composeTestRule.setContent {
            Harness(fontScale = 1f, reduceMotion = reduceMotion) {
                DetailList(
                    detail = detail,
                    masked = false,
                    onPaymentClick = {},
                    onDayClick = {},
                    expanded = state.value,
                    onToggleExpand = { state.value = !state.value }
                )
            }
        }
    }

    @Composable
    private fun Harness(fontScale: Float, reduceMotion: Boolean, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale),
            LocalReduceMotion provides reduceMotion
        ) {
            MspTheme(animateColors = false) { content() }
        }
    }

    /**
     * Avanza [frames] frames con el reloj congelado.
     *
     * El `waitForIdle()` de la primera línea NO es adorno (trampa medida, no supuesta): con
     * `autoAdvance = false`, el cambio de estado hecho desde `runOnUiThread` no llega a la
     * composición hasta que algo drena la cola de snapshots. Sin él, los frames se consumen
     * ANTES de que Compose se entere del cambio y la aserción mide el árbol viejo — falso
     * negativo que se ve exactamente igual que "la animación no corrió".
     */
    private fun advanceFrames(frames: Int) {
        composeTestRule.waitForIdle()
        repeat(frames) { composeTestRule.mainClock.advanceTimeByFrame() }
    }

    /**
     * Borde superior del control de expansión = alto real de la lista pintada, porque el control
     * va SIEMPRE debajo de la cola. Sirve de sonda de geometría para distinguir "ya llegó" de
     * "va a medio camino" sin comparar píxeles contra constantes.
     */
    private fun toggleTop() = composeTestRule
        .onNodeWithText("Ver menos")
        .getUnclippedBoundsInRoot()
        .top

    /** Cuenta filas de pago pintadas — ver el KDoc de la clase para el porqué de "Saldo". */
    private fun assertPaintedRows(expected: Int) {
        composeTestRule.onAllNodesWithText("Saldo").assertCountEquals(expected)
    }

    private fun assertFolioCount(folio: String, expected: Int) {
        composeTestRule
            .onAllNodesWithText("Folio $folio", substring = true)
            .assertCountEquals(expected)
    }

    /** Ningún control de expansión, en ninguna de sus dos etiquetas. */
    private fun assertNoToggle() {
        composeTestRule.onAllNodesWithText("Ver los", substring = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Ver menos").assertCountEquals(0)
    }

    private companion object {
        /** Más días que [EXPECTED_COLLAPSED_ROWS] — prueba que el umbral NO aplica a Semana. */
        const val DIAS_FUERA_DE_RANGO = 12

        /**
         * Lista con overflow pero corta: expandida cabe entera en el canvas de 800dp del test,
         * así que el control sigue siendo tocable/visible sin necesitar scroll del harness.
         */
        const val SHORT_OVERFLOW_PAYMENTS = 8
    }
}
