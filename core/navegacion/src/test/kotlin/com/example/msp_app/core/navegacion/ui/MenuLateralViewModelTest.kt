package com.example.msp_app.core.navegacion.ui

import com.example.msp_app.core.navegacion.application.EstadoDelMenu
import com.example.msp_app.core.navegacion.application.NavegacionTelemetria
import com.example.msp_app.core.navegacion.application.TogglesDelMenu
import com.example.msp_app.core.navegacion.domain.model.EstadoDeEnvio
import com.example.msp_app.core.navegacion.fake.FakeContadoresDelMenuPort
import com.example.msp_app.core.navegacion.fake.FakeEstadoDeEnvioPort
import com.example.msp_app.core.navegacion.fake.FakePrivacidadDelMenuPort
import com.example.msp_app.core.navegacion.fake.FakeSeccionesPlegadasPort
import com.example.msp_app.core.navegacion.fake.FakeTemaDelMenuPort
import com.example.msp_app.core.navegacion.fake.FakeZonaDelUsuarioPort
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MenuLateralViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val envio = FakeEstadoDeEnvioPort()
    private val contadores = FakeContadoresDelMenuPort()
    private val plegadas = FakeSeccionesPlegadasPort()
    private val zona = FakeZonaDelUsuarioPort()
    private val tema = FakeTemaDelMenuPort()
    private val privacidad = FakePrivacidadDelMenuPort()
    private val telemetria = RecordingTelemetry(FakeClock())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = MenuLateralViewModel(
        contadores,
        envio,
        plegadas,
        zona,
        TogglesDelMenu(tema, privacidad),
        telemetria
    )

    private fun errores(code: String) =
        telemetria.recorded.filter { it.type == TelemetryEventType.ERROR && it.name == code }

    @Test
    fun `antes de la primera lectura el envio es null, nunca todo enviado`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertNull(vm.estado.value.envio)
    }

    @Test
    fun `combina envio y plegadas en un solo estado`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        envio.flujo.emit(
            EstadoDeEnvio(pagosPendientes = 2, ventasPendientes = 0, ultimoEnvioHaceMinutos = null)
        )
        plegadas.valores.value = setOf("VENTAS")
        advanceUntilIdle()

        assertEquals(
            EstadoDelMenu(
                envio = EstadoDeEnvio(
                    pagosPendientes = 2,
                    ventasPendientes = 0,
                    ultimoEnvioHaceMinutos = null
                ),
                plegadas = setOf("VENTAS"),
                contadores = emptyMap(),
                nombreDeZona = null,
                temaOscuro = false,
                montosOcultos = false
            ),
            vm.estado.value
        )
    }

    @Test
    fun `abrir pide los contadores de las opciones visibles y los publica`() = runTest(dispatcher) {
        contadores.respuesta = mapOf("clientes" to "8 por volver", "ventas" to "1 sin subir")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }

        vm.alAbrir(setOf("inicio", "clientes"))
        advanceUntilIdle()

        assertEquals(listOf(setOf("inicio", "clientes")), contadores.pedidos)
        assertEquals(mapOf("clientes" to "8 por volver"), vm.estado.value.contadores)
    }

    @Test
    fun `si los contadores fallan el menu queda sin contadores y lo reporta`() = runTest(
        dispatcher
    ) {
        contadores.falla = IllegalStateException("room cerrada")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }

        vm.alAbrir(setOf("clientes"))
        advanceUntilIdle()

        assertEquals(emptyMap<String, String>(), vm.estado.value.contadores)
        val error = errores(NavegacionTelemetria.CODE_CONTADORES_FALLARON).single()
        assertEquals("IllegalStateException", error.props["excepcion"])
    }

    @Test
    fun `si la fuente de envio falla se reporta y la tarjeta no se pinta`() = runTest(dispatcher) {
        envio.falla = IOException("disco")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertNull(vm.estado.value.envio)
        assertEquals(1, errores(NavegacionTelemetria.CODE_ENVIO_FALLO).size)
    }

    @Test
    fun `cambiar plegado escribe la preferencia`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }

        vm.cambiarPlegado("VENTAS", plegar = true)
        advanceUntilIdle()

        assertEquals(listOf("VENTAS" to true), plegadas.escrituras)
        assertEquals(setOf("VENTAS"), vm.estado.value.plegadas)
    }

    @Test
    fun `la cabecera toma el nombre de zona que entrega el puerto`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        zona.flujo.emit("R/25")
        advanceUntilIdle()

        assertEquals("R/25", vm.estado.value.nombreDeZona)
    }

    @Test
    fun `sin zona el estado no inventa un nombre`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertNull(vm.estado.value.nombreDeZona)
    }

    @Test
    fun `si la zona falla se reporta y queda sin nombre`() = runTest(dispatcher) {
        zona.falla = IOException("room cerrada")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertNull(vm.estado.value.nombreDeZona)
        assertEquals(1, errores(NavegacionTelemetria.CODE_ZONA_FALLO).size)
    }

    @Test
    fun `alternarTema invierte el tema global y se refleja en el estado`() = runTest(dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        vm.alternarTema()
        advanceUntilIdle()

        assertEquals(1, tema.alternaciones)
        assertTrue(vm.estado.value.temaOscuro)
    }

    @Test
    fun `si el tema falla al leerlo se reporta y conserva el valor sincrono`() = runTest(
        dispatcher
    ) {
        tema.falla = IOException("preferencias corruptas")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertFalse(vm.estado.value.temaOscuro)
        assertEquals(1, errores(NavegacionTelemetria.CODE_TEMA_FALLO).size)
    }

    @Test
    fun `alternarMontos invierte los montos ocultos y se refleja en el estado`() = runTest(
        dispatcher
    ) {
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        vm.alternarMontos()
        advanceUntilIdle()

        assertEquals(1, privacidad.alternaciones)
        assertTrue(vm.estado.value.montosOcultos)
    }

    @Test
    fun `si los montos fallan al leerse se reporta y mantiene el valor sincrono`() = runTest(
        dispatcher
    ) {
        privacidad.falla = IOException("datastore caido")
        val vm = viewModel()
        backgroundScope.launch { vm.estado.collect {} }
        advanceUntilIdle()

        assertFalse(vm.estado.value.montosOcultos)
        assertEquals(1, errores(NavegacionTelemetria.CODE_MONTOS_FALLO).size)
    }
}
