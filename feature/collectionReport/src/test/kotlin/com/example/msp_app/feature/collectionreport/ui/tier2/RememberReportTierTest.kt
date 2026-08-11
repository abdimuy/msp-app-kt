package com.example.msp_app.feature.collectionreport.ui.tier2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Compose-test (Robolectric) de [rememberReportTier] — a diferencia de [ReportTierTest] (JVM
 * puro sobre [resolveTier]), este cubre el cableado real a [LocalFontSizeLevel]: el fix del bug
 * "Grande/Muy grande rompe el reporte" (ver KDoc de [rememberReportTier]) es precisamente que
 * GRANDE (`nominalScale` 1.5f) debe resolver [ReportTier.TIER_2], no [ReportTier.TIER_1] —
 * ANTES del fix esta función ignoraba [LocalFontSizeLevel] por completo y leía un
 * `LocalDensity.fontScale` que `ReportMspTheme` neutraliza a `1.0` dentro del reporte.
 */
class RememberReportTierTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `LocalFontSizeLevel NORMAL resuelve Tier 1`() {
        assertTier(FontSizeLevel.NORMAL, ReportTier.TIER_1)
    }

    @Test
    fun `LocalFontSizeLevel GRANDE resuelve Tier 2`() {
        assertTier(FontSizeLevel.GRANDE, ReportTier.TIER_2)
    }

    @Test
    fun `LocalFontSizeLevel MUY_GRANDE resuelve Tier 2`() {
        assertTier(FontSizeLevel.MUY_GRANDE, ReportTier.TIER_2)
    }

    @Test
    fun `sin LocalFontSizeLevel provisto (default NORMAL) resuelve Tier 1`() {
        var resolved: ReportTier? = null
        composeTestRule.setContent {
            resolved = rememberReportTier()
        }

        assertEquals(ReportTier.TIER_1, resolved)
    }

    private fun assertTier(level: FontSizeLevel, expected: ReportTier) {
        var resolved: ReportTier? = null
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides level) {
                resolved = rememberReportTier()
            }
        }

        assertEquals(expected, resolved)
    }
}
