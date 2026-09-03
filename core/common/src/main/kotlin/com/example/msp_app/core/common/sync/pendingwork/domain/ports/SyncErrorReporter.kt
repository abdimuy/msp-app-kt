package com.example.msp_app.core.common.sync.pendingwork.domain.ports

/**
 * Error channel for the sync domain of `:core:common`.
 *
 * **This is not a rival telemetry system.** It is `Telemetry.error`'s exact
 * signature, re-declared here because `:core:common` *cannot* depend on
 * `:core:telemetry`: that module already depends on this one (it uses
 * `AppClock` for event timestamps), so the reverse edge would close a Gradle
 * project cycle. The single production implementation forwards every call
 * verbatim to the `Telemetry` port, which stays the one channel required by
 * the error norm — the bridge lives in `:app` because that is where both faces
 * are visible.
 *
 * The anti-PII discipline of `Telemetry` applies here unchanged and is the
 * caller's responsibility: [code], [message] and [props] are developer-static
 * values. Never a client name, phone, address, free text typed by the user, or
 * an exact amount. For an exception, pass the exception's class name plus a
 * static context string — never a raw `e.message`, which can drag business
 * data along with it.
 */
interface SyncErrorReporter {

    /**
     * @param code named, unique, greppable constant identifying the failure.
     * @param message static technical context (no user or client data).
     * @param props static key/value pairs; pass `emptyMap()` when there are none.
     */
    fun report(code: String, message: String, props: Map<String, String>)
}
