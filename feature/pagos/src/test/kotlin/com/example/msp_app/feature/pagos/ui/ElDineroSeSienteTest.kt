package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.AccionDeNotas
import com.example.msp_app.feature.pagos.ui.components.CTA_NOTAS_TAG
import com.example.msp_app.feature.pagos.ui.components.CTA_PRIMARIO_TAG
import com.example.msp_app.feature.pagos.ui.components.CTA_VISITA_TAG
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **"Abonar $NNN" tiene que vibrar.** El design system lo declara regla dura
 * —*"las acciones de dinero deben sentirse físicas"* (spec §8.4, KDoc de
 * `PrimaryFieldButton`)— y el CTA primario del dock de venta es exactamente
 * eso: la puerta al abono.
 *
 * Existe porque el dock se pintaba a mano con un `Surface` que copiaba la
 * receta visual del componente compartido **y se quedaba sin el haptic**: un
 * defecto invisible para los goldens, que no fotografían vibraciones. Sin este
 * test, volver a pintarlo a mano pasaría el gate entero en verde.
 *
 * El **control positivo** son los otros dos slots: "visita" y "notas" no son
 * acciones de dinero, no van por el componente compartido y **no** deben
 * vibrar. Si el grabador de esta prueba registrara cualquier tap, los tres
 * casos darían igual y la afirmación no probaría nada.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElDineroSeSienteTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val vibraciones = mutableListOf<HapticFeedbackType>()

    private val hapticoGrabador = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            vibraciones += hapticFeedbackType
        }
    }

    private fun dockDeVenta() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                CompositionLocalProvider(LocalHapticFeedback provides hapticoGrabador) {
                    DockDeAcciones(
                        textoPrimario = "Abonar \$220",
                        onPrimario = {},
                        onVisita = {},
                        notas = AccionDeNotas(onAbrir = {}, conContenido = false, advierte = false)
                    )
                }
            }
        }
    }

    @Test
    fun `el tap en abonar dispara el haptic de LongPress`() {
        dockDeVenta()

        assertEquals("antes del tap nadie vibró", emptyList<HapticFeedbackType>(), vibraciones)

        composeTestRule.onNodeWithTag(CTA_PRIMARIO_TAG).performClick()

        assertEquals(
            "abonar es una acción de dinero: tiene que sentirse (spec §8.4)",
            listOf(HapticFeedbackType.LongPress),
            vibraciones
        )
    }

    @Test
    fun `control positivo - los slots que no son dinero no vibran`() {
        dockDeVenta()

        composeTestRule.onNodeWithTag(CTA_VISITA_TAG).performClick()
        composeTestRule.onNodeWithTag(CTA_NOTAS_TAG).performClick()

        assertEquals(
            "solo el CTA de dinero vibra; si vibran los tres, el grabador miente",
            emptyList<HapticFeedbackType>(),
            vibraciones
        )
    }
}
