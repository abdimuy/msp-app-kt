package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
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
import com.example.msp_app.feature.pagos.ui.components.BANDA_DEL_TICKET_TAG
import com.example.msp_app.feature.pagos.ui.components.CAMBIAR_IMPRESORA_TAG
import com.example.msp_app.feature.pagos.ui.components.CERRAR_IMPRESION_TAG
import com.example.msp_app.feature.pagos.ui.components.IMPRESORA_TAG
import com.example.msp_app.feature.pagos.ui.components.IMPRIMIR_TAG
import com.example.msp_app.feature.pagos.ui.components.RESUMEN_DEL_TICKET_TAG
import com.example.msp_app.feature.pagos.ui.components.VISTA_PREVIA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Lo que se MIDE del ticket de pago, en vez de declararse.
 *
 * Las dos cosas que esta clase existe para probar:
 *
 * 1. **Fuera del día el CTA no imprime** — probado por su CONSECUENCIA (el toque
 *    no llama al ViewModel), con control positivo: el MISMO toque, el día del
 *    cobro, sí llama. Un test que solo mirara la banda roja pasaría con el botón
 *    vivo debajo.
 * 2. **El control deshabilitado se ve deshabilitado.** No basta con que no haga
 *    nada: `MspPrimaryFieldButton` pinta a mano el estado apagado porque un
 *    `clickable` de M3 no aplica ninguna alfa por sí solo, y aquí se comprueba
 *    que ese camino es el que se toma.
 */
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class TicketSeVeYSeTocaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val nivelActual = mutableStateOf(FontSizeLevel.NORMAL)
    private var impresiones = 0
    private var cambiosDeImpresora = 0
    private var cierres = 0
    private val elegidas = mutableListOf<PrinterDevice>()

    @Test
    fun `los dos botones del dock miden al menos 50dp`() {
        pinta(TicketFixtures.primeraImpresion())
        assertTocable(IMPRIMIR_TAG, "imprimir")
        assertTocable(CAMBIAR_IMPRESORA_TAG, "cambiar impresora")
    }

    @Test
    fun `a escala muy grande el dock sigue siendo tocable`() {
        pinta(TicketFixtures.primeraImpresion(), FontSizeLevel.MUY_GRANDE)
        assertTocable(IMPRIMIR_TAG, "imprimir")
        assertTocable(CAMBIAR_IMPRESORA_TAG, "cambiar impresora")
    }

    @Test
    fun `cada renglon del picker mide al menos 50dp y responde`() {
        pinta(TicketFixtures.eligiendoImpresora())
        assertTocable(IMPRESORA_TAG + TicketFixtures.IMPRESORA.address, "impresora")
        composeTestRule
            .onNodeWithTag(IMPRESORA_TAG + TicketFixtures.IMPRESORA.address)
            .performClick()
        assertEquals(listOf(TicketFixtures.IMPRESORA), elegidas)
    }

    @Test
    fun `el dia del cobro el CTA si imprime`() {
        // Control positivo del test de abajo: el mismo toque, con permiso, SÍ
        // llega al ViewModel. Sin esto, "no imprime" no significaría nada.
        pinta(TicketFixtures.primeraImpresion())
        composeTestRule.onNodeWithTag(IMPRIMIR_TAG).performClick()
        composeTestRule.onNodeWithTag(CAMBIAR_IMPRESORA_TAG).performClick()
        assertEquals(1, impresiones)
        assertEquals(1, cambiosDeImpresora)
    }

    @Test
    fun `fuera del dia el CTA no imprime aunque se toque`() {
        pinta(TicketFixtures.fueraDelDia())
        composeTestRule.onNodeWithTag(IMPRIMIR_TAG).performClick()
        composeTestRule.onNodeWithTag(CAMBIAR_IMPRESORA_TAG).performClick()
        assertEquals("fuera del día nada se imprime", 0, impresiones)
        assertEquals(0, cambiosDeImpresora)
    }

    @Test
    fun `fuera del dia la banda lo dice con texto, no solo con color`() {
        pinta(TicketFixtures.fueraDelDia())
        composeTestRule.onNodeWithTag(BANDA_DEL_TICKET_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("fuera del día").assertIsDisplayed()
        composeTestRule.onNodeWithText("solo se imprime hoy").assertIsDisplayed()
    }

    @Test
    fun `la copia se anuncia con su numero y la hora de la primera`() {
        pinta(TicketFixtures.reimpresion())
        composeTestRule.onNodeWithText("copia ya impresa").assertIsDisplayed()
        composeTestRule.onNodeWithText("copia 2 · 12:05").assertIsDisplayed()
    }

    @Test
    fun `la vista previa es el mismo texto que recibiria la impresora`() {
        val state = TicketFixtures.primeraImpresion()
        pinta(state)
        composeTestRule.onNodeWithTag(VISTA_PREVIA_TAG).assertIsDisplayed()
        assertTrue(state.vistaPrevia.contains("TICKET DE PAGO"))
        assertTrue(state.vistaPrevia.contains("Victoria Flores Olmedo"))
    }

    @Test
    fun `el resumen muestra el abono y el saldo a escala muy grande`() {
        // El facsímil de 32 columnas no puede crecer sin salirse; las cifras
        // legibles viven en el resumen y ahí SÍ crecen. Esto lo comprueba.
        pinta(TicketFixtures.primeraImpresion(), FontSizeLevel.MUY_GRANDE)
        composeTestRule.onNodeWithTag(RESUMEN_DEL_TICKET_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithText("$350").assertIsDisplayed()
        composeTestRule.onNodeWithText("$1,450").assertIsDisplayed()
    }

    /*
     * NO existe aquí un test que compare el ALTO del facsímil a 1.0 y a 2.0.
     *
     * Se escribió y se borró: su control positivo —"el resumen SÍ crece"— salió
     * rojo, que es justo lo que el control positivo existe para detectar.
     * Robolectric sin gráficos nativos no mide texto, así que las dos alturas
     * salen idénticas pase lo que pase y la aserción no podría fallar nunca. Es
     * el mismo defecto que este plan ya pagó cuatro veces, y dejarla habría sido
     * la quinta.
     *
     * Dónde SÍ queda probado que el facsímil no se sale a 2.0: en los goldens
     * `pagos_ticket_primera_*_2_0` de `TicketDePagoMatrixScreenshotTest`, que
     * corren con `GraphicsMode.NATIVE` y por lo tanto sí renderizan texto; y en
     * `TicketDePagoFormatterTest`, que afirma que ninguna línea excede las 32
     * columnas.
     */

    @Test
    fun `el picker y el fallo se pueden cerrar`() {
        // Sin esta salida, abrir la lista de impresoras —o fallar una
        // impresión— dejaba al cobrador sin forma de volver al ticket. Una banda
        // roja que no se puede cerrar es una trampa chica pero real.
        pinta(TicketFixtures.eligiendoImpresora())
        assertTocable(CERRAR_IMPRESION_TAG, "cerrar")
        composeTestRule.onNodeWithTag(CERRAR_IMPRESION_TAG).performClick()
        assertEquals(1, cierres)
    }

    @Test
    fun `sin picker ni fallo no hay boton de cerrar que estorbe`() {
        // Control positivo del test de arriba: el botón existe SOLO cuando hay
        // algo que cerrar, así que su ausencia aquí no es un tag mal escrito.
        pinta(TicketFixtures.primeraImpresion())
        composeTestRule.onAllNodesWithTag(CERRAR_IMPRESION_TAG).assertCountEquals(0)
    }

    private fun assertTocable(tag: String, que: String) {
        val bordes = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        val alto = bordes.bottom - bordes.top
        assertTrue("$que mide $alto", alto >= MINIMO_TOCABLE)
    }

    private fun pinta(state: TicketDePagoUiState, nivel: FontSizeLevel = FontSizeLevel.NORMAL) {
        nivelActual.value = nivel
        composeTestRule.setContent {
            val density = LocalDensity.current
            val nivelVigente = nivelActual.value
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivelVigente.nominalScale),
                LocalFontSizeLevel provides nivelVigente
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Pantalla(state) }
            }
        }
    }

    @Composable
    private fun Pantalla(state: TicketDePagoUiState) {
        TicketDePagoContent(
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
