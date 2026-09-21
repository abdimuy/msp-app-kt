package com.example.msp_app.feature.ventacorreccion.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.ventacorreccion.domain.EstadoCorreccion
import com.example.msp_app.feature.ventacorreccion.domain.TextosCorreccion
import com.example.msp_app.feature.ventacorreccion.ui.components.EntradaCorreccion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Compose UI test de la Task 5: [EntradaCorreccion] es la única regla de "qué se pinta" en el
 * punto de entrada de la corrección — este test la ejercita directamente (no un doble a mano),
 * así que un mutante que deje el botón siempre visible se pone rojo aquí de verdad.
 *
 * Rojo sembrado documentado en el brief: dejar el botón visible sin importar el estado rompe
 * `YaSeEnvio no ofrece el boton y muestra su aviso` (`assertDoesNotExist` deja de cumplirse).
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class AvisoNoCorregibleTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `YaSeEnvio no ofrece el boton y muestra su aviso`() {
        setContent(EstadoCorreccion.YaSeEnvio)

        composeTestRule.onNodeWithText(TextosCorreccion.CORREGIR_VENTA).assertDoesNotExist()
        composeTestRule.onNodeWithText(TextosCorreccion.YA_SE_ENVIO).assertIsDisplayed()
    }

    @Test
    fun `LaRevisaLaOficina no ofrece el boton y muestra su aviso`() {
        setContent(EstadoCorreccion.LaRevisaLaOficina)

        composeTestRule.onNodeWithText(TextosCorreccion.CORREGIR_VENTA).assertDoesNotExist()
        composeTestRule.onNodeWithText(TextosCorreccion.LA_REVISA_LA_OFICINA).assertIsDisplayed()
    }

    @Test
    fun `SeEstaEnviando no ofrece el boton y muestra su aviso`() {
        setContent(EstadoCorreccion.SeEstaEnviando)

        composeTestRule.onNodeWithText(TextosCorreccion.CORREGIR_VENTA).assertDoesNotExist()
        composeTestRule.onNodeWithText(TextosCorreccion.SE_ESTA_ENVIANDO).assertIsDisplayed()
    }

    @Test
    fun `Corregible ofrece el boton y es clicable`() {
        var clics = 0
        setContent(EstadoCorreccion.Corregible, onCorregir = { clics++ })

        composeTestRule.onNodeWithText(TextosCorreccion.CORREGIR_VENTA)
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, clics)
    }

    @Test
    fun `null todavia no dice nada`() {
        setContent(null)

        composeTestRule.onNodeWithText(TextosCorreccion.CORREGIR_VENTA).assertDoesNotExist()
        composeTestRule.onNodeWithText(TextosCorreccion.YA_SE_ENVIO).assertDoesNotExist()
        composeTestRule.onNodeWithText(TextosCorreccion.SE_ESTA_ENVIANDO).assertDoesNotExist()
        composeTestRule.onNodeWithText(TextosCorreccion.LA_REVISA_LA_OFICINA).assertDoesNotExist()
    }

    private fun setContent(estado: EstadoCorreccion?, onCorregir: () -> Unit = {}) {
        composeTestRule.setContent {
            MspTheme {
                EntradaCorreccion(estado = estado, onCorregir = onCorregir)
            }
        }
    }
}
