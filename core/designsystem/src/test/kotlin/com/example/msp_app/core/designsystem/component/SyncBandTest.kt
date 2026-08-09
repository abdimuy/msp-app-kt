package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MspSyncBand]: expone dot + mensaje + hint
 * tal cual los recibe, sin copia hardcodeada, para ambos [SyncBandState]
 * (task-9-brief.md). El golden visual (Pending/Ok) vive en
 * `screenshot/SyncBandScreenshotTest`.
 */
class SyncBandTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `estado Pending muestra dot, mensaje y hint`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspSyncBand(
                    state = SyncBandState.Pending,
                    message = "3 pagos por subir",
                    hint = "se sube solo al recuperar señal"
                )
            }
        }

        composeTestRule.onNodeWithTag(SYNC_BAND_DOT_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("3 pagos por subir").assertIsDisplayed()
        composeTestRule.onNodeWithText("se sube solo al recuperar señal").assertIsDisplayed()
    }

    @Test
    fun `estado Ok tambien expone dot, mensaje y hint`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspSyncBand(
                    state = SyncBandState.Ok,
                    message = "Todo al día",
                    hint = "última sync hace 2 min"
                )
            }
        }

        composeTestRule.onNodeWithTag(SYNC_BAND_DOT_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("Todo al día").assertIsDisplayed()
        composeTestRule.onNodeWithText("última sync hace 2 min").assertIsDisplayed()
    }
}
