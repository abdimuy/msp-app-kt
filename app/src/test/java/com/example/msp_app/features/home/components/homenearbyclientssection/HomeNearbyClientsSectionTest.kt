package com.example.msp_app.features.home.components.homenearbyclientssection

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.core.database.entities.PaymentLocation
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Que la sección se calle cuando no tiene nada que decir.**
 *
 * La lista de cercanos depende de algo que falla seguido en la calle: el GPS.
 * Estos tests fijan que los dos casos reales —el cobrador que dijo que no al
 * permiso, y el teléfono sin fix— dejen la pantalla **entera**: sin sección, sin
 * hueco con título, y sobre todo **sin un cargando que nunca termina**, que es
 * la forma en que una lista así se rompe de verdad.
 *
 * Se afirma **texto y callbacks**, no geometría. Comparar `boundsInRoot` contra
 * la pantalla no sirve en este repo: un nodo fuera del viewport devuelve
 * `Rect.Zero` y pasa cualquier aserción de contención.
 *
 * El hermano de al lado ([PIE]) está en los tests a propósito: es el control
 * positivo de "la pantalla sigue entera". Sin él, "no encontré el título" se
 * cumpliría igual si nada se hubiera pintado nunca.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class HomeNearbyClientsSectionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // -----------------------------------------------------------------------
    // 1. Sin permiso de ubicación
    // -----------------------------------------------------------------------

    /**
     * El permiso negado llega a la sección como una posición nula — es lo que
     * `CurrentLocationReader.current()` devuelve sin permiso, medido en
     * `CurrentLocationReaderTest`. Acá se mide la otra mitad: qué hace la
     * pantalla con ese `null`.
     */
    @Test
    fun `sin permiso de ubicacion la seccion no se pinta y la pantalla sigue entera`() {
        val clientes = nearbyClientsFrom(
            position = null,
            centroidsBySale = listOf(grupo(cargo = 101, coord = CERCA)),
            sales = listOf(venta(cargo = 101, clienteId = 88))
        )

        contenido(clientes)

        composeTestRule.onNodeWithText(NEARBY_CLIENTS_TITLE).assertDoesNotExist()
        composeTestRule.onNodeWithText("Cliente 88").assertDoesNotExist()
        composeTestRule.onNodeWithText(PIE).assertIsDisplayed()
    }

    @Test
    fun `sin permiso de ubicacion no queda ningun indicador de carga`() {
        contenido(
            nearbyClientsFrom(
                position = null,
                centroidsBySale = emptyList(),
                sales = emptyList()
            )
        )

        assertNull(
            "la seccion dejo un progreso colgado en pantalla",
            composeTestRule.progresoEnPantalla()
        )
        composeTestRule.onNodeWithText(PIE).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // 2. Sin ubicación disponible (GPS apagado, o el proveedor devolvió null)
    // -----------------------------------------------------------------------

    /**
     * Mismo `null`, otro origen: acá el permiso está dado y lo que falta es el
     * fix. La sección no puede distinguirlos —ni debe—, pero el caso se mide
     * aparte porque es el que más se ve en campo: bajo techo, en una bodega, o
     * con el GPS apagado para ahorrar batería.
     */
    @Test
    fun `sin ubicacion disponible la seccion no se pinta y la pantalla sigue entera`() {
        val conCentroidesYVentas = nearbyClientsFrom(
            position = null,
            centroidsBySale = listOf(
                grupo(cargo = 101, coord = CERCA),
                grupo(cargo = 102, coord = CERCA)
            ),
            sales = listOf(venta(cargo = 101, clienteId = 88), venta(cargo = 102, clienteId = 99))
        )

        contenido(conCentroidesYVentas)

        composeTestRule.onNodeWithText(NEARBY_CLIENTS_TITLE).assertDoesNotExist()
        composeTestRule.onNodeWithText(PIE).assertIsDisplayed()
    }

    /**
     * Control positivo de los dos casos de arriba: con los MISMOS insumos y una
     * posición real, la sección SÍ aparece. Sin esto, "no se pintó" pasaría
     * igual si la sección estuviera rota para todo el mundo.
     */
    @Test
    fun `con una posicion real la misma seccion si aparece`() {
        val clientes = nearbyClientsFrom(
            position = POSICION,
            centroidsBySale = listOf(grupo(cargo = 101, coord = CERCA)),
            sales = listOf(venta(cargo = 101, clienteId = 88))
        )

        contenido(clientes)

        composeTestRule.onNodeWithText(NEARBY_CLIENTS_TITLE).assertIsDisplayed()
        composeTestRule.onNodeWithText("Cliente 88").assertIsDisplayed()
        composeTestRule.onNodeWithText(PIE).assertIsDisplayed()
    }

    // -----------------------------------------------------------------------
    // 3. Nada que mostrar: la sección se va entera, no deja un hueco
    // -----------------------------------------------------------------------

    @Test
    fun `con la lista vacia no pinta ni el encabezado`() {
        contenido(emptyList())

        composeTestRule.onNodeWithText(NEARBY_CLIENTS_TITLE).assertDoesNotExist()
        composeTestRule.onNodeWithText(PIE).assertIsDisplayed()
    }

    @Test
    fun `con la lista vacia no pinta ningun texto propio`() {
        contenido(emptyList())

        // El único texto en pantalla es el del hermano de al lado: la sección no
        // dejó ni un guion, ni un "sin ubicación", ni una tarjeta vacía.
        assertEquals(listOf(PIE), composeTestRule.textosVisibles())
    }

    // -----------------------------------------------------------------------
    // Lo que la sección sí hace cuando tiene datos
    // -----------------------------------------------------------------------

    @Test
    fun `el toque entrega el cliente de esa fila, no el de otra`() {
        val tocados = mutableListOf<Int>()
        val clientes = listOf(
            NearbyClient(
                clientId = 88,
                name = "María Fernanda Villalobos Treviño",
                address = "Av. Francisco I. Madero 1204, Cuauhtémoc",
                accounts = 1,
                distance = SaleDistance.of(850.0)
            ),
            NearbyClient(
                clientId = 99,
                name = "Ernesto Zúñiga Palomares",
                address = "Calle Zaragoza 88, Delicias",
                accounts = 2,
                distance = SaleDistance.of(1234.0)
            )
        )

        contenido(clientes) { tocados += it.clientId }

        composeTestRule.onNodeWithText("Ernesto Zúñiga Palomares").performClick()

        assertEquals(listOf(99), tocados)
    }

    @Test
    fun `la fila con varias cuentas lo dice, la de una sola no`() {
        contenido(
            listOf(
                NearbyClient(88, "Cliente 88", "Calle 1", accounts = 1, SaleDistance.of(850.0)),
                NearbyClient(99, "Cliente 99", "Calle 2", accounts = 2, SaleDistance.of(900.0))
            )
        )

        composeTestRule.onNodeWithText("2 cuentas").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 cuentas").assertDoesNotExist()
    }

    @Test
    fun `una puerta sin ubicacion muestra un guion legible para TalkBack`() {
        contenido(
            listOf(
                NearbyClient(88, "Cliente 88", "Calle 1", accounts = 1, SaleDistance.Unknown)
            )
        )

        composeTestRule.onNodeWithText("—").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sin ubicación").assertIsDisplayed()
    }

    @Test
    fun `la distancia se pinta formateada, nunca un numero crudo`() {
        contenido(
            listOf(
                NearbyClient(88, "Cliente 88", "Calle 1", accounts = 1, SaleDistance.of(1234.0))
            )
        )

        composeTestRule.onNodeWithText("1.2 km").assertIsDisplayed()
        assertTrue(
            "numero crudo en pantalla: ${composeTestRule.textosVisibles()}",
            composeTestRule.textosVisibles().none { it.contains("1234") }
        )
    }

    // -----------------------------------------------------------------------
    // Andamio
    // -----------------------------------------------------------------------

    private fun contenido(
        clientes: List<NearbyClient>,
        onClientClick: (NearbyClient) -> Unit = {}
    ) {
        composeTestRule.setContent {
            MaterialTheme {
                Column {
                    HomeNearbyClientsSection(
                        clients = clientes,
                        isDark = false,
                        onClientClick = onClientClick
                    )
                    Hermano()
                }
            }
        }
    }

    /** El control positivo: una sección hermana que siempre se pinta. */
    @Composable
    private fun Hermano() {
        Text(PIE)
    }

    private fun ComposeContentTestRule.textosVisibles(): List<String> =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
            .fetchSemanticsNodes()
            .flatMap { nodo -> nodo.config[SemanticsProperties.Text].map { it.text } }

    private fun ComposeContentTestRule.progresoEnPantalla() =
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNodes()
            .firstOrNull()

    private fun grupo(cargo: Int, coord: Coord) = PaymentLocationsGroup(
        saleId = cargo,
        locations = listOf(
            PaymentLocation(DOCTO_CC_ACR_ID = cargo, LAT = coord.lat, LNG = coord.lng)
        )
    )

    private companion object {
        const val PIE = "PIE DE LA PANTALLA"
        val POSICION = Coord(lat = 19.4326, lng = -99.1332)
        val CERCA = Coord(lat = 19.4336, lng = -99.1332)
    }
}
