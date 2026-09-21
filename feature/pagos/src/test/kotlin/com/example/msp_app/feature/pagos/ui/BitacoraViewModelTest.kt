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
import com.example.msp_app.feature.pagos.data.fake.FakeProductosPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.FiltroDeContactos
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
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
            reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                liquidacionPort = FakeLiquidacionPort(),
                resolverVentanaDeCobro = ResolverVentanaDeCobro(FakePeriodoDeCobroPort(), clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
            ),
            productosPort = FakeProductosPort()
        ),
        privacidad = FakePrivacidadPort(),
        tema = FakeTemaDeLaAppPort(),
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
        // Mayúscula inicial, tal como llega del catálogo cerrado — la mezcla ya
        // no fuerza `.lowercase()` (Task 1, principio 10).
        assertTrue("faltan las visitas", contactos.any { it.etiqueta == "No estaba" })
        assertTrue("faltan los abonos", contactos.any { it.etiqueta == "Cobré" })
    }

    /** El nombre viaja con la bitácora: la pantalla se abre sola y tiene que titularse. */
    @Test
    fun `la bitacora sabe de quien es`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("Victoria Flores Olmedo", checkNotNull(vm.state.value.bitacora).nombre)
    }

    /**
     * **Y de qué puerta es.** La dirección no la pinta esta pantalla: la pinta el
     * mapa que se abre al tocar un renglón, al pie, para decir de qué puerta se
     * trata. Sale de la misma fila representante del cliente de la que ya salía el
     * nombre, así que no cuesta una lectura más.
     */
    @Test
    fun `la bitacora sabe de que puerta es`() = runTest(testDispatcher) {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            "sin dirección, el mapa de un contacto se abre sin decir de quién es la puerta",
            "C. Hidalgo 214, Centro",
            checkNotNull(vm.state.value.bitacora).direccion
        )
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

    // --- El filtro vive sobre lo cargado -------------------------------------

    /**
     * **La pastilla cambia lo que se enseña, y NADA más.**
     *
     * El hueco que esto cierra: `LaLineaDiceQuienComoYCuandoTest` prueba la
     * pastilla con el estado en el propio test, así que una `BitacoraScreen`
     * que pasara `onFiltrar = {}` dejaría todo en verde. Aquí se cobra el otro
     * extremo del cable: que `filtrar(...)` llegue a `state.filtro`.
     *
     * Y se cobra **la mitad que cuesta dinero**: que filtrar no dispare otra
     * lectura. El filtro es una decisión de qué mirar sobre lo que ya está en
     * memoria; si rearmara la bitácora, cada toque de pastilla sería una vuelta
     * a Room —cuatro puertos— en un teléfono de gama baja parado en una puerta,
     * y además la lista parpadearía en blanco entre `cargando = true` y el
     * resultado. Se mide con los contadores de los fakes, no con el reloj.
     *
     * El `TODOS` de arranque se afirma antes de tocar nada: sin eso, un
     * `state.filtro` clavado en `COBROS` pasaría la aserción de abajo sin que
     * `filtrar` hiciera nada.
     */
    @Test
    fun `filtrar cambia el filtro del estado y no vuelve a leer nada`() = runTest(
        testDispatcher
    ) {
        sembrarLaBitacora()
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            "el arranque ya no es TODOS, así que la aserción de abajo no prueba nada",
            FiltroDeContactos.TODOS,
            vm.state.value.filtro
        )
        val lecturasDeLaCarga = lecturas()
        val cargado = checkNotNull(vm.state.value.bitacora)

        vm.filtrar(FiltroDeContactos.COBROS)
        advanceUntilIdle()

        assertEquals(FiltroDeContactos.COBROS, vm.state.value.filtro)
        assertEquals(
            "filtrar volvió a leer: eran $lecturasDeLaCarga consultas después de cargar y " +
                "ahora son ${lecturas()}. El filtro tiene que vivir sobre lo ya cargado, " +
                "no dispararle otra consulta a Room a cada toque de pastilla",
            lecturasDeLaCarga,
            lecturas()
        )
        assertSame(
            "filtrar rearmó la bitácora: el estado trae otro objeto, así que la pantalla " +
                "se recompuso entera en vez de sólo cambiar qué se enseña",
            cargado,
            vm.state.value.bitacora
        )
    }

    /**
     * **Control positivo del de arriba.** El mismo [lecturas] sí se mueve
     * cuando una recarga de verdad ocurre. Sin esto, la ausencia de arriba no
     * prueba nada: unos contadores que no contaran —o unos fakes que nadie
     * tocara— darían la misma cifra con y sin recarga, y el test se quedaría
     * verde para siempre.
     */
    @Test
    fun `control positivo - una recarga de verdad si mueve los contadores`() = runTest(
        testDispatcher
    ) {
        sembrarLaBitacora()
        val vm = viewModel()
        advanceUntilIdle()

        val lecturasDeLaCarga = lecturas()
        vm.cargar()
        advanceUntilIdle()

        assertTrue(
            "los contadores no detectan ni una recarga explícita ($lecturasDeLaCarga antes, " +
                "${lecturas()} después): el test de arriba no está midiendo nada",
            lecturas() > lecturasDeLaCarga
        )
    }

    /**
     * Las consultas que la bitácora le hace al teléfono, sumadas. Son **listas
     * que graban**, no las semillas: cada elemento es una llamada que de verdad
     * ocurrió.
     */
    private fun lecturas(): Int = ventasPort.clientesConsultados.size +
        visitasPort.clientesConsultados.size +
        pagosPort.ventasConsultadas.size

    /** La misma semilla de los tests de arriba: cobros y visitas mezclados. */
    private fun sembrarLaBitacora() {
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        visitasPort.visitas = listOf(
            PagosFixtures.visita("No estaba", fechaIso = "2026-09-01T16:00:00Z"),
            PagosFixtures.visita("Pidió reagendar visita", fechaIso = "2026-08-18T16:00:00Z")
        )
        pagosPort.pagos = PagosFixtures.pagosDeLaVenta()
    }
}
