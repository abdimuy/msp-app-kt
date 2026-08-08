package com.example.msp_app.core.common.sync.health

import kotlinx.coroutines.flow.Flow

/**
 * Port for observing [SyncHealth] over time. A single definition, no default
 * implementation here on purpose: computing real counts means querying Room
 * (`:core:database`, Plan 2) or the WorkManager-backed adapters that still
 * live in `:app` — neither dependency belongs in this pure-JVM domain module.
 * Tests drive this port with `RecordingSyncHealthSource` (`:core:testing`).
 */
fun interface SyncHealthSource {
    fun observe(): Flow<SyncHealth>
}
