package com.example.msp_app.core.designsystem.component

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (no golden) de [MspPaymentSyncPill]: el texto "N por subir" y,
 * sobre todo, el gate de reduce-motion (task-9-brief.md, obligatorio) — con
 * `ANIMATOR_DURATION_SCALE = 0` el `InfiniteTransition` del anillo de pulso
 * NUNCA se compone (mismo mecanismo de [ReducedMotionTest] en
 * `theme/ReducedMotionTest.kt`, un `Settings.Global` real vía el shadow de
 * Robolectric). El golden visual vive en
 * `screenshot/PaymentSyncPillScreenshotTest`.
 */
class PaymentSyncPillTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `muestra el conteo pendiente formateado`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspPaymentSyncPill(pendingCount = 3)
            }
        }

        composeTestRule.onNodeWithText("3 por subir").assertIsDisplayed()
    }

    @Test
    fun `con reduce-motion activo, el anillo de pulso no se compone (solo el dot solido)`() {
        setAnimatorDurationScale(0f)

        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspPaymentSyncPill(pendingCount = 5)
            }
        }

        composeTestRule
            .onNodeWithTag(PAYMENT_SYNC_PILL_PULSE_RING_TAG, useUnmergedTree = true)
            .assertDoesNotExist()
        composeTestRule
            .onNodeWithTag(PAYMENT_SYNC_PILL_DOT_TAG, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `sin reduce-motion, el anillo de pulso si se compone`() {
        setAnimatorDurationScale(1f)

        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                MspPaymentSyncPill(pendingCount = 5)
            }
        }

        composeTestRule
            .onNodeWithTag(PAYMENT_SYNC_PILL_PULSE_RING_TAG, useUnmergedTree = true)
            .assertExists()
    }

    private fun setAnimatorDurationScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            scale
        )
    }
}
