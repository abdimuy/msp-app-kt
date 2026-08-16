package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.ui.CollectionReportContent
import com.example.msp_app.feature.collectionreport.ui.CollectionReportUiState
import com.example.msp_app.feature.collectionreport.ui.DayRowUi
import com.example.msp_app.feature.collectionreport.ui.DetailUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Días de ciclo del caso que originó el encargo: una `FECHA_CARGA_INICIAL` de hace seis meses.
 * `RangeCalculator.cycleDays` enumera un día por cada día transcurrido, sin tope, y el resumen
 * por día de Semana pinta un renglón por cada uno.
 */
private const val CICLO_LARGO_DIAS = 182

/**
 * Techo de renglones compuestos con el ciclo largo, ya sea en reposo o después de recorrer la
 * lista entera.
 *
 * MEDIDO con [CICLO_LARGO_DIAS] días al final del recorrido: **12** renglones compuestos. El
 * techo se deja en 40 — holgado para que un cambio de altura de renglón o de colchón de la
 * lista no lo rompa por nada, y aun así una fracción inequívoca de 182: si alguien vuelve a
 * pintar el resumen de corrido, el conteo salta a la lista entera y esta suite truena.
 */
private const val MAX_RENGLONES_COMPUESTOS = 40

/**
 * Prefijo propio de la etiqueta de las fixtures de este test. No se parece a nada más del
 * tablero ("N pagos", "5 días", nombres de cliente), así que contar nodos que lo contengan ES
 * contar renglones de día compuestos — sobre el árbol de semántica MERGEADO cada renglón es
 * `clickable` y colapsa en UN nodo.
 */
private const val MARCA_DIA = "jornada-"

/**
 * Fija lo que de verdad importa del detalle de Semana: que sea PEREZOSO.
 *
 * El defecto medido en dispositivo (`Choreographer: Skipped 212 frames`, `HWUI: Davey!
 * duration=1747ms`, ~1.2 s de ellos en dibujado) venía de pintar de corrido un renglón por cada
 * día del ciclo. El KDoc de `DetailList` afirmaba que eran "5 filas por definición del ciclo";
 * no lo son y nada lo garantizaba. Estos tests son el candado de la corrección:
 *
 *  1. **En reposo no se compone NI UN renglón** con un ciclo largo — el resumen por día vive muy
 *     por debajo del pliegue. Con una `Column` de corrido se compondrían los 182.
 *  2. **Recorriendo la lista entera tampoco** se acumulan: la lista recicla y el conteo se queda
 *     por debajo de [MAX_RENGLONES_COMPUESTOS] incluso al final.
 *  3. **Perezoso no es inalcanzable**: el último día del ciclo se puede traer a pantalla y sigue
 *     respondiendo al toque con su índice.
 *  4. **La llave es la fecha, no la etiqueta**: dos días con etiqueta idéntica (un ciclo de más
 *     de un año repite "EEE d MMM") conviven sin reventar la lista.
 *
 * Se ejerce [CollectionReportContent] — la pantalla real — y no un arnés a la medida: la pereza
 * es una propiedad del contenedor, y probarla sobre un `LazyColumn` de mentiras no diría nada
 * del tablero.
 *
 * `@GraphicsMode(NATIVE)` + qualifiers fijos: mismo bring-up que el resto de los compose-tests
 * del módulo, para que el alto del lienzo (y por lo tanto cuántos renglones caben) sea el mismo
 * en cualquier máquina.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class DayDetailItemsTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `con un ciclo largo el tablero no compone ni un renglon de dia en reposo`() {
        setContent(cicloLargo())

        assertEquals(0, renglonesCompuestos())
    }

    @Test
    fun `recorrer el ciclo largo completo nunca acumula todos los renglones`() {
        val rows = cicloLargo()
        setContent(rows)

        scrollToDay(rows.last().label)

        val compuestos = renglonesCompuestos()
        assertTrue(
            "con $CICLO_LARGO_DIAS días compuestos=$compuestos; debería ser una fracción " +
                "(< $MAX_RENGLONES_COMPUESTOS), no la lista entera",
            compuestos in 1 until MAX_RENGLONES_COMPUESTOS
        )
        // Y el primero ya se recicló: la lista suelta lo que dejó atrás en vez de apilarlo.
        composeTestRule.onAllNodesWithText(rows.first().label).assertCountEquals(0)
    }

    @Test
    fun `el ultimo dia del ciclo largo se alcanza y responde al toque con su indice`() {
        val rows = cicloLargo()
        val clicked = mutableListOf<Int>()
        setContent(rows, onDayRowClick = { clicked += it })

        scrollToDay(rows.last().label)
        composeTestRule.onNodeWithText(rows.last().label).assertIsDisplayed()
        composeTestRule.onNodeWithText(rows.last().label).performClick()

        assertEquals(listOf(CICLO_LARGO_DIAS - 1), clicked)
    }

    @Test
    fun `dos dias con la MISMA etiqueta conviven - la llave es la fecha`() {
        // Un ciclo de más de un año repite "EEE d MMM": mismo día de la semana, mismo día del
        // mes, mismo mes. Con la etiqueta de llave, la lista revienta con "key was already
        // used"; con la fecha, no hay ambigüedad posible.
        val repetida = "${MARCA_DIA}lun 3 ago"
        val rows = listOf(
            dayRow(LocalDate.parse("2025-08-03"), repetida, index = 0),
            dayRow(LocalDate.parse("2026-08-03"), repetida, index = 1)
        )

        setContent(rows)
        scrollToDay(repetida)

        composeTestRule.onAllNodesWithText(repetida).assertCountEquals(2)
    }

    @Test
    fun `un ciclo sin dias muestra el mensaje vacio y no una tarjeta en blanco`() {
        setContent(emptyList())

        composeTestRule
            .onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Sin datos aún"))
        composeTestRule.onNodeWithText("Sin datos aún").assertIsDisplayed()
    }

    // ------------------------------------------------------------------------------- helpers

    private fun cicloLargo(): List<DayRowUi> = (0 until CICLO_LARGO_DIAS).map { i ->
        dayRow(CICLO_INICIO.plusDays(i.toLong()), "$MARCA_DIA%03d".format(i), index = i)
    }

    private fun dayRow(date: LocalDate, label: String, index: Int): DayRowUi = DayRowUi(
        date = date,
        label = label,
        amount = Money.of(BigDecimal("1000")),
        count = index,
        initials = "D$index",
        isToday = false
    )

    /** Renglones de día realmente compuestos — ver el KDoc de [MARCA_DIA]. */
    private fun renglonesCompuestos(): Int = composeTestRule
        .onAllNodesWithText(MARCA_DIA, substring = true)
        .fetchSemanticsNodes()
        .size

    private fun scrollToDay(label: String) {
        composeTestRule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
    }

    private fun setContent(rows: List<DayRowUi>, onDayRowClick: (Int) -> Unit = {}) {
        val state: CollectionReportUiState =
            MockupFixtures.stateSemana().copy(detail = DetailUi.Days(rows))
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
                    onDayRowClick = onDayRowClick
                )
            }
        }
    }

    private companion object {
        /** Seis meses antes del "hoy" del mockup (vie 7 ago 2026) — el caso real del encargo. */
        val CICLO_INICIO: LocalDate = LocalDate.parse("2026-02-07")
    }
}
