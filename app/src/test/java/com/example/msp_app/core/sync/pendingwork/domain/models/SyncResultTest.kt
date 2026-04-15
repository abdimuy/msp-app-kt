package com.example.msp_app.core.sync.pendingwork.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SyncResultTest {

    @Test
    fun `NothingPending is a singleton`() {
        assertSame(SyncResult.NothingPending, SyncResult.NothingPending)
    }

    @Test
    fun `Skipped is a singleton`() {
        assertSame(SyncResult.Skipped, SyncResult.Skipped)
    }

    @Test
    fun `Enqueued exposes item and work request counts`() {
        val result = SyncResult.Enqueued(itemCount = 12, workRequestCount = 10)
        assertEquals(12, result.itemCount)
        assertEquals(10, result.workRequestCount)
    }

    @Test
    fun `Enqueued value equality`() {
        val a = SyncResult.Enqueued(itemCount = 3, workRequestCount = 3)
        val b = SyncResult.Enqueued(itemCount = 3, workRequestCount = 3)
        val c = SyncResult.Enqueued(itemCount = 3, workRequestCount = 2)

        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `Failed wraps its cause`() {
        val boom = RuntimeException("boom")
        val failed = SyncResult.Failed(boom)
        assertSame(boom, failed.cause)
    }

    @Test
    fun `exhaustive when over sealed hierarchy compiles and branches`() {
        val values: List<SyncResult> = listOf(
            SyncResult.NothingPending,
            SyncResult.Skipped,
            SyncResult.Enqueued(itemCount = 1, workRequestCount = 1),
            SyncResult.Failed(IllegalStateException("x"))
        )

        val tagged = values.map { result ->
            when (result) {
                is SyncResult.NothingPending -> "nothing"
                is SyncResult.Skipped -> "skipped"
                is SyncResult.Enqueued -> "enqueued"
                is SyncResult.Failed -> "failed"
            }
        }

        assertEquals(listOf("nothing", "skipped", "enqueued", "failed"), tagged)
    }
}
