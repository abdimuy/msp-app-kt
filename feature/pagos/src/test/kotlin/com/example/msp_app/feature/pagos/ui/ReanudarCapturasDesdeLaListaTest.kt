package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.ControladorDeOrden
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.application.TogglesDeCabecera
import com.example.msp_app.feature.pagos.data.fake.FakeCercaniaPort
import com.example.msp_app.feature.pagos.data.fake.FakeOrdenGuardadoPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.port.ReanudarCapturasPort
import com.example.msp_app.feature.pagos.domain.port.VentaLocalSinTerminar
import com.example.msp_app.feature.pagos.domain.port.VentaLocalSinTerminarPort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * E-APP-119 (D15): el toque del aviso sin venta reanuda las visitas y
 * garantías capturadas que no se escribieron. Una reanudación a la vez: un
 * segundo toque mientras corre no lanza otra.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReanudarCapturasDesdeLaListaTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)
    private var llamadas = 0
    private val suelta = CompletableDeferred<Unit>()
    private val puerto = ReanudarCapturasPort {
        llamadas++
        suelta.await()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `E-APP-119 el toque del aviso reanuda una sola vez aunque se toque dos veces`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.reanudarCapturas()
            vm.reanudarCapturas()
            advanceUntilIdle()
            assertEquals(1, llamadas)

            suelta.complete(Unit)
            advanceUntilIdle()
            vm.reanudarCapturas()
            advanceUntilIdle()
            assertEquals("terminada la primera, otro toque reanuda otra vez", 2, llamadas)
        }

    @Test
    fun `E-APP-119 la venta local sin terminar llega al estado de la lista`() =
        runTest(testDispatcher) {
            val vm = viewModel(
                ventaLocal = VentaLocalSinTerminarPort {
                    flowOf(VentaLocalSinTerminar("venta-1", yaSeGuardo = false))
                }
            )
            advanceUntilIdle()

            assertEquals(
                VentaLocalSinTerminar("venta-1", yaSeGuardo = false),
                vm.state.value.ventaLocalSinTerminar
            )
        }

    private fun viewModel(
        ventaLocal: VentaLocalSinTerminarPort = VentaLocalSinTerminarPort.NINGUNA
    ) = ListaDeClientesViewModel(
        savedStateHandle = SavedStateHandle(),
        reunirCartera = ReunirCartera(
            ventasPort = FakeVentasPort(),
            pagosPort = FakePagosPort(),
            visitasPort = FakeVisitasPort(),
            resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
            telemetry = telemetria
        ),
        toggles = TogglesDeCabecera(FakeTemaDeLaAppPort(), FakePrivacidadPort()),
        controladorDeOrden = ControladorDeOrden(
            FakeCercaniaPort(),
            FakeOrdenGuardadoPort(),
            testDispatcher
        ),
        telemetry = telemetria,
        io = testDispatcher,
        reanudarCapturas = puerto,
        ventaLocalSinTerminar = ventaLocal
    )
}
