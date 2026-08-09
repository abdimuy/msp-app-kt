package com.example.msp_app.core.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test del esqueleto de `:core:telemetry` (Plan 4, Task 1): prueba
 * que la toolchain de test del módulo (JUnit vía `msp.test`) arranca y que
 * el módulo compila con Compose + Room + Hilt + KSP conviviendo. El
 * contenido real (store `telemetry_db`, entidad, DAO, cola) llega en T2-T4.
 */
class TelemetryModuleInfoTest {
    @Test
    fun `module name is core-telemetry`() {
        assertEquals("core-telemetry", TelemetryModuleInfo.NAME)
    }
}
