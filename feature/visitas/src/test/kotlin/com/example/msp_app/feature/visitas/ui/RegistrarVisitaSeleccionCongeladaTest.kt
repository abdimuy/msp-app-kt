package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
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
import com.example.msp_app.feature.visitas.domain.IdsDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
 * **E-VIS-055: una foto nunca cambia de visita.**
 *
 * La foto cuelga del ANCLA —la visita de la cuenta más baja— y su fila en
 * `visita_imagenes` se escribe con `REPLACE` sobre el id de la FOTO. Si entre un
 * guardado parcial y su reintento cambian las cuentas, cambia el ancla, y el
 * `REPLACE` muda la misma foto a otra visita: es el productor real de E-VIS-051
 * (y por él, de E-VIS-053 y E-VIS-054).
 *
 * La regla: desde que un guardado **pudo** haber escrito algo, el desenlace y
 * las cuentas quedan congelados —en el `SavedStateHandle`, así que también tras
 * la muerte del proceso— y el reintento usa exactamente los mismos.
 *
 * [tablaDeFotos] modela la llave primaria de `visita_imagenes`: el id de la foto
 * es la llave, y la última escritura gana (`VisitImageDao.insertAll`, REPLACE).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por camino; juntarlas escondería cuál se rompió.
class RegistrarVisitaSeleccionCongeladaTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(Instant.parse("2026-09-01T15:52:00Z"))
    private val telemetria = RecordingTelemetry(clock)

    private val contextoPort = FakeContextoDeVisitaPort()
    private val recomendacionesPort = FakeRecomendacionesPort()
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

    // ─── (1) quitar el ancla tras un parcial ─────────────────────────────────

    @Test
    fun `E-VIS-055 tras un parcial quitar el ancla no es posible y la foto se queda en su visita`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            parcialSobreLasDos(vm)
            val anclaEscrita = IdsDeLaVisita.idDe(vm.visitaId, VisitasFixtures.SALA)
            assertEquals(anclaEscrita, tablaDeFotos()[FOTO])

            vm.onCuenta(VisitasFixtures.SALA)
            reintentar(vm)

            assertEquals("la foto no cambia de visita", anclaEscrita, tablaDeFotos()[FOTO])
            assertEquals(
                "la cuenta ancla ya escrita no se puede desmarcar",
                setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR),
                vm.state.value.captura.cuentas
            )
            assertTrue(vm.state.value.registrada != null)
        }

    @Test
    fun `E-VIS-055 tras un parcial el atajo de todas y el cambio de desenlace no tocan la seleccion`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            parcialSobreLasDos(vm)
            val antes = vm.state.value.captura

            vm.onTodasLasCuentas()
            assertEquals(antes, vm.state.value.captura)
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)
            assertEquals(antes, vm.state.value.captura)
            vm.limpiarResultado()
            assertEquals(antes, vm.state.value.captura)
            assertTrue(vm.state.value.seleccionCongelada)
        }

    /**
     * Un reintento que tampoco escribe nada NO descongela: lo que escribió el
     * primer intento sigue en el teléfono con la foto atada a su ancla.
     */
    @Test
    fun `E-VIS-055 un reintento fallido sin escrituras no descongela lo que ya se escribio`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            parcialSobreLasDos(vm)
            registroPort.fallaEn = null
            registroPort.resultado = ResultadoDelRegistro.FALLO_EL_GUARDADO
            vm.guardar()
            advanceUntilIdle()

            vm.onCuenta(VisitasFixtures.SALA)

            assertTrue(vm.state.value.seleccionCongelada)
            assertEquals(
                setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR),
                vm.state.value.captura.cuentas
            )
        }

    // ─── (2) agregar una cuenta menor tras un parcial ────────────────────────

    @Test
    fun `E-VIS-055 tras un parcial agregar una cuenta menor no es posible`() =
        runTest(testDispatcher) {
            contextoPort.contexto = VisitasFixtures.victoria(
                ventas = listOf(cuentaMenor()) + VisitasFixtures.dosCuentas()
            )
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            vm.onCuenta(CUENTA_MENOR) // nace marcada; el cobrador la desmarca
            tomarFoto(vm)
            registroPort.fallaEn = 1 // SALA (ancla) se escribe, REFRIGERADOR falla
            vm.guardar()
            advanceUntilIdle()
            val anclaEscrita = IdsDeLaVisita.idDe(vm.visitaId, VisitasFixtures.SALA)
            assertEquals(anclaEscrita, tablaDeFotos()[FOTO])

            vm.onCuenta(CUENTA_MENOR)
            reintentar(vm)

            assertFalse(CUENTA_MENOR in vm.state.value.captura.cuentas)
            assertEquals("la foto no cambia de visita", anclaEscrita, tablaDeFotos()[FOTO])
            assertTrue(
                "ninguna visita nace para la cuenta agregada",
                registroPort.registradas.none { it.ventaId == CUENTA_MENOR }
            )
        }

    // ─── (3) muerte del proceso ──────────────────────────────────────────────

    @Test
    fun `E-VIS-055 tras restaurar el proceso con un parcial las cuentas y el ancla siguen congeladas`() =
        runTest(testDispatcher) {
            val handle = handle()
            val muerto = viewModel(handle)
            advanceUntilIdle()
            muerto.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            muerto.onEtiqueta(TipoVisitaCatalogo.NO_RESPONDE)
            parcialSobreLasDos(muerto, elegir = false)
            val anclaEscrita = IdsDeLaVisita.idDe(muerto.visitaId, VisitasFixtures.SALA)

            val vm = viewModel(handle)
            advanceUntilIdle()

            assertTrue(vm.state.value.seleccionCongelada)
            assertEquals(ResultadoDeVisita.VISITE_VUELVO, vm.state.value.captura.resultado)
            assertEquals(TipoVisitaCatalogo.NO_RESPONDE, vm.state.value.captura.etiqueta)
            assertEquals(
                setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR),
                vm.state.value.captura.cuentas
            )
            vm.onCuenta(VisitasFixtures.SALA)
            reintentar(vm)

            assertEquals("la foto no cambia de visita", anclaEscrita, tablaDeFotos()[FOTO])
            assertEquals(
                "el reintento reescribe las dos mismas visitas",
                setOf(anclaEscrita, IdsDeLaVisita.idDe(vm.visitaId, VisitasFixtures.REFRIGERADOR)),
                registroPort.registradas.drop(2).map { it.visitaId }.toSet()
            )
        }

    /**
     * El proceso muere **durante** la escritura: la pantalla nunca supo cuántas
     * quedaron. Congelar antes de escribir es lo que cubre este caso.
     */
    @Test
    fun `E-VIS-055 si el proceso muere a media escritura la seleccion vuelve congelada`() =
        runTest(testDispatcher) {
            val handle = handle()
            val muerto = viewModel(handle, ubicacion = UbicacionQueNoContesta)
            advanceUntilIdle()
            muerto.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            muerto.guardar() // la escritura queda en vuelo: el proceso muere ahí

            val vm = viewModel(handle)
            advanceUntilIdle()

            assertEquals("nada se escribió todavía", 0, registroPort.registradas.size)
            assertTrue(vm.state.value.seleccionCongelada)
            assertEquals(
                setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR),
                vm.state.value.captura.cuentas
            )
        }

    /**
     * Toda la puerta escribe UNA visita cuyo id es la semilla. Si el proceso
     * muere a media escritura y al volver se elige un desenlace de cuenta, el
     * ancla pasa a ser otra y la foto se mudaría igual.
     */
    @Test
    fun `E-VIS-055 un desenlace de toda la puerta tambien vuelve congelado tras la muerte del proceso`() =
        runTest(testDispatcher) {
            val handle = handle()
            val muerto = viewModel(handle, ubicacion = UbicacionQueNoContesta)
            advanceUntilIdle()
            muerto.onResultado(ResultadoDeVisita.NO_ESTABA)
            muerto.guardar()

            val vm = viewModel(handle)
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)

            assertEquals(ResultadoDeVisita.NO_ESTABA, vm.state.value.captura.resultado)
            assertEquals(emptySet<Int>(), vm.state.value.captura.cuentas)
        }

    // ─── (4) controles: sin escritura previa todo se puede cambiar ───────────

    @Test
    fun `E-VIS-055 control sin guardar las cuentas se cambian normalmente`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)

            vm.onCuenta(VisitasFixtures.SALA)

            assertEquals(setOf(VisitasFixtures.REFRIGERADOR), vm.state.value.captura.cuentas)
            assertFalse(vm.state.value.seleccionCongelada)
        }

    @Test
    fun `E-VIS-055 control un fallo que no escribio nada no congela`() = runTest(testDispatcher) {
        val handle = handle()
        val vm = viewModel(handle)
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
        registroPort.resultado = ResultadoDelRegistro.FALLO_EL_GUARDADO
        vm.guardar()
        advanceUntilIdle()

        vm.onCuenta(VisitasFixtures.SALA)

        assertFalse(vm.state.value.seleccionCongelada)
        assertEquals(setOf(VisitasFixtures.REFRIGERADOR), vm.state.value.captura.cuentas)
        val restaurado = viewModel(handle)
        advanceUntilIdle()
        assertFalse(
            "sin escritura, la restauración tampoco congela",
            restaurado.state.value.seleccionCongelada
        )
    }

    // ─── utilidades ──────────────────────────────────────────────────────────

    /** "Visité, vuelvo" sobre las dos cuentas, con una foto: el ancla entra y la otra falla. */
    private fun parcialSobreLasDos(vm: RegistrarVisitaViewModel, elegir: Boolean = true) {
        if (elegir) vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
        tomarFoto(vm)
        registroPort.fallaEn = 1
        vm.guardar()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("el primer guardado fue parcial", vm.state.value.fallo != null)
        assertEquals(1, registroPort.registradas.count { it.comprobantes.isNotEmpty() })
    }

    private fun reintentar(vm: RegistrarVisitaViewModel) {
        registroPort.fallaEn = null
        vm.guardar()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun tomarFoto(vm: RegistrarVisitaViewModel) {
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.fotoTomada()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /**
     * La llave primaria de `visita_imagenes`: id de foto → visita. Cada
     * escritura que lleva la foto la reemplaza, como el `REPLACE` del DAO.
     * Solo cuentan las escrituras que el puerto aceptó.
     */
    private fun tablaDeFotos(): Map<String, String> {
        val tabla = mutableMapOf<String, String>()
        registroPort.registradas.forEachIndexed { indice, visita ->
            if (indice !in registroPort.fallidas) {
                visita.comprobantes.forEach { tabla[it.id] = visita.visitaId }
            }
        }
        return tabla
    }

    private fun cuentaMenor() = VisitasFixtures.dosCuentas().first().copy(
        ventaId = CUENTA_MENOR,
        folio = "V-4990",
        descripcion = "Comedor 6 sillas"
    )

    private fun handle() = SavedStateHandle(
        mapOf(
            VisitasRutas.ARG_CLIENTE_ID to VisitasFixtures.VICTORIA,
            VisitasRutas.ARG_VENTA_ID to VisitasRutas.SIN_VENTA
        )
    )

    private fun viewModel(
        savedStateHandle: SavedStateHandle = handle(),
        ubicacion: UbicacionPort = FakeUbicacionPort()
    ) = RegistrarVisitaViewModel(
        savedStateHandle = savedStateHandle,
        abrirRegistro = AbrirRegistroDeVisita(contextoPort, recomendacionesPort, telemetria),
        registrarVisita = RegistrarVisita(registroPort, ubicacion, telemetria),
        camara = camaraPort,
        dictado = DictadoFalso(),
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher
    )

    /** Un GPS sin fix: la escritura queda esperando, como cuando el proceso muere ahí. */
    private object UbicacionQueNoContesta : UbicacionPort {
        override suspend fun ubicacionActual(): UbicacionDeLaVisita? = awaitCancellation()
    }

    private companion object {
        const val FOTO = "IMG-1"
        const val CUENTA_MENOR = 76990
    }
}
