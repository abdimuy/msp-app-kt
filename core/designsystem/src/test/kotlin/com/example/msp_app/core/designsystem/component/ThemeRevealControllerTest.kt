package com.example.msp_app.core.designsystem.component

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM puro (sin Robolectric ni Compose runtime): `ThemeRevealController` es
 * un `mutableStateOf` de `compose-runtime` puro (sin dependencia de
 * Android/Robolectric para leer/escribir su valor fuera de composición,
 * igual que [com.example.msp_app.core.designsystem.theme.MspShapesTest] con
 * `RoundedCornerShape`) y [maxDistanceToCorner] es una función matemática
 * pura — corre igual que
 * [com.example.msp_app.core.designsystem.component.BrandGradientMathTest].
 * Cubre el bridge origen→dueño del flip (task-9-brief.md).
 */
class ThemeRevealControllerTest {

    @Test
    fun `requestRevealFrom setea origin al punto dado`() {
        val controller = ThemeRevealController()
        val origin = Offset(120f, 340f)

        controller.requestRevealFrom(origin)

        assertEquals(origin, controller.origin)
    }

    @Test
    fun `consume limpia origin a null`() {
        val controller = ThemeRevealController()
        controller.requestRevealFrom(Offset(50f, 60f))

        controller.consume()

        assertNull(controller.origin)
    }

    @Test
    fun `origin arranca en null antes de cualquier request`() {
        val controller = ThemeRevealController()

        assertNull(controller.origin)
    }

    @Test
    fun `un segundo requestRevealFrom reemplaza el origin anterior (re-tap rapido)`() {
        val controller = ThemeRevealController()
        controller.requestRevealFrom(Offset(10f, 10f))

        controller.requestRevealFrom(Offset(200f, 5f))

        assertEquals(Offset(200f, 5f), controller.origin)
    }

    @Test
    fun `maxDistanceToCorner con origen en el centro es la mitad de la diagonal`() {
        val width = 400f
        val height = 800f
        val origin = Offset(width / 2f, height / 2f)

        val result = maxDistanceToCorner(origin, width, height)

        assertEquals(hypot(width / 2f, height / 2f), result, TOLERANCE)
    }

    @Test
    fun `maxDistanceToCorner con origen en una esquina es la diagonal completa`() {
        val width = 400f
        val height = 800f

        val result = maxDistanceToCorner(Offset(0f, 0f), width, height)

        assertEquals(hypot(width, height), result, TOLERANCE)
    }

    @Test
    fun `maxDistanceToCorner con origen en la esquina opuesta tambien da la diagonal completa`() {
        val width = 400f
        val height = 800f

        val result = maxDistanceToCorner(Offset(width, height), width, height)

        assertEquals(hypot(width, height), result, TOLERANCE)
    }

    @Test
    fun `maxDistanceToCorner con origen fuera de la caja sigue devolviendo la distancia mas larga a una esquina`() {
        // Origen a la izquierda del root (coordenada negativa) — puede pasar con
        // botones parcialmente fuera de pantalla; la formula no clampea el
        // origen, solo mide distancia real a la esquina mas lejana.
        val width = 300f
        val height = 300f
        val origin = Offset(-50f, 150f)

        val result = maxDistanceToCorner(origin, width, height)

        val expectedDx = width - origin.x // 350, mas lejos que origin.x (-50 -> maxOf da 350)
        val expectedDy = maxOf(origin.y, height - origin.y) // 150
        assertEquals(hypot(expectedDx, expectedDy), result, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.01f
    }
}
