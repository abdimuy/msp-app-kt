package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El pie de la tarjeta de saldo ya no cuenta abonos.**
 *
 * Decía `abonos 3 / 12` en la primera de tres celdas. El dueño lo quitó, y
 * **sin reemplazo**: lo abonado en dinero ya tiene su renglón en "datos de la
 * venta", unos dedos más abajo en esta misma pantalla, y la barra de avance que
 * va justo encima del pie ya dice el progreso, sin números.
 *
 * Lo que se fija aquí:
 *
 * 1. Ni el rótulo ni la fracción aparecen — **con control positivo por
 *    separado para cada selector**: el de las celdas que quedaron y el de la
 *    fracción. Una ausencia no es hallazgo hasta demostrar que la consulta
 *    habría encontrado el dato.
 * 2. Las dos celdas que quedaron **se siguen viendo**. Que se repartan el
 *    ancho y se apilen a escala grande lo mide `NadaSeSaleDePantallaTest`, que
 *    ya tenía la geometría del pie y se re-apuntó a dos celdas.
 *
 * El conteo sigue vivo en el modelo ([DetalleVenta.abonosPagados] /
 * [DetalleVenta.abonosTotales]): de ahí sale la barra de avance y de ahí lo
 * imprime el ticket de pago. Lo que se quitó es este renglón, no el dato.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElPieDeLaVentaYaNoCuentaAbonosTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val detalle = PagosFixtures.detalleVenta()

    @Test
    fun `el rotulo abonos ya no esta en el pie`() {
        monta(detalle)

        assertEquals("el pie sigue contando abonos", 0, cuantos(enVersalitas("abonos")))
    }

    @Test
    fun `la fraccion de abonos ya no esta`() {
        monta(detalle)

        // El valor, no sólo el rótulo: un pie que perdiera la etiqueta y dejara
        // el "3 / 12" suelto pasaría el test de arriba y se vería peor que antes.
        assertEquals("la fracción del conteo sigue pintada", 0, cuantos(fraccionLegada()))
    }

    @Test
    fun `control positivo, el mismo selector SI ve las dos celdas que quedan`() {
        // Sin esto, el primer test pasaría igual si `DatoDelPie` cambiara de
        // forma —o dejara de poner sus rótulos en versalitas— y el selector
        // dejara de encontrar CUALQUIER celda del pie.
        monta(detalle)

        ve(enVersalitas("parcialidad"))
        ve(enVersalitas("frecuencia"))
    }

    @Test
    fun `control positivo, el selector de la fraccion SI la ve cuando esta pintada`() {
        // La otra mitad, y con una pieza REAL de la pantalla: el renglón de un
        // producto, que usa el mismo `FilaClaveValor` que el resto de la ficha.
        // Así el control mide el selector contra esta pantalla, no contra un
        // composable de juguete que en producción no existe.
        monta(detalle.copy(productos = listOf(ProductoDeVenta(fraccionLegada(), importe = null))))

        assertEquals(
            "el selector no ve la fracción ni cuando está pintada en esta pantalla",
            1,
            cuantos(fraccionLegada())
        )
    }

    /** `DatoDelPie` pinta sus rótulos en versalitas, como el `.pgrid .k` del mock. */
    private fun enVersalitas(clave: String): String = clave.uppercase(BUSINESS_LOCALE)

    /** El valor que el pie pintaba antes de este cambio. */
    private fun fraccionLegada(): String = "${detalle.abonosPagados} / ${detalle.abonosTotales}"

    private fun ve(texto: String) {
        composeTestRule.onNodeWithText(texto).performScrollTo().assertIsDisplayed()
    }

    private fun cuantos(texto: String): Int =
        composeTestRule.onAllNodesWithText(texto).fetchSemanticsNodes().size

    private fun monta(elDetalle: DetalleVenta) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
                MspTheme(animateColors = false) { Pantalla(elDetalle) }
            }
        }
    }

    @Composable
    private fun Pantalla(elDetalle: DetalleVenta) {
        DetalleVentaContent(
            state = DetalleVentaUiState(cargando = false, detalle = elDetalle),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }
}
