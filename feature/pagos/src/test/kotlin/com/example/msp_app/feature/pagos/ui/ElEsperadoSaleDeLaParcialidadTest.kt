package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.RegistrarAbono
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeComprobantesPort
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeRegistroDeAbonoPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.OrigenDeLaCuota
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import java.math.BigDecimal
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
import org.junit.Before
import org.junit.Test

/**
 * **El rediseño del esperado está apagado para el despliegue del 2026-09-22.**
 *
 * `CargarDetalleVenta.cuotaDe` ya no llama a la precedencia de tres escalones
 * de [com.example.msp_app.feature.pagos.domain.CuotaDeLaVenta.de]: la cuota
 * que la pantalla afirma es siempre la parcialidad capturada. Se apaga porque
 * defiende un caso posible pero no observado — el caso que lo trajo, la venta
 * `Y00002184` con `PARCIALIDAD = 3000`, resultó ser dato de prueba.
 *
 * La precedencia sigue cubierta a nivel de dominio, sin tocarse, en
 * [com.example.msp_app.feature.pagos.domain.CuotaDeLaVentaTest]. Lo que fijan
 * estas pruebas es el estado DESPLEGADO — de extremo a extremo, desde el
 * puerto hasta la pantalla — y se ponen rojas el día que se revierta el
 * apagado, que es exactamente lo que se quiere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ElEsperadoSaleDeLaParcialidadTest {

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
    private val registroPort = FakeRegistroDeAbonoPort()
    private val camaraPort = FakeComprobantesPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = listOf(AbonoFixtures.datosDeVenta())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `con historial, el esperado sigue siendo la parcialidad capturada`() = runTest(
        testDispatcher
    ) {
        // Con el rediseño encendido esto habría sido COMPORTAMIENTO a $150: la
        // moda de estos tres pagos. Apagado, la cuota no mira el historial.
        pagosPort.pagos = listOf(abono("150", 1), abono("150", 8), abono("150", 15))

        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals(OrigenDeLaCuota.PARCIALIDAD, state.venta!!.cuota.origen)
        assertEquals(dinero("220"), state.venta!!.cuota.monto)
        // El chip mantiene el rótulo de siempre, no el de "lo que paga".
        val esperado = state.sugeridos.first()
        assertEquals(MontosSugeridos.Sugerencia.ESPERADO_HOY, esperado.cual)
        assertEquals(dinero("220"), esperado.importe)
        // Y el teclado arranca en esa misma cifra.
        assertEquals(dinero("220"), state.monto.importe)
    }

    @Test
    fun `una parcialidad alta se sigue afirmando - el aviso esta apagado`() = runTest(
        testDispatcher
    ) {
        // El caso del reporte: cuota de $3,000, cero pagos, y una ruta donde el
        // percentil 99 de lo cobrado es $150 — con el rediseño encendido esto
        // habría salido DUDOSA. Apagado, la pantalla la sigue afirmando.
        ventasPort.ventas = listOf(conParcialidad("3000"))
        pagosPort.pagos = emptyList()
        pagosPort.importesDeLaRuta = muestraDeLaRuta()

        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals(OrigenDeLaCuota.PARCIALIDAD, state.venta!!.cuota.origen)
        assertEquals(dinero("3000"), state.venta!!.cuota.esperado)
    }

    @Test
    fun `la muestra de la ruta ya no se pide nunca`() = runTest(testDispatcher) {
        // Sin pagos: antes del apagado era justo el caso que SÍ la pedía (el
        // escalón 3). La muestra queda sembrada como control positivo: el cero
        // que se afirma abajo es "no se pidió", no "no había nada que pedir".
        pagosPort.pagos = emptyList()
        pagosPort.importesDeLaRuta = muestraDeLaRuta()

        viewModel()
        advanceUntilIdle()

        assertEquals(0, pagosPort.vecesQueSePidioLaRuta)
    }

    /**
     * Mil pagos con la forma real de la ruta: $100 / $200 / $150 dominando y
     * una cola delgada. El percentil 99 cae en $150.
     */
    private fun muestraDeLaRuta(): List<Money> =
        List(400) { dinero("100") } + List(350) { dinero("200") } + List(250) { dinero("150") }

    private fun conParcialidad(pesos: String) =
        AbonoFixtures.datosDeVenta().copy(parcialidad = dinero(pesos))

    private fun abono(pesos: String, dia: Int) = PagoDelHistorial(
        pagoId = "COB-$dia",
        ventaId = AbonoFixtures.VENTA_ID,
        fecha = Instant.parse("2026-08-%02dT12:00:00Z".format(dia)),
        importe = dinero(pesos),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null
    )

    private fun viewModel() = RegistrarAbonoViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(PagosRutas.ARG_VENTA_ID to AbonoFixtures.VENTA_ID)
        ),
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
        registrarAbono = RegistrarAbono(registroPort, telemetria),
        camara = camaraPort,
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
