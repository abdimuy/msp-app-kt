package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MspPrivacyEyeToggle]: el tap invoca
 * [onToggle] tanto en `masked = false` como `masked = true` — el estado lo
 * sostiene el caller, este componente solo reporta el tap (task-9-brief.md).
 * El golden visual (ojo/ojo-tachado) vive en
 * `screenshot/PrivacyEyeToggleScreenshotTest`.
 */
class PrivacyEyeToggleTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tap invoca onToggle con masked=false`() {
        var toggled = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspPrivacyEyeToggle(masked = false, onToggle = { toggled = true })
            }
        }

        composeTestRule.onNodeWithTag(PRIVACY_EYE_TOGGLE_TAG).performClick()

        assertTrue(toggled)
    }

    @Test
    fun `tap invoca onToggle con masked=true`() {
        var toggled = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspPrivacyEyeToggle(masked = true, onToggle = { toggled = true })
            }
        }

        composeTestRule.onNodeWithTag(PRIVACY_EYE_TOGGLE_TAG).performClick()

        assertTrue(toggled)
    }
}
