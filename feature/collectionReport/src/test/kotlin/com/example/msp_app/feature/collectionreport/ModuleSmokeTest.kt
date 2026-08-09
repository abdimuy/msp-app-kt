package com.example.msp_app.feature.collectionreport

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba mínima (TDD rojo→verde) de que la toolchain de test de este módulo
 * arranca: JUnit vía `msp.test` (traído por `:core:testing`). No ejerce
 * ningún componente real todavía — el reporte de cobranza llega en Task 2+
 * (ver task-1-brief.md, esqueleto sin dominio ni UI).
 */
class ModuleSmokeTest {

    @Test
    fun `la toolchain de test del modulo arranca`() {
        assertEquals(4, 2 + 2)
    }
}
