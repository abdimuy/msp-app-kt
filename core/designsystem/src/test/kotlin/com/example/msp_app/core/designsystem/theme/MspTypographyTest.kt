package com.example.msp_app.core.designsystem.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Robolectric (necesita `R.font` + contexto Android real, ver
 * `unitTests.isIncludeAndroidResources = true` en `build.gradle.kts`).
 * Congela la tabla del brief (`task-3-brief.md`) contra regresiones — mismo
 * enfoque de [MspColorsTest]: cualquier valor que no matchee kollect 1:1 es
 * un bug.
 */
@OptIn(ExperimentalTextApi::class)
class MspTypographyTest : RobolectricTestBase() {

    private val type = mspTypography()

    // --- 1. amountHero: 36sp/ExtraBold, tracking -0.03em, lh 1.4x, prop ----

    @Test
    fun `amountHero es 36sp ExtraBold con figuras proporcionales`() {
        assertEquals(36.sp, type.amountHero.fontSize)
        assertEquals(FontWeight.ExtraBold, type.amountHero.fontWeight)
        assertEquals((-0.03).em, type.amountHero.letterSpacing)
        assertEquals((36 * 1.4).sp, type.amountHero.lineHeight)

        val features = type.amountHero.fontFeatureSettings.orEmpty()
        assertTrue("esperaba 'lnum' en '$features'", features.contains("lnum"))
        assertFalse("no esperaba 'tnum' en '$features'", features.contains("tnum"))
    }

    // --- 2. amountCard: tabular (tnum Y lnum) -------------------------------

    @Test
    fun `amountCard usa cifras tabulares (tnum y lnum)`() {
        val features = type.amountCard.fontFeatureSettings.orEmpty()
        assertTrue("esperaba 'tnum' en '$features'", features.contains("tnum"))
        assertTrue("esperaba 'lnum' en '$features'", features.contains("lnum"))
    }

    // --- 3. Manrope resuelve sin excepcion ----------------------------------

    @Test
    fun `Manrope resuelve un typeface sin excepcion`() {
        assertEquals(5, (Manrope as FontListFontFamily).fonts.size)

        val resolver = createFontFamilyResolver(ApplicationProvider.getApplicationContext())
        val resolved = resolver.resolve(Manrope, FontWeight.ExtraBold)

        assertNotNull(resolved.value)
    }

    // --- 4. Muestreo de estilos no-dinero contra la tabla -------------------

    @Test
    fun `overline es 12sp SemiBold con tracking +0,05em`() {
        assertEquals(12.sp, type.overline.fontSize)
        assertEquals(FontWeight.SemiBold, type.overline.fontWeight)
        assertEquals(0.05.em, type.overline.letterSpacing)
        assertEquals((12 * 1.4).sp, type.overline.lineHeight)
    }

    @Test
    fun `caption es 11sp Normal con cifras tabulares`() {
        assertEquals(11.sp, type.caption.fontSize)
        assertEquals(FontWeight.Normal, type.caption.fontWeight)
        assertEquals((11 * 1.4).sp, type.caption.lineHeight)

        val features = type.caption.fontFeatureSettings.orEmpty()
        assertTrue(features.contains("tnum"))
        assertTrue(features.contains("lnum"))
    }

    @Test
    fun `buttonLarge es 16sp ExtraBold con tracking -0,01em`() {
        assertEquals(16.sp, type.buttonLarge.fontSize)
        assertEquals(FontWeight.ExtraBold, type.buttonLarge.fontWeight)
        assertEquals((-0.01).em, type.buttonLarge.letterSpacing)
        assertEquals((16 * 1.4).sp, type.buttonLarge.lineHeight)
    }

    // --- 5. toMaterialTypography mapea la escala a M3 -----------------------

    @Test
    fun `toMaterialTypography mapea displayMedium a amountHero`() {
        val material = type.toMaterialTypography()

        assertEquals(type.amountHero, material.displayMedium)
        assertEquals(type.greeting, material.headlineLarge)
        assertEquals(type.caption, material.labelSmall)
    }
}
