package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MspPrimaryFieldButton]: el tap habilitado
 * invoca [onClick]; deshabilitado, no (task-9-brief.md, "disabled = fill
 * plano `outline`... `Surface` clickable no aplica alfa disabled sola" — el
 * comportamiento de click en sí también debe apagarse, no solo el pintado).
 * El golden visual (Primary/Ghost/Danger + disabled) vive en
 * `screenshot/PrimaryFieldButtonScreenshotTest`.
 */
class PrimaryFieldButtonTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `tap habilitado invoca onClick`() {
        var clicked = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspPrimaryFieldButton(text = "Registrar pago", onClick = { clicked = true })
            }
        }

        composeTestRule.onNodeWithTag(PRIMARY_FIELD_BUTTON_TAG).performClick()

        assertTrue(clicked)
    }

    @Test
    fun `deshabilitado, el tap NO invoca onClick`() {
        var clicked = false
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspPrimaryFieldButton(
                    text = "Registrar pago",
                    onClick = { clicked = true },
                    enabled = false
                )
            }
        }

        composeTestRule.onNodeWithTag(PRIMARY_FIELD_BUTTON_TAG).performClick()

        assertFalse(clicked)
    }

    @Test
    fun `pinta el texto dado en los tres variants`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                Column {
                    MspPrimaryFieldButton(
                        text = "Primario",
                        onClick = {},
                        variant = PrimaryFieldButtonVariant.Primary
                    )
                    MspPrimaryFieldButton(
                        text = "Fantasma",
                        onClick = {},
                        variant = PrimaryFieldButtonVariant.Ghost
                    )
                    MspPrimaryFieldButton(
                        text = "Peligro",
                        onClick = {},
                        variant = PrimaryFieldButtonVariant.Danger
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Primario").assertIsDisplayed()
        composeTestRule.onNodeWithText("Fantasma").assertIsDisplayed()
        composeTestRule.onNodeWithText("Peligro").assertIsDisplayed()
    }
}
