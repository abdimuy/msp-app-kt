package com.example.msp_app.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El toggle de tema NO vibra, y eso es fidelidad a kollect, no un olvido.**
 *
 * El repo tiene la regla dura al revés —*"las acciones de dinero deben sentirse
 * físicas"*, spec §8.4— y ya cobró dos veces por no cumplirla (el CTA del dock
 * de venta y el de abono se pintaron a mano y perdieron el haptic; los cubre
 * `ElDineroSeSienteTest` en `:feature:pagos`). Así que al montar un control
 * nuevo la pregunta correcta es si le toca vibrar, y aquí la respuesta es no,
 * **medida en kollect**:
 *
 * ```
 * $ grep -c performHapticFeedback  <kollect-app>/core/designsystem/.../component/
 *   ThemeToggle.kt        0
 *   PrivacyEyeToggle.kt   0
 *   HeaderToggles.kt      0
 *   MiniActionButton.kt   1      ← control positivo del grep
 * ```
 *
 * El mismo comando sobre el mismo árbol encuentra haptics en `NumericKeypad`,
 * `MiniActionButton`, `PrimaryFieldButton`, `SaleCard`, `OutcomeGrid` y dos
 * pantallas: **el método encuentra haptics cuando existen**, así que su ausencia
 * en los tres archivos del cluster de toggles es real. Cambiar de tema no mueve
 * dinero y no es irreversible; vibrar ahí sería ruido que kollect no hace.
 *
 * ## El control positivo del grabador
 *
 * [MspPrimaryFieldButton] va en el MISMO árbol y con el MISMO grabador, y su tap
 * **sí** tiene que registrar `LongPress`. Sin él, un grabador roto (o un
 * `LocalHapticFeedback` que no llegara al subárbol) haría pasar esta prueba con
 * cualquier cableado, incluido uno que hubiera perdido el haptic del dinero.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElToggleDeTemaNoVibraTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val vibraciones = mutableListOf<HapticFeedbackType>()

    private val grabador = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            vibraciones += hapticFeedbackType
        }
    }

    private fun losDosControles() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                CompositionLocalProvider(LocalHapticFeedback provides grabador) {
                    Column {
                        MspThemeToggle(darkTheme = false, onToggle = {})
                        MspPrimaryFieldButton(
                            text = "abonar \$220",
                            onClick = {},
                            modifier = Modifier.testTag(DINERO_TAG)
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `el tap en el toggle de tema no vibra - kollect tampoco`() {
        losDosControles()

        composeTestRule.onNodeWithContentDescription(DESCRIPCION_A_OSCURO).performClick()

        assertEquals(
            "cambiar de tema no es una acción de dinero: no debe vibrar (1:1 kollect)",
            emptyList<HapticFeedbackType>(),
            vibraciones
        )
    }

    @Test
    fun `control positivo - el CTA de dinero del mismo arbol si vibra`() {
        losDosControles()

        composeTestRule.onNodeWithTag(DINERO_TAG).performClick()

        assertEquals(
            "si esto sale vacío el grabador miente y la prueba de arriba no mide nada",
            listOf(HapticFeedbackType.LongPress),
            vibraciones
        )
    }

    private companion object {
        const val DINERO_TAG = "cta_de_dinero"
    }
}
