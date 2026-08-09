package com.example.msp_app.core.designsystem.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MspThemeToggle]: decide entre reveal circular
 * y crossfade fallback según haya o no un [ThemeRevealController] instalado
 * vía [LocalThemeReveal] (task-9-brief.md). El golden visual (moon/sun) vive
 * en `screenshot/ThemeToggleScreenshotTest`.
 */
class ThemeToggleTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `sin host de reveal, el tap llama onToggle directo (crossfade fallback)`() {
        var toggled = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspThemeToggle(darkTheme = false, onToggle = { toggled = true })
            }
        }

        composeTestRule.onNodeWithTag(THEME_TOGGLE_TAG).performClick()

        assertTrue(toggled)
    }

    @Test
    fun `con host de reveal instalado, el tap pide la reveal y NO llama onToggle`() {
        var toggled = false
        val controller = ThemeRevealController()
        composeTestRule.setContent {
            CompositionLocalProvider(LocalThemeReveal provides controller) {
                MspTheme(animateColors = false) {
                    MspThemeToggle(darkTheme = false, onToggle = { toggled = true })
                }
            }
        }

        composeTestRule.onNodeWithTag(THEME_TOGGLE_TAG).performClick()

        assertFalse(toggled)
        assertNotNull(controller.origin)
    }
}
