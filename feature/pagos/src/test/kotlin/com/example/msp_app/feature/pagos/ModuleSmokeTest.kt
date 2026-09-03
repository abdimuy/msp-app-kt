package com.example.msp_app.feature.pagos

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba mínima (TDD rojo->verde) de que la toolchain de test de este módulo
 * arranca: JUnit vía `msp.test` (traído por `:core:testing`). No ejerce
 * ningún componente real todavía — la pantalla de pagos llega en Tasks
 * 16-21 (Task 15: andamio vacío y verde, moldeado en `:feature:collectionReport`).
 */
class ModuleSmokeTest {

    @Test
    fun `la toolchain de test del modulo arranca`() {
        assertEquals(4, 2 + 2)
    }
}
