package com.example.msp_app.core.common.sync.health

import app.cash.turbine.test
import com.example.msp_app.core.testing.sync.RecordingSyncHealthSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncHealthSourceTest {

    @Test
    fun `emits the transition from BACKLOG to HEALTHY as items get acked`() = runTest {
        val source = RecordingSyncHealthSource(
            emissions = listOf(
                SyncHealth(pending = 3, confirmed = 0),
                SyncHealth(pending = 1, confirmed = 2),
                SyncHealth(pending = 0, confirmed = 3)
            )
        )

        source.observe().test {
            assertEquals(SyncStatus.BACKLOG, awaitItem().status)
            assertEquals(SyncStatus.BACKLOG, awaitItem().status)
            assertEquals(SyncStatus.HEALTHY, awaitItem().status)
            awaitComplete()
        }
    }

    @Test
    fun `a single HEALTHY emission with nothing pending completes cleanly`() = runTest {
        val source =
            RecordingSyncHealthSource(emissions = listOf(SyncHealth(pending = 0, confirmed = 0)))

        source.observe().test {
            assertEquals(SyncStatus.HEALTHY, awaitItem().status)
            awaitComplete()
        }
    }

    @Test
    fun `an empty source completes without emitting`() = runTest {
        val source = RecordingSyncHealthSource(emissions = emptyList())

        source.observe().test {
            awaitComplete()
        }
    }
}
