package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.designsystem.theme.mspDarkColors
import com.example.msp_app.core.designsystem.theme.mspLightColors
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.collectionreport.ui.DayChipUi
import com.example.msp_app.feature.collectionreport.ui.MockupFixtures
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val LARGE_FONT_SCALE = 2.0f

/**
 * Compose-test (no golden) de la tira de días del ciclo ([DayStrip]) y de su tabla de colores
 * ([dayChipPalette]).
 *
 * Los tres estados visuales y su combinación se verifican DOS veces y a propósito: por color
 * exacto sobre [dayChipPalette] (pura, en JVM — un golden solo dice "cambió", no "cambió a lo
 * correcto") y por `contentDescription` sobre el árbol real (lo que oye un lector de pantalla).
 *
 * `@GraphicsMode(NATIVE)` + qualifiers fijos: el caso `fontScale = 2.0` mide `TextLayoutResult`
 * real, mismo motivo que `MoneyNoTruncationTest`/`DetailListTest`.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [33],
    qualifiers = "w360dp-h800dp-xhdpi",
    application = android.app.Application::class
)
class DayStripTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val hoy = MockupFixtures.HOY_RUTA_34
    private val diaDeCarga = MockupFixtures.CICLO_RUTA_34.first()

    // ─── la tira lista los días del ciclo ───────────────────────────────────────────────

    @Test
    fun `pinta un chip por dia del ciclo, con dia de semana y numero`() {
        setContent(MockupFixtures.cicloRuta34())

        // 8 días: jue 6 … jue 13. Cada uno con su número de día del mes.
        MockupFixtures.CICLO_RUTA_34.forEach { day ->
            composeTestRule
                .onAllNodesWithText(day.dayOfMonth.toString(), useUnmergedTree = true)
                .assertCountEquals(1)
        }
        // Y el nombre corto del día EN MAYÚSCULAS (fidelidad al mockup): el ciclo cubre 8 días,
        // así que "JUE" aparece dos veces.
        composeTestRule.onAllNodesWithText("JUE", useUnmergedTree = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText("DOM", useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `una tira vacia no pinta nada`() {
        setContent(emptyList())

        composeTestRule.onAllNodesWithText("13", useUnmergedTree = true).assertCountEquals(0)
    }

    // ─── los tres estados visuales y su combinación ─────────────────────────────────────

    @Test
    fun `hoy sin seleccionar usa el verde del design system, no el azul de marca`() {
        val colors = mspLightColors()
        val palette = dayChipPalette(colors, chip(hoy, isToday = true, isSelected = false))

        assertEquals(colors.statusPaidTint, palette.background)
        assertEquals(colors.statusPaid, palette.content)
    }

    @Test
    fun `seleccionado sin ser hoy usa el azul de marca lleno`() {
        val colors = mspLightColors()
        val palette = dayChipPalette(colors, chip(hoy.minusDays(1), isSelected = true))

        assertEquals(colors.brand, palette.background)
        assertEquals(colors.onBrand, palette.content)
    }

    @Test
    fun `hoy Y seleccionado se funden en verde lleno`() {
        val colors = mspLightColors()
        val palette = dayChipPalette(colors, chip(hoy, isToday = true, isSelected = true))

        assertEquals(colors.statusPaid, palette.background)
        assertEquals(colors.surface, palette.content)
    }

    /**
     * Lo que de verdad importa del diseño: los tres estados NO pueden verse igual. "Hoy" y
     * "seleccionado" son cosas distintas desde que se pueden ver días pasados.
     */
    @Test
    fun `los tres estados se distinguen entre si en ambos temas`() {
        listOf(mspLightColors(), mspDarkColors()).forEach { colors ->
            val soloHoy = dayChipPalette(colors, chip(hoy, isToday = true))
            val soloSeleccionado = dayChipPalette(colors, chip(hoy.minusDays(1), isSelected = true))
            val ambos = dayChipPalette(colors, chip(hoy, isToday = true, isSelected = true))
            val normal = dayChipPalette(colors, chip(hoy.minusDays(2)))

            assertNotEquals(soloHoy.background, soloSeleccionado.background)
            assertNotEquals(soloHoy.background, ambos.background)
            assertNotEquals(soloSeleccionado.background, ambos.background)
            assertNotEquals(soloHoy.background, normal.background)
        }
    }

    @Test
    fun `un dia sin cobros se atenua pero conserva el fondo normal`() {
        val colors = mspLightColors()
        val vacio = dayChipPalette(colors, chip(hoy.minusDays(2), hasCollections = false))
        val conCobros = dayChipPalette(colors, chip(hoy.minusDays(2), hasCollections = true))

        assertEquals(colors.surface, vacio.background)
        assertEquals(colors.onSurfaceMuted, vacio.content)
        assertNotEquals(conCobros.content, vacio.content)
    }

    @Test
    fun `la seleccion manda sobre el atenuado - un dia en cero elegido se ve elegido`() {
        val colors = mspLightColors()
        val palette = dayChipPalette(
            colors,
            chip(diaDeCarga, isSelected = true, hasCollections = false)
        )

        assertEquals(colors.brand, palette.background)
    }

    // ─── accesibilidad: el estado va en TEXTO, no solo en color ─────────────────────────

    @Test
    fun `cada chip anuncia su dia y todos sus estados`() {
        setContent(MockupFixtures.cicloRuta34(seleccionado = LocalDate.of(2026, 8, 12)))

        // Hoy, sin seleccionar. Se desplaza primero: la tira es horizontalmente desplazable y con
        // el ciclo de 8 días el último chip nace fuera del encuadre cuando el seleccionado NO es
        // el último (ahí no corre el anclaje automático al final).
        composeTestRule
            .onNodeWithContentDescription("jueves 13 de agosto, hoy")
            .performScrollTo()
            .assertIsDisplayed()
        // Seleccionado, no hoy.
        composeTestRule
            .onNodeWithContentDescription("miércoles 12 de agosto, seleccionado")
            .performScrollTo()
            .assertIsDisplayed()
        // Día de la carga: en cero, y lo dice.
        composeTestRule
            .onNodeWithContentDescription("jueves 6 de agosto, sin cobros")
            .assertExists()
    }

    @Test
    fun `hoy y seleccionado a la vez se anuncian los dos`() {
        setContent(MockupFixtures.cicloRuta34())

        composeTestRule
            .onNodeWithContentDescription("jueves 13 de agosto, hoy, seleccionado")
            .assertIsDisplayed()
    }

    @Test
    fun `el chip elegido queda marcado como seleccionado en la semantica`() {
        setContent(MockupFixtures.cicloRuta34(seleccionado = LocalDate.of(2026, 8, 9)))

        composeTestRule
            .onAllNodesWithSelected(true)
            .assertCountEquals(1)
    }

    // ─── comportamiento ─────────────────────────────────────────────────────────────────

    @Test
    fun `tocar un chip informa el dia que se toco`() {
        val elegidos = mutableListOf<LocalDate>()
        setContent(MockupFixtures.cicloRuta34(), onSelect = { elegidos += it })

        composeTestRule
            .onNodeWithContentDescription("domingo 9 de agosto")
            .performClick()

        assertEquals(listOf(LocalDate.of(2026, 8, 9)), elegidos)
    }

    @Test
    fun `la tira es stateless - sin el padre, tocar no mueve la seleccion`() {
        setContent(MockupFixtures.cicloRuta34(), onSelect = {})

        composeTestRule.onNodeWithContentDescription("domingo 9 de agosto").performClick()

        // El chip de hoy sigue siendo el único seleccionado: el estado lo manda el ViewModel.
        composeTestRule
            .onNodeWithContentDescription("jueves 13 de agosto, hoy, seleccionado")
            .assertIsDisplayed()
    }

    // ─── día en cero: las dos líneas honestas ───────────────────────────────────────────

    @Test
    fun `un dia sin cobros lo dice, y el de la carga explica por que`() {
        setContent(
            days = MockupFixtures.cicloRuta34(seleccionado = diaDeCarga),
            emptyDay = true,
            note = MockupFixtures.NOTA_CARGA_RUTA_34
        )

        composeTestRule.onNodeWithText("Sin cobros").assertIsDisplayed()
        composeTestRule.onNode(hasText("inicio de semana", substring = true)).assertIsDisplayed()
        composeTestRule.onNode(hasText("7:33", substring = true)).assertIsDisplayed()
    }

    @Test
    fun `un dia con cobros no inventa el rotulo de vacio ni la nota`() {
        setContent(MockupFixtures.cicloRuta34(seleccionado = LocalDate.of(2026, 8, 9)))

        composeTestRule.onAllNodesWithText("Sin cobros").assertCountEquals(0)
        composeTestRule
            .onAllNodesWithText("inicio de semana", substring = true)
            .assertCountEquals(0)
    }

    // ─── fontScale 2.0 ──────────────────────────────────────────────────────────────────

    @Test
    fun `a fontScale 2 ningun chip trunca su texto`() {
        setContent(MockupFixtures.cicloRuta34(), fontScale = LARGE_FONT_SCALE)

        MockupFixtures.CICLO_RUTA_34.forEach { day ->
            assertNoEllipsis(day.dayOfMonth.toString())
        }
        assertNoEllipsis("DOM")
    }

    @Test
    fun `a fontScale 2 los chips siguen siendo tocables`() {
        val elegidos = mutableListOf<LocalDate>()
        setContent(
            days = MockupFixtures.cicloRuta34(),
            onSelect = { elegidos += it },
            fontScale = LARGE_FONT_SCALE
        )

        composeTestRule.onNodeWithContentDescription("sábado 8 de agosto").performClick()

        assertEquals(listOf(LocalDate.of(2026, 8, 8)), elegidos)
    }

    @Test
    fun `a fontScale 2 las dos lineas de contexto siguen legibles`() {
        setContent(
            days = MockupFixtures.cicloRuta34(seleccionado = diaDeCarga),
            emptyDay = true,
            note = MockupFixtures.NOTA_CARGA_RUTA_34,
            fontScale = LARGE_FONT_SCALE
        )

        assertNoEllipsis("Sin cobros")
        composeTestRule.onNode(hasText("inicio de semana", substring = true)).assertExists()
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────────

    private fun chip(
        date: LocalDate,
        isToday: Boolean = false,
        isSelected: Boolean = false,
        hasCollections: Boolean = true
    ) = DayChipUi(
        date = date,
        isToday = isToday,
        isSelected = isSelected,
        hasCollections = hasCollections
    )

    private fun setContent(
        days: List<DayChipUi>,
        onSelect: (LocalDate) -> Unit = {},
        emptyDay: Boolean = false,
        note: String = "",
        fontScale: Float = 1f
    ) {
        composeTestRule.setContent {
            Harness(fontScale = fontScale) {
                DayStrip(
                    days = days,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth(),
                    emptyDay = emptyDay,
                    note = note
                )
            }
        }
    }

    @Composable
    private fun Harness(fontScale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale),
            LocalReduceMotion provides false
        ) {
            MspTheme(animateColors = false) { content() }
        }
    }

    /**
     * `useUnmergedTree = true`: cada chip es `clickable` y fusiona sus dos `Text`; sin desmergear,
     * `GetTextLayoutResult` resolvería sobre el nodo compuesto — la misma trampa documentada en
     * `MoneyNoTruncationTest`.
     */
    private fun assertNoEllipsis(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNode(hasText(text), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }

        assertTrue("no se obtuvo TextLayoutResult de '$text'", layouts.isNotEmpty())
        val layout = layouts.first()
        (0 until layout.lineCount).forEach { line ->
            assertFalse(
                "'$text' se truncó en la línea $line a fontScale $LARGE_FONT_SCALE",
                layout.isLineEllipsized(line)
            )
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithSelected(
        selected: Boolean
    ) = onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Selected, selected))
}
