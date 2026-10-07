package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.ui.components.PILDORA_DE_BUSCAR_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Los dos buscadores de la lista (el grande de la cabecera y el compacto de la barra)
 * se leen bien: texto de 17 sp en una caja visible de 44 dp, no los 56 dp de Material
 * (decisión del dueño, 2026-10-07, tras verlo en el teléfono: 20 sp / 35.5 dp fue
 * demasiado grande y delgado).
 *
 * Se MIDE el nodo, no el parámetro: el tamaño de letra sale del `TextLayoutResult` que
 * el propio campo reporta, y el alto, de los límites de la caja visible.
 *
 * Medido el 2026-10-07 ANTES del cambio (esta misma prueba sobre `71a4779b`, con el
 * alto tomado del nodo porque la caja visible no existía aparte): grande 13 sp y 56 dp,
 * compacto 15 sp y 56 dp — las constantes `*_ANTES`.
 *
 * `NATIVE`: en `LEGACY` el texto se mide casi en cero.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ElBuscadorSeLeeGrandeTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `el buscador grande escribe a 17 sp en una caja de 44 dp`() {
        montar(arrancaAbajo = false)
        val campo = composeTestRule.onNodeWithTag(BUSCADOR_TAG)
        campo.performTextInput("Rosa")

        val letra = tamanoDeLetra(campo)
        val caja = altoDe(BUSCADOR_TAG + CAJA_VISIBLE)
        println("MEDIDA grande letra=${letra}sp caja=$caja nodo=${altoDelNodo(BUSCADOR_TAG)}")
        assertEquals("letra", LETRA_DEL_BUSCADOR.value, letra, 0.01f)
        assertCajaDe44(caja)
        assertTrue("toque ${altoDelNodo(BUSCADOR_TAG)}", altoDelNodo(BUSCADOR_TAG) >= 50.dp)
    }

    @Test
    fun `el buscador compacto escribe a 17 sp en una caja de 44 dp`() {
        montar(arrancaAbajo = true)
        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).performClick()
        val campo = composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG)
        campo.performTextInput("Rosa")

        val letra = tamanoDeLetra(campo)
        val caja = altoDe(BUSCADOR_COMPACTO_TAG + CAJA_VISIBLE)
        println(
            "MEDIDA compacto letra=${letra}sp caja=$caja nodo=${altoDelNodo(BUSCADOR_COMPACTO_TAG)}"
        )
        assertEquals("letra", LETRA_DEL_BUSCADOR.value, letra, 0.01f)
        assertCajaDe44(caja)
        assertTrue("toque", altoDelNodo(BUSCADOR_COMPACTO_TAG) >= 50.dp)
    }

    /** Cursor, selección, ✕ y la acción "buscar" del teclado siguen funcionando. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `cursor seleccion borrar y buscar siguen funcionando en el compacto`() {
        montar(arrancaAbajo = true)
        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).performClick()
        val campo = composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG)
        campo.performTextInput("Rosa")
        assertEquals(TextRange(4), seleccion(campo))

        campo.performTextInputSelection(TextRange(0, 2))
        assertEquals(TextRange(0, 2), seleccion(campo))

        campo.performImeAction()
        campo.assertTextEquals("Rosa", includeEditableText = true)

        composeTestRule.onNodeWithTag(BORRAR_BUSQUEDA_COMPACTA_TAG).performClick()
        assertEquals("", campo.fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        campo.assertIsFocused()
    }

    private fun assertCajaDe44(caja: Dp) {
        val diferencia = (caja - ALTO_DE_LA_CAJA_DEL_BUSCADOR).value
        assertTrue("caja $caja, se esperaba 44 dp ±1", diferencia in -1f..1f)
        assertTrue("caja $caja no es más baja que antes", caja < ALTO_ANTES)
    }

    private fun tamanoDeLetra(campo: SemanticsNodeInteraction): Float {
        val resultados = mutableListOf<TextLayoutResult>()
        campo.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!(resultados)
        return resultados.first().layoutInput.style.fontSize.value
    }

    private fun seleccion(campo: SemanticsNodeInteraction): TextRange =
        campo.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange]

    private fun altoDe(tag: String): Dp =
        composeTestRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot().height

    private fun altoDelNodo(tag: String): Dp =
        composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().height

    private fun montar(arrancaAbajo: Boolean) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                var texto by remember { mutableStateOf("") }
                ListaDeClientesContent(
                    state = estado(texto),
                    onBuscar = { texto = it },
                    onElegirSegmento = {},
                    onAbrirCliente = {},
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onElegirOrden = {},
                    onQuitarOrden = {},
                    onAbrirMenu = {},
                    listState = rememberLazyListState(
                        initialFirstVisibleItemIndex = if (arrancaAbajo) INDICE_ABAJO else 0
                    )
                )
            }
        }
    }

    private fun estado(query: String): ListaDeClientesUiState {
        val clientes = (1..VEINTE).map { n ->
            ListaFixtures.cliente(
                clienteId = 70_000 + n,
                nombre = "Rosa Hernández Ortega $n",
                ventas = listOf(
                    ListaFixtures.venta(
                        ventaId = 71_000 + n,
                        folio = "V-$n",
                        descripcion = "Ropero",
                        saldo = ListaFixtures.dinero("1000"),
                        totalVenta = ListaFixtures.dinero("2000"),
                        enganche = ListaFixtures.dinero("200"),
                        fechaVenta = ListaFixtures.HOY.minusDays(n.toLong()),
                        estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
                    )
                )
            )
        }
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = query,
            hoy = ListaFixtures.HOY
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = query,
            hoy = ListaFixtures.HOY
        )
    }

    private companion object {
        const val VEINTE = 20
        const val INDICE_ABAJO = 8
        const val CAJA_VISIBLE = SUFIJO_CAJA_VISIBLE
        val ALTO_ANTES = 56.dp
    }
}
