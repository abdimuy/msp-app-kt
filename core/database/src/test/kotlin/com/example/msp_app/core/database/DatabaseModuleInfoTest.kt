package com.example.msp_app.core.database

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Smoke test del esqueleto de `:core:database` (Plan 2, Task 1): prueba que
 * la toolchain de test del módulo (JUnit vía `msp.test`) arranca y que el
 * módulo compila con Room + Hilt + KSP conviviendo. El contenido real
 * (`AppDatabase`, DAOs, migraciones) llega con el hoist en Task 2.
 */
class DatabaseModuleInfoTest {
    @Test
    fun `module name is core-database`() {
        assertEquals("core-database", DatabaseModuleInfo.NAME)
    }
}
