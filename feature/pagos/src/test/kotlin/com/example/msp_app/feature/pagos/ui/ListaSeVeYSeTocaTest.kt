package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
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
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_SEGMENTO_TAG
import com.example.msp_app.feature.pagos.ui.components.FILA_DE_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.HOY_VISIBLE
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

    @Composable
    private fun Lista(clientes: List<ClienteEnLista>) {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
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
     * **El chip "hoy" ya se pinta, y cuenta compromisos reales.**
     *
     * Lo que cambió no es el chip: es que la captura estructurada de la Task 19
     * —la única que escribe `PROMESA_FECHA`/`CITA_FECHA`— quedó **alcanzable**
     * con el cableado de la Task 21, y el `NewVisitDialog`, que metía la fecha
     * dentro del texto libre de `NOTA` y no llenaba ninguna de las dos columnas,
     * quedó retirado. Por eso el interruptor se mueve aquí y no antes.
     *
     * La ruta de prueba trae la promesa de Esperanza que cae hoy, y el chip
     * marca **1**. Su control positivo vive en el test de abajo: la MISMA
     * proyección sobre la ruta sin promesas marca **0**, así que este 1 lo
     * produce el dato y no una cuenta que siempre da uno.
     */
    @Test
    fun `el chip de hoy se pinta y cuenta el compromiso que cae hoy`() {
        pinta(clientes = ListaFixtures.rutaConPromesaDeHoy())
        assertEquals(true, HOY_VISIBLE)
        composeTestRule
            .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.HOY.name.lowercase())
            .assertIsDisplayed()
        // Los CUATRO chips están, y el de hoy trae su conteo al lado.
        SegmentoDeCobranza.entries.forEach {
            composeTestRule
                .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + it.name.lowercase())
                .assertIsDisplayed()
        }
        // El conteo va DENTRO del chip: la semántica del `Surface` clickeable
        // fusiona sus textos, así que se afirma sobre el nodo del chip.
        composeTestRule
            .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.HOY.name.lowercase())
            .assertTextContains("1")
    }

    /**
     * **Control positivo del chip encendido.** Sin ningún compromiso de hoy el
     * mismo chip se pinta con **0**: la cifra del test de arriba sale del dato.
     * Si este test viera un 1, el de arriba no probaría nada.
     */
    @Test
    fun `sin compromisos de hoy el chip de hoy marca cero`() {
        pinta(clientes = ListaFixtures.ruta())
        composeTestRule
            .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.HOY.name.lowercase())
            .assertTextContains("0")
    }

    /**
     * **El segmento sí cuenta**, aunque su chip esté apagado: la promesa de
     * Esperanza cae hoy y el conteo la ve. Es lo que hace que encender el
     * booleano en la Task 21 sea un cambio de una línea y no un rediseño.
     */
    @Test
    fun `el segmento de hoy cuenta la promesa que cae hoy`() {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.rutaConPromesaDeHoy(),
            segmento = SegmentoDeCobranza.TODOS,
            query = "",
            hoy = ListaFixtures.HOY
        )

        assertEquals(1, proyeccion.conteos[SegmentoDeCobranza.HOY])
        // Control positivo: sin esa puerta el chip vuelve a 0, así que el 1 de
        // arriba lo produce el dato y no una cuenta que siempre da uno.
        val sinPromesa = CarteraEnPantalla.proyectar(
            clientes = ListaFixtures.ruta(),
            segmento = SegmentoDeCobranza.TODOS,
            query = "",
            hoy = ListaFixtures.HOY
        )
        assertEquals(0, sinPromesa.conteos[SegmentoDeCobranza.HOY])
    }

    @Test
    fun `cada chip visible mide al menos 50dp de alto`() {
        pinta()
        SegmentoDeCobranza.entries.filter {
            HOY_VISIBLE || it != SegmentoDeCobranza.HOY
        }.forEach { segmento ->
            val bordes = composeTestRule
                .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + segmento.name.lowercase())
                .getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("el chip ${segmento.etiqueta} mide $alto", alto >= MINIMO_TOCABLE)
        }
    }

    /**
     * **Los CUATRO, no solo el primero.** A `MUY_GRANDE` el segmentado deja de
     * repartir el ancho y rueda en horizontal; el riesgo es que un segmento que
     * quedó fuera del viewport se mida distinto del que se ve. Se usan bordes
     * **sin recortar** justo por eso.
     */
    @Test
    fun `a escala muy grande los chips siguen siendo tocables`() {
        pinta(FontSizeLevel.MUY_GRANDE)
        SegmentoDeCobranza.entries.filter {
            HOY_VISIBLE || it != SegmentoDeCobranza.HOY
        }.forEach { segmento ->
            val bordes = composeTestRule
                .onNodeWithTag(CHIP_DE_SEGMENTO_TAG + segmento.name.lowercase())
                .getUnclippedBoundsInRoot()
            val alto = bordes.bottom - bordes.top
            assertTrue("el chip ${segmento.etiqueta} mide $alto", alto >= MINIMO_TOCABLE)
        }
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
