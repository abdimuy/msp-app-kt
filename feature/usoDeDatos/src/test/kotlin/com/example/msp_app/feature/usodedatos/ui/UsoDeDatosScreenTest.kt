package com.example.msp_app.feature.usodedatos.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.usodedatos.UsoDeDatosEjemplos
import com.example.msp_app.feature.usodedatos.UsoDeDatosUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** E-APP-175: estados de la pantalla "Uso de datos". */
class UsoDeDatosScreenTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pinta(estado: UsoDeDatosUiState, onBack: () -> Unit = {}) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) { UsoDeDatosContenido(estado, onBack) }
        }
    }

    @Test fun `vacio dice que todavia no hay datos y no pinta periodos`() {
        pinta(UsoDeDatosUiState(cargando = false))
        composeTestRule.onNodeWithTag(USO_DE_DATOS_VACIO_TAG).assertExists()
        composeTestRule.onNodeWithText("Hoy").assertDoesNotExist()
    }

    @Test fun `con datos muestra hoy semana filas y servicios en MB con un decimal`() {
        pinta(UsoDeDatosEjemplos.conDatos)
        composeTestRule.onNodeWithText("Uso de datos").assertExists()
        composeTestRule.onNodeWithText("Hoy").assertExists()
        composeTestRule.onNodeWithText("Últimos 7 días").assertExists()
        composeTestRule.onAllNodesWithText("Por servicio").assertCountEquals(2)
        listOf("Con datos móviles", "Con Wi-Fi", "Con la app abierta", "En segundo plano").forEach {
            composeTestRule.onAllNodesWithText(it).assertCountEquals(2) // hoy y 7 días
        }
        composeTestRule.onAllNodesWithText("Lista de clientes").assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Mapa, Firebase y otros").assertCountEquals(2)
        composeTestRule.onNodeWithText("48.3 MB").assertExists()
    }

    @Test fun `sin desglose avisa y no pinta lista de servicios`() {
        pinta(UsoDeDatosEjemplos.sinDesglose)
        composeTestRule.onAllNodesWithTag(USO_DE_DATOS_SIN_DETALLE_TAG).assertCountEquals(2)
        composeTestRule.onNodeWithText("Por servicio").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("Total").assertCountEquals(2)
    }

    @Test fun `no tiene botones de accion solo el de regresar`() {
        var atras = false
        pinta(UsoDeDatosEjemplos.conDatos) { atras = true }
        composeTestRule.onNodeWithTag(USO_DE_DATOS_BACK_TAG).performClick()
        assertTrue(atras)
    }
}
