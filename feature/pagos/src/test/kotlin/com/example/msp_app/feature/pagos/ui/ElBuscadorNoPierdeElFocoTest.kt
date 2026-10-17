package com.example.msp_app.feature.pagos.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.test.performTextReplacement
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **El buscador de la lista perdía el foco solo** (reportado en el Galaxy A25 del
 * dueño, 2026-10-07): al teclear unas letras, y sobre todo al borrar el último
 * carácter, el campo soltaba el cursor y el teclado se bajaba.
 *
 * ## La causa raíz (medida con esta prueba)
 *
 * Buscar **apaga el orden** ([CarteraEnPantalla.proyectar]: con `query` en blanco
 * ordena, con texto respeta el orden natural de la fuente). Así que el primer
 * carácter y el borrado del último REORDENAN la lista entera. Con `key = clienteId`
 * el `LazyListState` sigue anclado al cliente que estaba arriba, que en el orden
 * nuevo vive en otro índice: `firstVisibleItemIndex` salta de 0 a N sin que nadie
 * haya deslizado. La cabecera derivaba "grande o compacta" de ese índice, leyó el
 * salto como "bajó la lista", se encogió, y el campo grande vivía dentro de un
 * `if (cabeceraGrande)`: salió de la composición con el foco adentro.
 *
 * La prueba fuerza el reordenamiento de forma determinista —en blanco la lista va
 * al revés, con texto en orden natural— porque así es como se comporta la fuente
 * real cuando el orden de cobranza no coincide con el natural, que es casi siempre.
 */
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ElBuscadorNoPierdeElFocoTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `teclear borrar hasta vacio y seguir tecleando conserva el foco`() {
        montar()
        val campo = composeTestRule.onNodeWithTag(BUSCADOR_TAG)
        campo.performClick()
        campo.assertIsFocused()

        campo.performTextInput("C")
        campo.assertIsFocused()
        campo.performTextReplacement("Cli")
        campo.assertIsFocused()

        // Borrar de uno en uno, hasta vaciar — el último es el que más fallaba.
        for (texto in listOf("Cl", "C", "")) {
            campo.performTextReplacement(texto)
            composeTestRule.waitForIdle()
            campo.assertIsFocused()
        }

        campo.performTextInput("Gu")
        campo.assertIsFocused()
    }

    /**
     * Segunda mitad del mismo defecto: aunque el campo ya no se cayera, el
     * `LazyListState` seguía anclado por `key` al cliente de arriba y la búsqueda
     * mostraba sus resultados desde el índice 24. Cada consulta nueva empieza arriba.
     */
    @Test
    fun `cada consulta nueva empieza en el primer resultado`() {
        montar()
        val campo = composeTestRule.onNodeWithTag(BUSCADOR_TAG)
        campo.performClick()

        campo.performTextInput("C")
        composeTestRule.waitForIdle()
        assertEquals("con texto", 0, lista.firstVisibleItemIndex)

        campo.performTextReplacement("")
        composeTestRule.waitForIdle()
        assertEquals("al vaciar", 0, lista.firstVisibleItemIndex)
        composeTestRule.onNodeWithTag(TITULO_GRANDE_TAG).assertIsDisplayed()
    }

    private lateinit var lista: LazyListState

    private fun montar() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                var texto by remember { mutableStateOf("") }
                lista = rememberLazyListState()
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
                    listState = lista
                )
            }
        }
    }

    /** En blanco: orden "de cobranza" (al revés). Con texto: orden natural, filtrado. */
    private fun estado(query: String): ListaDeClientesUiState {
        val todos = clientesDePrueba()
        val clientes = if (query.isBlank()) {
            todos.reversed()
        } else {
            todos.filter { it.nombre.contains(query, ignoreCase = true) }
        }
        return ListaDeClientesUiState(
            cargando = false,
            clientes = clientes,
            segmento = SegmentoDeCobranza.POR_VISITAR,
            query = query,
            hoy = ListaFixtures.HOY
        )
    }

    private fun clientesDePrueba(): List<ClienteEnLista> = NOMBRES.mapIndexed { i, nombre ->
        ListaFixtures.cliente(
            clienteId = 40_000 + i,
            nombre = nombre,
            ventas = listOf(
                ListaFixtures.venta(
                    ventaId = 41_000 + i,
                    folio = "V-$i",
                    descripcion = "Ropero",
                    saldo = ListaFixtures.dinero("1000"),
                    totalVenta = ListaFixtures.dinero("2000"),
                    enganche = ListaFixtures.dinero("200"),
                    fechaVenta = ListaFixtures.HOY.minusDays(i.toLong()),
                    estado = ListaFixtures.estado(EstadoCuenta.SIN_TOCAR)
                )
            )
        )
    }

    private companion object {
        private val APELLIDOS = listOf(
            "Guzmán",
            "Hernández",
            "Ramírez",
            "Flores",
            "Zepeda",
            "Castillo",
            "Morales",
            "Ortega"
        )
        private val NOMBRES_DE_PILA = listOf(
            "Claudia",
            "Carlos",
            "Guadalupe",
            "Rosa",
            "Cecilia"
        )
        val NOMBRES: List<String> = NOMBRES_DE_PILA.flatMap { pila ->
            APELLIDOS.map { apellido -> "$pila $apellido" }
        }
    }
}
