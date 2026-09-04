package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleCliente
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.GuardarFichaDelCliente
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeFichaPort
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El ViewModel del detalle de cliente.
 *
 * **`StandardTestDispatcher`, nunca `UnconfinedTestDispatcher`** (ni el
 * `MainDispatcherRule` del repo, que usa Unconfined): con Unconfined el
 * `viewModelScope.launch` del `init` corre completo antes de que el test pueda
 * observar `cargando = true`, y esa transición es justo lo que hay que cubrir.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DetalleClienteViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val fichaPort = FakeFichaPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        pagosPort.pagos = PagosFixtures.pagosDeLaVenta()
        liquidacionPort.liquidaciones = mapOf(
            PagosFixtures.VENTA_EN_PROMESA to PagosFixtures.liquidacionDeLaVenta()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(clienteId: Int = PagosFixtures.CLIENTE_ID) = DetalleClienteViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PagosRutas.ARG_CLIENTE_ID to clienteId)),
        cargarDetalleCliente = CargarDetalleCliente(
            fichaPort = fichaPort,
            reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                liquidacionPort = liquidacionPort,
                resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
            )
        ),
        guardarFichaDelCliente = GuardarFichaDelCliente(fichaPort),
        telemetry = telemetria,
        io = testDispatcher
    )

    @Test
    fun `arranca cargando y despues entrega el detalle`() = runTest(testDispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.cargando)
        assertNull(vm.state.value.detalle)
        advanceUntilIdle()
        assertFalse(vm.state.value.cargando)
        val detalle = assertNotNull(vm.state.value.detalle).let { vm.state.value.detalle!! }
        assertEquals("Victoria Flores Olmedo", detalle.nombre)
        assertEquals(2, detalle.ventas.size)
    }

    @Test
    fun `el clienteId sale del SavedStateHandle, no de un parametro del composable`() {
        val vm = viewModel(clienteId = 909)
        assertEquals(909, vm.clienteId)
    }

    @Test
    fun `un cliente que el telefono no tiene no es un cascaron vacio`() = runTest(testDispatcher) {
        val vm = viewModel(clienteId = 999)
        advanceUntilIdle()
        assertNull(vm.state.value.detalle)
        assertEquals(ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO, vm.state.value.error)
    }

    @Test
    fun `un puerto que falla degrada a error y lo reporta por telemetria`() = runTest(
        testDispatcher
    ) {
        ventasPort.falla = IllegalStateException("room caido")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ErrorDeDetalle.FALLO_LA_CARGA, vm.state.value.error)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == PagosTelemetria.CODE_DETALLE_CLIENTE_FALLO
        }
        assertEquals("IllegalStateException", error.props[PagosTelemetria.PROP_EXCEPCION])
        // Anti-PII: el mensaje de la excepción NO viaja.
        assertFalse(error.props.values.any { it.contains("room caido") })
    }

    @Test
    fun `el estado de cada venta sale del catalogo de ocho`() = runTest(testDispatcher) {
        visitasPort.visitas = listOf(PagosFixtures.visita("Pidió reagendar visita"))
        pagosPort.pagos = emptyList()
        val vm = viewModel()
        advanceUntilIdle()
        val ventas = vm.state.value.detalle!!.ventas.associateBy { it.ventaId }
        assertEquals(
            EstadoCuenta.PROMETIO_PROXIMA,
            ventas.getValue(PagosFixtures.VENTA_EN_PROMESA).estado.estado
        )
        // Sin fecha de promesa -> se pinta como pendiente, no como diferida.
        assertNull(ventas.getValue(PagosFixtures.VENTA_EN_PROMESA).estado.fechaPromesa)
        assertEquals(
            TratoDelEstado.REGRESAS,
            EstadoCuentaUi.tratoDe(ventas.getValue(PagosFixtures.VENTA_EN_PROMESA).estado)
        )
    }

    @Test
    fun `una sola carga emite un solo screenView`() = runTest(testDispatcher) {
        viewModel()
        advanceUntilIdle()
        assertEquals(1, telemetria.recorded.count { it.type == TelemetryEventType.SCREEN_VIEW })
    }
}
