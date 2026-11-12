package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.DiaDeCobroDeLaVenta
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.ui.components.BARRA_DE_COBRO_TAG
import com.example.msp_app.feature.pagos.ui.components.DIA_GRANDE_DE_COBRO_TAG
import com.example.msp_app.feature.pagos.ui.components.ETIQUETA_DEL_ACORDADO_TAG
import com.example.msp_app.feature.pagos.ui.components.FRANJA_DE_COBRO_TAG
import com.example.msp_app.feature.pagos.ui.components.TRAMO_DE_COBRO_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * La franja "Día de cobro" y el grupo "El cobro" **en la pantalla**, con la
 * pantalla real ([DetalleVentaContent]) y la hoja de datos real.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElDiaDeCobroSeVeTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun monta(dia: DiaDeCobroDeLaVenta?) = composeTestRule.setContent {
        CompositionLocalProvider(LocalFontSizeLevel provides FontSizeLevel.NORMAL) {
            MspTheme(
                animateColors = false
            ) { Pantalla(PagosFixtures.detalleVenta().copy(diaDeCobro = dia)) }
        }
    }

    @Composable
    private fun Pantalla(detalle: DetalleVenta) {
        DetalleVentaContent(
            state = DetalleVentaUiState(cargando = false, detalle = detalle),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }

    private fun cuantos(tag: String) =
        composeTestRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun `con acordado y estimacion que coinciden se ve la franja entera`() {
        monta(DiaDeCobroFixtures.coincide())

        composeTestRule.onNodeWithTag(FRANJA_DE_COBRO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("DÍA DE COBRO").assertIsDisplayed()
        composeTestRule.onNodeWithTag(DIA_GRANDE_DE_COBRO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("✓ Acordado").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sáb 5").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mar 3").assertIsDisplayed()
        composeTestRule.onNodeWithText("Jue 2").assertIsDisplayed()
        assertEquals(3, cuantos(TRAMO_DE_COBRO_TAG))
    }

    @Test
    fun `solo acordado no pinta barra`() {
        monta(DiaDeCobroFixtures.soloAcordado())

        composeTestRule.onNodeWithText("Acordado al vender").assertIsDisplayed()
        assertEquals(0, cuantos(BARRA_DE_COBRO_TAG))
    }

    @Test
    fun `sin dia de cobro la franja no aparece, con control positivo`() {
        monta(null)
        assertEquals("la franja salió sin dato", 0, cuantos(FRANJA_DE_COBRO_TAG))
        assertEquals(0, cuantos(ETIQUETA_DEL_ACORDADO_TAG))
        // Control positivo: la MISMA búsqueda por `FRANJA_DE_COBRO_TAG` SÍ la
        // encuentra cuando hay dato — ver el primer test de esta clase, que
        // exige `assertIsDisplayed` sobre ese tag.
    }

    @Test
    fun `la hoja de datos tiene el grupo El cobro entre la puerta y el trato`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                CuerpoDeLosDatosDeLaVenta(
                    detalle = PagosFixtures.detalleVenta().copy(
                        diaDeCobro = DiaDeCobroFixtures.coincide()
                    ),
                    ocultos = false
                )
            }
        }
        listOf(
            "EL COBRO",
            "Día acordado",
            "lo anotó el vendedor al vender",
            "Suele pagar",
            "Otros días",
            "Calculado"
        )
            .forEach { composeTestRule.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
        composeTestRule.onNodeWithText("5 de sus últimos 10 pagos").assertIsDisplayed()
        // Orden: La puerta < El cobro < El trato.
        val etiquetas = listOf("LA PUERTA", "EL COBRO", "EL TRATO")
        val ys = etiquetas.map {
            composeTestRule.onNodeWithText(it).fetchSemanticsNode().positionInRoot.y
        }
        assertEquals(ys.sorted(), ys)
    }

    @Test
    fun `sin dia de cobro la hoja no abre el grupo El cobro`() {
        composeTestRule.setContent {
            MspTheme(animateColors = false) {
                CuerpoDeLosDatosDeLaVenta(detalle = PagosFixtures.detalleVenta(), ocultos = false)
            }
        }
        assertEquals(0, composeTestRule.onAllNodesWithText("EL COBRO").fetchSemanticsNodes().size)
        composeTestRule.onNodeWithText("LA PUERTA").assertIsDisplayed()
    }
}
