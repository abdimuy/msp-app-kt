package com.example.msp_app.core.designsystem.theme

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **De quién toma el tema una pantalla que se envuelve sola.**
 *
 * `MspTheme { }` sin argumento resolvía contra `isSystemInDarkTheme()`, o sea el
 * SISTEMA OPERATIVO, mientras que el resto de la app pinta con
 * `ThemeController.isDarkMode`, que son los 3 modos de Configuración ya
 * resueltos. Las dos direcciones de esa divergencia se midieron en el emulador:
 * app en oscuro + SO en claro da una pantalla **blanca** de noche, y app en claro
 * + SO en oscuro da una pantalla negra con la **barra de estado ilegible**.
 *
 * Estas dos pruebas fijan el contrato nuevo: `MspTheme` sigue a
 * [LocalAppDarkTheme] cuando el composition root lo reporta, y **conserva** el
 * comportamiento viejo cuando nadie lo reporta —que es el caso de todo
 * `@Preview` y de todo test de módulo—.
 */
class MspThemeSigueAlTemaDeLaAppTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fondoConLocal(temaDeLaApp: Boolean?): Color {
        var fondo: Color? = null
        composeTestRule.setContent {
            CompositionLocalProvider(LocalAppDarkTheme provides temaDeLaApp) {
                MspTheme(animateColors = false) { fondo = MspTheme.colors.background }
            }
        }
        composeTestRule.waitForIdle()
        return requireNotNull(fondo) { "no se compuso nada" }
    }

    @Test
    fun `con el tema de la app en oscuro, MspTheme pinta oscuro`() {
        assertEquals(mspDarkColors().background, fondoConLocal(temaDeLaApp = true))
    }

    @Test
    fun `con el tema de la app en claro, MspTheme pinta claro aunque el SO diga otra cosa`() {
        assertEquals(mspLightColors().background, fondoConLocal(temaDeLaApp = false))
    }

    /**
     * **La dirección que el emulador midió y la que más duele.** Con el SISTEMA
     * en oscuro y la app en claro, antes salía una pantalla negra con la barra de
     * estado ilegible; ahora manda la app. Sin el `qualifiers = "+night"` esta
     * afirmación no se estaría midiendo: bajo Robolectric el sistema es claro por
     * defecto, así que el local y el sistema coincidirían y el test pasaría
     * incluso con el default viejo.
     */
    @Test
    @Config(qualifiers = "+night")
    fun `con el SO en oscuro y la app en claro, manda la app`() {
        assertEquals(mspLightColors().background, fondoConLocal(temaDeLaApp = false))
    }

    /**
     * Sin composition root que lo reporte —`@Preview`, tests de módulo, cualquier
     * host que no sea `:app`— el comportamiento es el de antes: el del sistema.
     * Las dos mitades del `?:`, medidas.
     */
    @Test
    fun `sin nadie que reporte el tema de la app, MspTheme cae al del sistema claro`() {
        assertEquals(mspLightColors().background, fondoConLocal(temaDeLaApp = null))
    }

    @Test
    @Config(qualifiers = "+night")
    fun `sin nadie que reporte el tema de la app, MspTheme cae al del sistema oscuro`() {
        assertEquals(mspDarkColors().background, fondoConLocal(temaDeLaApp = null))
    }
}
