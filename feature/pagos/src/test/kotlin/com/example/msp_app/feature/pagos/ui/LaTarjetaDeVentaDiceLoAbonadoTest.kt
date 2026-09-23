package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import com.example.msp_app.feature.pagos.ui.components.FilaDeVenta
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La tarjeta de una venta dice cuánto ha dado, no cuántas veces.**
 *
 * Decía *"18 de 24 abonos"*. El dueño pidió el cambio: parado en la puerta la
 * pregunta es de dinero, y el conteo ya lo dice —sin números y de un vistazo—
 * la barra de avance que va justo encima. Ahora el pie dice **"Abonado"** con
 * el total abonado.
 *
 * Lo que se fija aquí:
 *
 * 1. El pie **dice "Abonado" con el monto**, formateado como el resto de la
 *    tarjeta.
 * 2. El conteo **ya no aparece** — con control positivo de que el mismo
 *    selector sí lo vería si alguien lo volviera a pintar. Sin ese control, un
 *    selector roto pasaría en verde igual que una ausencia de verdad.
 * 3. El monto **se tapa con el interruptor de privacidad**, igual que el saldo
 *    de la misma tarjeta: taparle uno al cobrador sin taparle el otro dejaría
 *    abierta la mitad de la privacidad de la fila.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaTarjetaDeVentaDiceLoAbonadoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val venta: VentaDelCliente = PagosFixtures.detalleCliente().ventas.first()

    @Test
    fun `la tarjeta dice Abonado con el monto`() {
        monta()

        composeTestRule
            .onNodeWithText("Abonado " + formatMoneyMxn(venta.abonado.amount))
            .assertIsDisplayed()
    }

    @Test
    fun `el conteo de abonos ya no aparece`() {
        monta()

        assertEquals(
            "la tarjeta sigue contando abonos en vez de decir cuánto ha dado",
            0,
            cuantosConteniendo(conteoLegado())
        )
    }

    @Test
    fun `control positivo, el selector SI ve el conteo cuando alguien lo pinta`() {
        // La ausencia de arriba sólo vale si la misma búsqueda habría
        // encontrado el conteo estando ahí. Se monta a mano, junto a la
        // tarjeta, y se exige exactamente UNO: el del control, no el de la
        // tarjeta.
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                Column {
                    Text(conteoLegado())
                    Tarjeta()
                }
            }
        }

        assertEquals(
            "el selector de la ausencia no ve el conteo ni cuando está pintado",
            1,
            cuantosConteniendo(conteoLegado())
        )
    }

    @Test
    fun `con el ojo cerrado el abonado se enmascara`() {
        monta(ocultos = true)

        composeTestRule.onNodeWithText("Abonado $MASKED_MONEY").assertIsDisplayed()
        assertEquals(
            "el monto abonado quedó a la vista con la privacidad activada",
            0,
            cuantosConteniendo(formatMoneyMxn(venta.abonado.amount))
        )
    }

    private fun cuantosConteniendo(texto: String): Int =
        composeTestRule.onAllNodesWithText(texto, substring = true).fetchSemanticsNodes().size

    /** El pie que la tarjeta pintaba antes de este cambio. */
    private fun conteoLegado(): String = "${venta.abonosPagados} de ${venta.abonosTotales} abonos"

    private fun monta(ocultos: Boolean = false) {
        composeTestRule.setContent {
            MspTheme(animateColors = false) { Tarjeta(ocultos) }
        }
    }

    @Composable
    private fun Tarjeta(ocultos: Boolean = false) {
        FilaDeVenta(venta = venta, onAbrir = {}, ocultos = ocultos)
    }
}
