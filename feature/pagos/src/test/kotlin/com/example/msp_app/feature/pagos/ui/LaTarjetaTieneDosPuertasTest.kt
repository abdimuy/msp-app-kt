package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.ENCABEZADO_DE_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.RENGLON_DE_VENTA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **La tarjeta del cliente tiene dos puertas** (decisión del dueño, 2026-10-01):
 * el encabezado —nombre e info— abre el CLIENTE, y cada venta abre ESA venta.
 *
 * Antes la tarjeta entera abría el cliente y la venta se elegía adentro; los
 * cobradores se quejaron del paso de más. Victoria carga dos ventas (77021 y
 * 77188): tocar la segunda tiene que abrir la 77188 y no tocar al cliente.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class LaTarjetaTieneDosPuertasTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val clientesAbiertos = mutableListOf<Int>()
    private val ventasAbiertas = mutableListOf<Int>()

    private fun pinta() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                ListaDeClientesContent(
                    state = ListaDeClientesUiState(
                        cargando = false,
                        clientes = ListaFixtures.ruta()
                    ),
                    onBuscar = {},
                    onElegirSegmento = {},
                    onAbrirCliente = { clientesAbiertos += it },
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onAbrirVenta = { ventasAbiertas += it }
                )
            }
        }
    }

    @Test
    fun `tocar una venta abre ESA venta y no el cliente`() {
        pinta()

        composeTestRule.onNodeWithTag(RENGLON_DE_VENTA_TAG + VENTA_2_DE_VICTORIA).performClick()

        assertEquals(listOf(VENTA_2_DE_VICTORIA), ventasAbiertas)
        assertTrue(clientesAbiertos.isEmpty())
    }

    @Test
    fun `tocar el encabezado abre el cliente y ninguna venta`() {
        pinta()

        composeTestRule.onAllNodesWithTag(ENCABEZADO_DE_CLIENTE_TAG)[0].performClick()

        assertEquals(listOf(ListaFixtures.ruta().first().clienteId), clientesAbiertos)
        assertTrue(ventasAbiertas.isEmpty())
    }

    /** La regla del repo: todo lo tocable mide al menos 50dp de alto. */
    @Test
    fun `el encabezado y cada venta miden al menos 50dp de alto`() {
        pinta()

        val encabezado = composeTestRule.onAllNodesWithTag(
            ENCABEZADO_DE_CLIENTE_TAG
        )[0].getUnclippedBoundsInRoot()
        val venta = composeTestRule.onNodeWithTag(RENGLON_DE_VENTA_TAG + VENTA_2_DE_VICTORIA)
            .getUnclippedBoundsInRoot()

        assertTrue("encabezado: ${encabezado.height}", encabezado.height >= MINIMO_TOCABLE)
        assertTrue("venta: ${venta.height}", venta.height >= MINIMO_TOCABLE)
    }

    private companion object {
        const val VENTA_2_DE_VICTORIA = 77188
        val MINIMO_TOCABLE = 50.dp
    }
}
