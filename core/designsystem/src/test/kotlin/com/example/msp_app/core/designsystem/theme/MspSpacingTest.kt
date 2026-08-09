package com.example.msp_app.core.designsystem.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM puro (sin Robolectric): `Dp` es una `value class` sobre `Float`, corre
 * igual que [MspColorsTest]. Congela la escala de espaciado del brief
 * (task-4-brief.md) — defensa explícita del acuerdo de accesibilidad
 * 48-56dp vía [MspSpacing.touchTarget].
 */
class MspSpacingTest {

    private val spacing = MspSpacing()

    @Test
    fun `touchTarget es 56dp`() {
        assertEquals(56.dp, spacing.touchTarget)
    }

    @Test
    fun `md es 16dp`() {
        assertEquals(16.dp, spacing.md)
    }

    @Test
    fun `xs es 4dp`() {
        assertEquals(4.dp, spacing.xs)
    }

    @Test
    fun `sm es 8dp`() {
        assertEquals(8.dp, spacing.sm)
    }

    @Test
    fun `lg es 24dp`() {
        assertEquals(24.dp, spacing.lg)
    }
}
