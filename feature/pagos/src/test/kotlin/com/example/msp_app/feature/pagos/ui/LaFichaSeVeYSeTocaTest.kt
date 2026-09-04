package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
import com.example.msp_app.feature.pagos.ui.components.EDITAR_FICHA_TAG
import com.example.msp_app.feature.pagos.ui.components.TARJETA_DE_LA_FICHA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Dónde cae la ficha, medido.** La Task 22 descubrió tarde que su sección
 * quedaba debajo de la línea de flotación; aquí la posición es un aserto y no
 * una suposición.
 *
 * El aserto de arriba viene con su **control positivo**: "datos del cliente"
 * —la sección que sí vive al fondo— NO se ve sin desplazar, en la misma
 * pantalla y con la misma consulta. Sin ese par, un `assertIsDisplayed` que
 * pasara siempre no probaría nada.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class LaFichaSeVeYSeTocaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var abrio = 0

    private fun cliente(
        ficha: FichaDelCliente? = PagosFixtures.fichaDelCliente(),
        nivel: FontSizeLevel = FontSizeLevel.NORMAL
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Pantalla(ficha) }
            }
        }
    }

    @Composable
    private fun Pantalla(ficha: FichaDelCliente?) {
        DetalleClienteContent(
            state = DetalleClienteUiState(
                cargando = false,
                detalle = PagosFixtures.detalleCliente().copy(ficha = ficha)
            ),
            onAtras = {},
            onAbrirVenta = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onMasAcciones = {},
            onUsarLiquidacion = {},
            onVerContactos = {},
            fichaDelCliente = AccionesDeLaFicha(onEditar = { abrio += 1 })
        )
    }

    private fun bordesDe(tag: String): DpRect =
        composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    // --- Dónde cae -----------------------------------------------------------

    @Test
    fun `la ficha se ve SIN desplazar la pantalla`() {
        cliente()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).assertIsDisplayed()
    }

    @Test
    fun `control positivo - la seccion del fondo NO se ve sin desplazar`() {
        cliente()
        composeTestRule.onNodeWithText("datos del cliente").assertIsNotDisplayed()
        // Y con desplazamiento sí: la consulta funciona, lo que falta es scroll.
        composeTestRule.onNodeWithText("datos del cliente")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `la ficha va ARRIBA de sus ventas`() {
        cliente()
        val tarjeta = bordesDe(TARJETA_DE_LA_FICHA_TAG)
        val ventas = composeTestRule.onNodeWithText("sus ventas").getUnclippedBoundsInRoot()
        assertTrue(
            "la ficha (${tarjeta.bottom}) deberia quedar arriba de sus ventas (${ventas.top})",
            tarjeta.bottom <= ventas.top
        )
    }

    // --- El afordante --------------------------------------------------------

    @Test
    fun `la fila del rotulo respeta el piso tocable`() {
        cliente()
        // `useUnmergedTree`: el rótulo y la palabra "editar" se fusionan en la
        // fila tocable, así que el nodo del texto solo existe en el árbol sin
        // fusionar.
        val fila = composeTestRule
            .onNodeWithTag(EDITAR_FICHA_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("el afordante existe y tiene alto", fila.bottom > fila.top)
        val tarjeta = bordesDe(TARJETA_DE_LA_FICHA_TAG)
        assertTrue(
            "la tarjeta es el blanco grande y mide ${tarjeta.bottom - tarjeta.top}",
            (tarjeta.bottom - tarjeta.top) >= PISO_TOCABLE
        )
    }

    @Test
    fun `tocar la tarjeta abre el editor`() {
        cliente()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).performClick()
        assertEquals(1, abrio)
    }

    @Test
    fun `tocar el rotulo tambien abre el editor`() {
        cliente()
        composeTestRule.onNodeWithText("editar").performClick()
        assertEquals(1, abrio)
    }

    // --- Los tres estados ----------------------------------------------------

    @Test
    fun `con senales se pintan sus etiquetas en espanol`() {
        cliente()
        composeTestRule.onNodeWithText("está en la noche").assertIsDisplayed()
        composeTestRule.onNodeWithText("atiende otra persona").assertIsDisplayed()
    }

    @Test
    fun `sin ficha se invita a anotar y el afordante dice anotar`() {
        cliente(ficha = FichaDelCliente())
        composeTestRule.onNodeWithText("sin ficha — anota lo que sirva mañana").assertIsDisplayed()
        composeTestRule.onNodeWithText("anotar").assertIsDisplayed()
    }

    @Test
    fun `si no se pudo leer se dice, y NO hay nada que tocar`() {
        cliente(ficha = null)
        composeTestRule.onNodeWithText("no se pudo leer la ficha").assertIsDisplayed()
        composeTestRule.onNodeWithTag(TARJETA_DE_LA_FICHA_TAG).performClick()
        assertEquals("una tarjeta muerta no puede abrir el editor", 0, abrio)
    }

    @Test
    fun `la nota de la VENTA se pinta aparte y con su propia etiqueta`() {
        cliente()
        composeTestRule.onNodeWithText("de la venta").assertIsDisplayed()
        composeTestRule.onNodeWithText("entrega en la puerta de atrás").assertIsDisplayed()
    }

    @Test
    fun `a escala MUY GRANDE la ficha sigue arriba de sus ventas`() {
        cliente(nivel = FontSizeLevel.MUY_GRANDE)
        val tarjeta = bordesDe(TARJETA_DE_LA_FICHA_TAG)
        val ventas = composeTestRule.onNodeWithText("sus ventas").getUnclippedBoundsInRoot()
        assertTrue(tarjeta.bottom <= ventas.top)
    }

    @Test
    fun `el catalogo entero cabe en la hoja de edicion`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                com.example.msp_app.feature.pagos.ui.components.CuerpoDeLaFicha(
                    senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE),
                    nota = "",
                    guardando = false,
                    fallo = false,
                    onSenal = {},
                    onNota = {},
                    onGuardar = {}
                )
            }
        }
        SenalDeFicha.entries.forEach { senal ->
            composeTestRule
                .onNodeWithTag(
                    com.example.msp_app.feature.pagos.ui.components.SENAL_TAG + senal.ordinal
                )
                .assertIsDisplayed()
        }
    }

    private companion object {
        /** El piso de toque del plan: 50px. */
        val PISO_TOCABLE = 50.dp
    }
}
