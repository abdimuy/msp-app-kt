package com.example.msp_app.di

import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.telemetry.adapter.DurableTelemetry
import com.example.msp_app.core.telemetry.adapter.StubTelemetrySink
import com.example.msp_app.core.telemetry.queue.DurableTelemetryQueue
import com.example.msp_app.core.telemetry.queue.TelemetryDatabase
import com.example.msp_app.core.telemetry.queue.TelemetrySink
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 8 (Plan 4, cierre): prueba que el grafo Hilt REAL de `:app` (no solo
 * el de `:core:telemetry` en aislamiento — `TelemetryModuleHiltGraphTest`)
 * resuelve [Telemetry] → [DurableTelemetry] drenando al [StubTelemetrySink]
 * y que un evento emitido a través del puerto inyectado en `:app` de verdad
 * llega a la cola durable — el humo pedido por el brief de cierre en vez de
 * una unidad nueva (ya cubierto exhaustivamente por T2-T4).
 *
 * Robolectric (no JVM puro): la cola durable pasa por Room real
 * (`TelemetryDatabase`) — mismo motivo que `TelemetryModuleHiltGraphTest`.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class TelemetryHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var telemetry: Telemetry

    @Inject
    lateinit var telemetrySink: TelemetrySink

    @Inject
    lateinit var telemetryQueue: DurableTelemetryQueue

    @Before
    fun setUp() {
        // Mismo motivo que TelemetryModuleHiltGraphTest: el classloader de
        // Robolectric puede reusarse entre métodos de esta clase/suite.
        TelemetryDatabase.clearInstance()
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        TelemetryDatabase.clearInstance()
    }

    @Test
    fun `el grafo Hilt de app resuelve Telemetry como DurableTelemetry con sink stub`() {
        assertTrue(telemetry is DurableTelemetry)
        assertTrue(telemetrySink is StubTelemetrySink)
    }

    @Test
    fun `Telemetry inyectado en app encola de verdad en la cola durable`() = runBlocking {
        assertEquals(0, telemetryQueue.pendingCount())

        telemetry.event("smoke_test_app_di", mapOf("origen" to "telemetry_hilt_graph_test"))

        // event() encola de forma asíncrona (ver KDoc de DurableTelemetry) —
        // se espera con timeout en vez de un sleep fijo, sin acoplarse a un
        // scheduler de test (el adapter usa un scope de IO real de producción
        // acá, como cualquier consumidor real de `:app`).
        withTimeout(5_000) {
            while (telemetryQueue.pendingCount() == 0) {
                delay(10)
            }
        }

        assertEquals(1, telemetryQueue.pendingCount())
    }
}
