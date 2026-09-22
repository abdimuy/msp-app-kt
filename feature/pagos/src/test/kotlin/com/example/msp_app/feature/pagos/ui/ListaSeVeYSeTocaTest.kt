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
            segmento = SegmentoDeCobranza.SIN_VISITAR,
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
            onAlternarPrivacidad = {}
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
     * **Los cuatro chips se pintan, y el conteo que cambia sale del dato.**
     *
     * La partición sólo sirve si las cuatro respuestas están a la vista: con
     * `TODOS` retirado, un chip que no se pinte se lleva consigo las cuentas que
     * sólo él enseña.
     *
     * La ruta de prueba trae la promesa de Esperanza, que cae **hoy** y por lo
     * tanto es trabajo pendiente: *volver* marca **1**. Su control positivo vive
     * en el test de abajo — la misma ruta sin esa puerta marca **0**—, así que
     * este 1 lo produce el dato y no una cuenta que siempre da uno.
     */
    @Test
    fun `los cuatro chips se pintan, y volver cuenta el compromiso de hoy`() {
        pinta(clientes = ListaFixtures.rutaConPromesaDeHoy())

        SegmentoDeCobranza.entries.forEach {
            composeTestRule
                .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + it.name.lowercase())
                .assertIsDisplayed()
        }
        // El conteo va DENTRO del chip: la semántica del `selectable` fusiona sus
        // textos, así que se afirma sobre el nodo del chip.
        composeTestRule
            .onNodeWithTag(
                CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.VOLVER_A_VISITAR.name.lowercase()
            )
            .assertTextContains("1")
    }

    /**
     * **Control positivo del conteo.** Sin ningún compromiso el mismo chip se
     * pinta con **0**: la cifra del test de arriba sale del dato. Si este test
     * viera un 1, el de arriba no probaría nada.
     */
    @Test
    fun `sin compromisos el chip de volver marca cero`() {
        pinta(clientes = ListaFixtures.ruta())
        composeTestRule
            .onNodeWithTag(
                CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.VOLVER_A_VISITAR.name.lowercase()
            )
            .assertTextContains("0")
    }

    /**
     * El mismo par, una capa más abajo: sobre la proyección pura, sin Compose.
     * Es donde se ve que el 1 y el 0 los decide la ruta y no la pantalla.
     */
    @Test
    fun `el segmento de volver cuenta la promesa que cae hoy`() {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.rutaConPromesaDeHoy(),
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY
        )

        assertEquals(1, proyeccion.conteos[SegmentoDeCobranza.VOLVER_A_VISITAR])
        // Control positivo: sin esa puerta el chip vuelve a 0.
        val sinPromesa = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.ruta(),
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY
        )
        assertEquals(0, sinPromesa.conteos[SegmentoDeCobranza.VOLVER_A_VISITAR])
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
     * **Los CUATRO, no sólo los que caben.** A `MUY_GRANDE` los rótulos crecen y
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
                        segmento = SegmentoDeCobranza.VOLVER_A_VISITAR
                    ),
                    onBuscar = {},
                    onElegirSegmento = {},
                    onAbrirCliente = {},
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {}
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
