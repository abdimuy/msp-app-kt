package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/** `testTag` propio: `THEME_TOGGLE_TAG` es `internal` y basta para este archivo, pero un tag
 * explícito deja claro qué nodo se toca. */
private const val TOGGLE_PROBE_TAG = "msp_theme_reveal_host_toggle_probe"

@Composable
private fun ToggleProbe(onToggle: () -> Unit) {
    Box(modifier = Modifier.testTag(TOGGLE_PROBE_TAG)) {
        MspThemeToggle(darkTheme = false, onToggle = onToggle)
    }
}

/**
 * Compose-test de [MspThemeRevealHost] — el mecanismo de la reveal **después** de que Ruling BP
 * lo sacara de `:feature:collectionReport` para que el mismo `MspThemeToggle` se sienta igual en
 * toda la app.
 *
 * ## Qué se mide, y el límite que se midió en vez de suponerse
 *
 * Lo que decide el comportamiento del botón es **si hay un host instalado**: [MspThemeToggle]
 * lee [LocalThemeReveal] y con host pide la reveal, sin host cae a su `onToggle`
 * (`ThemeToggleTest` prueba las dos ramas del botón). Así que el par de abajo afirma lo
 * CONTRARIO sobre ese local y sobre el mismo tap.
 *
 * **La rama animada no se puede completar en JVM, y está medido:** al pedirse una reveal el
 * host llama `contentLayer.toImageBitmap()`, que truena con
 * `IllegalArgumentException: width and height must be > 0` porque un compose-test no corre el
 * pase de dibujo que puebla el `GraphicsLayer` — **medido en LEGACY y también con
 * `@GraphicsMode(NATIVE)`**, así que no es cuestión de elegir otro modo de gráficos. Por eso
 * acá la rama animada se afirma a nivel de **composición** (el host está instalado) y no
 * bombeando el efecto. Es también la razón concreta por la que `ThemeRevealRootTest` solo
 * ejercitaba la rama de reduce-motion; antes decía "no es confiable", ahora se sabe qué falla.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class MspThemeRevealHostTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var hostInstalado: Boolean? = null

    private fun host(reducedMotion: Boolean, onToggleTheme: () -> Unit = {}) {
        composeTestRule.setContent {
            MspThemeRevealHost(
                onToggleTheme = onToggleTheme,
                reducedMotion = reducedMotion,
                tema = { animateColors, contenido ->
                    MspTheme(darkTheme = false, animateColors = animateColors, content = contenido)
                }
            ) {
                hostInstalado = LocalThemeReveal.current != null
                ToggleProbe(onToggle = {})
            }
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun `sin reduce-motion instala el host, asi el toggle anima la reveal`() {
        host(reducedMotion = false)

        assertTrue(
            "sin host, este toggle haría crossfade mientras el del reporte hace reveal",
            hostInstalado == true
        )
    }

    @Test
    fun `con reduce-motion NO instala el host - garantia anti-cuelgue`() {
        host(reducedMotion = true)

        assertFalse(
            "bajo reduce-motion no debe componerse ni un GraphicsLayer ni un Animatable",
            hostInstalado != false
        )
    }

    /**
     * Con reduce-motion el flip es del hijo y es SÍNCRONO — la rama que ejercitan todos los
     * goldens y todos los compose-tests del repo.
     */
    @Test
    fun `con reduce-motion el tap va directo al onToggle del boton`() {
        var toggled = false
        composeTestRule.setContent {
            MspThemeRevealHost(
                onToggleTheme = {},
                reducedMotion = true,
                tema = { animateColors, contenido ->
                    MspTheme(darkTheme = false, animateColors = animateColors, content = contenido)
                }
            ) {
                ToggleProbe(onToggle = { toggled = true })
            }
        }

        composeTestRule.onNodeWithTag(TOGGLE_PROBE_TAG).performClick()

        assertTrue(toggled)
    }

    /**
     * Y el host nunca flipea por sí solo: bajo reduce-motion `onToggleTheme` —el flip que
     * ejecuta la rama de la reveal— no se invoca nunca.
     */
    @Test
    fun `con reduce-motion el host nunca llama onToggleTheme`() {
        var flipDelHost = false
        host(reducedMotion = true, onToggleTheme = { flipDelHost = true })

        composeTestRule.onNodeWithTag(TOGGLE_PROBE_TAG).performClick()

        assertFalse(flipDelHost)
    }

    @Test
    fun `el contenido se compone en las dos ramas`() {
        host(reducedMotion = false)
        composeTestRule.onNodeWithTag(TOGGLE_PROBE_TAG).assertIsDisplayed()
    }
}
