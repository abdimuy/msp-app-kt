package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.telemetry.TelemetryEventType
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
import com.example.msp_app.feature.pagos.domain.model.FichaDelCliente
import com.example.msp_app.feature.pagos.domain.model.SenalDeFicha
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * La ficha vista desde el ViewModel: qué se siembra al abrir, qué se guarda, y
 * los dos finales que no se pueden aplanar.
 *
 * El aserto que más carga: **con la ficha ilegible la hoja no abre**. Sin él,
 * el camino "leer falló → editar → guardar" borra conocimiento real, y ningún
 * otro test de esta clase lo vería.
 */
/*
 * Nota sobre los `advanceUntilIdle()` que acompañan a cada acción del borrador:
 * desde que `state` se DERIVA de los puertos de tema y privacidad (Ruling BQ),
 * la emisión pasa por el `stateIn` y con `StandardTestDispatcher` eso cuesta un
 * tick. En producción el colector vive en `Dispatchers.Main.immediate`, así que
 * la hoja abre en el mismo frame; el tick es del dispatcher de prueba, no del
 * comportamiento.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FichaEnElDetalleTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val fichaPort = FakeFichaPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = PagosFixtures.datosDeVentas()
        pagosPort.pagos = PagosFixtures.pagosDeLaVenta()
        liquidacionPort.liquidaciones = mapOf(
            PagosFixtures.VENTA_EN_PROMESA to PagosFixtures.liquidacionDeLaVenta()
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        accionesExternas = FakeAccionesExternasPort(),
        tema = FakeTemaDeLaAppPort(),
        privacidad = FakePrivacidadPort(),
        telemetry = telemetria,
        io = testDispatcher
    )

    private fun errores(code: String) =
        telemetria.recorded.filter { it.type == TelemetryEventType.ERROR && it.name == code }

    // --- Abrir ---------------------------------------------------------------

    @Test
    fun `la hoja abre sembrada con lo que ya estaba guardado`() = runTest(testDispatcher) {
        fichaPort.fichas[PagosFixtures.CLIENTE_ID] = FichaDelCliente(
            senales = setOf(SenalDeFicha.ESTA_EN_LA_NOCHE),
            nota = "atiende la suegra"
        )
        val vm = viewModel()
        advanceUntilIdle()

        vm.editarFicha()
        advanceUntilIdle()
        val edicion = checkNotNull(vm.state.value.edicionDeLaFicha)
        assertEquals(setOf(SenalDeFicha.ESTA_EN_LA_NOCHE), edicion.senales)
        assertEquals("atiende la suegra", edicion.nota)
        assertFalse(edicion.guardando)
    }

    @Test
    fun `sin ficha la hoja abre en blanco, pero abre`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()
        advanceUntilIdle()
        val edicion = checkNotNull(vm.state.value.edicionDeLaFicha)
        assertTrue(edicion.senales.isEmpty())
        assertEquals("", edicion.nota)
    }

    @Test
    fun `si la ficha NO se pudo leer la hoja no abre`() = runTest(testDispatcher) {
        fichaPort.seLee = false
        val vm = viewModel()
        advanceUntilIdle()
        assertNull("no se pudo leer, no es que no haya", vm.state.value.detalle?.ficha)

        vm.editarFicha()
        advanceUntilIdle()
        assertNull(
            "abrir aqui invitaria a guardar una ficha en blanco encima de la buena",
            vm.state.value.edicionDeLaFicha
        )

        // Control positivo: con la lectura sana, el MISMO toque sí abre.
        fichaPort.seLee = true
        vm.cargar()
        advanceUntilIdle()
        vm.editarFicha()
        advanceUntilIdle()
        assertNotNull(vm.state.value.edicionDeLaFicha)
    }

    @Test
    fun `un fallo de la ficha no tumba el detalle`() = runTest(testDispatcher) {
        fichaPort.seLee = false
        val vm = viewModel()
        advanceUntilIdle()
        val detalle = checkNotNull(vm.state.value.detalle)
        assertEquals("Victoria Flores Olmedo", detalle.nombre)
        assertEquals(2, detalle.ventas.size)
        assertNull(vm.state.value.error)
    }

    // --- Editar el borrador --------------------------------------------------

    @Test
    fun `marcar y desmarcar una senal solo toca el borrador`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()

        vm.alternarSenal(SenalDeFicha.ESTA_EN_LA_TARDE)
        advanceUntilIdle()
        assertEquals(
            setOf(SenalDeFicha.ESTA_EN_LA_TARDE),
            vm.state.value.edicionDeLaFicha?.senales
        )
        vm.alternarSenal(SenalDeFicha.ESTA_EN_LA_TARDE)
        advanceUntilIdle()
        assertTrue(checkNotNull(vm.state.value.edicionDeLaFicha).senales.isEmpty())

        assertTrue("nada se escribió", fichaPort.guardados.isEmpty())
    }

    @Test
    fun `cerrar descarta el borrador sin escribir nada`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()
        vm.escribirNota("hay perro")
        vm.cerrarFicha()

        assertNull(vm.state.value.edicionDeLaFicha)
        assertTrue(fichaPort.guardados.isEmpty())
        assertNull(vm.state.value.detalle?.ficha?.nota)
    }

    // --- Guardar -------------------------------------------------------------

    @Test
    fun `guardar escribe, cierra la hoja y deja pintado lo que quedo escrito`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()
        vm.alternarSenal(SenalDeFicha.ESTA_EN_LA_NOCHE)
        vm.escribirNota("  atiende la suegra  ")
        vm.guardarFicha()
        advanceUntilIdle()

        val (cliente, pedida) = fichaPort.guardados.single()
        assertEquals(PagosFixtures.CLIENTE_ID, cliente)
        assertEquals(setOf(SenalDeFicha.ESTA_EN_LA_NOCHE), pedida.senales)
        assertEquals("la nota se normaliza ANTES de la base", "atiende la suegra", pedida.nota)

        assertNull("la hoja se cierra", vm.state.value.edicionDeLaFicha)
        val ficha = checkNotNull(vm.state.value.detalle?.ficha)
        assertEquals("atiende la suegra", ficha.nota)
        assertEquals(setOf(SenalDeFicha.ESTA_EN_LA_NOCHE), ficha.senales)
        assertEquals(
            "se pinta el sello del puerto, no una fecha inventada por la pantalla",
            fichaPort.actualizadaEn,
            ficha.actualizada
        )
    }

    @Test
    fun `una nota en blanco se guarda como AUSENTE, no como cadena vacia`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()
        vm.escribirNota("   \n  ")
        vm.guardarFicha()
        advanceUntilIdle()

        assertNull(fichaPort.guardados.single().second.nota)
    }

    @Test
    fun `si el guardado falla la hoja NO se cierra y conserva lo escrito`() = runTest(
        testDispatcher
    ) {
        fichaPort.seGuarda = false
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()
        vm.escribirNota("hay perro")
        vm.alternarSenal(SenalDeFicha.ESTA_EN_LA_MANANA)
        vm.guardarFicha()
        advanceUntilIdle()

        val edicion = checkNotNull(vm.state.value.edicionDeLaFicha)
        assertTrue("se avisa el fallo", edicion.fallo)
        assertFalse(edicion.guardando)
        assertEquals("no se tira lo que el cobrador escribió", "hay perro", edicion.nota)
        assertEquals(setOf(SenalDeFicha.ESTA_EN_LA_MANANA), edicion.senales)
        assertNull("nada se pinta como guardado", vm.state.value.detalle?.ficha?.nota)
        assertEquals(1, errores(PagosTelemetria.CODE_FICHA_NO_QUEDO_GUARDADA).size)
    }

    @Test
    fun `tocar de nuevo despues de un fallo limpia el aviso`() = runTest(testDispatcher) {
        fichaPort.seGuarda = false
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()
        vm.guardarFicha()
        advanceUntilIdle()
        assertTrue(checkNotNull(vm.state.value.edicionDeLaFicha).fallo)

        vm.escribirNota("hay perro")
        advanceUntilIdle()
        assertFalse(checkNotNull(vm.state.value.edicionDeLaFicha).fallo)
    }

    @Test
    fun `un guardado exitoso no emite ningun error`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()
        vm.escribirNota("hay perro")
        vm.guardarFicha()
        advanceUntilIdle()
        assertTrue(errores(PagosTelemetria.CODE_FICHA_NO_QUEDO_GUARDADA).isEmpty())
    }

    @Test
    fun `dos toques al guardar escriben UNA vez`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.editarFicha()
        vm.escribirNota("hay perro")
        vm.guardarFicha()
        // El segundo toque llega ANTES de que el primero termine: es el doble
        // toque real de un teléfono lento, no una llamada de laboratorio.
        vm.guardarFicha()
        advanceUntilIdle()
        assertEquals(1, fichaPort.guardados.size)
    }

    @Test
    fun `sin hoja abierta ninguna accion de la ficha escribe`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.alternarSenal(SenalDeFicha.ESTA_EN_LA_NOCHE)
        vm.escribirNota("hay perro")
        vm.guardarFicha()
        advanceUntilIdle()
        assertTrue(fichaPort.guardados.isEmpty())
        assertNull(vm.state.value.edicionDeLaFicha)
    }

    private fun assertNotNull(valor: Any?) = assertTrue("se esperaba no nulo", valor != null)
}
