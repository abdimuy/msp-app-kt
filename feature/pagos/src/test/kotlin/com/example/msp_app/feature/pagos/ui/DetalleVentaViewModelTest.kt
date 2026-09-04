package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** El ViewModel del detalle de venta. Mismo contrato de dispatcher que el de cliente. */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleVentaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val garantiasPort = FakeGarantiasPort()
    private val periodoPort = FakePeriodoDeCobroPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        pagosPort.pagos = PagosFixtures.pagosDeLaVenta()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(ventaId: Int = PagosFixtures.VENTA_EN_PROMESA) = DetalleVentaViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PagosRutas.ARG_VENTA_ID to ventaId)),
        cargarDetalleVenta = CargarDetalleVenta(
            ventasPort = ventasPort,
            garantiasPort = garantiasPort,
            reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                liquidacionPort = liquidacionPort,
                resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
            ),
            clock = clock
        ),
        telemetry = telemetria,
        io = testDispatcher
    )

    @Test
    fun `arranca cargando y despues entrega la venta con su ritmo y su riel`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        assertTrue(vm.state.value.cargando)
        advanceUntilIdle()
        val detalle = vm.state.value.detalle!!
        assertEquals("Refrigerador Mabe 14'", detalle.titulo)
        assertEquals(12, detalle.historial.semanas.size)
        assertEquals(6, detalle.historial.totalPagos)
        // El riel agrupa por mes y NO colapsa nada.
        assertEquals(listOf("agosto", "julio", "junio"), detalle.historial.meses.map { it.nombre })
    }

    @Test
    fun `solo necesita el ventaId — resuelve el cliente por dentro`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(PagosFixtures.CLIENTE_ID, vm.state.value.detalle!!.clienteId)
        assertTrue(ventasPort.clientesConsultados.contains(PagosFixtures.CLIENTE_ID))
    }

    @Test
    fun `una venta que el telefono no tiene no es un cascaron vacio`() = runTest(testDispatcher) {
        val vm = viewModel(ventaId = 1)
        advanceUntilIdle()
        assertNull(vm.state.value.detalle)
        assertEquals(ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO, vm.state.value.error)
    }

    @Test
    fun `un puerto que falla degrada a error y lo reporta`() = runTest(testDispatcher) {
        ventasPort.falla = IllegalStateException("room caido")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ErrorDeDetalle.FALLO_LA_CARGA, vm.state.value.error)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == PagosTelemetria.CODE_DETALLE_VENTA_FALLO
        }
        assertEquals("IllegalStateException", error.props[PagosTelemetria.PROP_EXCEPCION])
        assertFalse(error.props.values.any { it.contains("room caido") })
    }
}
