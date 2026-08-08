package com.example.msp_app.core.testing.roborazzi

import org.junit.Assert.assertTrue
import org.junit.Test

class RoborazziConfigTest {

    @Test
    fun `el umbral de cambio esta definido en un rango valido`() {
        assertTrue(RoborazziConfig.CHANGE_THRESHOLD in 0f..1f)
    }
}
