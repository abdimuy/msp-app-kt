package com.example.msp_app.feature.pagos.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * El cuerpo de la hoja "Ordenar por", montado SUELTO — sin el
 * `ModalBottomSheet` que lo envuelve en [HojaDeOrdenarPor]. Mismo motivo que
 * [CuerpoDeAbono]: `performClick()` bajo Robolectric no cruza a la ventana del
 * `Popup` donde M3 monta la hoja real.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class CuerpoDeOrdenarPorTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `trae las siete opciones`() {
        montar()

        OrdenDeLaLista.entries.forEach { orden ->
            composeTestRule.onNodeWithTag(
                "${OPCION_DE_ORDEN_TAG}_${orden.name}"
            ).assertIsDisplayed()
        }
    }

    @Test
    fun `tocar una opcion la elige`() {
        var elegido: OrdenDeLaLista? = null
        montar(onElegir = { elegido = it })

        composeTestRule.onNodeWithTag(
            "${OPCION_DE_ORDEN_TAG}_${OrdenDeLaLista.MAYOR_SALDO.name}"
        ).performClick()

        assertEquals(OrdenDeLaLista.MAYOR_SALDO, elegido)
    }

    private fun montar(
        seleccionado: OrdenDeLaLista = OrdenDeLaLista.COBRANZA,
        onElegir: (OrdenDeLaLista) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CuerpoDeOrdenarPor(seleccionado = seleccionado, onElegir = onElegir)
                }
            }
        }
    }
}
