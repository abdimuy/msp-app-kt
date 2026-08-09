package com.example.msp_app.core.designsystem.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/**
 * `MspTheme` expone los 5 grupos de tokens vía `CompositionLocal` y cambia
 * de paleta según `darkTheme`. Robolectric compose-test de lógica pura — el
 * golden visual vive en `screenshot/ThemeSwatchScreenshotTest`.
 */
class MspThemeTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `MspTheme light resuelve colors brand al azul del tema activo`() {
        var brand: Color? = null
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                brand = MspTheme.colors.brand
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(mspLightColors().brand, brand)
    }

    @Test
    fun `MspTheme dark resuelve colors brand del tema oscuro`() {
        var brand: Color? = null
        composeTestRule.setContent {
            MspTheme(darkTheme = true, animateColors = false) {
                brand = MspTheme.colors.brand
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(mspDarkColors().brand, brand)
        assertNotEquals(mspLightColors().brand, brand)
    }

    @Test
    fun `MspTheme expone type spacing shapes y motion via CompositionLocal`() {
        var amountHeroSize: TextUnit? = null
        var spacingMd: Dp? = null
        var cardShape: Shape? = null
        var standardSpring: SpringSpec<Float>? = null
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                amountHeroSize = MspTheme.type.amountHero.fontSize
                spacingMd = MspTheme.spacing.md
                cardShape = MspTheme.shapes.card
                standardSpring = MspTheme.motion.standard()
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(36.sp, amountHeroSize)
        assertEquals(16.dp, spacingMd)
        assertEquals(RoundedCornerShape(20.dp), cardShape)
        assertNotNull(standardSpring)
    }

    @Test(expected = IllegalStateException::class)
    fun `leer LocalMspColors fuera de MspTheme arroja error`() {
        composeTestRule.setContent {
            LocalMspColors.current
        }
    }
}
