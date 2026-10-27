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
 * **Avisar no es bloquear**, y desde el 2026-09-29 (decisión del dueño, su
 * jefe lo pidió el mismo día) **ningún nivel exige teclear nada**: nivel 2 y
 * nivel 3 confirman los dos con un solo toque — lo único que distingue al
 * nivel 3 es su propia banda roja, no un requisito de escritura. El nivel 3
 * llegó a exigir teclear el monto otra vez; esa historia y el diseño completo
 * (con dos variantes evaluadas) siguen en
 * `docs/design/mocks/confirmacion-escrita-del-abono.html` por si el requisito
 * vuelve.
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
    fun `el nivel 3 muestra su aviso y confirma con un solo toque, registrando exactamente una vez`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            // $1,400 son 6.36 cuotas de $220: nivel 3. Sigue por debajo del
            // saldo ($1,450), así que nada lo bloquea — lo único que cambia
            // es la banda que se pinta, no cuántos toques hacen falta.
            teclear(vm, "1400")
            assertEquals(NivelDeAviso.AFIRMAR, vm.state.value.aviso.nivel)

            vm.pedirConfirmacion()
            // Ningún nivel exige teclear nada: el paso dos ya se puede
            // confirmar apenas se abre, sin ninguna acción extra.
            assertTrue(vm.state.value.confirmacion!!.sePuedeConfirmar)

            vm.confirmar()
            advanceUntilIdle()
            assertEquals("un solo toque registra", 1, registroPort.registrados.size)
            assertEquals(dinero("1400"), registroPort.registrados.single().importe)

            // Y no dos: un segundo toque sobre el mismo ViewModel no duplica.
            vm.confirmar()
            advanceUntilIdle()
            assertEquals(
                "anti-doble-toque: sigue habiendo exactamente uno",
                1,
                registroPort.registrados.size
            )
        }

    @Test
    fun `ningun nivel exige teclear el monto otra vez`() = runTest(testDispatcher) {
        // Dos ViewModels frescos, uno por nivel — `teclear` sólo limpia por
        // completo un monto SUGERIDO (ver `MontoCapturado.sinUltimo`), así que
        // reusar el mismo ViewModel entre dos montos ya tecleados no repite el
        // borrado inicial y arrastra dígitos del monto anterior.

        // Nivel 2: $900 sobre $220 son 4.09 cuotas.
        val vmNivel2 = viewModel()
        advanceUntilIdle()
        teclear(vmNivel2, "900")
        vmNivel2.pedirConfirmacion()
        assertEquals(NivelDeAviso.CONFIRMAR, vmNivel2.state.value.confirmacion!!.aviso.nivel)
        assertTrue(
            "nivel 2 ya se puede confirmar sin teclear nada más",
            vmNivel2.state.value.confirmacion!!.sePuedeConfirmar
        )

        // Nivel 3: $1,400 sobre $220 son 6.36 cuotas.
        val vmNivel3 = viewModel()
        advanceUntilIdle()
        teclear(vmNivel3, "1400")
        vmNivel3.pedirConfirmacion()
        assertEquals(NivelDeAviso.AFIRMAR, vmNivel3.state.value.confirmacion!!.aviso.nivel)
        assertTrue(
            "nivel 3 tampoco pide teclear nada — decisión del dueño, 2026-09-29",
            vmNivel3.state.value.confirmacion!!.sePuedeConfirmar
        )
    }

    @Test
    fun `un monto normal tambien se puede confirmar de una`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        // Lo esperado hoy, que es el 68 % de los abonos de la ruta.
        vm.pedirConfirmacion()
        assertTrue(vm.state.value.confirmacion!!.sePuedeConfirmar)
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
