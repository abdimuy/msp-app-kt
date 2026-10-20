package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.telemetry.Telemetry
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.visitas.application.AbrirRegistroDeVisita
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.data.fake.DictadoFalso
import com.example.msp_app.feature.visitas.data.fake.FakeComprobantesDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeContextoDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeRecomendacionesPort
import com.example.msp_app.feature.visitas.data.fake.FakeRegistroDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.visitas.data.fake.FakeUbicacionPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.model.CapturaDeVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import java.time.Instant
import java.time.LocalDate
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * **E-VIS-074: una telemetría que lanza no tumba la captura de la visita.**
 *
 * `DurableTelemetry.error` inserta en Room de forma síncrona (`runBlocking`) y
 * puede lanzar. Cada prueba arma la pantalla (o el caso de uso) con una
 * telemetría cuyo `error()` lanza SIEMPRE, dispara UN camino que reporta y
 * afirma dos cosas:
 *
 * - **Control positivo:** la telemetría sí se pidió (`lanzadas >= 1`). Sin eso,
 *   una prueba verde podría ser un camino que ya no reporta nada.
 * - **El desenlace normal:** la excepción no sale de `runTest` (en el teléfono
 *   sería un crash del `viewModelScope`) y la pantalla queda como quedaría con
 *   una telemetría sana.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por llamada de telemetría.
