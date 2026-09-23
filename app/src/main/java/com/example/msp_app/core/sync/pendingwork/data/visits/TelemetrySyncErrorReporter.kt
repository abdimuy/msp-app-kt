package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.common.sync.pendingwork.domain.ports.SyncErrorReporter
import com.example.msp_app.core.telemetry.Telemetry
import javax.inject.Inject

/**
 * The bridge that makes `:core:common`'s [SyncErrorReporter] and the real
 * `Telemetry` port one and the same channel.
 *
 * It exists only because `:core:common` cannot depend on `:core:telemetry` —
 * that module already depends on this one for `AppClock`, so the reverse edge
 * would close a Gradle project cycle. `:app` is the one place that sees both
 * faces, which is exactly where the hexagonal contract puts an adapter whose
 * other half lives outside the module.
 *
 * It forwards verbatim and adds nothing: no re-coding, no enrichment, no
 * filtering. Anything else would mean two error vocabularies to grep instead
 * of one.
 */
class TelemetrySyncErrorReporter @Inject constructor(
    private val telemetry: Telemetry
) : SyncErrorReporter {

    override fun report(code: String, message: String, props: Map<String, String>) {
        telemetry.error(code = code, message = message, props = props)
    }
}
