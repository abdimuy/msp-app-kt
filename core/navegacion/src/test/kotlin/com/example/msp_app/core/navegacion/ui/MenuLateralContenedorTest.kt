package com.example.msp_app.core.navegacion.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MenuLateralContenedorTest : RobolectricTestBase() {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var abiertos = 0
    private var aperturaVista: AperturaDelMenu? = null

    private fun montar(reducido: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reducido) {
                MenuLateralContenedor(
                    hoja = { cerrar, apertura ->
                        aperturaVista = apertura
                        Button(
                            onClick = cerrar,
                            modifier = Modifier.testTag("cerrar")
                        ) { Text("Cerrar") }
                    },
                    onAbierto = { abiertos += 1 }
                ) { abrir ->
                    Button(onClick = abrir, modifier = Modifier.testTag("abrir")) { Text("Abrir") }
                }
            }
        }
    }

    private fun abrir() {
        compose.onNodeWithTag("abrir").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `cerrado no se ve y abrir lo muestra y avisa una vez`() {
        montar()
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
        abrir()
        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsDisplayed()
        assertEquals(1, abiertos)
    }

    @Test
    fun `atras con el menu abierto lo cierra y no deja la pantalla`() {
        montar()
        abrir()

        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()

        compose.onNodeWithTag(MENU_LATERAL_TAG).assertIsNotDisplayed()
        assertFalse(
            "atrás salió de la pantalla en vez de cerrar el menú",
            compose.activity.isFinishing
        )
    }

    @Test
    fun `cerrar el menu limpia la busqueda y las secciones tocadas`() {
        montar()
        abrir()
        compose.runOnUiThread {
            aperturaVista!!.consulta = "garantias"
            aperturaVista!!.tocar("COBRO")
        }

        compose.onNodeWithTag("cerrar").performClick()
        compose.waitForIdle()

        assertEquals("", aperturaVista!!.consulta)
        assertTrue(aperturaVista!!.tocadas.isEmpty())
    }

    @Test
    fun `con movimiento reducido abre sin animacion`() {
        montar(reducido = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("abrir").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertEquals(0f, compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value, 0.5f)
    }

    @Test
    fun `control positivo, sin movimiento reducido la entrada se anima`() {
        montar(reducido = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("abrir").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()

        assertTrue(compose.onNodeWithTag(MENU_LATERAL_TAG).getBoundsInRoot().left.value < 0f)
    }
}
