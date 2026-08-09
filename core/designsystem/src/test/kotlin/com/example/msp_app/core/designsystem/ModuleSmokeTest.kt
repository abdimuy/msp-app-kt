package com.example.msp_app.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Prueba mínima (TDD rojo→verde) de que la toolchain de test de este módulo
 * arranca: JUnit vía `msp.test`, traído aquí por `:core:testing`. No ejerce
 * ningún componente real todavía — el contenido del design system llega en
 * Task 2+ (ver task-1-brief.md, esqueleto sin tokens ni componentes).
 */
class ModuleSmokeTest {

    @Test
    fun `la toolchain de test del modulo arranca`() {
        assertEquals(4, 2 + 2)
    }
}
