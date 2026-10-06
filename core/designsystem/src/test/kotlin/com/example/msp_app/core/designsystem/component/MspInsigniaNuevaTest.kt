package com.example.msp_app.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
 * Compose-test (no golden) de [MspInsigniaNueva]: el texto "Nueva" llega a
 * TalkBack, el anillo respeta el gate de reduce-motion (mismo mecanismo de
 * [PaymentSyncPillTest]) y — el requisito duro de la tarea — el tamaño medido
 * NO cambia entre el pulso corriendo y movimiento reducido. El golden visual
 * vive en `screenshot/MspInsigniaNuevaScreenshotTest` (reposo) y
 * `screenshot/MspPulseRingFrameScreenshotTest` (frame intermedio).
 */
class MspInsigniaNuevaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el texto Nueva llega a TalkBack`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) { MspInsigniaNueva() }
        }

        composeTestRule.onNodeWithText("Nueva").assertIsDisplayed()
    }

    @Test
    fun `con reduce-motion activo, el anillo no se compone`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                MspTheme(animateColors = false) { MspInsigniaNueva() }
            }
        }

        composeTestRule
            .onNodeWithTag(MSP_INSIGNIA_NUEVA_RING_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `sin reduce-motion, el anillo si se compone`() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides false) {
                MspTheme(animateColors = false) { MspInsigniaNueva() }
            }
        }

        composeTestRule
            .onNodeWithTag(MSP_INSIGNIA_NUEVA_RING_TAG, useUnmergedTree = true)
            .assertExists()
    }

    /**
     * Requisito duro de la tarea: los anillos se dibujan con `drawBehind`, un
     * modificador de dibujo puro que nunca participa en medir/layout — así que
     * prenderlos o apagarlos (`LocalReduceMotion`) no puede mover ni un dp el
     * tamaño medido de la insignia. Si algún día un anillo se implementara con
     * un `Box` hijo de mayor tamaño "para que quepa", esta prueba se pondría
     * roja.
     */
    @Test
    fun `el anillo no cambia el tamano medido de la insignia`() {
        val reduceMotion = mutableStateOf(true)
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion.value) {
                MspTheme(animateColors = false) { MspInsigniaNueva() }
            }
        }
        val reposo = composeTestRule.onNodeWithTag(MSP_INSIGNIA_NUEVA_TAG).getBoundsInRoot()

        composeTestRule.runOnUiThread { reduceMotion.value = false }
        composeTestRule.waitForIdle()
        val animada = composeTestRule.onNodeWithTag(MSP_INSIGNIA_NUEVA_TAG).getBoundsInRoot()

        assertTrue("la insignia no midió nada: ancho ${reposo.width}", reposo.width.value > 0f)
        assertEquals(reposo.width, animada.width)
        assertEquals(reposo.height, animada.height)
    }
}
