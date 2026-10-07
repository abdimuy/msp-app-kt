package com.example.msp_app.core.navegacion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Densidad mdpi de Robolectric: 1 px = 1 dp, y el inset de la barra de estado vale 0.
 *
 * **Medido (R12): el modo gráfico NO afecta esta prueba.** A diferencia de
 * `MenuLateralContenedorTest` (que sí necesita `GraphicsMode.Mode.NATIVE` porque
 * toca nodos DENTRO de un `ModalDrawerSheet` de forma asimétrica), esta prueba no
 * monta ningún cajón: [ZonaQueAbreElMenu] se monta sola, con un `Box` + `Button`
 * de prueba. Confirmado deslizando el mismo `@GraphicsMode(GraphicsMode.Mode.NATIVE)`
 * temporalmente sobre esta clase: los 5 casos quedan igual de verdes (ver
 * `task-16-report.md`). Se deja en LEGACY (el default), como pide el brief.
 */
class ZonaQueAbreElMenuTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    private var aperturas = 0
    private var toquesAtras = 0

    private fun montar(borde: Int = 0) {
        compose.setContent {
            Box(Modifier.size(360.dp, 640.dp)) {
                ZonaQueAbreElMenu(onAbrir = { aperturas += 1 }, bordeIzquierdoPx = borde) {
                    Box(Modifier.fillMaxSize().testTag("pantalla")) {
                        Button(
                            onClick = { toquesAtras += 1 },
                            modifier = Modifier.testTag("atras").size(56.dp)
                        ) {
                            Text("Atrás")
                        }
                    }
                }
            }
        }
    }

    private fun deslizar(desdeX: Float, y: Float) {
        compose.onNodeWithTag("pantalla").performTouchInput {
            swipe(start = Offset(desdeX, y), end = Offset(desdeX + 240f, y), durationMillis = 250)
        }
        compose.waitForIdle()
    }

    @Test
    fun `deslizar a la derecha en la franja superior abre el menu`() {
        montar()
        deslizar(desdeX = 80f, y = 30f)
        assertEquals(1, aperturas)
    }

    @Test
    fun `deslizar debajo de la franja no abre`() {
        montar()
        deslizar(desdeX = 80f, y = 300f)
        assertEquals(0, aperturas)
    }

    @Test
    fun `tocar la flecha dentro de la franja sigue funcionando`() {
        montar()
        compose.onNodeWithTag("atras").performClick()
        assertEquals(1, toquesAtras)
        assertEquals(0, aperturas)
    }

    @Test
    fun `un gesto que nace en el borde del sistema es del sistema`() {
        montar(borde = 100)
        deslizar(desdeX = 50f, y = 30f)
        assertEquals(0, aperturas)
    }

    @Test
    fun `control positivo, el mismo gesto fuera del borde si abre`() {
        montar(borde = 100)
        deslizar(desdeX = 150f, y = 30f)
        assertEquals(1, aperturas)
    }
}
