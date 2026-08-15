package com.example.msp_app.core.appgate.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val DEADLINE = "vie 22"
private const val LARGE_FONT_SCALE = 2.0f

/**
 * Frames que se dejan correr con el reloj congelado tras voltear el tono.
 * Tres bastan para que Compose recomponga y aplique el primer valor animado
 * del spring, y son ~48ms — margen amplio en ambos sentidos, sin depender del
 * reloj de pared (mismo criterio y misma trampa del `waitForIdle` que
 * documenta `DaySwapTest` en `:feature:collectionReport`).
 */
private const val FRAMES_TO_SETTLE = 3

/**
 * La banda de cuenta regresiva.
 *
 * **Aserción discriminante, con gemela.** Cada caso de reduce-motion tiene su
 * pareja animada que afirma lo CONTRARIO sobre el mismo estímulo: si el
 * cambio de tono estuviera cableado al revés (o no estuviera), uno de los dos
 * tests de cada par fallaría. Se lee el color frame a frame con el reloj
 * congelado en vez de capturar píxeles: `captureToImage` exige un
 * `forceRedraw` que no convive con `autoAdvance = false`.
 */
class UpdateCountdownBandTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ─── par 1: ¿pasa por colores intermedios? ────────────────────────────────

    @Test
    fun `con reduce-motion el tono salta - nunca hay un color intermedio`() {
        val ready = mutableStateOf(false)
        val vistos = recordContainerColor(ready, reduceMotion = true)

        voltearTono(ready)

        assertTrue("no se registró ningún color", vistos.isNotEmpty())
        assertTrue(
            "con reduce-motion solo pueden verse los dos colores finales: $vistos",
            vistos.all { it in extremos(vistos) }
        )
        assertEquals(
            "solo el ámbar de partida y el verde de llegada",
            2,
            vistos.toSet().size
        )
    }

    @Test
    fun `sin reduce-motion el tono SI transiciona - aparece al menos un color intermedio`() {
        val ready = mutableStateOf(false)
        val vistos = recordContainerColor(ready, reduceMotion = false)

        voltearTono(ready)

        // Contraste exacto con el test de arriba, mismo estímulo: aquí SÍ hay
        // colores que no son ni el de partida ni el de llegada.
        assertTrue(
            "sin reduce-motion el tono debería pasar por valores intermedios: $vistos",
            vistos.toSet().size > 2
        )
    }

    // ─── par 2: ¿el primer frame ya es el color final? ────────────────────────

    @Test
    fun `con reduce-motion el primer frame tras voltear ya es el color final`() {
        val ready = mutableStateOf(false)
        val vistos = recordContainerColor(ready, reduceMotion = true)
        val inicial = vistos.first()

        voltearTono(ready)

        val primeroTrasVoltear = vistos.first { it != inicial }
        assertEquals(
            "el color justo después de voltear debe ser ya el definitivo",
            vistos.last(),
            primeroTrasVoltear
        )
    }

    @Test
    fun `sin reduce-motion el primer frame tras voltear NO es todavia el color final`() {
        val ready = mutableStateOf(false)
        val vistos = recordContainerColor(ready, reduceMotion = false)
        val inicial = vistos.first()

        voltearTono(ready)

        val primeroTrasVoltear = vistos.first { it != inicial }
        assertFalse(
            "el spring no puede llegar al destino en el primer frame: $vistos",
            primeroTrasVoltear == vistos.last()
        )
    }

    // ─── contenido ────────────────────────────────────────────────────────────

    @Test
    fun `sin el archivo la banda pide, con la fecha limite al frente`() {
        setContent(ready = false)

        composeTestRule.onNodeWithText("Actualiza antes del $DEADLINE").assertIsDisplayed()
        composeTestRule.onNodeWithTag(UPDATE_COUNTDOWN_ACTION_TAG).assertIsDisplayed()
    }

    @Test
    fun `con el archivo listo la banda ofrece en vez de exigir`() {
        setContent(ready = true)

        composeTestRule.onNodeWithText("Listo para instalar").assertIsDisplayed()
        composeTestRule.onNodeWithText("Instalar").assertIsDisplayed()
    }

    @Test
    fun `la accion informa al caller`() {
        var toques = 0
        setContent(ready = true, onAction = { toques++ })

        composeTestRule.onNodeWithTag(UPDATE_COUNTDOWN_ACTION_TAG).performClick()

        assertEquals(1, toques)
    }

    // ─── accesibilidad ────────────────────────────────────────────────────────

    @Test
    fun `a fontScale 2 la banda sigue pintando y su accion sigue siendo tocable`() {
        var toques = 0
        setContent(ready = false, fontScale = LARGE_FONT_SCALE, onAction = { toques++ })

        composeTestRule.onNodeWithTag(UPDATE_COUNTDOWN_BAND_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(UPDATE_COUNTDOWN_ACTION_TAG).performClick()

        assertEquals(1, toques)
    }

    @Test
    fun `a fontScale 2 la fecha limite no se trunca`() {
        setContent(ready = false, fontScale = LARGE_FONT_SCALE)

        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule
            .onNode(hasText("Actualiza antes del $DEADLINE"), useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }

        assertTrue("no se obtuvo TextLayoutResult", layouts.isNotEmpty())
        val layout = layouts.first()
        (0 until layout.lineCount).forEach { line ->
            assertFalse(
                "la fecha límite se truncó en la línea $line a fontScale 2.0",
                layout.isLineEllipsized(line)
            )
        }
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun setContent(ready: Boolean, fontScale: Float = 1f, onAction: () -> Unit = {}) {
        composeTestRule.setContent {
            Harness(fontScale = fontScale, reduceMotion = false) {
                UpdateCountdownBand(deadlineLabel = DEADLINE, ready = ready, onAction = onAction)
            }
        }
    }

    /** Monta [rememberBandContainerColor] y guarda cada color que compone. */
    private fun recordContainerColor(
        ready: MutableState<Boolean>,
        reduceMotion: Boolean
    ): List<Color> {
        val vistos = mutableListOf<Color>()
        composeTestRule.setContent {
            Harness(fontScale = 1f, reduceMotion = reduceMotion) {
                vistos += rememberBandContainerColor(ready.value)
            }
        }
        composeTestRule.waitForIdle()
        return vistos
    }

    /**
     * Voltea `ready` con el reloj CONGELADO y deja correr [FRAMES_TO_SETTLE]
     * frames, luego suelta el reloj para que el spring asiente.
     *
     * El `waitForIdle()` de la primera línea NO es adorno: con
     * `autoAdvance = false`, el cambio hecho desde `runOnUiThread` no llega a
     * la composición hasta que algo drena la cola de snapshots.
     */
    private fun voltearTono(ready: MutableState<Boolean>) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { ready.value = true }
        composeTestRule.waitForIdle()
        repeat(FRAMES_TO_SETTLE) { composeTestRule.mainClock.advanceTimeByFrame() }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
    }

    /** Primer y último color registrados: el de partida y el de llegada. */
    private fun extremos(vistos: List<Color>): Set<Color> = setOf(vistos.first(), vistos.last())

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
}
