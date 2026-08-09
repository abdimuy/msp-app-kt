package com.example.msp_app.core.designsystem.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Robolectric (necesita un `ContentResolver` real respaldado por el shadow
 * de Robolectric para `Settings.Global`, no JVM puro — mismo enfoque que
 * [MspTypographyTest]). Verifica el interruptor de reduce-motion contra las
 * dos posiciones del ajuste "Eliminar/Reducir animaciones" de Android
 * (`ANIMATOR_DURATION_SCALE`).
 */
class ReducedMotionTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ANIMATOR_DURATION_SCALE en 0 activa reduce-motion`() {
        setAnimatorDurationScale(0f)

        var reduced = false
        composeTestRule.setContent {
            reduced = rememberReducedMotionEnabled()
        }
        composeTestRule.waitForIdle()

        assertTrue(reduced)
    }

    @Test
    fun `ANIMATOR_DURATION_SCALE en 1 desactiva reduce-motion`() {
        setAnimatorDurationScale(1f)

        var reduced = true
        composeTestRule.setContent {
            reduced = rememberReducedMotionEnabled()
        }
        composeTestRule.waitForIdle()

        assertFalse(reduced)
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
