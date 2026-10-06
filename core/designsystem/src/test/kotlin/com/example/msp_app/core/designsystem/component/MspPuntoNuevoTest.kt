package com.example.msp_app.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MspPuntoNuevo]: decorativo (no aparece en el
 * árbol de accesibilidad), gate de reduce-motion, y tamaño medido estable con
 * los halos encendidos o apagados. El golden visual vive en
 * `screenshot/MspPuntoNuevoScreenshotTest` (reposo) y
 * `screenshot/MspPulseRingFrameScreenshotTest` (frame intermedio).
 */
class MspPuntoNuevoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    /**
     * La venta ya la describe [MspInsigniaNueva] con el texto "Nueva" — el
     * punto no debe agregar ningún nodo con texto/descripción propios al
     * árbol que lee TalkBack. Se renderizan los dos juntos (el uso real en la
     * tarjeta de cliente) y se cuenta cuántos nodos con texto hay: debe ser
     * exactamente uno, el de la insignia.
     */
    @Test
    fun `el punto es decorativo y no agrega nodos al arbol de accesibilidad`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspInsigniaNueva()
                MspPuntoNuevo()
            }
        }

        val nodosConTexto = composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()

        assertEquals(
            "solo la insignia (\"Nueva\") debe exponer texto; el punto no agrega nada",
            1,
            nodosConTexto.size
        )
    }

    @Test
    fun `con reduce-motion activo, los halos no se componen`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MspTheme(animateColors = false) { MspPuntoNuevo() }
            }
        }

        composeTestRule
            .onNodeWithTag(MSP_PUNTO_NUEVO_HALO_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `sin reduce-motion, los halos si se componen`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides false) {
                MspTheme(animateColors = false) { MspPuntoNuevo() }
            }
        }

        composeTestRule
            .onNodeWithTag(MSP_PUNTO_NUEVO_HALO_TAG, useUnmergedTree = true)
            .assertExists()
    }

    /**
     * Mismo requisito duro que [MspInsigniaNuevaTest]: los halos son
     * `drawBehind` puro, nunca un `Box` hijo más grande "para que quepan" — el
     * punto mide 10dp exactos, con o sin halos.
     */
    @Test
    fun `los halos no cambian el tamano medido del punto`() {
        val reduceMotion = mutableStateOf(true)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion.value) {
                MspTheme(animateColors = false) { MspPuntoNuevo() }
            }
        }
        val reposo = composeTestRule
            .onNodeWithTag(MSP_PUNTO_NUEVO_TAG, useUnmergedTree = true)
            .getBoundsInRoot()

        composeTestRule.runOnUiThread { reduceMotion.value = false }
        composeTestRule.waitForIdle()
        val animado = composeTestRule
            .onNodeWithTag(MSP_PUNTO_NUEVO_TAG, useUnmergedTree = true)
            .getBoundsInRoot()

        assertTrue("el punto no midió nada: ancho ${reposo.width}", reposo.width.value > 0f)
        assertEquals(reposo.width, animado.width)
        assertEquals(reposo.height, animado.height)
    }
}
