package com.example.msp_app.feature.collectionreport.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.theme.LocalReduceMotion
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Cobertura de [rememberReportReducedMotion] — la combinación OS-o-app que gatea las
 * animaciones del reporte (Task: "Reduce-motion on the report"). Robolectric (necesita un
 * `ContentResolver` real respaldado por el shadow de `Settings.Global`, mismo enfoque que
 * `ReducedMotionTest` de `:core:designsystem`).
 */
class ReportMotionTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ambas senales apagadas no reduce motion`() {
        setAnimatorDurationScale(1f)

        var reduced = true
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides false) {
                reduced = rememberReportReducedMotion()
            }
        }

        assertFalse(reduced)
    }

    @Test
    fun `solo la senal del SO activa reduce motion`() {
        setAnimatorDurationScale(0f)

        var reduced = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides false) {
                reduced = rememberReportReducedMotion()
            }
        }

        assertTrue(reduced)
    }

    @Test
    fun `solo la preferencia de la app activa reduce motion`() {
        setAnimatorDurationScale(1f)

        var reduced = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                reduced = rememberReportReducedMotion()
            }
        }

        assertTrue(reduced)
    }

    @Test
    fun `ambas senales activas siguen activando reduce motion`() {
        setAnimatorDurationScale(0f)

        var reduced = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides true) {
                reduced = rememberReportReducedMotion()
            }
        }

        assertTrue(reduced)
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
