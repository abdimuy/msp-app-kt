package com.example.msp_app.features.home.components.homenearbyclientssection

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.example.msp_app.core.database.entities.PaymentLocation
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.core.utils.FuenteDePosicion
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **La lista de cercanos sigue al cobrador, no se queda con la primera lectura.**
 *
 * Defecto reportado por el dueño el 2026-09-26: la pantalla principal leía la
 * ubicación una sola vez (`0471941f`, 2026-09-22) y la lista se quedaba con esa
 * primera lectura aunque el cobrador avanzara. Acá una fuente falsa emite dos
 * posiciones: junto a X y luego junto a Y. Con la lectura única la segunda
 * aserción queda roja.
 *
 * La primera aserción es el control positivo: prueba que la fuente, el
 * cálculo y la lectura del primer renglón funcionan, para que el rojo de la
 * segunda sólo pueda venir de no haber recolectado la segunda emisión.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = android.app.Application::class)
class PosicionEnVivoTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `la lista de cercanos se reordena cuando el cobrador se mueve`() {
        val posiciones = MutableSharedFlow<Coord>(replay = 1)
        val fuente = FuenteDePosicion { posiciones }

        composeTestRule.setContent {
            val posicion = posicionEnVivo(fuente, permitido = true)
            val clientes = nearbyClientsFrom(
                position = posicion,
                centroidsBySale = listOf(
                    grupo(cargo = 101, coord = PUERTA_X),
                    grupo(cargo = 102, coord = PUERTA_Y)
                ),
                sales = listOf(
                    venta(cargo = 101, clienteId = 88, cliente = "Cliente X"),
                    venta(cargo = 102, clienteId = 99, cliente = "Cliente Y")
                )
            )
            Column {
                clientes.firstOrNull()?.let { Text(it.name, modifier = Modifier.testTag(PRIMERO)) }
            }
        }

        runBlocking { posiciones.emit(JUNTO_A_X) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PRIMERO).assertTextEquals("Cliente X")

        runBlocking { posiciones.emit(JUNTO_A_Y) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(PRIMERO).assertTextEquals("Cliente Y")
        composeTestRule.onAllNodesWithTag(PRIMERO).fetchSemanticsNodes().size.let { check(it == 1) }
    }

    private fun grupo(cargo: Int, coord: Coord) = PaymentLocationsGroup(
        saleId = cargo,
        locations = listOf(
            PaymentLocation(DOCTO_CC_ACR_ID = cargo, LAT = coord.lat, LNG = coord.lng)
        )
    )

    private companion object {
        const val PRIMERO = "primer-cercano"

        // Dos puertas a ~2 km una de otra, en Delicias.
        val PUERTA_X = Coord(lat = 28.1900, lng = -105.4700)
        val PUERTA_Y = Coord(lat = 28.2080, lng = -105.4700)
        val JUNTO_A_X = Coord(lat = 28.1901, lng = -105.4700)
        val JUNTO_A_Y = Coord(lat = 28.2079, lng = -105.4700)
    }
}
