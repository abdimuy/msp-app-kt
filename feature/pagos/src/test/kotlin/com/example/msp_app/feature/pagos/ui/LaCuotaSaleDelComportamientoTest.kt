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
import com.example.msp_app.feature.pagos.domain.LineaBaseDeLaRuta
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **El esperado sale del comportamiento, no del dato capturado** — el camino
 * completo, desde el puerto hasta el estado de la pantalla.
 *
 * El caso que lo trajo: la venta `Y00002184` decía `PARCIALIDAD = 3000` y la
 * pantalla la repetía fiel — *"abono corto · esperado $3,000 · este abono
 * $600"*— y además ofrecía $3,000 en un chip. El dato estaba mal capturado y la
 * venta **no tenía un solo pago** que lo desmintiera.
 *
 * Las pruebas de dominio ([com.example.msp_app.feature.pagos.domain.CuotaDeLaVentaTest])
 * cubren la precedencia. Lo que se prueba **aquí** es lo que sólo se ve de
 * extremo a extremo:
 *
 * 1. que la carga real derive la cuota y no la parcialidad cruda;
 * 2. que el chip y el aviso salgan de la **misma** fuente, que es la regla que
 *    impide que la pantalla proponga una cifra y después la cuestione;
 * 3. que la muestra de la ruta —miles de filas— **sólo se pida cuando hace
 *    falta**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaCuotaSaleDelComportamientoTest {

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
    fun `con historial, el esperado es lo que esta cuenta paga y el chip lo dice`() = runTest(
        testDispatcher
    ) {
        // La venta dice $220 de parcialidad, pero esta cuenta paga $150.
        pagosPort.pagos = listOf(abono("150", 1), abono("150", 8), abono("150", 15))

        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals(OrigenDeLaCuota.COMPORTAMIENTO, state.venta!!.cuota.origen)
        assertEquals(dinero("150"), state.venta!!.cuota.monto)
        // El chip cambia de rótulo: el cobrador puede saber de dónde salió.
        val esperado = state.sugeridos.first()
        assertEquals(MontosSugeridos.Sugerencia.ESPERADO_POR_COSTUMBRE, esperado.cual)
        assertEquals(dinero("150"), esperado.importe)
        // Y el teclado arranca en esa misma cifra: una sola fuente.
        assertEquals(dinero("150"), state.monto.importe)
    }

    @Test
    fun `sin historial y con una parcialidad absurda, la pantalla no la afirma`() = runTest(
        testDispatcher
    ) {
        // El caso del reporte: cuota de $3,000, cero pagos en esta venta, y una
        // ruta donde el percentil 99 de lo cobrado es $150.
        ventasPort.ventas = listOf(conParcialidad("3000"))
        pagosPort.pagos = emptyList()
        pagosPort.importesDeLaRuta = muestraDeLaRuta()

        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value

        assertEquals(OrigenDeLaCuota.DUDOSA, state.venta!!.cuota.origen)
        // No se ofrece un esperado que no se sostiene — ni con un rótulo ni con
        // el otro. Es la misma fuente para el chip y para el aviso.
        assertTrue(
            state.sugeridos.none {
                it.cual == MontosSugeridos.Sugerencia.ESPERADO_HOY ||
                    it.cual == MontosSugeridos.Sugerencia.ESPERADO_POR_COSTUMBRE
            }
        )
        // El teclado tampoco se prellena con la cifra dudosa.
        assertEquals(Money.ZERO, state.monto.importe)
        // Y un abono de $600 ya no es "corto": no hay contra qué medirlo.
        teclear(vm, "600")
        assertFalse(
            com.example.msp_app.feature.pagos.domain.RarezaDelAbono.ABAJO_DE_LO_ESPERADO in
                vm.state.value.veredicto.rarezas
        )
    }

    @Test
    fun `control positivo - la MISMA venta sin muestra de ruta si afirma la parcialidad`() =
        runTest(testDispatcher) {
            // Sin línea base no hay autoridad para llamar absurdo a nada, y el
            // comportamiento es el de siempre. Sin este control, un `DUDOSA`
            // que saliera siempre dejaría el test de arriba en verde.
            ventasPort.ventas = listOf(conParcialidad("3000"))
            pagosPort.pagos = emptyList()
            pagosPort.importesDeLaRuta = emptyList()

            val vm = viewModel()
            advanceUntilIdle()

            assertEquals(OrigenDeLaCuota.PARCIALIDAD, vm.state.value.venta!!.cuota.origen)
            assertEquals(dinero("3000"), vm.state.value.venta!!.cuota.esperado)
        }

    @Test
    fun `la muestra de la ruta NO se pide cuando la venta tiene pagos`() = runTest(
        testDispatcher
    ) {
        // Son miles de filas y sólo sirven para el escalón 3. Cobrárselas al
        // 96 % de las cargas que no las usan sería pagar por todos el costo de
        // la excepción.
        pagosPort.pagos = listOf(abono("150", 1), abono("150", 8), abono("150", 15))

        viewModel()
        advanceUntilIdle()

        assertEquals(0, pagosPort.vecesQueSePidioLaRuta)
    }

    @Test
    fun `control positivo - sin pagos la muestra SI se pide`() = runTest(testDispatcher) {
        pagosPort.pagos = emptyList()

        viewModel()
        advanceUntilIdle()

        assertTrue(pagosPort.vecesQueSePidioLaRuta > 0)
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

    private fun teclear(vm: RegistrarAbonoViewModel, pesos: String) {
        vm.onBorrar()
        pesos.forEach { caracter ->
            if (caracter == '.') vm.onPunto() else vm.onDigito(caracter.digitToInt())
        }
    }

    private fun viewModel() = RegistrarAbonoViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(PagosRutas.ARG_VENTA_ID to AbonoFixtures.VENTA_ID)
        ),
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
        registrarAbono = RegistrarAbono(registroPort, telemetria),
        camara = camaraPort,
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private companion object {
        /** Sólo para que el KDoc de la clase pueda citarlo sin importarlo. */
        val PISO: Int = LineaBaseDeLaRuta.MINIMO_DE_PAGOS
    }
}
