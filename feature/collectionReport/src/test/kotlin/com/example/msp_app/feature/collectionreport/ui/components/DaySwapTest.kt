package com.example.msp_app.feature.collectionreport.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Frames que se dejan correr con el reloj congelado tras cambiar de día. Tres bastan para que
 * Compose recomponga y aplique el primer valor animado, y son ~48ms de los 300ms de la subida de
 * opacidad — margen amplio en ambos sentidos, sin depender de reloj de pared (mismo criterio y
 * misma trampa del `waitForIdle` que documenta `DetailListTest.advanceFrames`).
 */
private const val FRAMES_TO_SETTLE = 3

private const val OPAQUE = 1f

/**
 * Compose-test de [DaySwap] / [rememberDaySwapAlpha], la transición del contenido al cambiar el
 * día mostrado.
 *
 * **Aserción discriminante, con gemelo.** Cada caso de reduce-motion tiene su pareja animada que
 * afirma lo CONTRARIO sobre el mismo estímulo: si la transición estuviera cableada al revés (o
 * no estuviera), uno de los dos tests de cada par fallaría. Se lee la opacidad frame a frame con
 * el reloj congelado en vez de capturar píxeles: `captureToImage` exige un `forceRedraw` que no
 * convive con `autoAdvance = false` (medido — cuelga 2s y truena).
 */
class DaySwapTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val lunes = LocalDate.of(2026, 8, 10)
    private val domingo = LocalDate.of(2026, 8, 9)

    @Test
    fun `con reduce-motion el cambio de dia es instantaneo - la opacidad nunca baja`() {
        val day = mutableStateOf<LocalDate?>(lunes)
        val vistas = recordAlpha(day, reduceMotion = true)

        cambiarDia(day)

        assertTrue("no se registró ninguna opacidad", vistas.isNotEmpty())
        assertEquals(
            "con reduce-motion el contenido no debe atenuarse en ningún frame: $vistas",
            setOf(OPAQUE),
            vistas.toSet()
        )
    }

    @Test
    fun `sin reduce-motion el cambio de dia SI transiciona - la opacidad baja y vuelve a subir`() {
        val day = mutableStateOf<LocalDate?>(lunes)
        val vistas = recordAlpha(day, reduceMotion = false)

        cambiarDia(day)

        // Contraste exacto con el test de arriba, mismo estímulo: aquí SÍ hay atenuación.
        assertTrue(
            "sin reduce-motion el contenido debería atenuarse al cambiar de día: $vistas",
            vistas.any { it < OPAQUE }
        )

        // Y asienta sola: la transición termina en opacidad plena, no se queda a medias.
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        assertEquals(OPAQUE, vistas.last(), 0f)
    }

    @Test
    fun `la primera composicion no transiciona - entrar a la pantalla no atenua nada`() {
        val day = mutableStateOf<LocalDate?>(lunes)
        val vistas = recordAlpha(day, reduceMotion = false)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.waitForIdle()
        repeat(FRAMES_TO_SETTLE) { composeTestRule.mainClock.advanceTimeByFrame() }

        assertEquals(
            "montar la pantalla no debe disparar la transición de día: $vistas",
            setOf(OPAQUE),
            vistas.toSet()
        )
    }

    @Test
    fun `sin dia (Semana) no hay transicion que montar`() {
        val day = mutableStateOf<LocalDate?>(null)
        val vistas = recordAlpha(day, reduceMotion = false)

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { day.value = domingo }
        composeTestRule.waitForIdle()
        repeat(FRAMES_TO_SETTLE) { composeTestRule.mainClock.advanceTimeByFrame() }

        // Pasar de "sin día" (Semana) a un día es ENTRAR a Día, no cambiar de día dentro de Día:
        // el `DaySwap` recién se monta, y montar no transiciona.
        assertEquals(setOf(OPAQUE), vistas.toSet())
    }

    /**
     * El contenido se pinta igual en las dos ramas — la transición nunca puede "perder" el
     * tablero. Se ejerce el composable REAL ([DaySwap]), no solo el valor de opacidad.
     */
    @Test
    fun `DaySwap pinta su contenido con reduce-motion`() {
        assertTrue("DaySwap no pintó su contenido", pintaContenido(reduceMotion = true))
    }

    @Test
    fun `DaySwap pinta su contenido sin reduce-motion`() {
        assertTrue("DaySwap no pintó su contenido", pintaContenido(reduceMotion = false))
    }

    /** `setContent` solo admite una llamada por test — de ahí un test por rama. */
    private fun pintaContenido(reduceMotion: Boolean): Boolean {
        var pintado = false
        composeTestRule.setContent {
            Harness(reduceMotion = reduceMotion) {
                DaySwap(day = lunes) { pintado = true }
            }
        }
        composeTestRule.waitForIdle()
        return pintado
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────────

    /** Monta [rememberDaySwapAlpha] y va guardando cada opacidad que compone. */
    private fun recordAlpha(day: MutableState<LocalDate?>, reduceMotion: Boolean): List<Float> {
        val vistas = mutableListOf<Float>()
        composeTestRule.setContent {
            Harness(reduceMotion = reduceMotion) {
                vistas += rememberDaySwapAlpha(day.value)
            }
        }
        composeTestRule.waitForIdle()
        return vistas
    }

    /** Cambia el día con el reloj CONGELADO y deja correr [FRAMES_TO_SETTLE] frames. */
    private fun cambiarDia(day: MutableState<LocalDate?>) {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.runOnUiThread { day.value = domingo }
        composeTestRule.waitForIdle()
        repeat(FRAMES_TO_SETTLE) { composeTestRule.mainClock.advanceTimeByFrame() }
    }

    @Composable
    private fun Harness(reduceMotion: Boolean, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
            MspTheme(animateColors = false) { content() }
        }
    }
}
