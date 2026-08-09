package com.example.msp_app.core.telemetry.di

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
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 4: prueba que el grafo Hilt REAL resuelve [Telemetry] → [DurableTelemetry]
 * y [TelemetrySink] → [StubTelemetrySink] a través de [TelemetryModule], y que
 * los 3 bindings son singleton (misma instancia entre dos puntos de inyección
 * del mismo componente) — mismo patrón que `TelemetryDatabaseModuleHiltGraphTest`
 * (Task 3).
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, sdk = [33])
class TelemetryModuleHiltGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var telemetry: Telemetry

    // Segundo punto de inyección del MISMO tipo, para probar el scope
    // `@Singleton` sin inventar un `EntryPoint` propio: Dagger resuelve ambos
    // campos contra el mismo `SingletonComponent`, así que si el binding es
    // singleton, `telemetry` y `telemetryAgain` deben ser la misma instancia.
    @Inject
    lateinit var telemetryAgain: Telemetry

    @Inject
    lateinit var telemetrySink: TelemetrySink

    @Inject
    lateinit var telemetryQueue: DurableTelemetryQueue

    @Before
    fun clearSingletonBefore() {
        // Mismo motivo que TelemetryDatabaseModuleHiltGraphTest: el classloader
        // de Robolectric puede reusarse entre metodos de esta clase.
        TelemetryDatabase.clearInstance()
    }

    @After
    fun clearSingletonAfter() {
        TelemetryDatabase.clearInstance()
    }

    @Test
    fun `el grafo Hilt resuelve Telemetry como DurableTelemetry`() {
        hiltRule.inject()

        assertTrue(telemetry is DurableTelemetry)
    }

    @Test
    fun `el grafo Hilt resuelve TelemetrySink como StubTelemetrySink`() {
        hiltRule.inject()

        assertTrue(telemetrySink is StubTelemetrySink)
    }

    @Test
    fun `el grafo Hilt resuelve DurableTelemetryQueue`() {
        hiltRule.inject()

        assertNotNull(telemetryQueue)
    }

    @Test
    fun `Telemetry es singleton, dos campos inyectados del mismo componente son la misma instancia`() {
        hiltRule.inject()

        assertSame(telemetry, telemetryAgain)
    }
}
