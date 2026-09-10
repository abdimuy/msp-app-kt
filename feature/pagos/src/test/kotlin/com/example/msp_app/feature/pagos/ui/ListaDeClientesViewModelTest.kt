package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import java.io.IOException
import java.time.Instant
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El ViewModel de la lista.
 *
 * **`StandardTestDispatcher`, nunca `UnconfinedTestDispatcher`** — mismo
 * criterio que `DetalleClienteViewModelTest`: con Unconfined el `launch` del
 * `init` termina antes de que el test pueda ver `cargando = true`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListaDeClientesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val temaPort = FakeTemaDeLaAppPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        // Una visita real dentro de la ventana, para que el chip "vencidos"
        // tenga a alguien: el estado lo deriva `EstadoCuentaDeriver`, no el
        // fixture. Sin ella la ruta entera sale "sin tocar", que es la verdad.
        visitasPort.visitas = listOf(
            VisitaDelCliente(
                visitaId = "visita-guadalupe",
                clienteId = ListaFixtures.GUADALUPE,
                ventaId = 79115,
                fecha = Instant.parse("2026-09-01T16:00:00Z"),
                tipoVisita = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
                nota = null
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ListaDeClientesViewModel(
        savedStateHandle = SavedStateHandle(),
        reunirCartera = ReunirCartera(
            ventasPort = ventasPort,
            pagosPort = pagosPort,
            visitasPort = visitasPort,
            resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
            telemetry = telemetria
        ),
        tema = temaPort,
        telemetry = telemetria,
        io = testDispatcher
    )

    @Test
    fun `arranca cargando y termina con la ruta ordenada`() = runTest(testDispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.cargando)

        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.cargando)
        assertFalse(state.fallo)
        assertEquals(
            listOf(ListaFixtures.RICARDO, ListaFixtures.VICTORIA, ListaFixtures.GUADALUPE),
            state.clientes.map { it.clienteId }
        )
    }

    @Test
    fun `buscar filtra sin volver a leer la ruta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.buscar("Zepeda")
        // `state` se DERIVA con `combine(...).stateIn(...)` desde Ruling BQ (el tema no se
        // guarda, se re-aplica en cada emisión), así que hay que dejar correr al dispatcher.
        // En producción no hay lag: `viewModelScope` es `Dispatchers.Main.immediate` y la
        // emisión sale inline; el `advanceUntilIdle` es contabilidad del
        // `StandardTestDispatcher`, que esta clase usa a propósito (ver su KDoc).
        advanceUntilIdle()
        assertEquals(listOf(ListaFixtures.RICARDO), vm.state.value.clientes.map { it.clienteId })
        assertEquals("Zepeda", vm.state.value.query)
        // La búsqueda es sobre lo ya cargado: nada de releer Room por tecla.
        assertEquals(1, ventasPort.lecturasDeTodas)
    }

    @Test
    fun `elegir un chip filtra y se recuerda en el estado`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirSegmento(SegmentoDeCobranza.VENCIDOS)
        advanceUntilIdle() // ver el comentario del test de arriba (derivación de `state`)
        assertEquals(SegmentoDeCobranza.VENCIDOS, vm.state.value.segmento)
        // Solo Guadalupe tiene una visita en la ventana, y dice que se negó.
        assertEquals(listOf(ListaFixtures.GUADALUPE), vm.state.value.clientes.map { it.clienteId })
        assertEquals(1, ventasPort.lecturasDeTodas)
    }

    @Test
    fun `el chip y la busqueda sobreviven en el SavedStateHandle`() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val vm = ListaDeClientesViewModel(
            savedStateHandle = handle,
            reunirCartera = ReunirCartera(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
                telemetry = telemetria
            ),
            tema = temaPort,
            telemetry = telemetria,
            io = testDispatcher
        )
        advanceUntilIdle()
        vm.buscar("Flores")
        vm.elegirSegmento(SegmentoDeCobranza.SIN_VISITAR)

        assertEquals("Flores", handle.get<String>("pagos_lista_query"))
        assertEquals(
            SegmentoDeCobranza.SIN_VISITAR.ordinal,
            handle.get<Int>("pagos_lista_segmento")
        )
    }

    @Test
    fun `si la lectura falla se reporta y la pantalla lo dice`() = runTest(testDispatcher) {
        ventasPort.falla = IOException("Room se cayó")
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.fallo)
        assertTrue(vm.state.value.clientes.isEmpty())
        val evento = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_LISTA_CLIENTES_FALLO
        }
        // Se emite el nombre de la clase, nunca el `message` crudo.
        assertEquals("IOException", evento.props[PagosTelemetria.PROP_EXCEPCION])
        assertTrue(evento.props.values.none { it.contains("Room se cayó") })
    }

    @Test
    fun `reintentar vuelve a leer`() = runTest(testDispatcher) {
        ventasPort.falla = IOException("Room se cayó")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.fallo)

        ventasPort.falla = null
        vm.cargar()
        advanceUntilIdle()
        assertFalse(vm.state.value.fallo)
        assertEquals(3, vm.state.value.clientes.size)
    }

    @Test
    fun `la pantalla se anuncia a telemetria sin PII`() = runTest(testDispatcher) {
        viewModel()
        advanceUntilIdle()
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.SCREEN_VIEW && it.name == "pagos_lista_clientes"
            }
        )
    }
}
