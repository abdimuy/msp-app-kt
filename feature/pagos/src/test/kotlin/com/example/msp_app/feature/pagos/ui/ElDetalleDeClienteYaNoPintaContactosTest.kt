package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.CONTACTO_EN_LINEA_TAG
import com.example.msp_app.feature.pagos.ui.components.VER_LOS_CONTACTOS_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El detalle de CLIENTE ya no pinta ni una fila de contacto ni "ver los N
 * contactos".** Decisión del dueño: la sección "últimos contactos" se quitó
 * de esa pantalla porque mezclaba TODAS las cuentas del cliente en un lugar
 * que ya peleaba cada dp contra el saldo; la única puerta a "ver los N
 * contactos" es hoy el detalle de VENTA.
 *
 * ## Por qué el control positivo no es retórico
 *
 * Una ausencia se puede leer dos veces: "de verdad no está" o "el `testTag`
 * que busco ya no significa nada, en ningún árbol, porque lo escribí mal".
 * Este test corre la MISMA consulta ([CONTACTO_EN_LINEA_TAG]) contra el
 * detalle de VENTA, que sí pinta esas filas — si el tag estuviera mal
 * escrito, el control de abajo también daría cero, y ahí es donde se nota.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElDetalleDeClienteYaNoPintaContactosTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el detalle de cliente no pinta ninguna fila de contacto`() {
        composeTestRule.setContent {
            Tema { Cliente() }
        }

        assertEquals(
            "el detalle de cliente sigue pintando filas de contacto",
            0,
            composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `el detalle de cliente no ofrece ver los N contactos`() {
        composeTestRule.setContent {
            Tema { Cliente() }
        }

        assertEquals(
            "el detalle de cliente sigue ofreciendo \"ver los N contactos\"",
            0,
            composeTestRule.onAllNodesWithTag(VER_LOS_CONTACTOS_TAG).fetchSemanticsNodes().size
        )
    }

    /**
     * **Control positivo de los dos de arriba, en el árbol donde SÍ existen
     * hoy: el detalle de VENTA.** Si [CONTACTO_EN_LINEA_TAG] diera cero aquí
     * también, las dos ausencias de arriba no probarían nada — probarían que
     * la consulta está rota, no que la sección se quitó.
     */
    @Test
    fun `control positivo - la misma consulta SI encuentra filas en el detalle de venta`() {
        composeTestRule.setContent {
            Tema { Venta() }
        }

        val filas = composeTestRule.onAllNodesWithTag(CONTACTO_EN_LINEA_TAG).fetchSemanticsNodes()
        assertEquals(
            "el detalle de venta no pintó ni una fila con el MISMO tag: la consulta de " +
                "arriba no prueba nada",
            true,
            filas.isNotEmpty()
        )
    }

    // --- Montaje --------------------------------------------------------------

    @Composable
    private fun Tema(contenido: @Composable () -> Unit) {
        MspTheme(darkTheme = false, animateColors = false, content = contenido)
    }

    @Composable
    private fun Cliente() {
        DetalleClienteContent(
            state = DetalleClienteUiState(
                cargando = false,
                detalle = PagosFixtures.detalleCliente()
            ),
            onAtras = {},
            onAbrirVenta = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onAlternarTema = {},
            onAlternarPrivacidad = {}
        )
    }

    @Composable
    private fun Venta() {
        Column {
            LineaDeLaVenta(
                detalle = PagosFixtures.detalleVenta(),
                onVerAbonos = {},
                onVerContactos = {}
            )
        }
    }
}
