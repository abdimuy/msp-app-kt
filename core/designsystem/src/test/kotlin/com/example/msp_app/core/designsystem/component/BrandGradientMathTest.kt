package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM puro (sin Robolectric ni Compose runtime): `Size`/`Offset` de
 * `compose-ui` son clases de datos planas, corren igual que
 * [com.example.msp_app.core.designsystem.theme.MspShapesTest]. Verifica que
 * [brandGradientEndpoints] — el cálculo puro de `direction`/`length`
 * extraído de [brandGradientBackground] — produce un segmento coherente con
 * el ángulo CSS de 150° (task-6-brief.md), sin depender de dibujar nada.
 */
class BrandGradientMathTest {

    private val size = Size(width = 300f, height = 200f)

    @Test
    fun `los extremos son simetricos respecto al centro del rectangulo`() {
        val (start, end) = brandGradientEndpoints(size)
        val center = Offset(size.width / 2f, size.height / 2f)

        assertEquals(center.x.toDouble(), ((start.x + end.x) / 2f).toDouble(), TOLERANCE)
        assertEquals(center.y.toDouble(), ((start.y + end.y) / 2f).toDouble(), TOLERANCE)
    }

    @Test
    fun `la direccion del segmento coincide con 150 grados medidos desde arriba en sentido horario`() {
        val (start, end) = brandGradientEndpoints(size)
        val dx = (end.x - start.x).toDouble()
        val dy = (end.y - start.y).toDouble()

        // Convención CSS: 0 grados = hacia arriba (+Y negativo), gira en sentido horario.
        val angleFromVertical = (Math.toDegrees(atan2(dx, -dy)) + FULL_TURN_DEG) % FULL_TURN_DEG

        assertEquals(HERO_GRADIENT_ANGLE_DEG, angleFromVertical, TOLERANCE)
    }

    @Test
    fun `la longitud del segmento cubre la proyeccion CSS del rectangulo sobre el angulo`() {
        val (start, end) = brandGradientEndpoints(size)
        val actualLength = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble())

        // Formula CSS de "gradient line length": |width * sin(theta)| + |height * cos(theta)|,
        // derivada independientemente de la implementacion (no reusa direction/half de brandGradientEndpoints).
        val radians = Math.toRadians(HERO_GRADIENT_ANGLE_DEG)
        val expectedLength = abs(size.width * sin(radians)) + abs(size.height * cos(radians))

        assertEquals(expectedLength, actualLength, TOLERANCE)
    }

    @Test
    fun `un angulo distinto de 150 produce extremos distintos (el parametro no se ignora)`() {
        val default = brandGradientEndpoints(size)
        val other = brandGradientEndpoints(size, angleDeg = 45.0)

        assertNotEquals(default, other)
    }

    @Test
    fun `a 0 grados el gradiente corre estrictamente de abajo hacia arriba`() {
        val (start, end) = brandGradientEndpoints(size, angleDeg = 0.0)

        assertEquals(0f, end.x - start.x, TOLERANCE.toFloat())
        assertTrue("el extremo final debe quedar arriba del inicial", end.y < start.y)
    }

    private companion object {
        const val TOLERANCE = 0.01
        const val FULL_TURN_DEG = 360.0
    }
}
