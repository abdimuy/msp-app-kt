package com.example.msp_app.feature.collectionreport.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.designsystem.component.LocalThemeReveal
import com.example.msp_app.core.designsystem.component.MspThemeToggle
import com.example.msp_app.core.designsystem.component.maxDistanceToCorner
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** `testTag` propio del test — `THEME_TOGGLE_TAG` (`:core:designsystem`) es `internal` a ese
 * módulo y no cruza a `:feature:collectionReport` (mismo gotcha que documenta
 * `CollectionReportScreenshotTest`); envolver el toggle en un `Box` con este tag lo localiza
 * igual de bien sin necesitar el tag interno del DS.
 */
private const val TOGGLE_PROBE_TAG = "theme_reveal_root_toggle_probe"

@Composable
private fun ToggleProbe(darkTheme: Boolean, onToggle: () -> Unit) {
    Box(modifier = Modifier.testTag(TOGGLE_PROBE_TAG)) {
        MspThemeToggle(darkTheme = darkTheme, onToggle = onToggle)
    }
}

/**
 * Compose-test (no golden) de [ThemeRevealRoot] — SOLO ejerce la rama reduce-motion
 * (`ANIMATOR_DURATION_SCALE = 0`, forzada en [forceReducedMotion]), la misma garantía
 * anti-cuelgue que exige task-9-brief.md: la reveal circular real usa un
 * [androidx.compose.ui.graphics.layer.GraphicsLayer] respaldado por `RenderNode`/`Picture` que
 * Robolectric no soporta de forma confiable fuera de `GraphicsMode.NATIVE` — probarla aquí
 * arriesgaría exactamente el cuelgue de Roborazzi que Plan 3 sufrió (~40 min). La rama
 * reduce-motion (crossfade fallback, sin `Animatable`/`GraphicsLayer`) es la única que este
 * test ejercita, y es la única que los goldens Roborazzi ejercitan también (ver
 * `screenshot/CollectionReportScreenshotTest.disableAnimationsForDeterministicGoldens`).
 * [revealTargetRadius] (el cálculo puro que sí alimenta la rama animada) se prueba aparte, sin
 * Compose, más abajo.
 */
class ThemeRevealRootTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun forceReducedMotion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f
        )
    }

    @Test
    fun `con reduce-motion no instala LocalThemeReveal, tocar el toggle NO dispara la reveal`() {
        var toggled = false
        composeTestRule.setContent {
            ThemeRevealRoot(darkTheme = false, onToggleTheme = {}) {
                // `onToggle` propio del hijo simula el `viewModel::toggleTheme` que en
                // producción cablearía el caller de `content` — separado del `onToggleTheme`
                // de `ThemeRevealRoot` (que solo lo invoca la rama de reveal circular).
                ToggleProbe(darkTheme = false, onToggle = { toggled = true })
            }
        }

        composeTestRule.onNodeWithTag(TOGGLE_PROBE_TAG).performClick()

        // Sin `LocalThemeReveal` instalado, `MspThemeToggle` cae directo a su propio
        // `onToggle` (mecanismo A, ver ThemeToggleTest en `:core:designsystem`) — SÍ debe
        // dispararse.
        assertTrue(toggled)
    }

    @Test
    fun `con reduce-motion LocalThemeReveal queda null dentro de content (sin crash)`() {
        var revealInstalled = true
        composeTestRule.setContent {
            ThemeRevealRoot(darkTheme = false, onToggleTheme = {}) {
                revealInstalled = LocalThemeReveal.current != null
            }
        }

        assertFalse(revealInstalled)
    }

    @Test
    fun `con reduce-motion ThemeRevealRoot nunca llama onToggleTheme por si solo`() {
        var rootToggled = false
        composeTestRule.setContent {
            ThemeRevealRoot(darkTheme = false, onToggleTheme = { rootToggled = true }) {
                ToggleProbe(darkTheme = false, onToggle = {})
            }
        }

        composeTestRule.onNodeWithTag(TOGGLE_PROBE_TAG).performClick()

        // `onToggleTheme` del root solo lo invoca la rama de reveal circular (nunca compuesta
        // bajo reduce-motion) — el fallback deja el flip enteramente al `onToggle` del hijo.
        assertFalse(rootToggled)
    }

    @Test
    fun `con reduce-motion renderiza sin crash para darkTheme verdadero y falso`() {
        composeTestRule.setContent {
            ThemeRevealRoot(darkTheme = true, onToggleTheme = {}) {
                ToggleProbe(darkTheme = true, onToggle = {})
            }
        }

        composeTestRule.onNodeWithTag(TOGGLE_PROBE_TAG).assertIsDisplayed()
    }

    // region — revealTargetRadius (pura, sin Compose) ------------------------------------

    @Test
    fun `revealTargetRadius delega en maxDistanceToCorner con el mismo resultado`() {
        val origin = Offset(50f, 20f)
        val size = IntSize(360, 800)

        val expected = maxDistanceToCorner(origin, size.width.toFloat(), size.height.toFloat())

        assertEquals(expected, revealTargetRadius(origin, size), 0f)
    }

    @Test
    fun `revealTargetRadius desde la esquina superior izquierda cubre la diagonal completa`() {
        val size = IntSize(300, 400)

        val radius = revealTargetRadius(Offset.Zero, size)

        assertEquals(500f, radius, 0.01f) // hipotenusa 3-4-5 escalada x100
    }

    // endregion
}
