package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.component.MASKED_MONEY
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_PRIMER_ABONO_TAG
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import com.example.msp_app.feature.pagos.ui.components.TEXTO_DE_VENDIDA_TAG
import com.example.msp_app.feature.pagos.ui.components.textoVendida
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Una venta sin ningún abono se marca "Nueva" en la lista** (decisión del
 * dueño, 2026-10-05, mock "Decidido · v3"): insignia en el encabezado, punto
 * junto al nombre del mueble, chip "Primer abono $X" en vez del estado, y
 * "Vendida hace N días" en vez de la pastilla de atrasos.
 *
 * Las pruebas de aquí cubren lo que `CercanosConLaTarjetaDeLaListaTest` no
 * toca: el renglón de la venta (chip + texto) y el núcleo puro de
 * [textoVendida]. El retiro de la píldora "Falta x de x" lo cobran
 * `CercanosConLaTarjetaDeLaListaTest` y `LaTarjetaTieneDosPuertasTest`.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class LaVentaNuevaSeMarcaEnLaListaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** (a) Cliente con una venta nueva: insignia "Nueva" y chip "Primer abono $X". */
    @Test
    fun `una venta sin abonos muestra la insignia y el chip de primer abono`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                FilaDeCliente(
                    cliente = ListaFixtures.conVentaNueva(),
                    hoy = ListaFixtures.HOY,
                    onAbrirCliente = {}
                )
            }
        }

        composeTestRule.onAllNodesWithText("Nueva", useUnmergedTree = true).assertCountEquals(1)
        composeTestRule.onNodeWithTag(CHIP_DE_PRIMER_ABONO_TAG, useUnmergedTree = true)
            .assertTextEquals("Primer abono $280")
        composeTestRule.onNodeWithTag(TEXTO_DE_VENDIDA_TAG, useUnmergedTree = true)
            .assertTextEquals("Vendida hace 5 días")
    }

    /**
     * (b) Control: un cliente sin NINGUNA venta nueva no pinta insignia ni
     * chip de primer abono, y conserva el estado/atrasos de siempre — la
     * ausencia de arriba se puede medir porque aquí sí está lo viejo.
     */
    @Test
    fun `un cliente sin ventas nuevas no pinta insignia ni chip de primer abono`() {
        val guadalupe = ListaFixtures.guadalupe()
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                FilaDeCliente(cliente = guadalupe, hoy = ListaFixtures.HOY, onAbrirCliente = {})
            }
        }

        composeTestRule.onAllNodesWithText("Nueva", useUnmergedTree = true).assertCountEquals(0)
        composeTestRule.onAllNodesWithText("Primer abono", substring = true, useUnmergedTree = true)
            .assertCountEquals(0)
        // Lo viejo sigue exactamente igual: Guadalupe se negó y lleva 6 atrasos.
        // Por texto y no por `PASTILLA_DE_ATRASOS_TAG`: ese tag va en el `Box`
        // que envuelve al `Text`, no en el `Text` mismo, así que no carga la
        // propiedad de texto que `assertTextEquals` necesita.
        composeTestRule.onNodeWithText("6 atrasos", useUnmergedTree = true).assertExists()
    }

    /** (c) Con "esconder cantidades" el monto del primer abono se enmascara. */
    @Test
    fun `con montos ocultos el primer abono se enmascara`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                FilaDeCliente(
                    cliente = ListaFixtures.conVentaNueva(),
                    hoy = ListaFixtures.HOY,
                    montosOcultos = true,
                    onAbrirCliente = {}
                )
            }
        }

        composeTestRule.onNodeWithTag(CHIP_DE_PRIMER_ABONO_TAG, useUnmergedTree = true)
            .assertTextEquals("Primer abono $MASKED_MONEY")
    }

    /** (d) El núcleo puro: "hoy" / "ayer" / "hace N días" / la fecha completa. */
    @Test
    fun `textoVendida sigue los escalones de dias`() {
        val hoy = LocalDate.of(2026, 9, 1)
        assertEquals("Vendida hoy", textoVendida(hoy, hoy))
        assertEquals("Vendida ayer", textoVendida(hoy.minusDays(1), hoy))
        assertEquals("Vendida hace 5 días", textoVendida(hoy.minusDays(5), hoy))
        assertEquals("Vendida 18 jul 2026", textoVendida(hoy.minusDays(45), hoy))
    }

    /** (d) El filo exacto del escalón: 30 días sigue siendo número, 31 ya es fecha. */
    @Test
    fun `textoVendida cambia de escalon exactamente a los 30 dias`() {
        val hoy = LocalDate.of(2026, 9, 1)
        assertEquals("Vendida hace 30 días", textoVendida(hoy.minusDays(30), hoy))
        assertEquals("Vendida 1 ago 2026", textoVendida(hoy.minusDays(31), hoy))
    }

    /** Sin fecha de venta no hay nada que decir — no un hueco en blanco. */
    @Test
    fun `textoVendida es nulo sin fecha de venta`() {
        assertEquals(null, textoVendida(null, LocalDate.of(2026, 9, 1)))
    }
}
