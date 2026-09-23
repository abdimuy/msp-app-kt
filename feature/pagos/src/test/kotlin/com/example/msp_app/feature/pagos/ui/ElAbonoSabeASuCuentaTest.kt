package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.MainDispatcherRule
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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * **El abono sabe a qué cuenta va, y lo dice.**
 *
 * El defecto que estas pruebas cierran: `cuentaQueEncabeza` mandaba el dinero a
 * `ventas.firstOrNull()` sin decirlo. Con dos cuentas, el abono podía entrar a
 * la equivocada y nadie se enteraba hasta que cuadraban.
 *
 * Y las tres acciones que salen de la app: que se pidan con el dato correcto, y
 * que **su fallo quede reportado** — un teléfono sin WhatsApp o sin app de mapas
 * existe en la flota, y sin telemetría el síntoma sería "toco y no pasa nada".
 */
class ElAbonoSabeASuCuentaTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry()
    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val fichaPort = FakeFichaPort()
    private val accionesExternas = FakeAccionesExternasPort()

    private fun viewModel() = DetalleClienteViewModel(
        savedStateHandle = SavedStateHandle(
            mapOf(PagosRutas.ARG_CLIENTE_ID to PagosFixtures.CLIENTE_ID)
        ),
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

    /**
     * **Con dos cuentas NO navega: pregunta.**
     *
     * Antes, este mismo estado mandaba el dinero a la venta de arriba sin abrir
     * nada. Ahora `registrarAbono()` contesta `null` —no hay a dónde ir todavía—
     * y deja la hoja arriba.
     */
    @Test
    fun `con dos cuentas el dock abre la hoja en vez de elegir`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        val destino = vm.registrarAbono()
        advanceUntilIdle()

        assertNull("no puede navegar sin que el cobrador elija", destino)
        assertNotNull("la hoja tiene que estar arriba", vm.state.value.eleccionDeCuenta)
    }

    /**
     * **Y viene marcada la de atrasos.** En el fixture, la sala va primera y el
     * refrigerador es el que trae los dos atrasos.
     */
    @Test
    fun `la hoja abre con la cuenta de atrasos marcada`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        vm.registrarAbono()
        advanceUntilIdle()

        val detalle = checkNotNull(vm.state.value.detalle)
        val conAtrasos = detalle.ventas.maxByOrNull { it.atrasos }
        assertTrue("el fixture tiene que traer atrasos", (conAtrasos?.atrasos ?: 0) > 0)
        assertEquals(conAtrasos?.ventaId, vm.state.value.eleccionDeCuenta?.elegida)
    }

    /** Con UNA sola cuenta el flujo no cambia: va directo, sin hoja. */
    @Test
    fun `con una sola cuenta va directo y no abre nada`() = runTest(testDispatcher) {
        ventasPort.ventas = listOf(PagosFixtures.datosDeVentas().first())
        val vm = viewModel()
        advanceUntilIdle()

        val destino = vm.registrarAbono()
        advanceUntilIdle()

        assertEquals(PagosFixtures.VENTA_PAGADA, destino)
        assertNull("con una cuenta no hay nada que preguntar", vm.state.value.eleccionDeCuenta)
    }

    /** Elegir otra cuenta y confirmar navega a ESA, no a la preseleccionada. */
    @Test
    fun `elegir otra cuenta manda el dinero a esa`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()
        vm.registrarAbono()
        advanceUntilIdle()

        vm.elegirCuenta(PagosFixtures.VENTA_PAGADA)
        advanceUntilIdle()
        val destino = vm.confirmarCuenta()
        advanceUntilIdle()

        assertEquals(PagosFixtures.VENTA_PAGADA, destino)
        assertNull("la hoja se cierra antes de navegar", vm.state.value.eleccionDeCuenta)
    }

    /** Cerrar la hoja no registra nada ni navega. */
    @Test
    fun `cerrar la hoja no manda dinero a ningun lado`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()
        vm.registrarAbono()
        advanceUntilIdle()

        vm.cerrarEleccionDeCuenta()
        advanceUntilIdle()

        assertNull(vm.state.value.eleccionDeCuenta)
        assertNull("sin hoja no hay cuenta que confirmar", vm.confirmarCuenta())
    }

    // --- Las acciones que salen de la app ------------------------------------

    @Test
    fun `marcar pide el telefono del cliente`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        vm.marcar()
        advanceUntilIdle()

        assertEquals(listOf("238 162 7597"), accionesExternas.marcados)
    }

    @Test
    fun `como llegar usa el punto del ultimo cobro cuando existe`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        pagosPort.pagos = listOf(
            PagosFixtures.pagoConUbicacion(lat = 18.46, lng = -97.39)
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.comoLlegar()
        advanceUntilIdle()

        val destino = accionesExternas.destinos.single()
        assertEquals(18.46, checkNotNull(destino.lat), 0.0)
        assertEquals(-97.39, checkNotNull(destino.lng), 0.0)
        assertEquals("C. Hidalgo 214, Centro", destino.direccion)
    }

    /**
     * Sin ningún abono con coordenadas, el mapa va con la dirección escrita — no
     * con un punto inventado.
     */
    @Test
    fun `sin coordenadas como llegar va con la direccion`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        vm.comoLlegar()
        advanceUntilIdle()

        val destino = accionesExternas.destinos.single()
        assertNull(destino.lat)
        assertNull(destino.lng)
        assertEquals("C. Hidalgo 214, Centro", destino.direccion)
    }

    /**
     * **Un teléfono sin WhatsApp no se traga el fallo.**
     *
     * **Control de reversión:** borrar el `onFailure` de
     * `DetalleClienteViewModel.correr` pone este test en ROJO.
     */
    @Test
    fun `si no se puede abrir la app de fuera, se reporta`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        accionesExternas.falla = IllegalStateException("no hay app")
        val vm = viewModel()
        advanceUntilIdle()

        vm.escribirPorWhatsApp()
        advanceUntilIdle()

        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ACCION_EXTERNA_FALLO
        }
        assertEquals(
            PagosTelemetria.ACCION_WHATSAPP,
            error.props[PagosTelemetria.PROP_ACCION]
        )
        assertEquals(
            "IllegalStateException",
            error.props[PagosTelemetria.PROP_EXCEPCION]
        )
        // Anti-PII: ni el teléfono ni el nombre del cliente viajan.
        val texto = error.name + error.props.entries.joinToString { it.key + it.value }
        assertTrue(texto, !texto.contains("238") && !texto.contains("Victoria"))
    }

    /** Control positivo: cuando SÍ abre, no se reporta nada. */
    @Test
    fun `cuando la app de fuera abre no se reporta nada`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        vm.escribirPorWhatsApp()
        advanceUntilIdle()

        assertTrue(
            telemetria.recorded.none {
                it.type == TelemetryEventType.ERROR &&
                    it.name == PagosTelemetria.CODE_ACCION_EXTERNA_FALLO
            }
        )
    }

    /** "Pídele hoy" llega derivado, no en cero. */
    @Test
    fun `el resumen trae pidele hoy derivado`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        val resumen = checkNotNull(vm.state.value.detalle).resumen
        assertTrue("pídele hoy tiene que salir derivado", resumen.pideleHoy > Money.ZERO)
        assertEquals(Money.of(BigDecimal("300.00")), resumen.promedioDeMicrosip)
        assertEquals(12, resumen.semanasTotales)
    }
}
