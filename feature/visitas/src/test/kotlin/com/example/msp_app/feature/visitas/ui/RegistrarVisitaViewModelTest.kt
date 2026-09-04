package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.visitas.application.AbrirRegistroDeVisita
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.data.fake.FakeComprobantesDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeContextoDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeRecomendacionesPort
import com.example.msp_app.feature.visitas.data.fake.FakeRegistroDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeUbicacionPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.model.BloqueoDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
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
 * El ViewModel de registrar visita.
 *
 * Lo que estas pruebas protegen, en orden:
 *
 * 1. **Nada se guarda dos veces** — doble toque y guard persistido.
 * 2. **Nada se guarda a medias** — el CTA no puede escribir una promesa sin fecha.
 * 3. **Cambiar de desenlace no arrastra compromisos** que el cliente no hizo.
 *
 * `StandardTestDispatcher` + `advanceUntilIdle()`, nunca `UnconfinedTestDispatcher`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por camino; juntarlas escondería cuál se rompió.
class RegistrarVisitaViewModelTest {

    /** Martes 1-sep-2026, 09:52 en zona de negocio — la hora del mock. */
    private val ahora = java.time.Instant.parse("2026-09-01T15:52:00Z")
    private val hoy = LocalDate.of(2026, 9, 1)

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(ahora)
    private val telemetria = RecordingTelemetry(clock)

    private val contextoPort = FakeContextoDeVisitaPort()
    private val recomendacionesPort = FakeRecomendacionesPort()
    private val registroPort = FakeRegistroDeVisitaPort()
    private var ubicacionPort = FakeUbicacionPort()
    private val camaraPort = FakeComprobantesDeVisitaPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(
        ventaId: Int? = VisitasFixtures.REFRIGERADOR,
        estado: SavedStateHandle = SavedStateHandle(
            mapOf(
                VisitasRutas.ARG_CLIENTE_ID to VisitasFixtures.VICTORIA,
                VisitasRutas.ARG_VENTA_ID to (ventaId ?: VisitasRutas.SIN_VENTA)
            )
        )
    ) = RegistrarVisitaViewModel(
        savedStateHandle = estado,
        abrirRegistro = AbrirRegistroDeVisita(contextoPort, recomendacionesPort, telemetria),
        registrarVisita = RegistrarVisita(registroPort, ubicacionPort, telemetria),
        camara = camaraPort,
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher
    )

    // ─── carga ───────────────────────────────────────────────────────────────

