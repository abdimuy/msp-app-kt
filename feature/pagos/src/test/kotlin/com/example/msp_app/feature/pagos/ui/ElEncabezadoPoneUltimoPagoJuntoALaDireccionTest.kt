package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **"Últ. pago" va en el renglón de la DIRECCIÓN, no en el del nombre.**
 *
 * Defecto medido en `pagos_lista_light_1_0` (reporte del dueño, 2026-10-05): al
 * quitar la píldora "Falta x de x" de la columna derecha, "Últ. pago" se quedó
 * sin nada encima que lo empujara hacia abajo y flotó junto al NOMBRE en vez de
 * junto a la dirección — "Victoria Flores" apretada contra "Últ. pago 6 sept"
 * en el primer renglón. La causa era de **acomodo**, no de `basicMarquee`: el
 * encabezado seguía siendo dos COLUMNAS alineadas arriba (`Alignment.Top`) en
 * vez de dos RENGLONES.
 *
 * Victoria es el caso exacto que lo expone: tiene `ultimoPago` (pagó una de sus
 * cuentas esta semana) Y una venta sin abonos (`esNueva`), así que su tarjeta
 * pinta insignia, nombre, dirección Y "Últ. pago" a la vez — los cuatro
 * elementos cuyo acomodo relativo este test fija.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class ElEncabezadoPoneUltimoPagoJuntoALaDireccionTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `ultimo pago queda debajo del nombre y a la altura de la direccion`() {
        val victoria = ListaFixtures.victoria()
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                FilaDeCliente(cliente = victoria, hoy = ListaFixtures.HOY, onAbrirCliente = {})
            }
        }

        val tapaDelNombre = composeTestRule
            .onNodeWithText(victoria.nombre, substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top.value
        val tapaDeLaDireccion = composeTestRule
            .onNodeWithText(victoria.direccion, substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top.value
        val tapaDeUltimoPago = composeTestRule
            .onNodeWithText("Últ. pago", substring = true, useUnmergedTree = true)
            .getUnclippedBoundsInRoot().top.value

        assertTrue(
            "'Últ. pago' (tapa $tapaDeUltimoPago) debe quedar DEBAJO del nombre " +
                "(tapa $tapaDelNombre), no a su lado",
            tapaDeUltimoPago > tapaDelNombre
        )
        assertEquals(
            "'Últ. pago' debe compartir renglón con la dirección",
            tapaDeLaDireccion,
            tapaDeUltimoPago,
            TOLERANCIA
        )
    }

    private companion object {
        /** Mismo margen que usan las pruebas vecinas de línea base (redondeo a píxel). */
        const val TOLERANCIA = 1.0f
    }
}
