package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_SEGMENTO_TAG
import com.example.msp_app.feature.pagos.ui.components.FILA_DE_CLIENTE_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Dos cosas que se miden, no se declaran:
 *
 * 1. **Una fila por cliente.** La ruta de prueba tiene tres puertas y cuatro
 *    ventas —Victoria carga dos—. La lista vieja habría pintado cuatro filas de
 *    cliente; esta pinta tres. Ese es el defecto entero, medido en la pantalla.
 * 2. **Los chips se pueden tocar.** El plan pide >=50px y la Task 16 shipeó un
 *    control de 49.5dp que hubo que corregir, así que aquí se mide el alto real
 *    de cada chip en vez de confiar en el modificador.
 */
// Pantalla alta a propósito: `LazyColumn` solo compone lo visible, así que con
// 800dp de alto el conteo de filas mediría cuántas caben, no cuántas hay.
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class ListaSeVeYSeTocaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun pinta(nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Lista() }
            }
        }
    }

    @Composable
    private fun Lista() {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.ruta(),
            segmento = SegmentoDeCobranza.TODOS,
            query = "",
            hoy = ListaFixtures.HOY
        )
        ListaDeClientesContent(
            state = ListaDeClientesUiState(
                cargando = false,
                clientes = proyeccion.clientes,
                conteos = proyeccion.conteos
            ),
            onAtras = {},
            onBuscar = {},
            onElegirSegmento = {},
            onAbrirCliente = {},
            onAbrirVenta = {},
            onReintentar = {}
        )
    }

    @Test
    fun `cuatro ventas de tres clientes pintan TRES filas de cliente`() {
        pinta()
        assertEquals(
            3,
            composeTestRule.onAllNodesWithTag(FILA_DE_CLIENTE_TAG)
                .fetchSemanticsNodes()
                .size
        )
    }

    @Test
    fun `cada chip mide al menos 50dp de alto`() {
        pinta()
        SegmentoDeCobranza.entries.forEach { segmento ->
            val bordes = composeTestRule
                .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + segmento.name.lowercase())
                .getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("el chip ${segmento.etiqueta} mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    @Test
    fun `a escala muy grande los chips siguen siendo tocables`() {
        pinta(FontSizeLevel.MUY_GRANDE)
        val bordes = composeTestRule
            .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.TODOS.name.lowercase())
            .getUnclippedBoundsInRoot()
        val alto = bordes.bottom - bordes.top
        assertTrue("el chip mide $alto", alto >= MINIMO_TOCABLE)
    }

    @Test
    fun `sin clientes en el chip se dice, no se deja el hueco`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                ListaDeClientesContent(
                    state = ListaDeClientesUiState(
                        cargando = false,
                        segmento = SegmentoDeCobranza.HOY
                    ),
                    onAtras = {},
                    onBuscar = {},
                    onElegirSegmento = {},
                    onAbrirCliente = {},
                    onAbrirVenta = {},
                    onReintentar = {}
                )
            }
        }
        composeTestRule.onNodeWithTag(LISTA_VACIA_TAG).assertIsDisplayed()
        // Y el buscador sigue ahí: el chip vacío no deja al cobrador sin salida.
        composeTestRule.onNodeWithTag(BUSCADOR_TAG).assertIsDisplayed()
    }

    private companion object {
        /** El piso del plan. El token del design system (56dp) va por encima. */
        val MINIMO_TOCABLE = 50.dp
    }
}
