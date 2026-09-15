package com.example.msp_app.feature.pagos.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.IncidenciaCobranza
import com.example.msp_app.core.designsystem.theme.MspTheme
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.RobolectricTestBase
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * **Una vez por carga, no una por cliente ni una por recomposición.**
 *
 * La lista deriva el periodo de la RUTA COMPLETA de golpe, así que aquí hay dos
 * formas de inundar la cola durable de telemetría, no una: emitir por cliente
 * (habría 3 eventos con esta ruta) o emitir por recomposición (habría uno por
 * scroll y por cambio de tema). El test carga una vez, cuenta 1, y después
 * recompone la pantalla real doce veces con el mismo estado.
 *
 * Es el gemelo de `IncidenciasUnaVezPorSyncTest`, que prueba lo mismo para el
 * detalle de cliente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidenciasUnaVezPorCargaDeListaTest : RobolectricTestBase() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        // Un literal fuera del catálogo cerrado de 14 -> una incidencia.
        visitasPort.visitas = listOf(PagosFixtures.visita("Se fue de vacaciones"))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun incidencias() = telemetria.recorded.count {
        it.type == TelemetryEventType.ERROR &&
            it.name == IncidenciaCobranza.CODE_TIPO_VISITA_FUERA_DE_CATALOGO
    }

    @Test
    fun `la incidencia se emite una vez por carga y ninguna por recomposicion`() = runTest(
        testDispatcher
    ) {
        val viewModel = ListaDeClientesViewModel(
            savedStateHandle = SavedStateHandle(),
            reunirCartera = ReunirCartera(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
                telemetry = telemetria
            ),
            tema = FakeTemaDeLaAppPort(),
            privacidad = FakePrivacidadPort(),
            telemetry = telemetria,
            io = testDispatcher
        )
        advanceUntilIdle()
        // Tres clientes en la ruta y UN solo evento: no se emite por cliente.
        assertEquals(3, viewModel.state.value.clientes.size)
        assertEquals(1, incidencias())

        val estado = viewModel.state.value
        var recomposiciones by mutableStateOf(0)
        composeTestRule.setContent {
            MspTheme(darkTheme = recomposiciones % 2 == 0, animateColors = false) {
                ListaDeClientesContent(
                    state = estado,
                    onBuscar = {},
                    onElegirSegmento = {},
                    onAbrirCliente = {},
                    onAbrirVenta = {},
                    onReintentar = {},
                    onAlternarTema = {},
                    onAlternarPrivacidad = {}
                )
            }
        }
        repeat(RECOMPOSICIONES) {
            recomposiciones += 1
            composeTestRule.waitForIdle()
        }

        assertEquals(1, incidencias())
    }

    private companion object {
        /** Suficientes para que un emisor por recomposición se delate a gritos. */
        const val RECOMPOSICIONES = 12
    }
}
