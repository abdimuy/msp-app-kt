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
import com.example.msp_app.feature.pagos.application.CargarDetalleCliente
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.GuardarFichaDelCliente
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeAccionesExternasPort
import com.example.msp_app.feature.pagos.data.fake.FakeFichaPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
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
 * **La prueba de "una vez por sincronización, no una por recomposición".**
 *
 * Es el punto entero de haber puesto el reenvío de incidencias en
 * `application/` y no en un `@Composable`: una pantalla se recompone N veces
 * por el mismo estado (cada scroll, cada cambio de tema, cada cambio de
 * `fontScale`), y emitir desde ahí llenaría la cola durable de telemetría con
 * el mismo evento repetido.
 *
 * El test carga UNA vez, cuenta 1, y después recompone el contenido real
 * muchas veces con el mismo estado. El conteo tiene que seguir en 1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IncidenciasUnaVezPorSyncTest : RobolectricTestBase() {

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
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        // Un literal fuera del catálogo cerrado de 14 -> incidencia.
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
        val viewModel = DetalleClienteViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(PagosRutas.ARG_CLIENTE_ID to PagosFixtures.CLIENTE_ID)
            ),
            cargarDetalleCliente = CargarDetalleCliente(
                fichaPort = FakeFichaPort(),
                productosPort = FakeProductosPort(),
                clock = clock,
                reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
                    ventasPort = ventasPort,
                    pagosPort = pagosPort,
                    visitasPort = visitasPort,
                    liquidacionPort = FakeLiquidacionPort(),
                    resolverVentanaDeCobro = ResolverVentanaDeCobro(
                        FakePeriodoDeCobroPort(),
                        clock
                    ),
                    derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
                )
            ),
            guardarFichaDelCliente = GuardarFichaDelCliente(FakeFichaPort()),
            accionesExternas = FakeAccionesExternasPort(),
            tema = FakeTemaDeLaAppPort(),
            privacidad = FakePrivacidadPort(),
            telemetry = telemetria,
            io = testDispatcher
        )
        advanceUntilIdle()
        assertEquals(1, incidencias())

        val estado = viewModel.state.value
        var recomposiciones by mutableStateOf(0)
        composeTestRule.setContent {
            MspTheme(darkTheme = recomposiciones % 2 == 0, animateColors = false) {
                DetalleClienteContent(
                    state = estado.copy(cargando = false),
                    onAtras = {},
                    onAbrirVenta = {},
                    onRegistrarAbono = {},
                    onRegistrarVisita = {},
                    onVerContactos = {},
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
