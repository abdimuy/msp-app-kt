package com.example.msp_app.core.designsystem.theme

import androidx.compose.animation.core.Spring
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM puro (sin Robolectric): `spring(...)` construye un `SpringSpec` plano
 * (campos `dampingRatio`/`stiffness` públicos), corre igual que
 * [MspColorsTest]. Congela las dos springs del brief (task-4-brief.md) —
 * cualquier valor que no matchee kollect 1:1 es un bug, y `emphasized` es
 * el único spring con rebote (reservado a "pago confirmado", Plan 5).
 */
class MspMotionTest {

    private val motion = MspMotion()

    @Test
    fun `standard es criticamente amortiguado (DampingRatioNoBouncy) con StiffnessMedium`() {
        val spec = motion.standard<Float>()

        assertEquals(Spring.DampingRatioNoBouncy, spec.dampingRatio)
        assertEquals(Spring.StiffnessMedium, spec.stiffness)
    }

    @Test
    fun `emphasized tiene rebote leve (DampingRatioMediumBouncy) con StiffnessMediumLow`() {
        val spec = motion.emphasized<Float>()

        assertEquals(Spring.DampingRatioMediumBouncy, spec.dampingRatio)
        assertEquals(Spring.StiffnessMediumLow, spec.stiffness)
    }

    @Test
    fun `standard y emphasized son springs distintos`() {
        val standard = motion.standard<Float>()
        val emphasized = motion.emphasized<Float>()

        assertEquals(false, standard.dampingRatio == emphasized.dampingRatio)
        assertEquals(false, standard.stiffness == emphasized.stiffness)
    }
}
