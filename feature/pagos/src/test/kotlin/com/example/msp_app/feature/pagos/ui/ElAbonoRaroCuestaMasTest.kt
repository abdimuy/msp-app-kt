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
import com.example.msp_app.feature.pagos.domain.NivelDeAviso
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import java.math.BigDecimal
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
 * **Los avisos escalonados, en el ViewModel.**
 *
 * Vive aparte de `RegistrarAbonoViewModelTest` —mismo ViewModel, mismo
 * aparato— porque aquella clase ya estaba en el techo de `LargeClass` y
 * porque lo que se prueba aquí es otra cosa: no "ninguna ruta guarda dos
 * veces", sino **cuánto cuesta decir que sí** según lo raro que sea el monto.
 *
 * Los dos hechos que estas pruebas fijan:
 *
 * 1. **Avisar no es bloquear.** Ningún nivel impide registrar; el nivel 3 sólo
 *    exige que el monto se teclee otra vez.
 * 2. **La guarda del eco vive en el ViewModel**, no en el `enabled` del botón.
 *    Un `enabled` es presentación y cualquier otro llamador de `confirmar()`
 *    se lo saltaría — y éste es el único camino que escribe dinero.
 *
 * La cuenta es la del mock: **cuota $220, saldo $1,450**.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ElAbonoRaroCuestaMasTest {

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
        liquidacionPort.liquidaciones = mapOf(
            AbonoFixtures.VENTA_ID to Liquidacion(
                monto = AbonoFixtures.LIQUIDACION,
                vigenteHasta = null,
                categoria = "precio a 4 meses"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `cuatro cuotas de golpe piden confirmar con un toque`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        // $900 sobre una cuota de $220 son 4.09 cuotas: nivel 2. Es múltiplo de
        // 50 y está por encima de lo esperado, así que la ÚNICA señal es ésa.
        teclear(vm, "900")

        val aviso = vm.state.value.aviso
        assertEquals(NivelDeAviso.CONFIRMAR, aviso.nivel)
        assertEquals(listOf("Son 4 cuotas de \$220"), aviso.mensajes)
        assertTrue("avisar no es bloquear", vm.state.value.sePuedeRegistrar)
    }

    @Test
    fun `el nivel 3 no escribe nada hasta que el monto se teclea otra vez`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        // $1,400 son 6.36 cuotas de $220: nivel 3. Sigue por debajo del saldo
        // ($1,450), así que nada lo bloquea — lo único que cambia es el costo
        // de decir que sí.
        teclear(vm, "1400")
        assertEquals(NivelDeAviso.TECLEAR, vm.state.value.aviso.nivel)

        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertTrue("sin el eco no se escribe nada", registroPort.registrados.isEmpty())

        vm.onEcoDelMonto("1400")
        assertTrue(vm.state.value.confirmacion!!.sePuedeConfirmar)
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1, registroPort.registrados.size)
        assertEquals(dinero("1400"), registroPort.registrados.single().importe)
    }

    @Test
    fun `un eco equivocado deja el paso dos cerrado`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        teclear(vm, "1400")
        vm.pedirConfirmacion()

        // Un dígito de menos: justo el resbalón que esto viene a atrapar.
        vm.onEcoDelMonto("140")
        assertFalse(vm.state.value.confirmacion!!.sePuedeConfirmar)
        vm.confirmar()
        advanceUntilIdle()
        assertTrue(registroPort.registrados.isEmpty())
    }

    @Test
    fun `el eco compara dinero y no mecanografia`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        teclear(vm, "1400")
        vm.pedirConfirmacion()

        vm.onEcoDelMonto("1400.00")
        assertTrue(
            "1400 y 1400.00 son el mismo monto",
            vm.state.value.confirmacion!!.sePuedeConfirmar
        )
    }

    @Test
    fun `el eco no mueve el monto que se va a registrar`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        teclear(vm, "1400")
        vm.pedirConfirmacion()
        vm.onEcoDelMonto("9")

        assertEquals(
            "lo tecleado en el eco es una comprobación, no un monto",
            dinero("1400"),
            vm.state.value.monto.importe
        )
        assertEquals(dinero("1400"), vm.state.value.confirmacion!!.importe)
    }

    @Test
    fun `un monto normal no pide teclear nada`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        // Lo esperado hoy, que es el 68 % de los abonos de la ruta.
        vm.pedirConfirmacion()
        assertFalse(vm.state.value.confirmacion!!.pideTeclearElMonto)
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1, registroPort.registrados.size)
    }

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
