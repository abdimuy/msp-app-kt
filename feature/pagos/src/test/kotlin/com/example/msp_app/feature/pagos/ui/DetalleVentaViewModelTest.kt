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
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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

    private val productosPort = FakeProductosPort()
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
            productosPort = productosPort,
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
        privacidad = FakePrivacidadPort(),
        tema = FakeTemaDeLaAppPort(),
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

    // --- La línea ya no tiene filtro ni alcance propios -----------------------
    //
    // `filtrar(...)` y `alcance(...)` se quitaron del ViewModel: la línea de
    // "lo que ha pasado" del detalle de venta ya no tiene pastillas — pinta
    // siempre los cinco contactos más recientes de ESTA cuenta, sin más, y
    // "ver los N contactos" lleva a la bitácora completa, que es donde hoy
    // viven los filtros. Ver el KDoc de [DetalleVentaUiState].

    /**
     * **Control positivo.** El mismo [lecturas] sí se mueve con una recarga de
     * verdad. Sin esto, unos contadores que no contaran darían la misma cifra
     * siempre.
     */
    @Test
    fun `control positivo - una recarga de verdad si mueve los contadores`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()

        val lecturasDeLaCarga = lecturas()
        vm.cargar()
        advanceUntilIdle()

        assertTrue(
            "los contadores no detectan ni una recarga explícita ($lecturasDeLaCarga antes, " +
                "${lecturas()} después): los tests de arriba no están midiendo nada",
            lecturas() > lecturasDeLaCarga
        )
    }

    /**
     * Las consultas que el detalle le hace al teléfono, sumadas. Son listas que
     * **graban** la llamada, no las semillas de entrada.
     */
    private fun lecturas(): Int = ventasPort.clientesConsultados.size +
        visitasPort.clientesConsultados.size +
        pagosPort.ventasConsultadas.size

    // --- recargar(): la que usa la pantalla al reanudarse --------------------

    /**
     * **El corazón del arreglo**: después de registrar un abono, la pantalla a
     * la que se vuelve tiene que enseñarlo. `recargar()` no puede ser un alias
     * de "repintar lo que ya había en memoria" — tiene que volver a leer el
     * teléfono. Se mueve un dato real entre la carga inicial y la recarga (un
     * abono nuevo) y se comprueba que el detalle lo refleja.
     */
    @Test
    fun `recargar vuelve a leer del puerto`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(6, vm.state.value.detalle!!.historial.totalPagos)

        pagosPort.pagos = pagosPort.pagos +
            PagosFixtures.pagoConUbicacion(19.4, -99.1).copy(pagoId = "COB-RECARGA")

        vm.recargar()
        advanceUntilIdle()

        assertEquals(7, vm.state.value.detalle!!.historial.totalPagos)
    }

    /**
     * **`recargar()` no parpadea.** A diferencia de [DetalleVentaViewModel.cargar]
     * —que reemplaza el estado por uno en blanco con `cargando = true`—,
     * `recargar()` nunca lo enciende. Se mide sobre la SECUENCIA de estados
     * emitidos y no solo sobre el valor final, porque un `cargando` que se
     * prendiera y apagara en el mismo tick no se vería mirando sólo
     * `state.value` al final.
     *
     * El contraste con `cargar()` es el control positivo: la MISMA forma de
     * medir sí ve el encendido cuando de verdad ocurre, así que la ausencia de
     * arriba prueba algo y no es un contador que nunca se mueve.
     */
    @Test
    fun `recargar no enciende cargando, a diferencia de cargar`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val estados = mutableListOf<Boolean>()
        backgroundScope.launch { vm.state.collect { estados += it.cargando } }
        // `advanceUntilIdle` y no `runCurrent`: con `runCurrent` el colector
        // todavía no había arrancado, así que las emisiones de la recarga no
        // llegaban a la lista y la prueba se caía por su propio control de
        // "no midió nada" — el control hizo exactamente su trabajo.
        advanceUntilIdle()
        estados.clear() // solo interesan las emisiones de aquí en adelante

        pagosPort.pagos = pagosPort.pagos +
            PagosFixtures.pagoConUbicacion(19.4, -99.1).copy(pagoId = "COB-RECARGA-1")
        vm.recargar()
        advanceUntilIdle()

        assertTrue(
            "recargar encendió cargando en algún momento observable: $estados",
            estados.none { it }
        )
        assertTrue(
            "recargar no produjo ninguna emisión nueva: esta prueba no midió nada",
            estados.isNotEmpty()
        )

        estados.clear()
        pagosPort.pagos = pagosPort.pagos +
            PagosFixtures.pagoConUbicacion(19.5, -99.2).copy(pagoId = "COB-RECARGA-2")
        vm.cargar()
        advanceUntilIdle()

        assertTrue(
            "cargar ya no enciende cargando: el contraste de arriba no prueba nada",
            estados.any { it }
        )
    }
}
