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
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
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
import org.junit.Assert.assertSame
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
            pagosPort = pagosPort,
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

    // --- El filtro y el alcance viven sobre lo cargado -----------------------

    /**
     * **La pastilla cambia lo que se enseña, y NADA más.**
     *
     * El hueco que esto cierra es el mismo de la bitácora: el test de la pieza
     * (`LaLineaDiceQuienComoYCuandoTest`) monta las pastillas con el estado en
     * el propio test, así que un `DetalleVentaScreen` que pasara
     * `onFiltrar = {}` quedaría en verde. Aquí se cobra que `filtrar(...)`
     * llegue a `state.filtro` y que **no dispare otra lectura**: el filtro es
     * una decisión sobre lo que ya está en memoria, y rearmar el detalle a cada
     * toque de pastilla sería volver a Room —venta, productos, garantías y la
     * cobranza entera del cliente— parado en una puerta.
     *
     * El `TODOS` de arranque se afirma antes de tocar nada: sin eso un
     * `state.filtro` clavado en `COBROS` pasaría igual.
     */
    @Test
    fun `filtrar cambia el filtro del estado y no vuelve a leer nada`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            "el arranque ya no es TODOS, así que la aserción de abajo no prueba nada",
            FiltroDeContactos.TODOS,
            vm.state.value.filtro
        )
        val lecturasDeLaCarga = lecturas()
        val cargado = checkNotNull(vm.state.value.detalle)

        vm.filtrar(FiltroDeContactos.PROMESAS)
        advanceUntilIdle()

        assertEquals(FiltroDeContactos.PROMESAS, vm.state.value.filtro)
        assertEquals(
            "filtrar volvió a leer: eran $lecturasDeLaCarga consultas después de cargar y " +
                "ahora son ${lecturas()}. El filtro vive sobre lo ya cargado",
            lecturasDeLaCarga,
            lecturas()
        )
        assertSame(
            "filtrar rearmó el detalle: el estado trae otro objeto, así que la pantalla se " +
                "recompuso entera en vez de sólo cambiar qué se enseña de la línea",
            cargado,
            vm.state.value.detalle
        )
    }

    /**
     * **El interruptor *Esta venta / Todo el cliente* tampoco lee de nuevo.**
     *
     * Y ésta es la que más tentaba a recargar: "todo el cliente" suena a que
     * hay que ir por más datos. No los hay — `CargarDetalleVenta` ya trae la
     * cobranza entera del cliente y la venta sólo la recorta, así que abrir el
     * alcance es quitar un filtro, no pedir nada.
     *
     * `soloEstaVenta` arranca en `true` (la pantalla se llama "detalle de
     * venta") y eso se afirma antes de moverlo.
     */
    @Test
    fun `abrir el alcance a todo el cliente no vuelve a leer nada`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(
            "el detalle ya no arranca angostado a su venta: mover el alcance a false no " +
                "probaría el cambio",
            vm.state.value.soloEstaVenta
        )
        val lecturasDeLaCarga = lecturas()
        val cargado = checkNotNull(vm.state.value.detalle)

        vm.alcance(soloEstaVenta = false)
        advanceUntilIdle()

        assertFalse(vm.state.value.soloEstaVenta)
        assertEquals(
            "abrir el alcance volvió a leer: eran $lecturasDeLaCarga consultas y ahora son " +
                "${lecturas()}. La cobranza del cliente entero ya venía cargada",
            lecturasDeLaCarga,
            lecturas()
        )
        assertSame(cargado, vm.state.value.detalle)
    }

    /**
     * **Control positivo de los dos de arriba.** El mismo [lecturas] sí se
     * mueve con una recarga de verdad. Sin esto, unos contadores que no
     * contaran darían la misma cifra siempre y las dos ausencias de arriba
     * serían verdes vacíos.
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
}
