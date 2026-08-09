package com.example.msp_app.feature.collectionreport.ui.tier2

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test JVM puro (sin Compose/Robolectric) de [resolveTier] — la selección Tier 1/2 por
 * `fontScale` proxy (task-9-brief.md, "Parked for user"). Cubre los tres puntos de la matriz
 * de escalas del Plan 3 ({1.0, 1.3, 2.0}, ver `docs/superpowers/plans/2026-08-09-plan3-designsystem.md`
 * Task 10): 1.0/1.3 son Tier 1 ("Normal"/"Grande"), 2.0 es Tier 2 ("Muy grande").
 */
class ReportTierTest {

    @Test
    fun `fontScale 1_0 normal resuelve Tier 1`() {
        assertEquals(ReportTier.TIER_1, resolveTier(1.0f))
    }

    @Test
    fun `fontScale 1_3 grande sigue siendo Tier 1`() {
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
