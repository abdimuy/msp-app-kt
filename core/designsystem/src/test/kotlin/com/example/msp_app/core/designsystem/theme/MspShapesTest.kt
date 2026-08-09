package com.example.msp_app.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * JVM puro (sin Robolectric ni Compose runtime): `RoundedCornerShape` es una
 * clase Kotlin normal sobre `CornerSize`/`Dp` con `equals`/`hashCode`
 * estructurales, corre igual que [MspColorsTest]. Congela la tabla de radios
 * del brief (task-4-brief.md) contra regresiones.
 */
class MspShapesTest {

    private val shapes = MspShapes()

    @Test
    fun `card es 20dp`() {
        assertEquals(RoundedCornerShape(20.dp), shapes.card)
    }

    @Test
    fun `heroCard es 22dp`() {
        assertEquals(RoundedCornerShape(22.dp), shapes.heroCard)
    }

    @Test
    fun `tile es 16dp`() {
        assertEquals(RoundedCornerShape(16.dp), shapes.tile)
    }

    @Test
    fun `control es 12dp`() {
        assertEquals(RoundedCornerShape(12.dp), shapes.control)
    }

    @Test
    fun `chip es pill completo (percent 50), no un dp fijo`() {
        assertEquals(RoundedCornerShape(percent = 50), shapes.chip)
    }

    @Test
    fun `button es 16dp`() {
        assertEquals(RoundedCornerShape(16.dp), shapes.button)
    }

    @Test
    fun `field es 14dp`() {
        assertEquals(RoundedCornerShape(14.dp), shapes.field)
    }

    @Test
    fun `sectionCard es 18dp`() {
        assertEquals(RoundedCornerShape(18.dp), shapes.sectionCard)
    }

    @Test
    fun `payIcon es 11dp`() {
        assertEquals(RoundedCornerShape(11.dp), shapes.payIcon)
    }

    @Test
    fun `chip9 es 9dp`() {
        assertEquals(RoundedCornerShape(9.dp), shapes.chip9)
    }
}
