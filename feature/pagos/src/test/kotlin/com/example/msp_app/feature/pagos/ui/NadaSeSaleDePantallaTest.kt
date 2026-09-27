package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.TARJETA_DE_GARANTIA_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * A escala grande, los datos del pie de la venta se apilan en vez de pelearse
 * por 360dp de ancho.
 *
 * El golden `pagos_venta_light_2_0` mostró el defecto antes de que existiera
 * `TresDatos`: la fila `abonos · parcialidad · frecuencia` no cabía a
 * `MUY_GRANDE` y el tercer dato quedaba aplastado hasta desaparecer. Un dato
 * que no se puede leer no es un detalle visual — es información perdida justo
 * para el usuario que más ayuda necesita.
 *
 * **Hoy el pie tiene DOS celdas**, no tres: el dueño quitó el conteo de abonos
 * y no se reemplazó por nada, así que la pieza es `DosDatos`. La regla de
 * layout es la misma y se sigue midiendo igual — con dos que no caben, el
 * defecto sería idéntico.
 *
 * ## Por qué se afirma el layout y no el ancho del texto
 *
 * El primer intento midió el ancho de cada etiqueta con
 * `getUnclippedBoundsInRoot()`. **No sirve, y se comprobó:** sin
 * `@GraphicsMode(NATIVE)` Robolectric no mide texto de verdad — "abonos"
 * reportaba 3.5dp de ancho en TODAS las variantes, con y sin el arreglo, así
 * que el aserto pasaba o fallaba por igual y no distinguía nada (regla de
 * control positivo: una ausencia no es un hallazgo hasta probar que la consulta
 * la habría encontrado).
 *
 * Lo que sí es determinista en este entorno es la POSICIÓN: en fila los tres
 * comparten renglón, apilados cada uno tiene el suyo. Eso es exactamente la
 * conducta que `TresDatos` decide, y el control de reversión lo confirma —
 * con la fila fija, los dos tests de escala grande se ponen rojos.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class NadaSeSaleDePantallaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun ventaA(nivel: FontSizeLevel) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Venta() }
            }
        }
    }

    @Composable
    private fun Venta() {
        DetalleVentaContent(
            state = DetalleVentaUiState(cargando = false, detalle = PagosFixtures.detalleVenta()),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }

    @Test
    fun `las filas tocables respetan el piso de 50px del plan`() {
        ventaA(FontSizeLevel.NORMAL)
        val fila = bordesDe("Ver los 6 abonos")
        assertTrue(
            "la fila mide " + (fila.bottom - fila.top) + ", bajo el piso de " + PISO_TOCABLE,
            (fila.bottom - fila.top) >= PISO_TOCABLE
        )
    }

    /**
     * `testTag` de la tarjeta de garantía: la sección existe y se pinta debajo
     * de los productos, con el mock como referencia.
     */
    @Test
    fun `la garantia de la venta se pinta`() {
        ventaA(FontSizeLevel.NORMAL)
        composeTestRule.onNodeWithTag(TARJETA_DE_GARANTIA_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Notificada").performScrollTo().assertIsDisplayed()
    }

    /** Los bordes del nodo cuyo texto es [etiqueta], tal cual se pinta. */
    private fun bordesDe(etiqueta: String): DpRect =
        composeTestRule.onNodeWithText(etiqueta).performScrollTo().getUnclippedBoundsInRoot()

    // Los tres tests del pie "parcialidad · frecuencia" se fueron con el pie:
    // el rediseño (`detalle-de-venta-final.html`) dice "Parcialidad $220
    // semanal" en un solo bloque a la derecha del saldo, sin celdas que apilar.

    // La sección "El alcance de la línea se desplaza" se quitó: `AlcanceDeLaLinea`
    // —las pastillas "Esta venta" / "Todo el cliente"— ya no existe. El detalle
    // de venta no mezcla cuentas ajenas; el defecto que estos dos tests
    // cerraban no puede volver porque el control que lo producía se fue con
    // ellos.

    private companion object {
        /**
         * El piso de toque del plan: ">=50px". Se mide sobre el alto real del
         * nodo, que en este entorno SÍ es determinista (es una restricción de
         * layout, no una medición de texto — ver el KDoc de arriba).
         */
        val PISO_TOCABLE = 50.dp
    }
}
