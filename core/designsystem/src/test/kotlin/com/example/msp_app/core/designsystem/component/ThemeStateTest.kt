package com.example.msp_app.core.designsystem.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM puro — mismo criterio que [ThemeRevealControllerTest]: `ThemeState` es
 * un `mutableStateOf` plano, sin necesidad de Robolectric para leer/escribir
 * fuera de composición.
 */
class ThemeStateTest {

    @Test
    fun `arranca en el valor dado al constructor`() {
        assertTrue(ThemeState(darkTheme = true).darkTheme)
        assertFalse(ThemeState(darkTheme = false).darkTheme)
    }

    @Test
    fun `toggle invierte darkTheme`() {
        val state = ThemeState(darkTheme = false)

        state.toggle()

        assertTrue(state.darkTheme)
    }

    @Test
    fun `dos toggle seguidos vuelven al valor original`() {
        val state = ThemeState(darkTheme = false)

        state.toggle()
        state.toggle()

        assertFalse(state.darkTheme)
    }
}
