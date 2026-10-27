package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeWithVelocity
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
 * con `performScrollToIndex`: desde el 2026-10-07 la cabecera se mueve SÓLO con
 * gestos (`nestedScroll`), y un salto programático no genera ninguno — a
 * propósito: es lo que impide que un reordenamiento de la lista la encoja (ver
 * `ElBuscadorNoPierdeElFocoTest`).
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
     * **Umbral de recorrido** (decisión del dueño, 2026-10-07): a media lista, subir la
     * lista menos de [RECORRIDO_PARA_DESPLEGAR] (aquí 150 px = 75 dp, despacio) no abre
     * la cabecera.
     */
    @Test
    fun `a media lista un recorrido menor al umbral deja la cabecera colapsada`() {
        montar()
        bajarLaLista()
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput {
            swipeDown(startY = centerY, endY = centerY + JALON_CORTO_PX, durationMillis = LENTO_MS)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TITULO_COMPACTO_TAG).assertIsDisplayed()
    }

    /**
     * Pasado el umbral (500 px = 250 dp, despacio para que no cuente como fling) la
     * cabecera se despliega completa aunque la lista siga a media altura. Control de
     * rojo: con el comportamiento anterior (`exitUntilCollapsed`, sólo en el tope) sale
     * roja.
     */
    @Test
    fun `a media lista un recorrido mayor al umbral despliega la cabecera`() {
        montar()
        bajarLaLista()
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput {
            swipeDown(
                startY = top + 10f,
                endY = top + 10f + JALON_LARGO_PX,
                durationMillis = LENTO_MS
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BUSCADOR_TAG).assertIsDisplayed()
    }

    /**
     * **Umbral de velocidad**: un fling corto hacia arriba (75 dp, terminando a 3000 dp/s) la
     * despliega aunque el dedo no recorra el umbral. El desplazamiento del fling no
     * cuenta para el acumulador (sólo el del dedo), así que esto lo decide la velocidad.
     */
    @Test
    fun `a media lista un fling rapido hacia arriba despliega la cabecera`() {
        montar()
        bajarLaLista()
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()

        composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput {
            swipeWithVelocity(
                start = center,
                end = Offset(centerX, centerY + JALON_CORTO_PX),
                endVelocity = VELOCIDAD_DE_FLING_PX,
                durationMillis = RAPIDO_MS
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertIsDisplayed()
    }

    @Test
    fun `al regresar la lista al tope la cabecera queda expandida`() {
        montar()
        bajarLaLista()
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()

        repeat(REPETICIONES_PARA_EL_TOPE) {
            composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput { swipeDown() }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BUSCADOR_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TITULO_COMPACTO_TAG).assertDoesNotExist()
    }

    /** El colapso se guarda con `rememberSaveable`: volver de un abono no reabre la cabecera. */
    @Test
    fun `la cabecera colapsada sobrevive a la restauracion`() {
        val restaurador = StateRestorationTester(composeTestRule)
        restaurador.setContent { Contenido() }
        bajarLaLista()
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()

        restaurador.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TITULO_COMPACTO_TAG).assertIsDisplayed()
    }

    @Test
    fun `tocar la pildora abre el campo compacto con foco`() {
        montar()
        bajarLaLista()

        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).performClick()

        composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG).assertIsFocused()
    }

    /**
     * Con la búsqueda compacta abierta, los botones de tema y de montos se retiran para
     * dejarle el ancho al campo; al cerrarla (aquí: la cabecera vuelve a abrirse en el
     * tope, que cierra el campo compacto) regresan. Decisión del dueño, 2026-10-07.
     */
    @Test
    fun `con la busqueda compacta abierta los botones se esconden y al cerrarla vuelven`() {
        montar()
        bajarLaLista()
        composeTestRule.onNodeWithTag(TEMA_TAG).assertIsDisplayed()

        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(TEMA_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(OJO_TAG).assertDoesNotExist()

        repeat(REPETICIONES_PARA_EL_TOPE) {
            composeTestRule.onNodeWithTag(LISTA_DE_CLIENTES_TAG).performTouchInput { swipeDown() }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(TEMA_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(OJO_TAG).assertIsDisplayed()
    }

    /**
     * Teclear MIENTRAS los botones se están retirando no le quita el foco al campo: el
     * campo sólo crece, no cambia de rama (la misma clase de defecto que
     * `ElBuscadorNoPierdeElFocoTest`). Con el reloj detenido se teclea a media
     * transición.
     */
    @Test
    fun `teclear durante la transicion de los botones conserva el foco`() {
        montar()
        bajarLaLista()
        composeTestRule.onNodeWithTag(PILDORA_DE_BUSCAR_TAG).performClick()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.mainClock.advanceTimeBy(MEDIA_TRANSICION_MS)

        val campo = composeTestRule.onNodeWithTag(BUSCADOR_COMPACTO_TAG)
        campo.performTextInput("Cli")
        composeTestRule.mainClock.advanceTimeBy(MEDIA_TRANSICION_MS)
        campo.assertIsFocused()
        campo.performTextInput("ente")
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        campo.assertIsFocused()
        campo.assertTextContains("Cliente")
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
        composeTestRule.setContent { Contenido(query) }
    }

    @Composable
    private fun Contenido(query: String = "") {
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

    private fun estado(query: String): ListaDeClientesUiState {
        val proyeccion = CarteraEnPantalla.proyectar(
            clientes = clientesDePrueba(),
            segmento = SegmentoDeCobranza.POR_VISITAR,
            query = query,
            hoy = ListaFixtures.HOY
        )
        return ListaDeClientesUiState(
            cargando = false,
            clientes = proyeccion.clientes,
            conteos = proyeccion.conteos,
            segmento = SegmentoDeCobranza.POR_VISITAR,
            query = query,
            hoy = ListaFixtures.HOY
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
        const val REPETICIONES_PARA_EL_TOPE = 30
        const val JALON_CORTO_PX = 150f
        const val MEDIA_TRANSICION_MS = 120L
        const val TEMA_TAG = "msp_theme_toggle"
        const val OJO_TAG = "msp_privacy_eye_toggle"
        const val JALON_LARGO_PX = 500f
        const val LENTO_MS = 2_000L
        const val VELOCIDAD_DE_FLING_PX = 6_000f
        const val RAPIDO_MS = 40L
    }
}
