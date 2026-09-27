package com.example.msp_app.feature.ubicacion.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.ubicacion.domain.ClaseDeLugar
import com.example.msp_app.feature.ubicacion.screenshot.MapaDelDuenoFixture
import com.example.msp_app.feature.ubicacion.ui.components.CARD_TAG
import com.example.msp_app.feature.ubicacion.ui.components.PANEL_FILTROS_TAG
import com.example.msp_app.feature.ubicacion.ui.components.VOLVER_A_LA_LISTA_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El botón atrás de Android hace lo mismo que la flecha "<" del detalle.**
 *
 * El defecto, en palabras del dueño: *"ese sheet nuevo tiene una flecha de
 * regresar, pero la gente por instinto le da regresar del sistema operativo, lo
 * que los saca de la pantalla"*. El detalle del lugar vive dentro de la hoja de
 * la pantalla —no es un `ModalBottomSheet`—, así que sin un `BackHandler` propio
 * el evento llegaba directo al `NavHost`.
 *
 * Igual que `AtrasCierraLasHojasTest` de `:feature:pagos`: el `NavHost` se
 * imita con un [BackHandler] **montado antes** que la pantalla (el despachador
 * atiende al último callback registrado), y cada prueba cierra con su control
 * positivo: sin capa abierta, atrás **sí** llega al "NavHost". Sin ese control,
 * `navegaciones == 0` pasaría en verde con un "NavHost" sordo.
 */
@Config(qualifiers = "w360dp-h800dp-xhdpi")
class AtrasDeshaceLaHojaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var despachador: OnBackPressedDispatcher
    private var navegaciones = 0
    private var estadoActual: UbicacionUiState? = null

    @Test
    fun `atras en el detalle vuelve a la lista sin salir de la pantalla`() {
        montar(MapaDelDuenoFixture.estado(ClaseDeLugar.OTRO_LUGAR))
        composeTestRule.onNodeWithTag(VOLVER_A_LA_LISTA_TAG).assertIsDisplayed()

        atras()

        assertEquals("atrás salió de la pantalla con el detalle abierto", 0, navegaciones)
        assertNull("atrás no deseleccionó el lugar", estadoActual?.lugarTocado)
        composeTestRule.onNodeWithTag(VOLVER_A_LA_LISTA_TAG).assertDoesNotExist()
        val cardPrincipal = CARD_TAG + ClaseDeLugar.DONDE_MAS_PAGA.name
        composeTestRule.onNodeWithTag(cardPrincipal).assertIsDisplayed()
        controlPositivo()
    }

    @Test
    fun `atras cierra filtros sin salir de la pantalla`() {
        montar(MapaDelDuenoFixture.estado(), filtros = true)
        composeTestRule.onNodeWithTag(PANEL_FILTROS_TAG).assertIsDisplayed()

        atras()

        assertEquals("atrás salió de la pantalla con Filtros abierto", 0, navegaciones)
        composeTestRule.onNodeWithTag(PANEL_FILTROS_TAG).assertDoesNotExist()
        controlPositivo()
    }

    /** Filtros encima del detalle: el primer atrás cierra Filtros, el segundo el detalle. */
    @Test
    fun `atras deshace filtros y luego el detalle en orden de capa`() {
        montar(MapaDelDuenoFixture.estado(ClaseDeLugar.OTRO_LUGAR), filtros = true)

        atras()
        composeTestRule.onNodeWithTag(PANEL_FILTROS_TAG).assertDoesNotExist()
        composeTestRule.onNodeWithTag(VOLVER_A_LA_LISTA_TAG).assertIsDisplayed()

        atras()
        assertNull("el segundo atrás no deseleccionó el lugar", estadoActual?.lugarTocado)
        assertEquals("atrás salió de la pantalla con una capa abierta", 0, navegaciones)
        controlPositivo()
    }

    @Test
    fun `atras baja la lista arrastrada a reposo sin salir de la pantalla`() {
        montar(MapaDelDuenoFixture.estado(), hoja = ModoDeLaHoja.EXPANDIDA)
        val alta = alturaDeLaHoja()

        atras()

        assertEquals("atrás salió de la pantalla con la lista arrastrada", 0, navegaciones)
        val baja = alturaDeLaHoja()
        assert(baja < alta) { "atrás no bajó la hoja: $alta → $baja" }
        controlPositivo()
    }

    private fun montar(
        inicial: UbicacionUiState,
        hoja: ModoDeLaHoja = ModoDeLaHoja.REPOSO,
        filtros: Boolean = false
    ) {
        composeTestRule.setContent {
            despachador = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            BackHandler { navegaciones++ }
            var estado by remember { mutableStateOf(inicial) }
            estadoActual = estado
            UbicacionScreen(
                state = estado,
                onAtras = {},
                onComoLlegar = {},
                onFiltro = {},
                // Lo que hace el ViewModel real: `tocarLugar` reemplaza `lugarTocado`.
                onTocarLugar = { estado = estado.copy(lugarTocado = it) },
                hojaInicial = hoja,
                filtrosAbiertos = filtros,
                pintarMapa = false
            )
        }
        composeTestRule.waitForIdle()
    }

    private fun alturaDeLaHoja(): Float {
        composeTestRule.waitForIdle()
        return composeTestRule.onNodeWithTag(HOJA_TAG).fetchSemanticsNode().size.height.toFloat()
    }

    private fun atras() {
        composeTestRule.runOnIdle { despachador.onBackPressed() }
        composeTestRule.waitForIdle()
    }

    /** Ya sin capa abierta, atrás SÍ tiene que llegar al "NavHost". */
    private fun controlPositivo() {
        atras()
        assertEquals("el control no llegó al NavHost: la prueba no mide nada", 1, navegaciones)
    }
}
