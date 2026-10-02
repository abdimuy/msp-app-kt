package com.example.msp_app.feature.pagos.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.ui.components.DISTANCIA_DEL_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.ENCABEZADO_DE_CLIENTE_TAG
import com.example.msp_app.feature.pagos.ui.components.FilaDeCliente
import com.example.msp_app.feature.pagos.ui.components.RENGLON_DE_VENTA_TAG
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * **Los cercanos de Inicio se pintan con la tarjeta de la lista de clientes**,
 * con la distancia en el encabezado (decisión del dueño del 2026-10-02).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(qualifiers = "w360dp-h2400dp-xhdpi")
class CercanosConLaTarjetaDeLaListaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)
    private val ventasPort = FakeVentasPort()

    @Before
    fun preparar() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
    }

    @After
    fun limpiar() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = CercanosViewModel(
        reunirCartera = ReunirCartera(
            ventasPort = ventasPort,
            pagosPort = FakePagosPort(),
            visitasPort = FakeVisitasPort(),
            resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
            telemetry = telemetria
        ),
        privacidad = FakePrivacidadPort(),
        io = testDispatcher
    )

    @Test
    fun `la cartera trae a cada cliente con todas sus ventas, por clienteId`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()

        val victoria = vm.clientes.value[ListaFixtures.VICTORIA]
        assertEquals("Victoria carga dos ventas", 2, victoria?.ventas?.size)
        assertEquals(ListaFixtures.ruta().map { it.clienteId }.toSet(), vm.clientes.value.keys)
    }

    /** Un fallo al leer deja la cartera vacía: Inicio cae al renglón de siempre. */
    @Test
    fun `si falla la lectura la cartera queda vacia y no lanza`() = runTest(testDispatcher) {
        ventasPort.falla = IOException("Room")
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.clientes.value.isEmpty())
    }

    @Test
    fun `la tarjeta de cercano lleva la distancia y las dos puertas`() {
        val clientes = mutableListOf<Unit>()
        val ventas = mutableListOf<Int>()
        val victoria = ListaFixtures.ruta().first { it.clienteId == ListaFixtures.VICTORIA }
        composeTestRule.setContent {
            TarjetaDeCercano(
                cliente = victoria,
                distancia = "350 m",
                montosOcultos = false,
                onAbrirCliente = { clientes += Unit },
                onAbrirVenta = { ventas += it }
            )
        }

        composeTestRule.onNodeWithTag(
            DISTANCIA_DEL_CLIENTE_TAG,
            useUnmergedTree = true
        ).assertTextEquals("350 m")
        composeTestRule.onNodeWithTag(RENGLON_DE_VENTA_TAG + VENTA_2_DE_VICTORIA).performClick()
        composeTestRule.onAllNodesWithTag(ENCABEZADO_DE_CLIENTE_TAG)[0].performClick()

        // En cercanos no va el "Falta x de x": manda la distancia.
        composeTestRule.onAllNodesWithText("Falta 1 de 2", useUnmergedTree = true)
            .assertCountEquals(0)
        assertEquals(listOf(VENTA_2_DE_VICTORIA), ventas)
        assertEquals(1, clientes.size)
    }

    /** En la lista de clientes no hay distancia: el dato es sólo de cercanos. */
    @Test
    fun `en la lista de clientes la tarjeta no pinta distancia`() {
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                FilaDeCliente(cliente = ListaFixtures.ruta().first(), onAbrirCliente = {})
            }
        }

        composeTestRule.onAllNodesWithTag(
            DISTANCIA_DEL_CLIENTE_TAG,
            useUnmergedTree = true
        ).assertCountEquals(0)
    }

    /** Control: en la lista el aviso sigue, así que la ausencia de arriba se puede medir. */
    @Test
    fun `en la lista de clientes el aviso Falta x de x sigue`() {
        val victoria = ListaFixtures.ruta().first { it.clienteId == ListaFixtures.VICTORIA }
        composeTestRule.setContent {
            MspTheme(darkTheme = false, animateColors = false) {
                FilaDeCliente(cliente = victoria, onAbrirCliente = {})
            }
        }

        composeTestRule.onAllNodesWithText("Falta 1 de 2", useUnmergedTree = true)
            .assertCountEquals(1)
    }

    private companion object {
        const val VENTA_2_DE_VICTORIA = 77188
    }
}