class TelemetriaQueLanzaNoTumbaLaVisitaTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(Instant.parse("2026-09-01T15:52:00Z"))
    private val sana = RecordingTelemetry(clock)
    private val lanza = TelemetriaQueSiempreLanza(sana)

    private val registroPort = FakeRegistroDeVisitaPort()
    private val camaraPort = FakeComprobantesDeVisitaPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── el caso de uso ──────────────────────────────────────────────────────

    @Test
    fun `E-VIS-074 caso de uso captura bloqueada con telemetria que lanza contesta fallo`() =
        runTest(testDispatcher) {
            val resultado = RegistrarVisita(registroPort, FakeUbicacionPort(), lanza).invoke(
                visitaId = "visita-1",
                clienteId = VisitasFixtures.VICTORIA,
                ventaId = VisitasFixtures.REFRIGERADOR,
                captura = CapturaDeVisita(
                    resultado = ResultadoDeVisita.PROMETIO,
                    etiqueta = TipoVisitaCatalogo.PIDE_REAGENDAR,
                    cuentas = setOf(VisitasFixtures.REFRIGERADOR),
                    fechaPromesa = HOY.minusDays(1)
                ),
                hoy = HOY
            )

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
            assertTrue(registroPort.registradas.isEmpty())
        }

    @Test
    fun `E-VIS-074 caso de uso ubicacion que lanza con telemetria que lanza registra igual`() =
        runTest(testDispatcher) {
            val resultado = registrarNoEstaba(
                FakeUbicacionPort(falla = IllegalStateException("play services caido"))
            )

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
            assertNull(registroPort.registradas.single().ubicacion)
        }

    @Test
    fun `E-VIS-074 caso de uso ubicacion nula con telemetria que lanza registra igual`() =
        runTest(testDispatcher) {
            val resultado = registrarNoEstaba(FakeUbicacionPort(ubicacion = null))

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(ResultadoDelRegistro.REGISTRADA, resultado)
            assertEquals(1, registroPort.registradas.size)
        }

    @Test
    fun `E-VIS-074 caso de uso parcial con telemetria que lanza contesta el fallo`() =
        runTest(testDispatcher) {
            registroPort.fallaEn = 1
            val escritas = mutableListOf<Unit>()

            val resultado = RegistrarVisita(registroPort, FakeUbicacionPort(), lanza).invoke(
                visitaId = "visita-1",
                clienteId = VisitasFixtures.VICTORIA,
                ventaId = null,
                captura = CapturaDeVisita(
                    resultado = ResultadoDeVisita.VISITE_VUELVO,
                    etiqueta = CatalogoDeResultados.etiquetaPorDefecto(
                        ResultadoDeVisita.VISITE_VUELVO
                    ),
                    cuentas = setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)
                ),
                hoy = HOY,
                alEscribir = { escritas += Unit }
            )

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(ResultadoDelRegistro.FALLO_EL_GUARDADO, resultado)
            assertEquals(1, escritas.size)
        }

    // ─── la pantalla ─────────────────────────────────────────────────────────

    @Test
    fun `E-VIS-074 pantalla aplicar un fallo con telemetria que lanza suelta guardando`() =
        runTest(testDispatcher) {
            registroPort.resultado = ResultadoDelRegistro.FALLO_EL_GUARDADO
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)
            vm.guardar()
            advanceUntilIdle()

            assertTrue(lanza.lanzadas >= 1)
            assertFalse(vm.state.value.guardando)
            assertNotNull(vm.state.value.fallo)
        }

    @Test
    fun `E-VIS-074 pantalla la carga que falla con telemetria que lanza pinta el error`() =
        runTest(testDispatcher) {
            val vm = viewModel(
                contexto = FakeContextoDeVisitaPort(falla = IllegalStateException("room"))
            )
            advanceUntilIdle()

            assertTrue(lanza.lanzadas >= 1)
            assertFalse(vm.state.value.cargando)
            assertEquals(ErrorDeLaVisita.FALLO_LA_CARGA, vm.state.value.error)
        }

    @Test
    fun `E-VIS-074 pantalla la recomendacion que falla con telemetria que lanza abre igual`() =
        runTest(testDispatcher) {
            val vm = viewModel(
                recomendaciones = FakeRecomendacionesPort(falla = IllegalStateException("room"))
            )
            advanceUntilIdle()

            assertTrue(lanza.lanzadas >= 1)
            assertNull(vm.state.value.error)
            assertNotNull(vm.state.value.contexto)
        }

    @Test
    fun `E-VIS-074 pantalla una entrada ilegible del handle con telemetria que lanza se descarta`() =
        runTest(testDispatcher) {
            val handle = handle().also {
                it[CLAVE_COMPROBANTES] =
                    arrayListOf("basura-sin-campos", "IMG-9\nimage/jpeg\n/f.jpg")
            }
            val vm = viewModel(handle)
            advanceUntilIdle()

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(listOf("IMG-9"), vm.state.value.comprobantes.map { it.id })
        }

    @Test
    fun `E-VIS-074 pantalla una foto sin destino con telemetria que lanza no truena`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()

            vm.fotoTomada()
            advanceUntilIdle()

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(emptyList<Any>(), vm.state.value.comprobantes)
        }

    @Test
    fun `E-VIS-074 pantalla la camara que falla con telemetria que lanza pinta ambar y registra`() =
        runTest(testDispatcher) {
            camaraPort.fallaAlPreparar = IllegalStateException("no hay camara")
            val vm = viewModel()
            advanceUntilIdle()
            vm.onOrigen(OrigenDeLaFoto.CAMARA)
            advanceUntilIdle()

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(
                listOf(FalloDeLaFoto.NO_SE_PUDO_TOMAR),
                vm.state.value.intentos.map { it.motivo }
            )
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)
            vm.guardar()
            advanceUntilIdle()
            assertEquals(vm.visitaId, vm.state.value.registrada)
        }

    @Test
    fun `E-VIS-074 pantalla un tipo no permitido con telemetria que lanza se rechaza igual`() =
        runTest(testDispatcher) {
            camaraPort.mimeAceptado = "image/heic"
            val vm = viewModel()
            advanceUntilIdle()
            tomarFoto(vm)

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(
                listOf(FalloDeLaFoto.TIPO_NO_PERMITIDO),
                vm.state.value.intentos.map { it.motivo }
            )
            assertEquals(1, camaraPort.descartados.size)
        }

    @Test
    fun `E-VIS-074 pantalla una miniatura que revienta con telemetria que lanza deja la foto`() =
        runTest(testDispatcher) {
            camaraPort.fallaAlPedirMiniatura = OutOfMemoryError("no hay heap")
            val vm = viewModel()
            advanceUntilIdle()
            tomarFoto(vm)

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(1, vm.state.value.comprobantes.size)
        }

    @Test
    fun `E-VIS-074 pantalla una foto que llega tarde con telemetria que lanza no truena`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)
            vm.onOrigen(OrigenDeLaFoto.CAMARA)
            advanceUntilIdle()
            vm.fotoTomada()
            vm.guardar()
            advanceUntilIdle()

            assertTrue(lanza.lanzadas >= 1)
            assertEquals(vm.visitaId, vm.state.value.registrada)
            assertEquals(
                listOf(FalloDeLaFoto.LLEGO_TARDE),
                vm.state.value.intentos.map { it.motivo }
            )
            assertTrue(camaraPort.descartados.isNotEmpty())
        }

    // ─── utilidades ──────────────────────────────────────────────────────────

    private suspend fun registrarNoEstaba(ubicacion: FakeUbicacionPort) =
        RegistrarVisita(registroPort, ubicacion, lanza).invoke(
            visitaId = "visita-1",
            clienteId = VisitasFixtures.VICTORIA,
            ventaId = VisitasFixtures.REFRIGERADOR,
            captura = CapturaDeVisita(
                resultado = ResultadoDeVisita.NO_ESTABA,
                etiqueta = CatalogoDeResultados.etiquetaPorDefecto(ResultadoDeVisita.NO_ESTABA),
                cuentas = emptySet()
            ),
            hoy = HOY
        )

    private fun tomarFoto(vm: RegistrarVisitaViewModel) {
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.fotoTomada()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun handle() = SavedStateHandle(
        mapOf(
            VisitasRutas.ARG_CLIENTE_ID to VisitasFixtures.VICTORIA,
            VisitasRutas.ARG_VENTA_ID to VisitasFixtures.REFRIGERADOR
        )
    )

    private fun viewModel(
        savedStateHandle: SavedStateHandle = handle(),
        contexto: FakeContextoDeVisitaPort = FakeContextoDeVisitaPort(),
        recomendaciones: FakeRecomendacionesPort = FakeRecomendacionesPort()
    ) = RegistrarVisitaViewModel(
        savedStateHandle = savedStateHandle,
        abrirRegistro = AbrirRegistroDeVisita(contexto, recomendaciones, lanza),
        registrarVisita = RegistrarVisita(registroPort, FakeUbicacionPort(), lanza),
        camara = camaraPort,
        dictado = DictadoFalso(),
        tema = FakeTemaDeLaAppPort(),
        telemetry = lanza,
        clock = clock,
        io = testDispatcher
    )

    /** `DurableTelemetry.error` cuyo insert de Room falla siempre. */
    private class TelemetriaQueSiempreLanza(
        private val real: RecordingTelemetry
    ) : Telemetry by real {
        var lanzadas = 0

        override fun error(code: String, message: String, props: Map<String, String>) {
            lanzadas++
            throw IllegalStateException("la cola de telemetria no pudo insertar")
        }
    }

    private companion object {
        val HOY: LocalDate = LocalDate.of(2026, 9, 1)

        /** Misma clave que el ViewModel; privada allá. */
        const val CLAVE_COMPROBANTES = "visitas_comprobantes"
    }
}
