package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.DetalleVenta
import com.example.msp_app.feature.pagos.ui.components.CTA_CONDONAR_TAG
import com.example.msp_app.feature.pagos.ui.components.CTA_PRIMARIO_TAG
import com.example.msp_app.feature.pagos.ui.components.CTA_VISITA_TAG
import com.example.msp_app.feature.pagos.ui.components.DockDeAcciones
import com.example.msp_app.feature.pagos.ui.components.MENU_DEL_DOCK_TAG
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Lo que el rediseño aprobado (`detalle-de-venta-final.html`) promete y un
 * golden no puede afirmar:
 *
 *  - (c) **el ojo tapa TODA cantidad** de la pantalla y de la hoja de datos;
 *  - (d) **a letra grande el dock no se apila**, el CTA dice "Abonar" y ningún
 *    texto del dock ocupa dos renglones;
 *  - (f) **a 2.0× la hoja de datos es de una columna**;
 *  - "Último pago" no se pinta cuando la venta no tiene abonos.
 *
 * Cada ausencia lleva su control positivo con el MISMO selector.
 * `GraphicsMode.NATIVE` porque las medidas de texto (renglones) salen del
 * layout real.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class ElRedisenoDeLaVentaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // --- (c) el ojo -----------------------------------------------------------

    @Test
    fun `con el ojo cerrado no queda ningun monto en la pantalla ni en la hoja`() {
        monta(ocultos = true)

        val visibles = textosConDinero()
        assertTrue("quedaron montos a la vista: $visibles", visibles.isEmpty())
        composeTestRule.onNodeWithText("Abonar", useUnmergedTree = true).fetchSemanticsNode()
    }

    @Test
    fun `control positivo - con el ojo abierto el mismo selector si ve los montos`() {
        monta(ocultos = false)

        val visibles = textosConDinero()
        // Saldo, parcialidad, promesa, cinco pastillas, liquidación, CTA y la hoja.
        assertTrue("el selector no ve montos ni con el ojo abierto: $visibles", visibles.size > 10)
        assertTrue(visibles.contains("Abonar $220"))
    }

    // --- Último pago -----------------------------------------------------------

    @Test
    fun `sin abonos no se pinta el ultimo pago`() {
        monta(ocultos = false, detalle = PagosFixtures.detalleVenta().copy(ultimoPago = null))

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ULTIMO_PAGO_DE_VENTA_TAG).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `control positivo - con abonos el ultimo pago se pinta con su fecha`() {
        monta(
            ocultos = false,
            detalle = PagosFixtures.detalleVenta().copy(ultimoPago = LocalDate.of(2026, 9, 17))
        )

        assertEquals(
            1,
            composeTestRule.onAllNodesWithTag(ULTIMO_PAGO_DE_VENTA_TAG).fetchSemanticsNodes().size
        )
    }

    // --- (d) el dock a letra grande --------------------------------------------

    @Test
    fun `a 1_5 el dock va en una fila, el CTA dice Abonar y nada ocupa dos renglones`() =
        dockEnUnaFila(FontSizeLevel.GRANDE)

    @Test
    fun `a 2_0 el dock va en una fila, el CTA dice Abonar y nada ocupa dos renglones`() =
        dockEnUnaFila(FontSizeLevel.MUY_GRANDE)

    @Test
    fun `a letra normal el CTA dice la cantidad`() {
        monta(ocultos = false)

        assertEquals(1, renglonesDe("Abonar $220"))
    }

    /**
     * Control positivo de las DOS mediciones: el dock del cliente (sin
     * `unaSolaFila`) SÍ se apila a 2.0 —el CTA y "Visita" no comparten
     * renglón— y un CTA largo SÍ ocupa dos renglones. Si el selector o el
     * conteo de renglones estuvieran rotos, esto saldría igual que la venta.
     */
    @Test
    fun `control positivo - el dock apilado y un texto partido si se detectan`() {
        composeTestRule.setContent {
            Escala(FontSizeLevel.MUY_GRANDE) {
                MspTheme(darkTheme = false, animateColors = false) {
                    Box {
                        DockDeAcciones(
                            textoPrimario = "Registrar abono de la semana",
                            onPrimario = {},
                            onVisita = {}
                        )
                    }
                }
            }
        }

        val cta = composeTestRule.onNodeWithTag(CTA_PRIMARIO_TAG).getUnclippedBoundsInRoot()
        val visita = composeTestRule.onNodeWithTag(CTA_VISITA_TAG).getUnclippedBoundsInRoot()
        assertTrue("el dock del cliente no se apiló", visita.top >= cta.bottom)
        assertTrue(renglonesDe("Registrar abono de la semana") >= 2)
    }

    private fun dockEnUnaFila(nivel: FontSizeLevel) {
        monta(ocultos = false, nivel = nivel)

        val cta = composeTestRule.onNodeWithTag(CTA_PRIMARIO_TAG).getUnclippedBoundsInRoot()
        val visita = composeTestRule.onNodeWithTag(CTA_VISITA_TAG).getUnclippedBoundsInRoot()
        val condonar = composeTestRule.onNodeWithTag(CTA_CONDONAR_TAG).getUnclippedBoundsInRoot()
        assertTrue(
            "el dock se apiló: CTA ${cta.top}..${cta.bottom}, Visita ${visita.top}",
            visita.top < cta.bottom
        )
        assertTrue("Cond. se fue a otro renglón", condonar.top < cta.bottom)
        assertTrue("Visita no va a la derecha del CTA", visita.left >= cta.right)
        assertTrue("Cond. no va a la derecha de Visita", condonar.left >= visita.right)
        assertTrue("Cond. se sale de la pantalla: ${condonar.right}", condonar.right.value <= 360f)
        assertEquals("el CTA se partió", 1, renglonesDe("Abonar"))
        assertEquals("Visita se partió", 1, renglonesDe("Visita"))
        assertEquals("Cond. se partió", 1, renglonesDe("Cond."))
        // Sin "⋯": Condonar era lo único del menú del detalle de venta.
        composeTestRule.onAllNodesWithTag(MENU_DEL_DOCK_TAG).assertCountEquals(0)
    }

    // --- (f) la hoja a 2.0 -------------------------------------------------------

    @Test
    fun `a 2_0 la hoja de datos es de una columna`() {
        hoja(FontSizeLevel.MUY_GRANDE)

        val rotulo = composeTestRule.onAllNodesWithTag(
            ROTULO_DE_DATOS_TAG
        )[0].getUnclippedBoundsInRoot()
        val valor = composeTestRule.onAllNodesWithTag(
            VALOR_DE_DATOS_TAG
        )[0].getUnclippedBoundsInRoot()
        assertTrue("el valor no va debajo del rótulo", valor.top >= rotulo.bottom)
        assertEquals("el valor no arranca donde el rótulo", rotulo.left, valor.left)
    }

    @Test
    fun `control positivo - a 1_0 la hoja es de dos columnas`() {
        hoja(FontSizeLevel.NORMAL)

        val rotulo = composeTestRule.onAllNodesWithTag(
            ROTULO_DE_DATOS_TAG
        )[0].getUnclippedBoundsInRoot()
        val valor = composeTestRule.onAllNodesWithTag(
            VALOR_DE_DATOS_TAG
        )[0].getUnclippedBoundsInRoot()
        assertTrue("a 1.0 el valor debería ir a la derecha", valor.left >= rotulo.right)
        assertTrue(valor.top < rotulo.bottom)
    }

    // ---------------------------------------------------------------------------

    private fun textosConDinero(): List<String> {
        val dinero = Regex("""\$\s?\d""")
        return composeTestRule.onAllNodes(
            SemanticsMatcher(
                "tiene texto"
            ) { it.config.getOrNull(SemanticsProperties.Text) != null },
            useUnmergedTree = true
        ).fetchSemanticsNodes()
            .flatMap { nodo -> nodo.config[SemanticsProperties.Text].map { it.text } }
            .filter { dinero.containsMatchIn(it) }
    }

    private fun renglonesDe(texto: String): Int {
        val nodo = composeTestRule.onNodeWithText(
            texto,
            useUnmergedTree = true
        ).fetchSemanticsNode()
        val resultados = mutableListOf<TextLayoutResult>()
        nodo.config[SemanticsActions.GetTextLayoutResult].action?.invoke(resultados)
        return resultados.single().lineCount
    }

    private fun monta(
        ocultos: Boolean,
        detalle: DetalleVenta = PagosFixtures.detalleVenta(),
        nivel: FontSizeLevel = FontSizeLevel.NORMAL
    ) {
        composeTestRule.setContent {
            Escala(nivel) {
                MspTheme(darkTheme = false, animateColors = false) {
                    Box {
                        Pantalla(detalle, ocultos)
                        // La hoja de datos, montada por su cuerpo: el `Popup`
                        // del `ModalBottomSheet` no entra a estas pruebas.
                        if (nivel == FontSizeLevel.NORMAL) {
                            CuerpoDeLosDatosDeLaVenta(detalle = detalle, ocultos = ocultos)
                        }
                    }
                }
            }
        }
    }

    private fun hoja(nivel: FontSizeLevel) {
        composeTestRule.setContent {
            Escala(nivel) {
                MspTheme(darkTheme = false, animateColors = false) {
                    CuerpoDeLosDatosDeLaVenta(
                        detalle = PagosFixtures.detalleVenta(),
                        ocultos = false
                    )
                }
            }
        }
    }

    /**
     * La escala COMPLETA: el nivel y la densidad con su `fontScale`, como
     * `PagosScreenshotTest.capture`. Sólo el nivel sin la densidad deja la letra
     * a 1.0 y la prueba mediría otra pantalla.
     */
    @Composable
    private fun Escala(nivel: FontSizeLevel, contenido: @Composable () -> Unit) {
        val densidad = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(densidad.density, nivel.nominalScale),
            LocalFontSizeLevel provides nivel,
            content = contenido
        )
    }

    @Composable
    private fun Pantalla(detalle: DetalleVenta, ocultos: Boolean) {
        DetalleVentaContent(
            state = DetalleVentaUiState(
                cargando = false,
                detalle = detalle,
                montosOcultos = ocultos
            ),
            onAtras = {},
            onRegistrarAbono = {},
            onRegistrarVisita = {},
            onUsarLiquidacion = {},
            onVerAbonos = {},
            onVerGarantia = {}
        )
    }
}
