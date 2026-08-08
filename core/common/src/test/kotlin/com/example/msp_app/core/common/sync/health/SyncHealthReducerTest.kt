package com.example.msp_app.core.common.sync.health

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncHealthReducerTest {

    @Test
    fun `reduces the counts of the 5 pending-work types into one SyncHealth`() {
        // Orden: payments, guarantees, guarantee events, local sales, visits.
        val counts = listOf(
            SyncTypeCount(pending = 2, confirmed = 10),
            SyncTypeCount(pending = 0, confirmed = 5),
            SyncTypeCount(pending = 1, confirmed = 0),
            SyncTypeCount(pending = 0, confirmed = 3),
            SyncTypeCount(pending = 0, confirmed = 8)
        )

        val health = SyncHealthReducer.reduce(counts)

        assertEquals(SyncHealth(pending = 3, confirmed = 26), health)
        assertEquals(SyncStatus.BACKLOG, health.status)
    }

    @Test
    fun `empty input reduces to the HEALTHY zero state`() {
        val health = SyncHealthReducer.reduce(emptyList())

        assertEquals(SyncHealth(pending = 0, confirmed = 0), health)
        assertEquals(SyncStatus.HEALTHY, health.status)
    }

    @Test
    fun `a single type with backlog is reflected as-is`() {
        val health = SyncHealthReducer.reduce(listOf(SyncTypeCount(pending = 4, confirmed = 1)))

        assertEquals(SyncHealth(pending = 4, confirmed = 1), health)
    }
}
