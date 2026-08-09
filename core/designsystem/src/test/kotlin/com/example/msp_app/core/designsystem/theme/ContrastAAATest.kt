package com.example.msp_app.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Umbral WCAG "AAA-large"/"AA-normal" (mismo valor numérico, criterios
 * distintos — ver cada aserción): texto grande (≥18sp/700, la lectura
 * adoptada en task-10-brief.md "Parked: umbrales AAA") o texto normal en su
 * piso AA.
 */
private const val THRESHOLD_LARGE_TEXT = 4.5

/** Umbral WCAG "AAA-normal": texto de tamaño normal sobre superficies neutras. */
private const val THRESHOLD_NORMAL_TEXT = 7.0

/**
 * Umbral WCAG 1.4.11 "Non-text Contrast" (elementos gráficos, p. ej. una
 * barra de progreso) — también el piso "AA-large" para texto pequeño que no
 * alcanza [THRESHOLD_LARGE_TEXT] (ver estatus ámbar/teal abajo).
 */
private const val THRESHOLD_UI_COMPONENT = 3.0

/**
 * Validación de contraste WCAG AAA sobre los pares críticos del design
 * system Msp (Task 10, spec §5). JVM puro, igual que [MspColorsTest] —
 * [Color] es una `value class` plana, no necesita Robolectric.
 *
 * **Lectura de umbrales adoptada (task-10-brief.md, "Parked: umbrales AAA"):**
 * con Azul A (`brand #2563EB` light / `#3B82F6` dark) el par `onBrand`/`brand`
 * no llega a AAA-normal (7:1) en ningún punto del gradiente, así que el
 * criterio real por par es:
 * - **AAA-large (4.5:1)** para el monto hero (`amountHero`, 36sp/800 —
 *   texto grande) sobre el **promedio** del gradiente `brand→brand2`: el
 *   punto que representa dónde cae visualmente el texto centrado sobre
 *   [brandGradientBackground][com.example.msp_app.core.designsystem.component.brandGradientBackground].
 * - **piso UI-component (3:1)** para `onBrand` sobre el extremo **plano**
 *   `brand` — el mismo token respalda además `MspPrimaryFieldButton.Primary`
 *   (fill sólido `brand`, sin gradiente) y es el punto de peor contraste del
 *   gradiente en ambos temas; en dark ese extremo (`onBrand`/`brand` ≈3.68:1)
 *   no alcanza ni AAA-large, así que el piso duro que SÍ debe sostener en
 *   ambos temas es el de elemento no-textual.
 * - **UI-component (3:1)** para `heroProgressFill` sobre el gradiente — es un
 *   elemento gráfico (barra), no texto.
 * - **AAA-normal (7:1)** para `onSurface` sobre `surface`/`background` — texto
 *   normal sobre superficies neutras, el caso que Azul A no toca.
 * - `status*`/`status*Tint` (chips, texto `chipLabel` 12sp Bold — normal, no
 *   grande): la paleta 1:1-kollect heredada da 4 de 6 pares ≥4.5:1 en ambos
 *   temas (`statusPaid`/`statusOverdue`/`statusPending`/`statusInfo`), pero
 *   `statusPartial` (ámbar) y `statusTeal` solo llegan a AA-normal en dark —
 *   en light caen a ≈3.7:1/4.2:1, por debajo incluso de AA-normal (4.5:1).
 *   Mismo fenómeno que el Parked de arriba (un matiz de marca/estado que no
 *   alcanza el umbral AAA con el hue heredado), extendido aquí a los dos
 *   colores de estado menos saturados en luminancia; la mitigación real es
 *   la regla dura de [MspStatusChip][com.example.msp_app.core.designsystem.component.MspStatusChip]
 *   ("nunca solo color" — ícono + texto siempre acompañan, ver
 *   `component/MspStatusChipTest`), no un contraste perfecto. El piso que
 *   estos dos SÍ sostienen en ambos temas es UI-component (3:1).
 */
class ContrastAAATest {

    private val light = mspLightColors()
    private val dark = mspDarkColors()

    // --- 1. Monto hero sobre el gradiente (AAA-large) -----------------------

    @Test
    fun `onBrand sobre el promedio del gradiente brand-brand2 cumple AAA-large en light`() {
        assertContrastAtLeast(light.onBrand, gradientAverage(light), THRESHOLD_LARGE_TEXT)
    }

    @Test
    fun `onBrand sobre el promedio del gradiente brand-brand2 cumple AAA-large en dark`() {
        assertContrastAtLeast(dark.onBrand, gradientAverage(dark), THRESHOLD_LARGE_TEXT)
    }

    // --- 2. onBrand sobre el extremo plano brand (piso UI-component) --------

    @Test
    fun `onBrand sobre brand plano cumple el piso UI-component en light`() {
        assertContrastAtLeast(light.onBrand, light.brand, THRESHOLD_UI_COMPONENT)
    }

