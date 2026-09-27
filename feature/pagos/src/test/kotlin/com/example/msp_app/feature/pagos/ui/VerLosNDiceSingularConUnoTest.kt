package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.VerLosContactos
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **"Ver los N" dice singular con uno, plural con más de uno.**
 *
 * "Ver los 1 contactos" y "Ver los 1 abonos" leen mal — el mismo defecto que
 * ya se había cerrado en el subtítulo de la bitácora (`"1 contacto"` vs
 * `"N contactos"`) seguía vivo en los dos enlaces "ver los N" del detalle de
 * venta y la bitácora.
 *
 * Cada caso trae su control positivo: con dos o más, el texto sigue en
 * plural. Sin el control, un `if` que SIEMPRE devolviera singular pasaría el
 * primer test sin arreglar nada.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class VerLosNDiceSingularConUnoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `VerLosContactos dice Ver 1 contacto en singular`() {
        montarVerLosContactos(cuantos = 1)

        composeTestRule.onNodeWithText("Ver 1 contacto").assertExists()
    }

    @Test
    fun `control positivo - VerLosContactos con dos sigue en plural`() {
        montarVerLosContactos(cuantos = 2)

        composeTestRule.onNodeWithText("Ver los 2 contactos").assertExists()
    }

    @Test
    fun `ver los N abonos dice Ver 1 abono en singular`() {
        montarLineaDeLaVenta(totalPagos = 1)

        composeTestRule.onNodeWithText("Ver 1 abono").assertExists()
    }

    @Test
    fun `control positivo - ver los N abonos con dos sigue en plural`() {
        montarLineaDeLaVenta(totalPagos = 2)

        composeTestRule.onNodeWithText("Ver los 2 abonos").assertExists()
    }

    private fun montarVerLosContactos(cuantos: Int) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                VerLosContactos(cuantos = cuantos, onVer = {})
            }
        }
    }

    private fun montarLineaDeLaVenta(totalPagos: Int) {
        val base = PagosFixtures.detalleVenta()
        val detalle = base.copy(historial = base.historial.copy(totalPagos = totalPagos))
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                LineaDeLaVenta(detalle = detalle, onVerAbonos = {}, onVerContactos = {})
            }
        }
    }
}
