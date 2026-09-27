package com.example.msp_app.feature.ubicacion.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar
import com.example.msp_app.feature.ubicacion.screenshot.MapaDelDuenoFixture
import com.example.msp_app.feature.ubicacion.ui.components.AlturaDelDetalle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Los gestos sobre la hoja no atraviesan al mapa.**
 *
 * El dueño, en el aparato: deslizar sobre el fondo vacío de la hoja movía el
 * mapa de atrás. Aquí el mapa es un `Box` con contador montado DEBAJO de la
 * pantalla (con `pintarMapa = false` no hay `GoogleMap`), igual que el
 * `GoogleMap` real queda debajo de la hoja.
 *
 * Control positivo: el mismo deslizamiento sobre el mapa (arriba de la hoja)
 * SÍ llega al contador.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaHojaSeQuedaConLosGestosTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var toquesAlMapa = 0

    @Test
    fun `deslizar sobre el fondo vacio de la hoja no llega al mapa`() {
        composeTestRule.setContent {
            Box(Modifier.fillMaxSize().testTag("raiz")) {
                Box(
                    Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            toquesAlMapa++
                        }
                    }
                )
                UbicacionScreen(
                    state = MapaDelDuenoFixture.estado(ClaseDeLugar.OTRO_LUGAR),
                    onAtras = {},
                    onComoLlegar = {},
                    onFiltro = {},
                    detalleInicial = AlturaDelDetalle.MINIMA,
                    pintarMapa = false
                )
            }
        }
        composeTestRule.waitForIdle()
        val hoja = composeTestRule.onNodeWithTag(HOJA_TAG).fetchSemanticsNode().boundsInRoot
        // El fondo vacío: el renglón más bajo de la hoja en la mínima, bajo el contenido.
        val y = hoja.bottom - 6f

        composeTestRule.onNodeWithTag("raiz").performTouchInput {
            swipe(Offset(hoja.left + 60f, y), Offset(hoja.right - 60f, y), 200L)
        }
        composeTestRule.waitForIdle()
        assertEquals("el deslizamiento sobre la hoja llegó al mapa", 0, toquesAlMapa)

        // Control positivo: sobre el mapa, arriba de la hoja, sí llega.
        val yMapa = hoja.top - 200f
        composeTestRule.onNodeWithTag("raiz").performTouchInput {
            swipe(Offset(hoja.left + 60f, yMapa), Offset(hoja.right - 60f, yMapa), 200L)
        }
        composeTestRule.waitForIdle()
        assertEquals("el control no llegó al mapa: la prueba no mide nada", 1, toquesAlMapa)
    }
}
