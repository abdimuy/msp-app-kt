package com.example.msp_app.core.time

import java.time.Instant

/**
 * Abstraction over "what time is it now". Inject it into ViewModels, use cases
 * and repositories instead of calling [Instant.now] directly.
 *
 * The [System] default is for production; tests must pass a FakeClock to get
 * deterministic time and to exercise timezone/DST boundaries.
 *
 * See `docs/standards/timezones.md` for the full standard.
 */
interface AppClock {
    fun now(): Instant

    companion object {
        val System: AppClock = SystemClock

        private object SystemClock : AppClock {
            override fun now(): Instant = Instant.now()
        }
    }
}
