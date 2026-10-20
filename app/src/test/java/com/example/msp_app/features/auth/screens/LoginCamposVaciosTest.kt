package com.example.msp_app.features.auth.screens

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Iniciar sesión con un campo vacío no llama a Firebase.**
 *
 * Medido el 2026-10-18 en emulador: tocar "Iniciar sesión" con los campos
 * vacíos tumbaba la app, porque `signInWithEmailAndPassword("", "")` lanza
 * `IllegalArgumentException` ("Given String is empty or null"). Aquí un
 * [IniciarSesion] falso anota cada llamada: con la validación quitada, las
 * pruebas de campos vacíos quedan en rojo.
 *
 * El control positivo (los dos campos llenos) prueba que el botón sí llega al
 * proveedor, para que el "cero llamadas" sólo pueda venir de la validación.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class LoginCamposVaciosTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val llamadas = mutableListOf<Pair<String, String>>()

    private fun pantalla(correo: String, contrasena: String) {
        composeTestRule.setContent {
            LoginScreen(
                onLoginSuccess = {},
                iniciarSesion = IniciarSesion { c, p, _ -> llamadas += c to p }
            )
        }
        if (correo.isNotEmpty()) {
            composeTestRule.onNodeWithText("Correo electrónico").performTextInput(correo)
        }
        if (contrasena.isNotEmpty()) {
            composeTestRule.onNodeWithText("Contraseña").performTextInput(contrasena)
        }
        composeTestRule.onNode(hasText("Iniciar sesión") and hasClickAction()).performClick()
        composeTestRule.waitForIdle()
    }

    private fun sinLlamadaYConAviso() {
        assertEquals(
            "no debe llamar al proveedor de sesión",
            emptyList<Pair<String, String>>(),
            llamadas
        )
        composeTestRule.onNodeWithText(MENSAJE_FALTAN_CREDENCIALES).assertExists()
    }

    @Test
    fun `los dos campos vacios no llaman a Firebase y avisan`() {
        pantalla("", "")
        sinLlamadaYConAviso()
    }

    @Test
    fun `campos con solo espacios no llaman a Firebase`() {
        pantalla("   ", "   ")
        sinLlamadaYConAviso()
    }

    @Test
    fun `contrasena vacia no llama a Firebase`() {
        pantalla("maria.lopez@muebleriamsp.mx", "")
        sinLlamadaYConAviso()
    }

    @Test
    fun `correo vacio no llama a Firebase`() {
        pantalla("", "secreto")
        sinLlamadaYConAviso()
    }

    @Test
    fun `control positivo con los dos campos llenos llama una vez con los valores recortados`() {
        pantalla(" maria.lopez@muebleriamsp.mx ", "secreto ")
        assertEquals(listOf("maria.lopez@muebleriamsp.mx" to "secreto"), llamadas)
        composeTestRule.onNodeWithText(MENSAJE_FALTAN_CREDENCIALES).assertDoesNotExist()
    }
}
