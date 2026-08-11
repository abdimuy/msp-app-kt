package com.example.msp_app.feature.collectionreport.ui.tier2

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test JVM puro (sin Compose/Robolectric) de [resolveTier] — la función pura que decide
 * Tier 1/2 a partir de una escala ya resuelta (llamante real: [rememberReportTier], cubierto
 * aparte por [com.example.msp_app.feature.collectionreport.ui.tier2.RememberReportTierTest]
 * porque necesita Compose/`LocalFontSizeLevel`). El umbral coincide con
 * [com.example.msp_app.core.designsystem.theme.FontSizeLevel.GRANDE.nominalScale] (`1.5f`) —
 * ver el fix documentado en el KDoc de [rememberReportTier].
 */
class ReportTierTest {

    @Test
    fun `fontScale 1_0 normal resuelve Tier 1`() {
        assertEquals(ReportTier.TIER_1, resolveTier(1.0f))
    }

    @Test
    fun `fontScale 1_3 (menor al umbral de Grande) sigue siendo Tier 1`() {
        assertEquals(ReportTier.TIER_1, resolveTier(1.3f))
    }

    @Test
    fun `fontScale 2_0 muy grande resuelve Tier 2`() {
        assertEquals(ReportTier.TIER_2, resolveTier(2.0f))
    }

    @Test
    fun `fontScale menor al umbral resuelve Tier 1`() {
        assertEquals(ReportTier.TIER_1, resolveTier(1.49f))
    }

    @Test
    fun `fontScale exactamente en el umbral resuelve Tier 2`() {
        assertEquals(ReportTier.TIER_2, resolveTier(1.5f))
    }

    @Test
    fun `fontScale muy pequeno (accesibilidad chica) resuelve Tier 1`() {
        assertEquals(ReportTier.TIER_1, resolveTier(0.85f))
    }
}