    @Test
    fun `onBrand sobre brand plano cumple el piso UI-component en dark`() {
        assertContrastAtLeast(dark.onBrand, dark.brand, THRESHOLD_UI_COMPONENT)
    }

    // --- 3. heroProgressFill sobre el gradiente (elemento gráfico, 3-1) -----

    @Test
    fun `heroProgressFill sobre el gradiente de marca cumple 3-1 en light`() {
        assertContrastAtLeast(
            light.heroProgressFill,
            gradientAverage(light),
            THRESHOLD_UI_COMPONENT
        )
    }

    @Test
    fun `heroProgressFill sobre el gradiente de marca cumple 3-1 en dark`() {
        assertContrastAtLeast(dark.heroProgressFill, gradientAverage(dark), THRESHOLD_UI_COMPONENT)
    }

    // --- 4. onSurface sobre superficies neutras (AAA-normal) ----------------

    @Test
    fun `onSurface sobre surface cumple AAA-normal en light`() {
        assertContrastAtLeast(light.onSurface, light.surface, THRESHOLD_NORMAL_TEXT)
    }

    @Test
    fun `onSurface sobre surface cumple AAA-normal en dark`() {
        assertContrastAtLeast(dark.onSurface, dark.surface, THRESHOLD_NORMAL_TEXT)
    }

    @Test
    fun `onSurface sobre background cumple AAA-normal en light`() {
        assertContrastAtLeast(light.onSurface, light.background, THRESHOLD_NORMAL_TEXT)
    }

    @Test
    fun `onSurface sobre background cumple AAA-normal en dark`() {
        assertContrastAtLeast(dark.onSurface, dark.background, THRESHOLD_NORMAL_TEXT)
    }

    // --- 5. status*/status*Tint de los chips ---------------------------------

    @Test
    fun `statusPaid, statusOverdue, statusPending e statusInfo cumplen AAA-large sobre su tint en light`() {
        assertContrastAtLeast(light.statusPaid, light.statusPaidTint, THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(light.statusOverdue, light.statusOverdueTint, THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(light.statusPending, light.statusPendingTint, THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(light.statusInfo, light.statusInfoTint, THRESHOLD_LARGE_TEXT)
    }

    @Test
    fun `statusPaid, statusOverdue, statusPending e statusInfo cumplen AAA-large sobre su tint en dark`() {
        assertContrastAtLeast(dark.statusPaid, dark.statusPaidTint, THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(dark.statusOverdue, dark.statusOverdueTint, THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(dark.statusPending, dark.statusPendingTint, THRESHOLD_LARGE_TEXT)
        assertContrastAtLeast(dark.statusInfo, dark.statusInfoTint, THRESHOLD_LARGE_TEXT)
    }

    @Test
    fun `statusPartial y statusTeal, los dos matices que no llegan a AAA-large, sostienen el piso UI-component`() {
        assertContrastAtLeast(light.statusPartial, light.statusPartialTint, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(light.statusTeal, light.statusTealTint, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.statusPartial, dark.statusPartialTint, THRESHOLD_UI_COMPONENT)
        assertContrastAtLeast(dark.statusTeal, dark.statusTealTint, THRESHOLD_UI_COMPONENT)
    }

    // --- helpers -------------------------------------------------------------

    private fun gradientAverage(colors: MspColors): Color = lerp(colors.brand, colors.brand2, HALF)

    private fun assertContrastAtLeast(fg: Color, bg: Color, minimum: Double) {
        val ratio = wcagContrastRatio(fg, bg)
        assertTrue(
            "contraste $ratio:1 por debajo del mínimo $minimum:1 ($fg sobre $bg)",
            ratio >= minimum
        )
    }

    private companion object {
        const val HALF = 0.5f
    }
}

/**
 * Relación de contraste WCAG 2.x: `(L1 + 0.05) / (L2 + 0.05)`, con `L1`/`L2`
 * las luminancias relativas (la mayor y la menor) de [fg]/[bg] — fórmula
 * exacta del spec (§1.4.3/§1.4.6), independiente de tema/plataforma. Pura,
 * testeable sin Robolectric (mismo criterio que
 * [com.example.msp_app.core.designsystem.component.brandGradientEndpoints]).
 */
internal fun wcagContrastRatio(fg: Color, bg: Color): Double {
    val l1 = relativeLuminance(fg)
    val l2 = relativeLuminance(bg)
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + LUMINANCE_OFFSET) / (darker + LUMINANCE_OFFSET)
}

/** Luminancia relativa sRGB (WCAG §1.4.3): cada canal se linealiza antes de ponderar. */
private fun relativeLuminance(color: Color): Double {
    val r = linearizeChannel(color.red.toDouble())
    val g = linearizeChannel(color.green.toDouble())
    val b = linearizeChannel(color.blue.toDouble())
    return LUMINANCE_R_WEIGHT * r + LUMINANCE_G_WEIGHT * g + LUMINANCE_B_WEIGHT * b
}

/** Transferencia sRGB -> lineal de un canal ya normalizado en `[0,1]` (WCAG §1.4.3). */
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
