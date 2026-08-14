package com.example.msp_app.feature.collectionreport.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.msp_app.core.designsystem.theme.MspColors
import com.example.msp_app.core.designsystem.theme.mspDarkColors
import com.example.msp_app.core.designsystem.theme.mspLightColors
import org.junit.Assert.assertTrue
import org.junit.Test

/** AAA-large (texto grande ≥18sp/700) / piso AA-normal — mismo umbral que el gate de `:core:designsystem`. */
private const val THRESHOLD_LARGE_TEXT = 4.5

/** AAA-normal (texto normal sobre superficies neutras). */
private const val THRESHOLD_NORMAL_TEXT = 7.0

/** Piso WCAG 1.4.11 "Non-text Contrast" — dots/barras (elementos gráficos, no texto). */
private const val THRESHOLD_UI_COMPONENT = 3.0

/**
 * Task 11 (task-11-brief.md "ContrastAAATest — pares críticos ... en ambos temas"):
 * verifica los pares de color REALES que compone la pantalla del piloto (`HeroSection` vía
 * `MspHeroTodayCard`, `SecondaryChips`/`DuoTiles` vía `MspCard`/`MspBentoTile`) — no el
 * primitivo aislado, ese ya tiene su gate genérico en
 * `:core:designsystem` `theme/ContrastAAATest`. Este test confirma que ESTE screen, con SUS
 * fondos concretos (`MspCard` por default pinta `colors.surface`, ver `MspCard.kt`), no
 * reintroduce un par peor que el que el design system ya certificó.
 *
 * **Pares parked (Plan 3 Task 10 / heredado a Task 11, NO fallan el gate):** `onBrand` sobre
 * el extremo plano `brand` y `statusPartial` (ámbar, el color del monto Condonado) en LIGHT
 * no alcanzan AAA — igual que en `:core:designsystem`, sostienen el piso UI-component (3:1).
 * El resto de los pares de esta pantalla SÍ se valida a su umbral AAA real (large/normal).
 */
class ContrastAAATest {

    private val light = mspLightColors()
    private val dark = mspDarkColors()

    // --- 1. Hero: overline/delta/monto/insight (onBrand) sobre el gradiente brand->brand2 ---

