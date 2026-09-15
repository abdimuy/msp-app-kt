package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.MainDispatcherRule
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarBitacoraDelCliente
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * **La bitácora completa.**
 *
 * Lo que defiende: que la pantalla que reemplaza al "⋯" enseñe **todo** lo que
 * pasó en el domicilio —no los tres del detalle—, que mezcle visitas y abonos en
 * una sola línea de tiempo, y que un fallo de lectura se reporte en vez de
 * quedarse en blanco.
 */
class BitacoraViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry()
    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()

    private fun viewModel(clienteId: Int = PagosFixtures.CLIENTE_ID) = BitacoraViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PagosRutas.ARG_CLIENTE_ID to clienteId)),
        cargarBitacoraDelCliente = CargarBitacoraDelCliente(
            ReunirCobranzaDelCliente(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                liquidacionPort = FakeLiquidacionPort(),
                resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
            )
        ),
        privacidad = FakePrivacidadPort(),
        telemetry = telemetria,
        io = testDispatcher
    )

    /**
     * **Todo, no los tres del detalle.** El detalle recorta a
     * `BitacoraDelCliente.VISIBLES_EN_EL_DETALLE`; ésta es la pantalla que existe
     * justamente para enseñar el resto.
     */
    @Test
    fun `trae todos los contactos, no solo los del detalle`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        visitasPort.visitas = listOf(
            PagosFixtures.visita("No estaba", fechaIso = "2026-09-01T16:00:00Z"),
            PagosFixtures.visita("Se negó a pagar", fechaIso = "2026-08-25T16:00:00Z"),
            PagosFixtures.visita("Pidió reagendar visita", fechaIso = "2026-08-18T16:00:00Z"),
            PagosFixtures.visita("No estaba", fechaIso = "2026-08-11T16:00:00Z")
        )
        pagosPort.pagos = PagosFixtures.pagosDeLaVenta()

        val vm = viewModel()
        advanceUntilIdle()

        val contactos = checkNotNull(vm.state.value.bitacora).contactos
        assertEquals(4 + pagosPort.pagos.size, contactos.size)
        assertTrue(
            "tiene que traer más de los tres que pinta el detalle",
            contactos.size > 3
        )
    }

    /** Visitas y abonos van MEZCLADOS y del más reciente al más viejo. */
    @Test
    fun `visitas y abonos van en una sola linea de tiempo`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        visitasPort.visitas = listOf(
            PagosFixtures.visita("No estaba", fechaIso = "2026-09-01T16:00:00Z")
        )
        pagosPort.pagos = PagosFixtures.pagosDeLaVenta()

        val vm = viewModel()
        advanceUntilIdle()

        val contactos = checkNotNull(vm.state.value.bitacora).contactos
        assertEquals(
            "tiene que venir ordenada de lo más reciente a lo más viejo",
            contactos.map { it.fecha }.sortedDescending(),
            contactos.map { it.fecha }
        )
        assertTrue("faltan las visitas", contactos.any { it.etiqueta == "no estaba" })
        assertTrue("faltan los abonos", contactos.any { it.etiqueta == "cobré" })
    }

    /** El nombre viaja con la bitácora: la pantalla se abre sola y tiene que titularse. */
    @Test
    fun `la bitacora sabe de quien es`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Victoria Flores Olmedo", checkNotNull(vm.state.value.bitacora).nombre)
    }

    /** Un cliente que el teléfono no tiene se dice, no se pinta como "nunca pasó nada". */
    @Test
    fun `un cliente que no esta en el telefono no es una bitacora vacia`() =
        runTest(testDispatcher) {
            val vm = viewModel(clienteId = 999)
            advanceUntilIdle()

            assertNull(vm.state.value.bitacora)
            assertEquals(ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO, vm.state.value.error)
        }

    /**
     * **Un fallo de lectura no se traga.**
     *
     * **Control de reversión:** borrar el `telemetry.error(...)` del `catch` de
     * `BitacoraViewModel.leer` pone este test en ROJO.
     */
    @Test
    fun `si la lectura falla se reporta`() = runTest(testDispatcher) {
        ventasPort.falla = IllegalStateException("Room se cayó")

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ErrorDeDetalle.FALLO_LA_CARGA, vm.state.value.error)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == PagosTelemetria.CODE_BITACORA_FALLO
        }
        assertEquals(
            "IllegalStateException",
            error.props[PagosTelemetria.PROP_EXCEPCION]
        )
        // Anti-PII: el id del cliente no viaja.
        assertTrue(error.props.values.none { it.contains(PagosFixtures.CLIENTE_ID.toString()) })
    }
}
