package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.ui.components.CHIP_DE_SEGMENTO_TAG
import java.time.LocalDate
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

    /**
     * La "✕" vive ahora en la fila del título, que se esconde al bajar (compactado
     * 2026-10-07): el regreso a COBRANZA se dispara por el mismo camino que usa
     * `onQuitarOrden` —el estado cambia de orden— en vez de tocar un nodo escondido.
     */
    @Test
    fun `quitar el orden estando abajo regresa al primer elemento`() {
        lateinit var estado: LazyListState
        lateinit var cambiarOrden: (OrdenDeLaLista) -> Unit
        montarConOrdenElegible(
            ordenInicial = OrdenDeLaLista.VENTAS_NUEVAS,
            capturarListState = { estado = it },
            capturarCambiarOrden = { cambiarOrden = it }
        )

        bajarLaLista()
        assertTrue(
            "la lista debe estar desplazada antes de quitar el orden",
            estado.firstVisibleItemIndex > 0
        )

        cambiarOrden(OrdenDeLaLista.COBRANZA)
        composeTestRule.waitForIdle()

        assertEquals(0, estado.firstVisibleItemIndex)
    }

    @Test
    fun `cada segmento recuerda su propia posicion de scroll`() {
        montarConDosSegmentos()

        bajarLaLista()
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_POR_VISITAR).assertDoesNotExist()

        composeTestRule.onNodeWithTag(
            CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.NO_PAGA.name.lowercase()
        )
            .performClick()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_DESPUES)
            .assertIsDisplayed() // un segmento recién visitado arranca en el tope

        composeTestRule.onNodeWithTag(
            CHIP_DE_SEGMENTO_TAG + SegmentoDeCobranza.POR_VISITAR.name.lowercase()
        )
            .performClick()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()

        // "Por visitar" debe seguir en la posición donde se dejó, no reiniciar.
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_POR_VISITAR).assertDoesNotExist()
    }

    /**
     * **Residual de la re-revisión final** (R20 en `progress.md`): el mapa
     * `segmento → LazyListState` vivía en un `remember`, que no sobrevive a
     * salir de la lista y volver (navegar a registrar un abono y regresar, o
     * que Android recree la actividad). Al volver, el único estado que sí se
     * restauraba era el `listState` hospedado —el del PRIMER segmento que se
     * mostró—, y el mapa nuevo se lo asignaba al segmento VISIBLE: "Después"
     * aparecía con el scroll de "Sin visitar", y "Sin visitar" volvía al tope.
     *
     * El segmento vive FUERA de la composición (como en el ViewModel, que sí
     * sobrevive), así que tras la restauración la pantalla vuelve al mismo
     * chip con el que se fue.
     */
    @Test
    fun `al volver a la lista cada segmento recupera su propio scroll`() {
        val restaurador = StateRestorationTester(composeTestRule)
        var segmento by mutableStateOf(SegmentoDeCobranza.POR_VISITAR)
        restaurador.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
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

        bajarLaLista()
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_POR_VISITAR).assertDoesNotExist()
        elegirChip(SegmentoDeCobranza.NO_PAGA)
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_DESPUES).assertIsDisplayed()

        restaurador.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // El segmento visible al volver sigue en SU tope, no hereda el de "sin visitar".
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_DESPUES).assertIsDisplayed()
        elegirChip(SegmentoDeCobranza.POR_VISITAR)
        // Y "sin visitar" sigue donde se dejó, no reinicia en el tope.
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_POR_VISITAR).assertDoesNotExist()
    }

    /**
     * Mismo residual, el caso de proceso nuevo: Android mató la app con la
     * lista abajo y un orden elegido hoy. El ViewModel NUEVO arranca
     * `cargando` y con el orden por defecto (COBRANZA); cuando la carga
     * termina, el orden restaurado de `ControladorDeOrden` llega como un
     * "cambio" de orden. Ese cambio NO lo hizo el cobrador y no debe mandar
     * la lista al tope — sólo uno elegido de verdad (ver la prueba de arriba
     * "elegir un orden nuevo...").
     */
    @Test
    fun `al recrear la pantalla con un orden ya elegido la lista no vuelve al tope`() {
        val restaurador = StateRestorationTester(composeTestRule)
        // Lo que emite el ViewModel NUEVO tras recrear: arranca cargando y en COBRANZA.
        var estadoDelViewModelNuevo by mutableStateOf(ListaDeClientesUiState())
        var creaciones = 0
        restaurador.setContent {
            // `remember` vuelve a correr sólo cuando la restauración recrea el
            // contenido: así el estado "de proceso nuevo" entra EXACTAMENTE en la
            // primera composición restaurada, sin depender de cuándo corra un cuadro.
            val creacion = remember { creaciones++ }
            MspTheme(darkTheme = false, animateColors = false) {
                ListaDeClientesContent(
                    state = if (creacion == 0) {
                        estadoDeUnSegmento(OrdenDeLaLista.VENTAS_NUEVAS)
                    } else {
                        estadoDelViewModelNuevo
                    },
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
        bajarLaLista()
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_POR_VISITAR).assertDoesNotExist()

        restaurador.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()
        // La carga termina con el orden restaurado del día.
        estadoDelViewModelNuevo = estadoDeUnSegmento(OrdenDeLaLista.VENTAS_NUEVAS)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(PRIMER_CLIENTE_POR_VISITAR).assertDoesNotExist()
    }

    /**
     * Minor de la re-revisión final ("Final minors" en `progress.md`): elegir
     * un orden nuevo regresaba al tope SÓLO el segmento visible; los demás
     * conservaban la posición del orden viejo, que en el orden nuevo apunta a
     * otros clientes. Al volver a ellos el cobrador caía a media lista sin
     * saber por qué.
     */
    @Test
    fun `cambiar el orden tambien regresa al tope a los otros segmentos`() {
        var segmento by mutableStateOf(SegmentoDeCobranza.POR_VISITAR)
        var orden by mutableStateOf(OrdenDeLaLista.COBRANZA)
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                ListaDeClientesContent(
                    state = estadoDeDosSegmentos(segmento, orden),
                    onBuscar = {},
                    onElegirSegmento = { segmento = it },
                    onAbrirCliente = {},
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {},
                    onElegirOrden = { orden = it },
                    onQuitarOrden = {},
                    onAbrirMenu = {}
                )
            }
        }
        bajarLaLista()
        composeTestRule.onNodeWithText(PRIMER_CLIENTE_POR_VISITAR).assertDoesNotExist()
        elegirChip(SegmentoDeCobranza.NO_PAGA)

        orden = OrdenDeLaLista.VENTAS_NUEVAS
        composeTestRule.waitForIdle()
        elegirChip(SegmentoDeCobranza.POR_VISITAR)

        composeTestRule.onNodeWithText(PRIMER_CLIENTE_POR_VISITAR).assertIsDisplayed()
    }

    /**
     * **La cabecera tiene UN solo estado para los cuatro segmentos** (decisión del
     * dueño, 2026-10-07; sustituye a "la cabecera grande sigue el scroll del segmento
     * visible", e9d93f40). Bajar en "Sin visitar" la encoge; cambiar a "Después", que
     * está en su tope, la deja encogida — y al regresar, sigue igual.
     */
    @Test
    fun `cambiar de segmento conserva el estado de la cabecera`() {
        montarConDosSegmentos()
        bajarLaLista()
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()

        elegirChip(SegmentoDeCobranza.NO_PAGA)
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TITULO_COMPACTO_TAG).assertIsDisplayed()

        elegirChip(SegmentoDeCobranza.POR_VISITAR)
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()
    }

    /**
     * Si la cabecera viene compacta de un segmento largo y el nuevo trae una lista que
     * cabe entera (no se desplaza), jalar hacia abajo todavía la abre — sin esto se
     * quedaba atorada compacta: Material no le pasa el gesto si la lista "no puede
     * desplazarse". Control de rojo: con `canScroll` sólo de la lista, sale roja.
     */
    @Test
    fun `con la cabecera compacta y un segmento corto jalar hacia abajo la abre`() {
        montarConDosSegmentos(cantidadDespues = 1)
        bajarLaLista()
        elegirChip(SegmentoDeCobranza.NO_PAGA)
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertIsDisplayed()
    }

    @Test
    fun `cambiar de segmento con la cabecera abierta la deja abierta`() {
        montarConDosSegmentos()

        elegirChip(SegmentoDeCobranza.NO_PAGA)

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TITULO_COMPACTO_TAG).assertDoesNotExist()
    }

    private fun elegirChip(segmento: SegmentoDeCobranza) {
        composeTestRule.onNodeWithTag(CHIP_DE_SEGMENTO_TAG + segmento.name.lowercase())
            .performClick()
        composeTestRule.mainClock.advanceTimeBy(2_000)
        composeTestRule.waitForIdle()
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

    private fun montarConDosSegmentos(cantidadDespues: Int = CUARENTA) {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                var segmento by remember { mutableStateOf(SegmentoDeCobranza.POR_VISITAR) }
                ListaDeClientesContent(
                    state = estadoDeDosSegmentos(segmento, cantidadDespues = cantidadDespues),
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
            clientesDePrueba(SegmentoDeCobranza.POR_VISITAR, EstadoCuenta.SIN_TOCAR, CUARENTA, 0)
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
            segmento = SegmentoDeCobranza.POR_VISITAR,
            query = "",
            hoy = ListaFixtures.HOY,
            orden = orden
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            segmento = SegmentoDeCobranza.POR_VISITAR,
            hoy = ListaFixtures.HOY,
            orden = orden
        )
    }

    private fun estadoDeDosSegmentos(
        segmento: SegmentoDeCobranza,
        orden: OrdenDeLaLista = OrdenDeLaLista.COBRANZA,
        cantidadDespues: Int = CUARENTA
    ): ListaDeClientesUiState {
        val clientes = clientesDePrueba(SegmentoDeCobranza.POR_VISITAR, EstadoCuenta.SIN_TOCAR, CUARENTA, 0) +
            clientesDePrueba(
                SegmentoDeCobranza.NO_PAGA,
                EstadoCuenta.PROMETIO_PROXIMA,
                cantidadDespues,
                1000,
                // E-APP-070: sólo una promesa con fecha posterior a hoy cae en No paga.
                fechaPromesa = ListaFixtures.HOY.plusDays(3)
            )
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientes,
            segmento = segmento,
            query = "",
            hoy = ListaFixtures.HOY,
            orden = orden
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            segmento = segmento,
            hoy = ListaFixtures.HOY,
            orden = orden
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
        base: Int,
        fechaPromesa: LocalDate? = null
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
                    estado = ListaFixtures.estado(estadoCuenta, fechaPromesa = fechaPromesa)
                )
            )
        )
    }

    private companion object {
        const val CUARENTA = 40
        const val REPETICIONES_DEL_SWIPE = 6
        const val PRIMER_CLIENTE_POR_VISITAR = "Cliente POR_VISITAR Número 1"
        const val PRIMER_CLIENTE_DESPUES = "Cliente NO_PAGA Número 1"
        const val FECHA_VENTA_FIJA = 10L
    }
}
