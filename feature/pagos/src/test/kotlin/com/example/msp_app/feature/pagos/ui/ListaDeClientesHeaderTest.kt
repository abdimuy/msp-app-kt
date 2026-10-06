package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.ABRIR_ORDEN_TAG
import com.example.msp_app.feature.pagos.ui.components.HOJA_DE_ORDEN_TAG
import com.example.msp_app.feature.pagos.ui.components.PILDORA_DE_BUSCAR_TAG
import com.example.msp_app.feature.pagos.ui.components.RENGLON_DE_ORDEN_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * La cabecera que se encoge al bajar, la hamburguesa, el renglón de orden y
 * el buscador flotante (mock "Título"/"Buscador"/"Orden", decisión del dueño
 * 2026-10-05) — todo montado sobre [ListaDeClientesContent] completo, para
 * medir cómo se comportan juntos.
 *
 * La ruta de prueba trae [CUARENTA] clientes a propósito: con dos o tres —lo
 * que trae [ListaFixtures]— la lista no alcanza a desbordar el viewport.
 *
 * El scroll se simula con `performTouchInput { swipeUp()/swipeDown() }` y NO
 * con `performScrollToIndex`: medido, ese salto programático reporta un
 * "settle" de un índice hacia atrás justo después de saltar (`38,184` →
 * `37,357` en la corrida que lo descubrió) que la regla de "regreso rápido"
 * interpreta como que el cobrador subió el dedo — un falso colapso-se-deshace
 * que nunca ocurre con un gesto real, continuo, sin saltos.
 */
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ListaDeClientesHeaderTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private var menuAbierto = 0

    @Test
    fun `la hamburguesa invoca onAbrirMenu`() {
        montar()

        composeTestRule.onNodeWithTag(BOTON_MENU_TAG).performClick()

        assertEquals(1, menuAbierto)
    }

    @Test
    fun `con busqueda el renglon de orden se oculta`() {
        montar(query = "Zepeda")

        composeTestRule.onNodeWithTag(RENGLON_DE_ORDEN_TAG).assertDoesNotExist()
    }

    @Test
    fun `sin busqueda el renglon de orden se muestra y abre la hoja`() {
        montar()

        composeTestRule.onNodeWithTag(RENGLON_DE_ORDEN_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ABRIR_ORDEN_TAG).performClick()
        composeTestRule.onNodeWithTag(HOJA_DE_ORDEN_TAG).assertIsDisplayed()
    }

    @Test
    fun `al principio el titulo grande se ve y la pildora no`() {
        montar()

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TITULO_COMPACTO_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).assertDoesNotExist()
    }

    @Test
    fun `bajar colapsa el titulo grande y muestra la pildora`() {
        montar()

        bajarLaLista()

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TITULO_COMPACTO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).assertIsDisplayed()
    }

    /**
     * **"Regreso rápido"**: subir un poco —sin llegar al principio— vuelve a
     * mostrar la cabecera grande. Un solo `swipeDown()` después de bajar
     * mucho no alcanza a devolver la lista al tope (`CUARENTA` filas de
     * sobra), así que el control es real: si bastara con llegar al principio,
     * un solo swipe de vuelta ya habría llegado.
     */
    @Test
    fun `subir un poco sin llegar al principio reexpande la cabecera`() {
        montar()
        bajarLaLista()
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput { swipeDown() }

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertIsDisplayed()
    }

    @Test
    fun `tocar la pildora abre el campo compacto con foco`() {
        montar()
        bajarLaLista()

        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).performClick()

        composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG).assertIsFocused()
    }

    @Test
    fun `la X del campo compacto borra el texto y conserva el foco`() {
        montar()
        bajarLaLista()
        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).performClick()
        composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG).performTextInput("Zepeda")

        composeTestRule.onNodeWithTag(BORRAR_BUSQUEDA_COMPACTA_TAG).performClick()

        composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG).assertIsFocused()
    }

    /** Varios `swipeUp()` reales — no un salto — para no disparar el "settle" documentado arriba. */
    private fun bajarLaLista() {
        repeat(REPETICIONES_DEL_SWIPE) {
            composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput { swipeUp() }
        }
    }

    private fun montar(query: String = "") {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                var texto by remember { mutableStateOf(query) }
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
                    onAbrirMenu = { menuAbierto++ }
                )
            }
        }
    }

    private fun estado(query: String): ListaDeClientesUiState {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientesDePrueba(),
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
            hoy = ListaFixtures.HOY,
            contextoSemana = "semana del 31 ago al 6 sep"
        )
    }

    private fun clientesDePrueba(): List<ClienteEnLista> = (1..CUARENTA).map { n ->
        ListaFixtures.cliente(
            clienteId = 20_000 + n,
            nombre = "Cliente Número $n",
            ventas = listOf(
                ListaFixtures.venta(
                    ventaId = 30_000 + n,
                    folio = "V-$n",
                    descripcion = "Mueble $n",
                    saldo = ListaFixtures.dinero("1000"),
                    totalVenta = ListaFixtures.dinero("2000"),
                    enganche = ListaFixtures.dinero("200"),
                    fechaVenta = ListaFixtures.HOY.minusDays(n.toLong()),
                    estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
                )
            )
        )
    }

    private companion object {
        const val CUARENTA = 40
        const val REPETICIONES_DEL_SWIPE = 6
    }
}
