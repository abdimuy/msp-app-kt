package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.msp_app.core.designsystem.theme.FontSizeLevel
import com.example.msp_app.core.designsystem.theme.LocalFontSizeLevel
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_SEGMENTO_TAG
import com.example.msp_app.feature.pagos.ui.components.CONTROL_SEGMENTADO_TAG
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

    private fun pinta(
        nivel: FontSizeLevel = FontSizeLevel.NORMAL,
        clientes: List<ClienteEnLista> = ListaFixtures.ruta()
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, nivel.nominalScale),
                LocalFontSizeLevel provides nivel
            ) {
                MspTheme(darkTheme = false, animateColors = false) { Lista(clientes) }
            }
        }
    }

    /**
     * La pantalla con **la ruta entera** en la lista y los conteos de los cuatro
     * chips calculados por la proyección de verdad.
     *
     * Las filas NO pasan por el filtro de un chip, y es deliberado: desde que
     * `TODOS` se retiró, los cuatro chips **particionan** el catálogo, así que
     * ningún chip enseña la ruta completa. Lo que estas pruebas miden es el
     * renderizador —cuántas tarjetas pinta y qué tan alto mide cada chip—, no el
     * filtro; el filtro lo cobra `CarteraEnPantallaTest` chip por chip. Pasar la
     * ruta entera deja las dos preguntas separadas en vez de medir una a través
     * de la otra.
     */
    @Composable
    private fun Lista(clientes: List<ClienteEnLista>) {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
            segmento = SegmentoDeCobranza.POR_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY
        )
        ListaDeClientesContent(
            state = ListaDeClientesUiState(
                cargando = false,
                clientes = clientes,
                conteos = proyeccion.conteos
            ),
            onBuscar = {},
            onElegirSegmento = {},
            onAbrirCliente = {},
            onReintentar = {},
            onAlternarTema = {},
            onAlternarPrivacidad = {},
            onElegirOrden = {},
            onQuitarOrden = {},
            onAbrirMenu = {}
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

    /**
     * **Las tres pastillas se pintan, y el conteo sale del dato** (E-APP-070).
     *
     * La ruta de prueba trae la promesa de Esperanza (hoy: trabajo pendiente) y
     * la de Ofelia (dentro de tres días: No paga). Por visitar marca **4**, No
     * paga **1** y Pagados **1** (la otra venta de Victoria). El control positivo
     * está en el test de abajo: la misma ruta sin esas dos puertas marca 3, 0 y 1.
     */
    @Test
    fun `E-APP-070 las tres pastillas se pintan, y cada una cuenta lo suyo`() {
        pinta(clientes = ListaFixtures.rutaConTresPastillas())

        SegmentoDeCobranza.entries.forEach {
            composeTestRule
                .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + it.name.lowercase())
                .assertIsDisplayed()
        }
        // El conteo va DENTRO del chip: la semántica del `selectable` fusiona sus
        // textos, así que se afirma sobre el nodo del chip.
        mapOf(
            SegmentoDeCobranza.POR_VISITAR to "4",
            SegmentoDeCobranza.NO_PAGA to "1",
            SegmentoDeCobranza.PAGADOS to "1"
        ).forEach { (segmento, conteo) ->
            composeTestRule
                .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + segmento.name.lowercase())
                .assertTextContains(segmento.etiqueta)
                .assertTextContains(conteo)
        }
    }

    /**
     * **Control positivo del conteo.** Sin las dos promesas el mismo control marca
     * otras cifras: los números del test de arriba salen del dato.
     */
    @Test
    fun `E-APP-070 sin promesas No paga marca cero y Por visitar una menos`() {
        pinta(clientes = ListaFixtures.ruta())
        composeTestRule
            .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.NO_PAGA.name.lowercase())
            .assertTextContains("0")
        composeTestRule
            .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.POR_VISITAR.name.lowercase())
            .assertTextContains("3")
    }

    /**
     * El mismo par, una capa más abajo: sobre la proyección pura, sin Compose.
     * La promesa de hoy suma a Por visitar; la de mañana en adelante, a No paga.
     */
    @Test
    fun `E-APP-070 la promesa de hoy cuenta en Por visitar y la futura en No paga`() {
        val conPromesas = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.rutaConTresPastillas(),
            segmento = SegmentoDeCobranza.POR_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY
        )
        assertEquals(4, conPromesas.conteos[SegmentoDeCobranza.POR_VISITAR])
        assertEquals(1, conPromesas.conteos[SegmentoDeCobranza.NO_PAGA])

        // Control positivo: sin esas puertas las cifras bajan.
        val sinPromesas = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.ruta(),
            segmento = SegmentoDeCobranza.POR_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY
        )
        assertEquals(3, sinPromesas.conteos[SegmentoDeCobranza.POR_VISITAR])
        assertEquals(0, sinPromesas.conteos[SegmentoDeCobranza.NO_PAGA])
    }

    @Test
    fun `cada chip mide al menos 50dp de alto`() {
        pinta()
        SegmentoDeCobranza.entries.forEach { segmento ->
            val alto = altoDe(segmento)
            assertTrue("el chip ${segmento.etiqueta} mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    /**
     * **Las TRES, no sólo las que caben.** A `MUY_GRANDE` los rótulos crecen y
     * el segmentado —un `LazyRow` desde 2026-09-22— deja de componer lo que no
     * cabe en pantalla; el riesgo es que un segmento fuera del viewport mida
     * distinto del que se ve, o que la prueba lo dé por bueno sin haberlo tocado.
     * Por eso [altoDe] lo trae a la vista antes de medirlo, y por eso se miden
     * bordes **sin recortar**.
     */
    @Test
    fun `a escala muy grande los chips siguen siendo tocables`() {
        pinta(FontSizeLevel.MUY_GRANDE)
        SegmentoDeCobranza.entries.forEach { segmento ->
            val alto = altoDe(segmento)
            assertTrue("el chip ${segmento.etiqueta} mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    /**
     * El alto real del chip, **después de traerlo a la vista**.
     *
     * El `performScrollToNode` va sobre el único nodo deslizable que cuelga del
     * control segmentado — no sobre `hasScrollAction()` a secas, que también
     * casaría con la `LazyColumn` de la lista. Si los cuatro caben, no desliza
     * nada y la medición es la misma.
     */
    private fun altoDe(segmento: SegmentoDeCobranza): Dp {
        val tag = CHIP_DE_SEGMENTO_TAG + segmento.name.lowercase()
        composeTestRule
            .onNode(hasScrollAction() and hasAnyAncestor(hasTestTag(CONTROL_SEGMENTADO_TAG)))
            .performScrollToNode(hasTestTag(tag))
        val bordes = composeTestRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
        return bordes.bottom - bordes.top
    }

    @Test
    fun `sin clientes en el chip se dice, no se deja el hueco`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                ListaDeClientesContent(
                    state = ListaDeClientesUiState(
                        cargando = false,
                        segmento = SegmentoDeCobranza.POR_VISITAR
                    ),
                    onBuscar = {},
                    onElegirSegmento = {},
                    onAbrirCliente = {},
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onElegirOrden = {},
                    onQuitarOrden = {},
                    onAbrirMenu = {}
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
