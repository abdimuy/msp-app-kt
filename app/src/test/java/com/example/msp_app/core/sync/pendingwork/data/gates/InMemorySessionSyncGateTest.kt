package com.example.msp_app.core.sync.pendingwork.data.gates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemorySessionSyncGateTest {

    @Test
    fun `first call for a user wins`() {
        val gate = InMemorySessionSyncGate()
        assertTrue(gate.markIfNotSynced("u1"))
    }

    @Test
    fun `repeat call for same user loses`() {
        val gate = InMemorySessionSyncGate()
        assertTrue(gate.markIfNotSynced("u1"))
        assertFalse(gate.markIfNotSynced("u1"))
        assertFalse(gate.markIfNotSynced("u1"))
    }

    @Test
    fun `switching user resets and wins again`() {
        val gate = InMemorySessionSyncGate()
        assertTrue(gate.markIfNotSynced("u1"))
        assertTrue(gate.markIfNotSynced("u2"))
        // And u1 again — it is no longer the last synced, so it wins.
        assertTrue(gate.markIfNotSynced("u1"))
    }

    @Test
    fun `concurrent callers for same user exactly one wins`() = runBlocking {
        val gate = InMemorySessionSyncGate()
        val attempts = 50
        val wins = withContext(Dispatchers.Default) {
            (1..attempts).map {
                async { if (gate.markIfNotSynced("u1")) 1 else 0 }
            }.awaitAll().sum()
        }
        assertEquals(1, wins)
    }
}
