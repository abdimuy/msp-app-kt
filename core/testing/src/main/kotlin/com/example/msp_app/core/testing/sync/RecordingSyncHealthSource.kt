package com.example.msp_app.core.testing.sync

import com.example.msp_app.core.common.sync.health.SyncHealth
import com.example.msp_app.core.common.sync.health.SyncHealthSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test fake of [SyncHealthSource]: replays a fixed, ordered sequence of
 * [SyncHealth] snapshots and then completes. Meant to be driven with Turbine
 * to assert state transitions (e.g. BACKLOG -> HEALTHY) without any
 * Room/WorkManager dependency — the real counting implementation lives in
 * `:core:database`/`:app` in later plans.
 */
class RecordingSyncHealthSource(private val emissions: List<SyncHealth>) : SyncHealthSource {
    override fun observe(): Flow<SyncHealth> = flow {
        emissions.forEach { emit(it) }
    }
}
