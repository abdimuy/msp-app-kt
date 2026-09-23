package com.example.msp_app.feature.visitas.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.visitas.ui.components.DockDeLaVisita
import com.example.msp_app.feature.visitas.ui.components.GUARDAR_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * El CTA de guardar la visita va por el `MspPrimaryFieldButton` compartido, así
 * que **vibra** como cualquier CTA de campo del design system (spec §8.4). Se
 * pintaba a mano con un `Surface` y esa copia se quedaba sin el haptic.
 *
 * El **control positivo** es el botón apagado: con `enabled = false` el
 * `clickable` ni invoca el lambda, así que no puede haber vibración. Si las dos
 * pruebas dieran verde con el haptic desconectado, ninguna probaría nada — la
 * primera exige la vibración y la segunda exige su ausencia.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElDockDeLaVisitaSeSienteTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val vibraciones = mutableListOf<HapticFeedbackType>()

    private val hapticoGrabador = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            vibraciones += hapticFeedbackType
        }
    }

    private fun dock(habilitado: Boolean) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                CompositionLocalProvider(LocalHapticFeedback provides hapticoGrabador) {
                    DockDeLaVisita(
                        texto = "Guardar visita",
                        habilitado = habilitado,
                        pie = if (habilitado) null else "Elige un resultado",
                        onGuardar = {}
                    )
                }
            }
        }
    }

    @Test
    fun `el tap en guardar visita dispara el haptic de LongPress`() {
        dock(habilitado = true)

        assertEquals("antes del tap nadie vibró", emptyList<HapticFeedbackType>(), vibraciones)

        composeTestRule.onNodeWithTag(GUARDAR_TAG).performClick()

        assertEquals(
            listOf(HapticFeedbackType.LongPress),
            vibraciones
        )
    }

    @Test
    fun `control positivo - apagado no vibra`() {
        dock(habilitado = false)

        composeTestRule.onNodeWithTag(GUARDAR_TAG).performClick()

        assertEquals(
            "un botón apagado no puede vibrar: no llega a ejecutar el onClick",
            emptyList<HapticFeedbackType>(),
            vibraciones
        )
    }
}
