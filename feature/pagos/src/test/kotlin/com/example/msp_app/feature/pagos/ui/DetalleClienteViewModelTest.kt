package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleCliente
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.GuardarFichaDelCliente
import com.example.msp_app.feature.pagos.application.PagosTelemetria
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
import java.math.BigDecimal
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

    private val accionesExternas = FakeAccionesExternasPort()

    private fun viewModel(clienteId: Int = PagosFixtures.CLIENTE_ID) = DetalleClienteViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PagosRutas.ARG_CLIENTE_ID to clienteId)),
        cargarDetalleCliente = CargarDetalleCliente(
            fichaPort = fichaPort,
            productosPort = FakeProductosPort(),
            clock = clock,
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
        accionesExternas = accionesExternas,
        tema = FakeTemaDeLaAppPort(),
        privacidad = FakePrivacidadPort(),
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

    // --- recargar(): la que usa la pantalla al reanudarse --------------------

    /** Una venta nueva del mismo cliente, con cifras propias y sin dependencias externas. */
    private fun ventaExtra() = PagosFixtures.datosDeVenta(
        ventaId = 77900,
        folio = "V-7900",
        descripcion = "Comedor 6 sillas",
        cifras = PagosFixtures.Cifras(
            total = Money.of(BigDecimal("7400")),
            restante = Money.of(BigDecimal("2600")),
            cuota = Money.of(BigDecimal("300")),
            cubierto = Money.of(BigDecimal("4800"))
        )
    )

    /**
     * **El corazón del arreglo**: después de registrar un abono o una visita, la
     * pantalla a la que se vuelve tiene que enseñarlo. `recargar()` no puede ser
     * un alias de "repintar lo que ya había en memoria" — tiene que volver a
     * leer el teléfono. Se agrega una venta real entre la carga inicial y la
     * recarga y se comprueba que el detalle la refleja.
     */
    @Test
    fun `recargar vuelve a leer del puerto`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.detalle!!.ventas.size)

        ventasPort.ventas = ventasPort.ventas + ventaExtra()

        vm.recargar()
        advanceUntilIdle()

        assertEquals(3, vm.state.value.detalle!!.ventas.size)
    }

    /**
     * **`recargar()` no cierra la hoja de la ficha de golpe.** [leer] arma un
     * `DetalleClienteUiState` desde cero; sin el `copy` explícito de
     * `recargar()`, una recarga disparada al reanudar la app con la hoja
     * abierta —por ejemplo, tras un cambio de app a medio escribir la nota—
     * la cerraría y tiraría el borrador del cobrador.
     */
    @Test
    fun `recargar conserva la hoja de la ficha abierta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.editarFicha()
        vm.escribirNota("nueva nota sin guardar")
        // La hoja se abre en una corrutina: sin esto se afirmaba sobre un
        // estado que todavía no la tenía, y la prueba se caía antes de llegar
        // a medir lo que vino a medir.
        advanceUntilIdle()
        val edicionAntes = checkNotNull(vm.state.value.edicionDeLaFicha)

        ventasPort.ventas = ventasPort.ventas + ventaExtra()
        vm.recargar()
        advanceUntilIdle()

        assertEquals(edicionAntes, vm.state.value.edicionDeLaFicha)
    }

    /**
     * **Y tampoco cierra la hoja "¿a cuál cuenta?".** Mismo argumento que la de
     * arriba, sobre la otra hoja que este estado puede tener abierta.
     */
    @Test
    fun `recargar conserva la eleccion de cuenta abierta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val navegoDirecto = vm.registrarAbono()
        assertNull("con dos cuentas cobrables tiene que abrir la hoja, no navegar", navegoDirecto)
        // Misma razón que en la prueba de la ficha: la hoja se abre en una
        // corrutina y sin esto se afirmaba sobre un estado que aún no la tenía.
        advanceUntilIdle()
        val eleccionAntes = checkNotNull(vm.state.value.eleccionDeCuenta)

        vm.recargar()
        advanceUntilIdle()

        assertEquals(eleccionAntes, vm.state.value.eleccionDeCuenta)
    }

    /**
     * **`recargar()` no parpadea.** A diferencia de [DetalleClienteViewModel.cargar]
     * —que reemplaza el estado por uno en blanco con `cargando = true`—,
     * `recargar()` nunca lo enciende. Se mide sobre la SECUENCIA de estados
     * emitidos y no solo sobre el valor final, porque un `cargando` que se
     * prendiera y apagara en el mismo tick no se vería mirando sólo
     * `state.value` al final.
     *
     * El contraste con `cargar()` es el control positivo: la MISMA forma de
     * medir sí ve el encendido cuando de verdad ocurre.
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

        ventasPort.ventas = ventasPort.ventas + ventaExtra()
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
        vm.cargar()
        advanceUntilIdle()

        assertTrue(
            "cargar ya no enciende cargando: el contraste de arriba no prueba nada",
            estados.any { it }
        )
    }
}
