package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_SEGMENTO_TAG
import com.example.msp_app.feature.pagos.ui.components.QUITAR_ORDEN_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Hallazgo 1 y 2 de la revisión final de `feat/lista-venta-nueva-orden`
 * (`.superpowers/sdd/2026-10-06-menu-lateral/final-fix-report.md`):
 *
 * 1. Cambiar el orden (hoja "Ordenar por") deja la lista anclada al cliente
 *    que estaba primero antes del cambio — con `key = clienteId`, ese cliente
 *    simplemente se mueve a su posición nueva y el `LazyListState` sigue
 *    mirando el mismo índice.
 * 2. Un solo [LazyListState] para los cuatro segmentos: cambiar de chip
 *    conserva el scroll del segmento anterior, y durante el deslizamiento dos
 *    `LazyColumn` (el que sale y el que entra) comparten el mismo estado.
 *
 * Para (1) se cambia `state.orden` directo, sin pasar por la hoja real
 * (`ModalBottomSheet`): `performClick()` no cruza al `Popup` donde Material 3
 * la monta bajo Robolectric (mismo límite medido en el KDoc de
 * `HojaDeOrdenarPor`/`ListaMatrixScreenshotTest`). Lo que esta prueba cubre es
 * la reacción de [ListaDeClientesContent] a que `state.orden` cambie — la
 * elección en sí la cubren `ListaDeClientesHeaderTest`/`ListaDeClientesViewModelTest`.
 */
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ScrollDeLaListaPorOrdenYSegmentoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `elegir un orden nuevo estando abajo regresa al primer elemento`() {
        lateinit var estado: LazyListState
        lateinit var cambiarOrden: (OrdenDeLaLista) -> Unit
        montarConOrdenElegible(
            capturarListState = { estado = it },
            capturarCambiarOrden = { cambiarOrden = it }
        )

        bajarLaLista()
        assertTrue(
            "la lista debe estar desplazada antes de elegir orden",
            estado.firstVisibleItemIndex > 0
        )

        cambiarOrden(OrdenDeLaLista.VENTAS_NUEVAS)
        composeTestRule.waitForIdle()

        assertEquals(0, estado.firstVisibleItemIndex)
    }

    @Test
    fun `quitar el orden estando abajo regresa al primer elemento`() {
        lateinit var estado: LazyListState
        montarConOrdenElegible(
            ordenInicial = OrdenDeLaLista.VENTAS_NUEVAS,
            capturarListState = { estado = it }
        )

        bajarLaLista()
        assertTrue(
            "la lista debe estar desplazada antes de quitar el orden",
            estado.firstVisibleItemIndex > 0
        )

        composeTestRule.onNodeWithTag(QUITAR_ORDEN_TAG).performClick()
        composeTestRule.waitForIdle()

        assertEquals(0, estado.firstVisibleItemIndex)
    }

    @Test
    fun `cada segmento recuerda su propia posicion de scroll`() {
        montarConDosSegmentos()

        bajarLaLista()
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_SIN_VISITAR).assertDoesNotExist()

        composeTestRule.onNodeWithTag(
            CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.YA_NO_ESTA_SEMANA.name.lowercase()
        )
            .performClick()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_DESPUES)
            .assertIsDisplayed() // un segmento recién visitado arranca en el tope

        composeTestRule.onNodeWithTag(
            CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.SIN_VISITAR.name.lowercase()
        )
            .performClick()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()

        // "sin visitar" debe seguir en la posición donde se dejó, no reiniciar.
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_SIN_VISITAR).assertDoesNotExist()
    }

    private fun bajarLaLista() {
        repeat(REPETICIONES_DEL_SWIPE) {
            composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput { swipeUp() }
        }
    }

    private fun montarConOrdenElegible(
        ordenInicial: OrdenDeLaLista = OrdenDeLaLista.COBRANZA,
        capturarListState: (LazyListState) -> Unit,
        capturarCambiarOrden: ((OrdenDeLaLista) -> Unit) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                var orden by remember { mutableStateOf(ordenInicial) }
                capturarCambiarOrden { orden = it }
                val listState = rememberLazyListState()
                capturarListState(listState)
                ListaDeClientesContent(
                    state = estadoDeUnSegmento(orden),
                    onBuscar = {},
                    onElegirSegmento = {},
                    onAbrirCliente = {},
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onElegirOrden = { orden = it },
                    onQuitarOrden = { orden = OrdenDeLaLista.COBRANZA },
                    onAbrirMenu = {},
                    listState = listState
                )
            }
        }
    }

    private fun montarConDosSegmentos() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                var segmento by remember { mutableStateOf(SegmentoDeCobranza.SIN_VISITAR) }
                ListaDeClientesContent(
                    state = estadoDeDosSegmentos(segmento),
                    onBuscar = {},
                    onElegirSegmento = { segmento = it },
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
    }

    private fun estadoDeUnSegmento(orden: OrdenDeLaLista): ListaDeClientesUiState {
        val clientes =
            clientesDePrueba(SegmentoDeCobranza.SIN_VISITAR, EstadoCuenta.SIN_TOCAR, CUARENTA, 0)
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY,
            orden = orden
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            segmento = SegmentoDeCobranza.SIN_VISITAR,
            hoy = ListaFixtures.HOY,
            orden = orden,
            contextoSemana = "semana del 31 ago al 6 sep"
        )
    }

    private fun estadoDeDosSegmentos(segmento: SegmentoDeCobranza): ListaDeClientesUiState {
        val clientes = clientesDePrueba(SegmentoDeCobranza.SIN_VISITAR, EstadoCuenta.SIN_TOCAR, CUARENTA, 0) +
            clientesDePrueba(SegmentoDeCobranza.YA_NO_ESTA_SEMANA, EstadoCuenta.SE_NEGO, CUARENTA, 1000)
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
            segmento = segmento,
            query = "",
            hoy = ListaFixtures.HOY
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            segmento = segmento,
            hoy = ListaFixtures.HOY,
            contextoSemana = "semana del 31 ago al 6 sep"
        )
    }

    /**
     * Los 40 clientes son IDÉNTICOS entre sí salvo por `clienteId` —misma
     * fecha de venta, mismo saldo, mismo estado— a propósito: el orden
     * COBRANZA que usa esta prueba desempata al final por `clienteId`
     * ascendente ([OrdenDeLaLista], KDoc de la clase), así que "Número 1"
     * (el `clienteId` más bajo) es SIEMPRE el primero de la lista. Variar la
     * fecha de venta (como hacía el primer intento) deja que el orden de
     * cobranza reordene por antigüedad y "Número 1" deja de ser el primero.
     */
    private fun clientesDePrueba(
        segmento: SegmentoDeCobranza,
        estadoCuenta: EstadoCuenta,
        cantidad: Int,
        base: Int
    ): List<ClienteEnLista> = (1..cantidad).map { n ->
        ListaFixtures.cliente(
            clienteId = 40_000 + base + n,
            nombre = "Cliente ${segmento.name} Número $n",
            ventas = listOf(
                ListaFixtures.venta(
                    ventaId = 50_000 + base + n,
                    folio = "V-${base + n}",
                    descripcion = "Mueble ${base + n}",
                    saldo = ListaFixtures.dinero("1000"),
                    totalVenta = ListaFixtures.dinero("2000"),
                    enganche = ListaFixtures.dinero("200"),
                    fechaVenta = ListaFixtures.HOY.minusDays(FECHA_VENTA_FIJA),
                    estado = ListaFixtures.estado(estadoCuenta)
                )
            )
        )
    }

    private companion object {
        const val CUARENTA = 40
        const val REPETICIONES_DEL_SWIPE = 6
        const val PRIMER_CLIENTE_SIN_VISITAR = "Cliente SIN_VISITAR Número 1"
        const val PRIMER_CLIENTE_DESPUES = "Cliente YA_NO_ESTA_SEMANA Número 1"
        const val FECHA_VENTA_FIJA = 10L
    }
}
