package com.example.msp_app.feature.visitas.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.visitas.ui.components.BANDA_DEL_TICKET_TAG
import com.example.msp_app.feature.visitas.ui.components.CAMBIAR_IMPRESORA_TAG
import com.example.msp_app.feature.visitas.ui.components.CERRAR_IMPRESION_TAG
import com.example.msp_app.feature.visitas.ui.components.IMPRESORA_TAG
import com.example.msp_app.feature.visitas.ui.components.IMPRIMIR_TAG
import com.example.msp_app.feature.visitas.ui.components.RESUMEN_DEL_TICKET_TAG
import com.example.msp_app.feature.visitas.ui.components.VISTA_PREVIA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Lo que se MIDE del ticket de visita, gemelo de `TicketSeVeYSeTocaTest` en
 * `:feature:pagos`:
 *
 * 1. **Fuera del día el CTA no imprime**, probado por su CONSECUENCIA y con
 *    control positivo (el mismo toque, el día de la visita, sí llega).
 * 2. **El control deshabilitado se ve deshabilitado**, y la banda dice por qué
 *    con texto y no solo con color.
 *
 * Igual que en `:feature:pagos`, NO se compara aquí el alto del facsímil entre
 * escalas: Robolectric sin gráficos nativos no mide texto y esa aserción no
 * podría fallar nunca. Eso vive en los goldens
 * `visitas_ticket_primera_*_2_0`, que sí corren con `GraphicsMode.NATIVE`.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class TicketDeVisitaSeVeYSeTocaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var impresiones = 0
    private var cambiosDeImpresora = 0
    private var cierres = 0
    private val elegidas = mutableListOf<PrinterDevice>()

    @Test
    fun `los dos botones del dock miden al menos 50dp`() {
        pinta(TicketDeVisitaFixtures.primeraImpresion())
        assertTocable(IMPRIMIR_TAG, "imprimir")
        assertTocable(CAMBIAR_IMPRESORA_TAG, "cambiar impresora")
    }

    @Test
    fun `a escala muy grande el dock sigue siendo tocable`() {
        pinta(TicketDeVisitaFixtures.primeraImpresion(), FontSizeLevel.MUY_GRANDE)
        assertTocable(IMPRIMIR_TAG, "imprimir")
        assertTocable(CAMBIAR_IMPRESORA_TAG, "cambiar impresora")
    }

    @Test
    fun `cada renglon del picker mide al menos 50dp y responde`() {
        pinta(TicketDeVisitaFixtures.eligiendoImpresora())
        assertTocable(IMPRESORA_TAG + TicketDeVisitaFixtures.IMPRESORA.address, "impresora")
        composeTestRule
            .onNodeWithTag(IMPRESORA_TAG + TicketDeVisitaFixtures.IMPRESORA.address)
            .performClick()
        assertEquals(listOf(TicketDeVisitaFixtures.IMPRESORA), elegidas)
    }

    @Test
    fun `el dia de la visita el CTA si imprime`() {
        // Control positivo del test de abajo.
        pinta(TicketDeVisitaFixtures.primeraImpresion())
        composeTestRule.onNodeWithTag(IMPRIMIR_TAG).performClick()
        composeTestRule.onNodeWithTag(CAMBIAR_IMPRESORA_TAG).performClick()
        assertEquals(1, impresiones)
        assertEquals(1, cambiosDeImpresora)
    }

    @Test
    fun `fuera del dia el CTA no imprime aunque se toque`() {
        pinta(TicketDeVisitaFixtures.fueraDelDia())
        composeTestRule.onNodeWithTag(IMPRIMIR_TAG).performClick()
        composeTestRule.onNodeWithTag(CAMBIAR_IMPRESORA_TAG).performClick()
        assertEquals("fuera del día nada se imprime", 0, impresiones)
        assertEquals(0, cambiosDeImpresora)
    }

    @Test
    fun `fuera del dia la banda lo dice con texto, no solo con color`() {
        pinta(TicketDeVisitaFixtures.fueraDelDia())
        composeTestRule.onNodeWithTag(BANDA_DEL_TICKET_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("fuera del día").assertIsDisplayed()
        composeTestRule.onNodeWithText("solo se imprime hoy").assertIsDisplayed()
    }

    @Test
    fun `la copia se anuncia con su numero y la hora de la primera`() {
        pinta(TicketDeVisitaFixtures.reimpresion())
        composeTestRule.onNodeWithText("copia ya impresa").assertIsDisplayed()
        composeTestRule.onNodeWithText("copia 2 · 12:05").assertIsDisplayed()
    }

    @Test
    fun `el resumen muestra el saldo y la promesa a escala muy grande`() {
        pinta(TicketDeVisitaFixtures.conPromesa(), FontSizeLevel.MUY_GRANDE)
        composeTestRule.onNodeWithTag(RESUMEN_DEL_TICKET_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("$3,550").assertIsDisplayed()
        composeTestRule.onNodeWithText("$220").assertIsDisplayed()
    }

    @Test
    fun `la vista previa es el mismo texto que recibiria la impresora`() {
        val state = TicketDeVisitaFixtures.primeraImpresion()
        pinta(state)
        composeTestRule.onNodeWithTag(VISTA_PREVIA_TAG).assertIsDisplayed()
        assertTrue(state.vistaPrevia.contains("TICKET DE VISITA"))
        assertTrue(state.vistaPrevia.contains("Victoria Flores Olmedo"))
    }

    @Test
    fun `el picker y el fallo se pueden cerrar`() {
        // Sin esta salida, abrir la lista de impresoras —o fallar una
        // impresión— dejaba al cobrador sin forma de volver al ticket. Una banda
        // roja que no se puede cerrar es una trampa chica pero real.
        pinta(TicketDeVisitaFixtures.eligiendoImpresora())
        assertTocable(CERRAR_IMPRESION_TAG, "cerrar")
        composeTestRule.onNodeWithTag(CERRAR_IMPRESION_TAG).performClick()
        assertEquals(1, cierres)
    }

    @Test
    fun `sin picker ni fallo no hay boton de cerrar que estorbe`() {
        // Control positivo del test de arriba: el botón existe SOLO cuando hay
        // algo que cerrar, así que su ausencia aquí no es un tag mal escrito.
        pinta(TicketDeVisitaFixtures.primeraImpresion())
        composeTestRule.onAllNodesWithTag(CERRAR_IMPRESION_TAG).assertCountEquals(0)
    }

    private fun assertTocable(tag: String, que: String) {
        val bordes = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val alto = bordes.bottom - bordes.top
        assertTrue("$que mide $alto", alto >= MINIMO_TOCABLE)
    }

    private fun pinta(state: TicketDeVisitaUiState, nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Pantalla(state) }
            }
        }
    }

    @Composable
    private fun Pantalla(state: TicketDeVisitaUiState) {
        TicketDeVisitaContent(
            state = state,
            onAtras = {},
            onImprimir = { impresiones++ },
            onCambiarImpresora = { cambiosDeImpresora++ },
            onElegirImpresora = { elegidas += it },
            onCerrarImpresion = { cierres++ },
            onReintentar = {}
        )
    }

    private companion object {
        val MINIMO_TOCABLE: Dp = 50.dp
    }
}
