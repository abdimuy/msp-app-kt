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
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.port.AbonoEnCurso
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La pantalla del abono deriva del marcador `capturas_en_curso` (E-APP-084):
 * ESCRITA → "Abono registrado"; CAPTURADA → "Abono de $X sin guardar" con
 * "Guardar" y sin descartar (E-APP-090); la hora del pago es la de captura
 * (E-APP-085); textos aprobados por el dueño (E-APP-089).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReanudacionDelAbonoTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)
    private val ventasPort = FakeVentasPort()
    private val registroPort = FakeRegistroDeAbonoPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = listOf(AbonoFixtures.datosDeVenta())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- E-APP-084/085/089/090: la pantalla deriva del marcador -------------

    @Test
    fun `E-APP-085 confirmar manda la hora del toque como hora de captura`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()

        assertEquals(clock.now(), registroPort.registrados.single().capturadoEn)
    }

    @Test
    fun `E-APP-084 un abono registrado cierra su marcador`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()

        assertEquals(listOf(vm.abonoId), registroPort.cerrados)
    }

    @Test
    fun `E-APP-084 con marcador ESCRITA la pantalla dice Abono registrado y no navega sola`() =
        runTest(testDispatcher) {
            registroPort.enCurso = enCurso(escrito = true)
            val vm = viewModel()
            advanceUntilIdle()

            val state = vm.state.value
            assertEquals(MARCADOR, state.yaRegistrado)
            assertEquals("no es el final que navega al ticket", null, state.registrado)
            assertEquals(false, state.sePuedeRegistrar)
            assertEquals(MARCADOR, vm.abonoId)
        }

    @Test
    fun `E-APP-084 Capturar otro cierra el marcador y acuna un UUID nuevo`() = runTest(
        testDispatcher
    ) {
        registroPort.enCurso = enCurso(escrito = true)
        val vm = viewModel()
        advanceUntilIdle()

        vm.capturarOtro()
        advanceUntilIdle()

        assertEquals(listOf(MARCADOR), registroPort.cerrados)
        assertTrue("UUID nuevo", vm.abonoId != MARCADOR)
        assertEquals(null, vm.state.value.yaRegistrado)
        assertTrue(vm.state.value.sePuedeCapturar)
    }

    @Test
    fun `E-APP-084 con marcador CAPTURADA se ofrece Guardar con el mismo UUID, importe y hora`() =
        runTest(testDispatcher) {
            registroPort.enCurso = enCurso(escrito = false)
            val vm = viewModel()
            advanceUntilIdle()

            val sinGuardar = vm.state.value.sinGuardar!!
            assertEquals(dinero("180"), sinGuardar.importe)
            assertEquals(MARCADOR, vm.abonoId)
            assertEquals(
                "E-APP-090: sin descarte, la captura queda cerrada al teclado",
                false,
                vm.state.value.sePuedeCapturar
            )

            vm.guardarSinGuardar()
            advanceUntilIdle()

            val escrito = registroPort.registrados.single()
            assertEquals(MARCADOR, escrito.abonoId)
            assertEquals(dinero("180"), escrito.importe)
            assertEquals(
                "E-APP-085: la hora de la captura, no la de ahora",
                CAPTURADO_EN,
                escrito.capturadoEn
            )
            assertEquals(MARCADOR, vm.state.value.registrado)
        }

    @Test
    fun `E-APP-089 los textos aprobados del abono se dicen tal cual`() {
        assertEquals("Abono registrado", TEXTO_ABONO_REGISTRADO)
        assertEquals("Guardar", TEXTO_GUARDAR)
        assertEquals("Ver ticket", TEXTO_VER_TICKET)
        assertEquals("Capturar otro", TEXTO_CAPTURAR_OTRO)
        assertEquals("Abono de \$180 sin guardar", textoAbonoSinGuardar(dinero("180")))
    }

    private fun enCurso(escrito: Boolean) = AbonoEnCurso(
        abonoId = MARCADOR,
        escrito = escrito,
        importe = dinero("180"),
        metodo = MetodoDeCobro.EFECTIVO,
        comprobantes = emptyList(),
        capturadoEn = CAPTURADO_EN
    )

    private fun viewModel() = RegistrarAbonoViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(PagosRutas.ARG_VENTA_ID to AbonoFixtures.VENTA_ID)
        ),
        cargarDetalleVenta = CargarDetalleVenta(
            ventasPort = ventasPort,
            garantiasPort = FakeGarantiasPort(),
            productosPort = FakeProductosPort(),
            reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
                ventasPort = ventasPort,
                pagosPort = FakePagosPort(),
                visitasPort = FakeVisitasPort(),
                liquidacionPort = FakeLiquidacionPort(),
                resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
            ),
            clock = clock
        ),
        registrarAbono = RegistrarAbono(registroPort, telemetria),
        camara = FakeComprobantesPort(),
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private companion object {
        const val MARCADOR = "8d2e4f60-1a3b-4c5d-9e7f-0a1b2c3d4e5f"
        val CAPTURADO_EN: Instant = Instant.parse("2026-10-20T13:00:00Z")
    }
}