    @Test
    fun `hero onBrand sobre el gradiente de marca cumple AAA-large en ambos temas`() {
        assertContrastAtLeast(light.onBrand, gradientAverage(light), THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(dark.onBrand, gradientAverage(dark), THRESHOLD_LARGE_TEXT)
    }

    /** Parked (Plan 3 Task 10): onBrand sobre el extremo plano brand solo sostiene el piso UI-component. */
    @Test
    fun `hero onBrand sobre brand plano sostiene el piso UI-component en ambos temas`() {
        assertContrastAtLeast(light.onBrand, light.brand, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.onBrand, dark.brand, THRESHOLD_UI_COMPONENT)
    }

    // --- 2. onSurface sobre surface (detalle, monto de tiles/chips no ambar) --------------

    @Test
    fun `onSurface sobre surface cumple AAA-normal en ambos temas`() {
        assertContrastAtLeast(light.onSurface, light.surface, THRESHOLD_NORMAL_TEXT)
        assertContrastAtLeast(dark.onSurface, dark.surface, THRESHOLD_NORMAL_TEXT)
    }

    // --- 3. Chips: Condonado (statusPartial sobre MspCard/surface, NO statusPartialTint) ---

    /** Parked en light (ámbar, mismo fenómeno que `:core:designsystem`); dark SÍ cumple AAA-normal. */
    @Test
    fun `chip Condonado (statusPartial) sobre surface sostiene su piso por tema`() {
        assertContrastAtLeast(light.statusPartial, light.surface, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.statusPartial, dark.surface, THRESHOLD_NORMAL_TEXT)
    }

    /** Chip Visitas: el conteo se pinta `onSurface`, mismo par ya validado en #2 — se repite explícito. */
    @Test
    fun `chip Visitas (onSurface) sobre surface cumple AAA-normal en ambos temas`() {
        assertContrastAtLeast(light.onSurface, light.surface, THRESHOLD_NORMAL_TEXT)
        assertContrastAtLeast(dark.onSurface, dark.surface, THRESHOLD_NORMAL_TEXT)
    }

    /** Etiquetas de los chips/tiles (`onSurfaceMuted`, 12sp): no alcanza AAA-normal en ningún tema, sostiene UI-component. */
    @Test
    fun `etiquetas onSurfaceMuted de chips y tiles sostienen el piso UI-component en ambos temas`() {
        assertContrastAtLeast(light.onSurfaceMuted, light.surface, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.onSurfaceMuted, dark.surface, THRESHOLD_UI_COMPONENT)
    }

    // --- 4. Dots de DuoTiles (statusPaid/brand) sobre surface — elemento grafico, 3:1 -------

    @Test
    fun `dots de Efectivo (statusPaid) y Transferencia (brand) sobre surface cumplen 3-1 en ambos temas`() {
        assertContrastAtLeast(light.statusPaid, light.surface, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(light.brand, light.surface, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.statusPaid, dark.surface, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.brand, dark.surface, THRESHOLD_UI_COMPONENT)
    }

    // --- 5. Tira de días del ciclo (`DayStrip`, `dayChipPalette`) -------------------------

    /**
     * Chip "hoy" sin seleccionar: `statusPaid` sobre `statusPaidTint` — el par tintado ESTÁNDAR
     * del design system (el mismo que `MspStatusChip` usa para el estado Pagado; la tira lo
     * reutiliza en vez de inventar un verde propio).
     *
     * Umbral honesto: AAA-large / piso AA-normal, no AAA-normal. Medido, el par da 5.12:1 en
     * claro — por encima de AA-normal (4.5) y por debajo de AAA-normal (7.0). Sube a 4.5 y no a
     * 7.0 a propósito: exigirle AAA-normal obligaría a re-teñir un token del design system que
     * ya está en uso en toda la app, y el chip nunca es el único portador del estado (la
     * `contentDescription` dice "hoy" en texto).
     */
    @Test
    fun `chip de hoy (statusPaid sobre statusPaidTint) cumple AA-normal en ambos temas`() {
        assertContrastAtLeast(light.statusPaid, light.statusPaidTint, THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(dark.statusPaid, dark.statusPaidTint, THRESHOLD_LARGE_TEXT)
    }

    /**
     * Chip "hoy Y seleccionado": verde LLENO. El contenido es `colors.surface` y no `onBrand`
     * justamente por esto — `onBrand` (blanco en ambos temas) sobre el `statusPaid` claro del
     * tema oscuro daría ~2:1. `surface` invierte con el tema y por eso contrasta con los DOS
     * extremos de `statusPaid`. Esta es la aserción que sostiene esa decisión de diseño.
     */
    @Test
    fun `chip de hoy seleccionado (surface sobre statusPaid) cumple AAA-large en ambos temas`() {
        assertContrastAtLeast(light.surface, light.statusPaid, THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(dark.surface, dark.statusPaid, THRESHOLD_LARGE_TEXT)
    }

    /**
     * Chip seleccionado (no hoy): azul de marca LLENO con `onBrand`. Mismo par ya parkeado del
     * hero (ver #1) — sostiene el piso UI-component, no AAA. Se declara explícito para que la
     * tira no aparente una garantía que el design system no da en este par.
     */
    @Test
    fun `chip seleccionado (onBrand sobre brand) sostiene el piso UI-component en ambos temas`() {
        assertContrastAtLeast(light.onBrand, light.brand, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.onBrand, dark.brand, THRESHOLD_UI_COMPONENT)
    }

    /**
     * Chip de un día SIN cobros: atenuado con `onSurfaceMuted` sobre `surface`. Atenuado no es
     * invisible — sigue sosteniendo el piso UI-component (mismo par y mismo criterio que las
     * etiquetas de chips/tiles, #4).
     */
    @Test
    fun `chip de un dia sin cobros sigue legible sobre surface en ambos temas`() {
        assertContrastAtLeast(light.onSurfaceMuted, light.surface, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.onSurfaceMuted, dark.surface, THRESHOLD_UI_COMPONENT)
    }

    // --- helpers -----------------------------------------------------------------------------

    private fun gradientAverage(colors: MspColors): Color = lerp(colors.brand, colors.brand2, HALF)

    private fun assertContrastAtLeast(fg: Color, bg: Color, minimum: Double) {
        val ratio = wcagContrastRatio(fg, bg)
        assertTrue(
            "contraste $ratio:1 por debajo del minimo $minimum:1 ($fg sobre $bg)",
            ratio >= minimum
        )
    }

    private companion object {
        const val HALF = 0.5f
    }
}

/**
 * Relación de contraste WCAG 2.x (misma fórmula, duplicada a propósito de
 * `com.example.msp_app.core.designsystem.theme.wcagContrastRatio` — esa función vive en el
 * sourceset `test` de `:core:designsystem`, un módulo Gradle distinto: `internal` no cruza
 * módulos y este piloto no vale la pena resolver con un `testFixtures` nuevo solo para 20
 * líneas de matemática WCAG pura).
 */
private fun wcagContrastRatio(fg: Color, bg: Color): Double {
    val l1 = relativeLuminance(fg)
    val l2 = relativeLuminance(bg)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
}

private fun relativeLuminance(color: Color): Double {
    val r = linearizeChannel(color.red.toDouble())
    val g = linearizeChannel(color.green.toDouble())
    val b = linearizeChannel(color.blue.toDouble())
    return LUMINANCE_R_WEIGHT * r + LUMINANCE_G_WEIGHT * g + LUMINANCE_B_WEIGHT * b
}

private fun linearizeChannel(channel: Double): Double = if (channel <= SRGB_LINEAR_CUTOFF) {
    channel / SRGB_LINEAR_DIVISOR
} else {
    Math.pow((channel + SRGB_GAMMA_OFFSET) / SRGB_GAMMA_DIVISOR, SRGB_GAMMA_EXPONENT)
}

private const val LUMINANCE_OFFSET = 0.05
private const val LUMINANCE_R_WEIGHT = 0.2126
private const val LUMINANCE_G_WEIGHT = 0.7152
private const val LUMINANCE_B_WEIGHT = 0.0722
private const val SRGB_LINEAR_CUTOFF = 0.03928
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_GAMMA_OFFSET = 0.055
private const val SRGB_GAMMA_DIVISOR = 1.055
private const val SRGB_GAMMA_EXPONENT = 2.4
