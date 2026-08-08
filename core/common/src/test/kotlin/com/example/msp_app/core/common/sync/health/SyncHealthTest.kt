package com.example.msp_app.core.common.sync.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncHealthTest {

    @Test
    fun `0 pending and 0 confirmed is HEALTHY with no backlog`() {
        val health = SyncHealth(pending = 0, confirmed = 0)

        assertEquals(SyncStatus.HEALTHY, health.status)
        assertFalse(health.hasBacklog)
    }

    @Test
    fun `any pending item means BACKLOG`() {
        val health = SyncHealth(pending = 1, confirmed = 5)

        assertEquals(SyncStatus.BACKLOG, health.status)
        assertTrue(health.hasBacklog)
    }

    @Test
    fun `confirmed-only backlog with nothing pending stays HEALTHY`() {
        val health = SyncHealth(pending = 0, confirmed = 42)

        assertEquals(SyncStatus.HEALTHY, health.status)
        assertFalse(health.hasBacklog)
    }

    @Test
    fun `total is pending plus confirmed`() {
        val health = SyncHealth(pending = 3, confirmed = 7)

        assertEquals(10, health.total)
    }

    @Test
    fun `total of the zero state is zero`() {
        assertEquals(0, SyncHealth(pending = 0, confirmed = 0).total)
    }

    @Test
    fun `negative pending is rejected defensively`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncHealth(pending = -1, confirmed = 0)
        }
    }

    @Test
    fun `negative confirmed is rejected defensively`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncHealth(pending = 0, confirmed = -1)
        }
    }

    @Test
    fun `case table of pending, confirmed to status`() {
        data class Case(val pending: Int, val confirmed: Int, val expected: SyncStatus)

        val cases = listOf(
            Case(pending = 0, confirmed = 0, expected = SyncStatus.HEALTHY),
            Case(pending = 0, confirmed = 10, expected = SyncStatus.HEALTHY),
            Case(pending = 1, confirmed = 0, expected = SyncStatus.BACKLOG),
            Case(pending = 5, confirmed = 5, expected = SyncStatus.BACKLOG)
        )

        cases.forEach { case ->
            val health = SyncHealth(pending = case.pending, confirmed = case.confirmed)
            assertEquals(
                "pending=${case.pending} confirmed=${case.confirmed}",
                case.expected,
                health.status
            )
        }
    }

    @Test
    fun `value equality between equivalent instances`() {
        val a = SyncHealth(pending = 2, confirmed = 4)
        val b = SyncHealth(pending = 2, confirmed = 4)

        assertEquals(a, b)
    }
}