    @Test
    fun `arranca con el cliente cargado y el CTA apagado`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals("Victoria Flores Olmedo", state.contexto?.nombre)
        assertEquals(hoy, state.hoy)
        assertEquals(listOf(BloqueoDeLaVisita.SIN_RESULTADO), state.bloqueos)
        assertFalse("sin desenlace no se guarda nada", state.sePuedeGuardar)
        assertEquals("elige un resultado", state.razonDelBloqueo)
    }

    @Test
    fun `emite el screenView de la pantalla`() = runTest(testDispatcher) {
        viewModel()
        advanceUntilIdle()

        assertTrue(telemetria.recorded.any { it.name == VisitasTelemetria.PANTALLA })
    }

    @Test
    fun `sin cuentas del cliente la pantalla dice que no esta`() = runTest(testDispatcher) {
        contextoPort.contexto = null

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(ErrorDeLaVisita.CLIENTE_NO_ESTA, vm.state.value.error)
        assertFalse(vm.state.value.sePuedeGuardar)
    }

    @Test
    fun `un fallo al leer el cliente se reporta y ofrece reintento`() = runTest(testDispatcher) {
        val roto = FakeContextoDeVisitaPort(falla = IllegalStateException("room caido"))
        val vm = RegistrarVisitaViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(VisitasRutas.ARG_CLIENTE_ID to VisitasFixtures.VICTORIA)
            ),
            abrirRegistro = AbrirRegistroDeVisita(roto, recomendacionesPort, telemetria),
            registrarVisita = RegistrarVisita(registroPort, ubicacionPort, telemetria),
            camara = camaraPort,
            telemetry = telemetria,
            clock = clock,
            io = testDispatcher
        )
        advanceUntilIdle()

        assertEquals(ErrorDeLaVisita.FALLO_LA_CARGA, vm.state.value.error)
        val evento = telemetria.recorded.single {
            it.name == VisitasTelemetria.CODE_CONTEXTO_FALLO
        }
        assertEquals("IllegalStateException", evento.props[VisitasTelemetria.PROP_EXCEPCION])
    }

    // ─── la captura ──────────────────────────────────────────────────────────

    @Test
    fun `elegir un desenlace pone su etiqueta por defecto`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onResultado(ResultadoDeVisita.NO_ESTABA)

        assertEquals(TipoVisitaCatalogo.NO_SE_ENCONTRABA, vm.state.value.captura.etiqueta)
        assertTrue("no estaba se sostiene solo", vm.state.value.sePuedeGuardar)
    }

    @Test
    fun `una etiqueta ajena al desenlace se ignora`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.NO_ESTABA)

        vm.onEtiqueta(TipoVisitaCatalogo.FUE_GROSERO)

        assertEquals(TipoVisitaCatalogo.NO_SE_ENCONTRABA, vm.state.value.captura.etiqueta)
    }

    /**
     * Cambiar de desenlace **borra** la fecha, el monto y la hora: arrastrarlos
     * escribiría un compromiso que el cliente no hizo.
     */
    @Test
    fun `cambiar de desenlace limpia la promesa capturada`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.PROMETIO)
        vm.onFechaPromesa(hoy.plusDays(3))
        vm.onMontoPrometido(Money.of(BigDecimal("220")))

        vm.onResultado(ResultadoDeVisita.NO_ESTABA)

        assertNull(vm.state.value.captura.fechaPromesa)
        assertNull(vm.state.value.captura.montoPrometido)
    }

    /** La venta por la que se entró queda propuesta como destino de la promesa. */
    @Test
    fun `la promesa arranca apuntando a la venta por la que se entro`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onResultado(ResultadoDeVisita.PROMETIO)

        assertEquals(VisitasFixtures.REFRIGERADOR, vm.state.value.captura.ventaDeLaPromesa)
    }

    @Test
    fun `una promesa sin fecha deja el CTA apagado con su razon`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onResultado(ResultadoDeVisita.PROMETIO)

        assertFalse(vm.state.value.sePuedeGuardar)
        assertEquals("falta la fecha", vm.state.value.razonDelBloqueo)
    }

    @Test
    fun `una promesa con fecha pasada deja el CTA apagado y no escribe`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.PROMETIO)
        vm.onFechaPromesa(hoy.minusDays(1))

        vm.guardar()
        advanceUntilIdle()

        assertEquals("esa fecha ya pasó", vm.state.value.razonDelBloqueo)
        assertTrue("nada debio escribirse", registroPort.registradas.isEmpty())
    }

    /** El calendario no puede dejar caer una cita hacia atrás: el CTA la para. */
    @Test
    fun `una cita con dia pasado deja el CTA apagado y no escribe`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.CITA)
        vm.onFechaCita(hoy.minusDays(1))

        vm.guardar()
        advanceUntilIdle()

        assertEquals("esa fecha ya pasó", vm.state.value.razonDelBloqueo)
        assertTrue("nada debio escribirse", registroPort.registradas.isEmpty())
    }

    /** Y tampoco un año mal tecleado, que es como se llega a 2126 con dos toques. */
    @Test
    fun `un compromiso mas alla del horizonte deja el CTA apagado`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.PROMETIO)

        vm.onFechaPromesa(hoy.plusYears(2))

        assertFalse(vm.state.value.sePuedeGuardar)
        assertEquals("está demasiado lejos", vm.state.value.razonDelBloqueo)
    }

    @Test
    fun `una cita sin dia deja el CTA apagado`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onResultado(ResultadoDeVisita.CITA)
        vm.onHoraCita(LocalTime.of(16, 0))

        assertFalse(vm.state.value.sePuedeGuardar)
        assertEquals("falta el día", vm.state.value.razonDelBloqueo)
    }

    @Test
    fun `una cita con dia y sin hora si se guarda`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.CITA)
        vm.onFechaCita(hoy.plusDays(2))

        vm.guardar()
        advanceUntilIdle()

        val visita = registroPort.registradas.single()
        assertEquals(hoy.plusDays(2), visita.cita?.fecha)
        assertNull(visita.cita?.hora)
    }

    /** Volver a la lista de desenlaces borra lo capturado bajo el anterior. */
    @Test
    fun `volver a la lista limpia la promesa capturada`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.PROMETIO)
        vm.onFechaPromesa(hoy.plusDays(3))

        vm.limpiarResultado()

        assertNull(vm.state.value.captura.resultado)
        assertNull(vm.state.value.captura.fechaPromesa)
        assertEquals(listOf(BloqueoDeLaVisita.SIN_RESULTADO), vm.state.value.bloqueos)
    }

    // ─── el guardado ─────────────────────────────────────────────────────────

    @Test
    fun `guardar escribe la promesa estructurada y deja la pantalla en su final`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.PROMETIO)
            vm.onFechaPromesa(hoy.plusDays(3))
            vm.onMontoPrometido(Money.of(BigDecimal("220")))

            vm.guardar()
            advanceUntilIdle()

            val visita = registroPort.registradas.single()
            assertEquals(vm.visitaId, visita.visitaId)
            assertEquals(hoy.plusDays(3), visita.promesa?.fecha)
            assertEquals(Money.of(BigDecimal("220")), visita.promesa?.monto)
            assertEquals(vm.visitaId, vm.state.value.registrada)
            assertFalse("ya no se puede capturar", vm.state.value.sePuedeCapturar)
        }

    /** **Doble toque:** el guard es sincrónico, así que solo entra una escritura. */
    @Test
    fun `un doble toque solo escribe una vez`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.NO_ESTABA)

        vm.guardar()
        vm.guardar()
        advanceUntilIdle()

        assertEquals(1, registroPort.registradas.size)
    }

    /** El id de la visita sobrevive: es la clave de idempotencia del envío. */
    @Test
    fun `el id de la visita sobrevive al SavedStateHandle`() = runTest(testDispatcher) {
        val estado = SavedStateHandle(
            mapOf(VisitasRutas.ARG_CLIENTE_ID to VisitasFixtures.VICTORIA)
        )
        val primero = viewModel(estado = estado)
        advanceUntilIdle()
        val id = primero.visitaId

        val segundo = viewModel(estado = estado)
        advanceUntilIdle()

        assertEquals("el mismo destino conserva el mismo id", id, segundo.visitaId)
    }

    @Test
    fun `un fallo del puerto deja el fallo a la vista y libera el reintento`() =
        runTest(testDispatcher) {
            registroPort.resultado = ResultadoDelRegistro.SIN_COBRADOR
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)

            vm.guardar()
            advanceUntilIdle()

            assertEquals(FalloDeLaVisita.SIN_COBRADOR, vm.state.value.fallo)
            assertNull(vm.state.value.registrada)
            assertTrue("el reintento tiene que estar vivo", vm.state.value.sePuedeGuardar)
            val evento = telemetria.recorded.single {
                it.name == VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO
            }
            assertEquals(
                ResultadoDelRegistro.SIN_COBRADOR.name,
                evento.props[VisitasTelemetria.PROP_RESULTADO]
            )
        }

    /** El reintento reusa el MISMO id: no puede convertirse en una segunda visita. */
    @Test
    fun `el reintento va con el mismo id`() = runTest(testDispatcher) {
        registroPort.resultado = ResultadoDelRegistro.FALLO_EL_GUARDADO
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.NO_ESTABA)
        vm.guardar()
        advanceUntilIdle()

        registroPort.resultado = ResultadoDelRegistro.REGISTRADA
        vm.guardar()
        advanceUntilIdle()

        assertEquals(2, registroPort.registradas.size)
        assertEquals(
            registroPort.registradas.first().visitaId,
            registroPort.registradas.last().visitaId
        )
    }

    // ─── la recomendación ────────────────────────────────────────────────────

    /**
     * **La recomendación mostrada viaja junto al desenlace.** Sin esta atadura,
     * el recomendador nunca se puede evaluar.
     */
    @Test
    fun `la recomendacion mostrada se guarda junto al desenlace`() = runTest(testDispatcher) {
        recomendacionesPort.recomendacion = VisitasFixtures.recomendacion()
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals("rec-victoria-1", vm.state.value.recomendacion?.recomendacionId)

        vm.onResultado(ResultadoDeVisita.NO_ESTABA)
        vm.guardar()
        advanceUntilIdle()

        assertEquals("rec-victoria-1", registroPort.registradas.single().recomendacionId)
    }

    /**
     * Control positivo: sin recomendación, no se inventa ninguna. Sin esto, la
     * prueba de arriba no distinguiría "se ató la que había" de "siempre ata algo".
     */
    @Test
    fun `sin recomendacion no se ata ninguna`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.NO_ESTABA)

        vm.guardar()
        advanceUntilIdle()

        assertNull(registroPort.registradas.single().recomendacionId)
    }

    // ─── los diálogos ────────────────────────────────────────────────────────

    @Test
    fun `el calendario deja el dia en la promesa y se cierra`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.PROMETIO)
        vm.abrirCalendario()
        assertTrue(vm.state.value.eligiendoDia)

        vm.onDiaDelCalendario(hoy.plusDays(9))

        assertEquals(hoy.plusDays(9), vm.state.value.captura.fechaPromesa)
        assertFalse(vm.state.value.eligiendoDia)
    }

    @Test
    fun `el calendario deja el dia en la cita cuando el desenlace es cita`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.CITA)
            vm.abrirCalendario()

            vm.onDiaDelCalendario(hoy.plusDays(4))

            assertEquals(hoy.plusDays(4), vm.state.value.captura.fechaCita)
            assertNull(vm.state.value.captura.fechaPromesa)
        }

    @Test
    fun `el reloj deja la hora en la cita y se cierra`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.CITA)
        vm.abrirReloj()

        vm.onHoraDelReloj(LocalTime.of(17, 30))

        assertEquals(LocalTime.of(17, 30), vm.state.value.captura.horaCita)
        assertFalse(vm.state.value.eligiendoHora)
    }

    /** Con la visita registrada, ningún control captura nada más. */
    @Test
    fun `registrada la visita, la captura queda muerta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.NO_ESTABA)
        vm.guardar()
        advanceUntilIdle()

        vm.onNota("otra cosa")
        vm.abrirCalendario()

        assertEquals("", vm.state.value.captura.nota)
        assertFalse(vm.state.value.eligiendoDia)
    }
}
