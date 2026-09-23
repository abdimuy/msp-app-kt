package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El detalle de cliente ya no repite la lista de productos.**
 *
 * Decisión del dueño: esos mismos nombres encabezan cada renglón de "sus
 * ventas" —la cuenta se nombra por su producto, no por su folio— así que la
 * hoja los repetía un dedo más abajo y empujaba la bitácora y las Notas hacia
 * el fondo.
 *
 * Lo que se fija aquí:
 *
 * 1. La hoja **no existe**, con control positivo de que el mismo selector sí
 *    encuentra las hojas que siguen en la pantalla — una ausencia no es
 *    hallazgo hasta demostrar que la consulta habría visto la sección.
 * 2. Cada producto **se sigue viendo**, en el renglón de su venta. Que la hoja
 *    se fuera no puede significar que el cobrador deje de leer qué hay en esa
 *    casa.
 *
 * La sección "productos" del detalle de VENTA no se toca y no se prueba acá:
 * ahí no es redundante, es la única lista de lo que se compró en esa cuenta.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElClienteYaNoRepiteSusProductosTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `la hoja de productos ya no se pinta`() {
        monta()

        assertEquals(
            "la hoja \"productos\" sigue repitiendo lo que ya dicen los renglones de sus ventas",
            0,
            cuantos(TITULO_PRODUCTOS)
        )
    }

    @Test
    fun `control positivo, el mismo selector SI ve las hojas que siguen`() {
        // Sin esto, el test de arriba pasaría igual si `TituloDeHoja` cambiara
        // de forma y el selector dejara de encontrar CUALQUIER título.
        monta()

        composeTestRule.onNodeWithText(TITULO_VENTAS).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText(TITULO_CONTACTOS).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `cada producto se sigue viendo en el renglon de su venta`() {
        monta()

        // Ésta es la otra mitad del cambio, y la que importa: la hoja se fue,
        // el dato no. El fixture nombra cada venta con su producto —que es
        // justo el argumento del dueño para quitar la hoja—, así que el nombre
        // sigue en pantalla aunque la sección ya no exista.
        //
        // No se exige "exactamente una vez" a propósito: la bitácora de abajo
        // también identifica cada cobro por el nombre de su cuenta, así que el
        // conteo depende de qué contactos trae el fixture y no de esta
        // decisión. Lo que aquí se cobra es que el nombre NO desapareció.
        PRODUCTOS.forEach { producto ->
            assertTrue(
                "\"$producto\" desapareció de la pantalla al quitar la hoja de productos",
                cuantos(producto) >= 1
            )
        }
    }

    private fun cuantos(texto: String): Int =
        composeTestRule.onAllNodesWithText(texto).fetchSemanticsNodes().size

    private fun monta() {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
                MspTheme(animateColors = false) {
                    DetalleClienteContent(
                        state = DetalleClienteUiState(
                            cargando = false,
                            detalle = PagosFixtures.detalleCliente()
                        ),
                        onAtras = {},
                        onAbrirVenta = {},
                        onRegistrarAbono = {},
                        onRegistrarVisita = {},
                        onVerContactos = {},
                        onAlternarTema = {},
                        onAlternarPrivacidad = {}
                    )
                }
            }
        }
    }

    private companion object {
        /** `TituloDeHoja` pinta en mayúsculas. */
        const val TITULO_PRODUCTOS = "PRODUCTOS"
        const val TITULO_VENTAS = "SUS VENTAS"
        const val TITULO_CONTACTOS = "ÚLTIMOS CONTACTOS"
        val PRODUCTOS = listOf("Sala 3 piezas + base", "Refrigerador Mabe 14'")
    }
}
