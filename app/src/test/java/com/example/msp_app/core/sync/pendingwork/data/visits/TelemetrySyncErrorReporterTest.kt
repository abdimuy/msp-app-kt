package com.example.msp_app.core.sync.pendingwork.data.visits

import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bridge from `:core:common`'s `SyncErrorReporter` to the real `Telemetry`
 * port. What is worth proving is that nothing is lost or renamed in transit:
 * the greppable `code` the domain chose is the `code` telemetry records.
 */
class TelemetrySyncErrorReporterTest {

    @Test
    fun `reenvia code, message y props al puerto Telemetry sin tocarlos`() {
        val telemetry = RecordingTelemetry()

        TelemetrySyncErrorReporter(telemetry).report(
            code = "visitas_reconcile_consulta_fallida",
            message = "ReconcileVisitsUseCase.findExisting: IOException",
            props = mapOf("tamano_lote" to "100")
        )

        val event = telemetry.recorded.single()
        assertEquals(TelemetryEventType.ERROR, event.type)
        assertEquals("visitas_reconcile_consulta_fallida", event.name)
        assertEquals("100", event.props["tamano_lote"])
        assertEquals("ReconcileVisitsUseCase.findExisting: IOException", event.props["message"])
    }

    @Test
    fun `props vacios no inventan nada`() {
        val telemetry = RecordingTelemetry()

        TelemetrySyncErrorReporter(telemetry).report(
            code = "visitas_reconcile_pendientes_ilegibles",
            message = "ReconcileVisitsUseCase.pendingVisitIds: IllegalStateException",
            props = emptyMap()
        )

        val event = telemetry.recorded.single()
        assertEquals("visitas_reconcile_pendientes_ilegibles", event.name)
        assertEquals(setOf("message"), event.props.keys)
    }
}
