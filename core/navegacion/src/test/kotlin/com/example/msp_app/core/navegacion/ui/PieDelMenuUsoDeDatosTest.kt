package com.example.msp_app.core.navegacion.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.navegacion.ui.components.PieDelMenu
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** E-APP-175: la entrada "Uso de datos" vive en el pie, junto a Configuración, y es opcional. */
class PieDelMenuUsoDeDatosTest : RobolectricTestBase() {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `sin callback no hay fila de uso de datos`() {
        compose.setContent {
            MspTheme(animateColors = false) { PieDelMenu("Versión 1", onConfiguracion = {}) }
        }
        compose.onNodeWithTag(FILA_DEL_MENU_TAG + ID_USO_DE_DATOS).assertDoesNotExist()
    }

    @Test
    fun `con callback la fila existe mide el toque minimo y avisa al tocarla`() {
        var toques = 0
        compose.setContent {
            MspTheme(animateColors = false) {
                PieDelMenu("Versión 1", onConfiguracion = {}, onUsoDeDatos = { toques++ })
            }
        }
        compose.onNodeWithText("Uso de datos").assertExists()
        compose.onNodeWithTag(
            FILA_DEL_MENU_TAG + ID_USO_DE_DATOS
        ).assertHeightIsAtLeast(50.dp).performClick()
        assertEquals(1, toques)
    }
}
