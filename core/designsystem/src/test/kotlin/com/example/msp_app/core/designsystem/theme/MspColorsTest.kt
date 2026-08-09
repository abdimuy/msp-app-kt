package com.example.msp_app.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM puro (sin Robolectric ni Compose runtime): `androidx.compose.ui.graphics.Color`
 * es una `value class` sobre `ULong`, corre igual que cualquier tipo Kotlin
 * plano. Congela la tabla de hex del brief (task-2-brief.md) contra
 * regresiones — cualquier valor que no matchee kollect 1:1 (salvo los 6 hex
 * de marca + `heroProgressFill`) es un bug.
 */
class MspColorsTest {

    private val light = mspLightColors()
    private val dark = mspDarkColors()

    // --- 1. Marca: Azul A ---------------------------------------------------

    @Test
    fun `brand light es azul A 2563EB`() {
        assertEquals(Color(0xFF2563EB), light.brand)
    }

    @Test
    fun `brand dark es azul A 3B82F6`() {
        assertEquals(Color(0xFF3B82F6), dark.brand)
    }

    // --- 2. heroProgressFill mint-teal, igual en ambos temas ---------------

    @Test
    fun `heroProgressFill es el mint-teal dedicado en light`() {
        assertEquals(Color(0xFF6FE3C2), light.heroProgressFill)
    }

    @Test
    fun `heroProgressFill es el mismo mint-teal en dark`() {
        assertEquals(Color(0xFF6FE3C2), dark.heroProgressFill)
    }

    @Test
    fun `heroProgressFill NO es el verde de kollect`() {
        assertNotEquals(Color(0xFF7FE0A6), light.heroProgressFill)
        assertNotEquals(Color(0xFF7FE0A6), dark.heroProgressFill)
    }

    // --- 3. statusPaid conserva el verde semántico (no es la marca) --------

    @Test
    fun `statusPaid light es el verde semantico 177245, distinto de brand`() {
        assertEquals(Color(0xFF177245), light.statusPaid)
        assertNotEquals(light.brand, light.statusPaid)
    }

    // --- 4. lerpMspColors interpola de verdad -------------------------------

    @Test
    fun `lerp en fraction 0 devuelve el tema de inicio`() {
        assertMspColorsClose(light, lerpMspColors(light, dark, 0f))
    }

    @Test
    fun `lerp en fraction 1 devuelve el tema de destino`() {
        assertMspColorsClose(dark, lerpMspColors(light, dark, 1f))
    }

    @Test
    fun `lerp a fraction 0,5 cae entre ambos temas, no snapea`() {
        val mid = lerpMspColors(light, dark, 0.5f)

        val minRed = minOf(light.brand.red, dark.brand.red)
        val maxRed = maxOf(light.brand.red, dark.brand.red)
        assertTrue(
            "mid.brand.red=${mid.brand.red} fuera de [$minRed, $maxRed]",
            mid.brand.red in minRed..maxRed
        )
        assertNotEquals(light.brand.red, mid.brand.red)
        assertNotEquals(dark.brand.red, mid.brand.red)
    }

    // --- 5. Muestreo anti-regresión de neutros/status (1:1 kollect) --------

    @Test
    fun `outline light 1 a 1 kollect`() {
        assertEquals(Color(0xFFE4E8E6), light.outline)
    }

    @Test
    fun `danger light 1 a 1 kollect`() {
        assertEquals(Color(0xFF9F1239), light.danger)
    }

    @Test
    fun `onDanger dark 1 a 1 kollect (invertido a proposito, oscuro sobre color brillante)`() {
        assertEquals(Color(0xFF210A07), dark.onDanger)
    }

    @Test
    fun `statusTeal dark 1 a 1 kollect`() {
        assertEquals(Color(0xFF33B6C9), dark.statusTeal)
    }

    // --- 6. toColorScheme mapea marca -> roles M3 ---------------------------
    // Plano (sin Robolectric): lightColorScheme()/darkColorScheme() son
    // factories Kotlin normales, no @Composable — no requieren runtime de
    // Android. `toColorScheme` es `internal`, visible desde el source set de
    // test del mismo módulo.

    @Test
    fun `toColorScheme light mapea brand a primary`() {
        val scheme = light.toColorScheme(darkTheme = false)
        assertEquals(light.brand, scheme.primary)
        assertEquals(light.onBrand, scheme.onPrimary)
        assertEquals(light.statusOverdue, scheme.error)
    }

    @Test
    fun `toColorScheme dark mapea brand a primary y background OLED`() {
        val scheme = dark.toColorScheme(darkTheme = true)
        assertEquals(dark.brand, scheme.primary)
        assertEquals(Color(0xFF000000), scheme.background)
    }

    // --- helpers -------------------------------------------------------------

    /**
     * Comparación por canal con tolerancia: `lerpMspColors(a, b, 1f)` pasa por
     * aritmética float (`a + (b - a) * fraction`), que no garantiza igualdad
     * bit-a-bit contra `b` por redondeo IEEE754 — comparar todos los campos
     * así evita un test frágil sin debilitar la aserción semántica ("en los
     * extremos, el resultado ES el tema de inicio/destino").
     */
    private fun assertMspColorsClose(
        expected: MspColors,
        actual: MspColors,
        epsilon: Float = 1e-3f
    ) {
        expected.namedChannels().zip(actual.namedChannels()).forEach { (e, a) ->
            val (name, expectedColor) = e
            val (_, actualColor) = a
            assertColorClose(name, expectedColor, actualColor, epsilon)
        }
    }

    private fun assertColorClose(name: String, expected: Color, actual: Color, epsilon: Float) {
        assertTrue(
            "$name.red: esperado=${expected.red} real=${actual.red}",
            abs(expected.red - actual.red) <= epsilon
        )
        assertTrue(
            "$name.green: esperado=${expected.green} real=${actual.green}",
            abs(expected.green - actual.green) <= epsilon
        )
        assertTrue(
            "$name.blue: esperado=${expected.blue} real=${actual.blue}",
            abs(expected.blue - actual.blue) <= epsilon
        )
        assertTrue(
            "$name.alpha: esperado=${expected.alpha} real=${actual.alpha}",
            abs(expected.alpha - actual.alpha) <= epsilon
        )
    }

    private fun MspColors.namedChannels(): List<Pair<String, Color>> = listOf(
        "brand" to brand,
        "brand2" to brand2,
        "onBrand" to onBrand,
        "brandTint" to brandTint,
        "background" to background,
        "surface" to surface,
        "surface2" to surface2,
        "onSurface" to onSurface,
        "onSurfaceMuted" to onSurfaceMuted,
        "outline" to outline,
        "statusPaid" to statusPaid,
        "statusPaidTint" to statusPaidTint,
        "statusPartial" to statusPartial,
        "statusPartialTint" to statusPartialTint,
        "statusOverdue" to statusOverdue,
        "statusOverdueTint" to statusOverdueTint,
        "statusPending" to statusPending,
        "statusPendingTint" to statusPendingTint,
        "statusInfo" to statusInfo,
        "statusInfoTint" to statusInfoTint,
        "statusTeal" to statusTeal,
        "statusTealTint" to statusTealTint,
        "danger" to danger,
        "dangerTint" to dangerTint,
        "onDanger" to onDanger,
        "promise" to promise,
        "promiseTint" to promiseTint,
        "navSurface" to navSurface,
        "heroProgressFill" to heroProgressFill,
        "progressTrack" to progressTrack,
        "chartTrack" to chartTrack
    )
}
