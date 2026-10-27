package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.visitas.application.AbrirRegistroDeVisita
import com.example.msp_app.feature.visitas.application.RegistrarVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.data.fake.DictadoFalso
import com.example.msp_app.feature.visitas.data.fake.FakeComprobantesDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeContextoDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeRecomendacionesPort
import com.example.msp_app.feature.visitas.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.visitas.data.fake.FakeUbicacionPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.IdsDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.CuentaAusentePort
import com.example.msp_app.feature.visitas.domain.port.RegistroDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.UbicacionDeLaVisita
import com.example.msp_app.feature.visitas.domain.port.UbicacionPort
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Adversario ronda 4 sobre E-VIS-055 (selección congelada), a nivel de la VM.
 *
 * Dos clases de prueba conviven aquí y el nombre lo dice:
 *  - **ESPERADA ROJA**: afirma el defecto tal como está hoy, para que la suite
 *    siga verde y el hallazgo quede fijado; se pone ROJA el día que alguien lo
 *    arregle, y entonces se reescribe con el esperado bueno. El adversario no
 *    toca el código de producción: el coordinador decide.
 *  - El resto: el código resiste y la prueba lo fija.
 *
 * [PuertoConTabla] modela la llave primaria de `visita_imagenes` (id de foto →
 * visita, la última escritura que QUEDÓ gana) y permite que una escritura
 * quede en la base aunque el puerto conteste fallo: el camino real del
 * adaptador lo mide `AdversarioSeleccionCongeladaR4Test` en `:app`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por ataque; juntarlas escondería cuál se rompió.
class RegistrarVisitaAdversarioR4Test {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(Instant.parse("2026-09-01T15:52:00Z"))
    private val telemetria = RecordingTelemetry(clock)

    private val contextoPort = FakeContextoDeVisitaPort()
    private val recomendacionesPort = FakeRecomendacionesPort()
    private val puerto = PuertoConTabla()
    private val camaraPort = FakeComprobantesDeVisitaPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ─── H-r4-1: escribió, contestó fallo, se descongela ─────────────────────

    /**
     * **E-VIS-071** (era H-r4-1, ESPERADA ROJA). Toda la puerta: UNA fila, con
     * la semilla de id. La fila y su foto quedan, el puerto contesta fallo y
     * `alEscribir` nunca se llama. Antes la VM soltaba la selección y, con otro
     * desenlace, la foto se mudaba. Ahora FALLO_EL_GUARDADO no descongela.
     */
    @Test
    fun `E-VIS-071 toda la puerta escrita con respuesta de fallo sigue congelada y la foto no se muda`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.NO_ESTABA)
            tomarFoto(vm)
            val foto = vm.state.value.comprobantes.single().id
            puerto.escribeYContestaFallo = setOf(0)
            vm.guardar()
            advanceUntilIdle()
            assertEquals("la semilla quedó con la foto", vm.visitaId, puerto.tabla[foto])

            assertTrue(vm.state.value.seleccionCongelada)
            vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            assertEquals(ResultadoDeVisita.NO_ESTABA, vm.state.value.captura.resultado)
            reintentar(vm)

            assertEquals("la foto sigue en la semilla", vm.visitaId, puerto.tabla[foto])
        }

    /** **E-VIS-071**, varias cuentas: el ancla escribe y contesta fallo, la otra falla. */
    @Test
    fun `E-VIS-071 varias cuentas con el ancla escrita y respuesta de fallo siguen congeladas`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            tomarFoto(vm)
            val foto = vm.state.value.comprobantes.single().id
            puerto.escribeYContestaFallo = setOf(0)
            puerto.fallaSinEscribir = setOf(1)
            vm.guardar()
            advanceUntilIdle()
            val anclaEscrita = IdsDeLaVisita.idDe(vm.visitaId, VisitasFixtures.SALA)
            assertEquals(anclaEscrita, puerto.tabla[foto])

            vm.onCuenta(VisitasFixtures.SALA)
            assertEquals(LAS_DOS, vm.state.value.captura.cuentas)
            reintentar(vm)

            assertEquals("la foto sigue en el ancla", anclaEscrita, puerto.tabla[foto])
        }

    // ─── H-r4-2: la etiqueta no está congelada ───────────────────────────────

    /**
     * **H-r4-2** (era ESPERADA ROJA contra lo dicho). La etiqueta queda
     * congelada con el desenlace y las cuentas: el reintento reescribe las
     * filas con la MISMA etiqueta y el ancla no se mueve.
     */
    @Test
    fun `H-r4-2 tras un parcial la etiqueta no se puede cambiar`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        parcialSobreLasDos(vm)
        val foto = vm.state.value.comprobantes.single().id
        val anclaEscrita = IdsDeLaVisita.idDe(vm.visitaId, VisitasFixtures.SALA)
        val original = vm.state.value.captura.etiqueta

        vm.onEtiqueta(TipoVisitaCatalogo.SE_ESCUCHAN_RUIDOS)

        assertEquals(original, vm.state.value.captura.etiqueta)
        reintentar(vm)
        assertEquals(anclaEscrita, puerto.tabla[foto])
        assertTrue(
            "el reintento reescribe las dos con la etiqueta original",
            puerto.llamadas.drop(2).all { it.tipoVisita == original }
        )
    }

    // ─── H-r4-3: quitar una foto ya escrita ──────────────────────────────────

    /**
     * **E-VIS-072** (era H-r4-3). Con la selección congelada, una foto que el
     * guardado se pudo llevar ya no se quita: ni se borra su archivo ni sale
     * del reintento. Su fila sigue en el ancla y ahora tiene archivo. La mitad
     * Room del defecto la fija `AdversarioSeleccionCongeladaR4Test` en `:app`.
     */
    @Test
    fun `E-VIS-072 con la seleccion congelada una foto ya escrita no se puede quitar`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            parcialSobreLasDos(vm)
            val comprobante = vm.state.value.comprobantes.single()
            val anclaEscrita = IdsDeLaVisita.idDe(vm.visitaId, VisitasFixtures.SALA)

            vm.quitarFoto(comprobante.id)
            advanceUntilIdle()

            assertEquals(listOf(comprobante), vm.state.value.comprobantes)
            assertFalse("el archivo no se borra", comprobante.archivo in camaraPort.descartados)
            assertEquals(setOf(comprobante.id), vm.state.value.fotosFijas)
            reintentar(vm)
            assertEquals(listOf(comprobante.id), puerto.llamadas[2].comprobantes.map { it.id })
            assertEquals("la fila sigue en el ancla", anclaEscrita, puerto.tabla[comprobante.id])
        }

    /** **E-VIS-072, control.** Una foto agregada DESPUÉS del parcial sí se quita. */
    @Test
    fun `E-VIS-072 control una foto agregada tras el parcial si se puede quitar`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            parcialSobreLasDos(vm)
            tomarFoto(vm)
            val nueva = vm.state.value.comprobantes.last()

            vm.quitarFoto(nueva.id)
            advanceUntilIdle()

            assertEquals(1, vm.state.value.comprobantes.size)
            assertTrue(nueva.archivo in camaraPort.descartados)
        }

    /** **E-VIS-072** tras la muerte del proceso: la foto sigue fija. */
    @Test
    fun `E-VIS-072 tras restaurar el proceso la foto ya escrita sigue sin poder quitarse`() =
        runTest(testDispatcher) {
            val handle = handle()
            val muerto = viewModel(handle)
            advanceUntilIdle()
            parcialSobreLasDos(muerto)
            val comprobante = muerto.state.value.comprobantes.single()

            val vm = viewModel(handle)
            advanceUntilIdle()
            vm.quitarFoto(comprobante.id)
            advanceUntilIdle()

            assertEquals(setOf(comprobante.id), vm.state.value.fotosFijas)
            assertEquals(listOf(comprobante.id), vm.state.value.comprobantes.map { it.id })
        }

    // ─── H-r4-4: "ya se guardó" sin haber guardado nada ──────────────────────

    /**
     * **E-VIS-073** (era H-r4-4, ESPERADA ROJA). El proceso muere después de
     * congelar y antes de la primera escritura. La pantalla restaurada vuelve
     * congelada, sin banda de fallo y sin registrar: es lo conservador, y por
     * eso el texto de la selección congelada ya no dice "ya se guardó" (lo
     * fija `VisitaSeVeYSeTocaTest`).
     */
    @Test
    fun `E-VIS-073 tras morir antes de escribir la pantalla vuelve congelada sin fallo y sin nada escrito`() =
        runTest(testDispatcher) {
            val handle = handle()
            val muerto = viewModel(handle, registro = PuertoQueNoContesta)
            advanceUntilIdle()
            muerto.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            muerto.guardar()

            val vm = viewModel(handle)
            advanceUntilIdle()

            assertEquals("nada se escribió", 0, puerto.llamadas.size)
            assertTrue(vm.state.value.seleccionCongelada)
            assertNull(vm.state.value.fallo)
            assertNull(vm.state.value.registrada)
        }

    // ─── H-r4-5: cuenta congelada que ya no está en el teléfono ──────────────

    /**
     * **H-r4-5.** Tras la muerte del proceso la selección congelada vuelve
     * aunque una de sus cuentas ya no esté en el contexto (se liquidó y el sync
     * la quitó). La fila no se pinta, no se puede desmarcar, y el reintento
     * escribe una visita sobre ella igual. Se fija como está: congelar protege
     * el ancla, y es decisión del dueño si escribir una cuenta invisible es
     * aceptable.
     */
    @Test
    fun `H-r4-5 la seleccion restaurada escribe una cuenta que ya no esta en el telefono`() =
        runTest(testDispatcher) {
            val handle = handle()
            val muerto = viewModel(handle)
            advanceUntilIdle()
            parcialSobreLasDos(muerto)
            contextoPort.contexto = VisitasFixtures.victoria(
                ventas = VisitasFixtures.dosCuentas().filter { it.ventaId == VisitasFixtures.SALA }
            )

            val vm = viewModel(handle)
            advanceUntilIdle()
            assertEquals(
                setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR),
                vm.state.value.captura.cuentas
            )
            reintentar(vm)

            assertTrue(vm.state.value.registrada != null)
            assertTrue(
                "se escribe la cuenta que ya no se pinta",
                puerto.llamadas.drop(2).any { it.ventaId == VisitasFixtures.REFRIGERADOR }
            )
            // Decisión del coordinador: igual, pero con rastro (solo el conteo).
            assertEquals(listOf(1), avisosDeAusentes)
            val evento = telemetria.recorded.single {
                it.name == VisitasTelemetria.CODE_VISITA_CUENTA_AUSENTE_AL_REINTENTAR
            }
            assertEquals("1", evento.props[VisitasTelemetria.PROP_OCURRENCIAS])
        }

    /** **H-r4-5, control.** El reintento congelado con todas sus cuentas presentes no avisa. */
    @Test
    fun `H-r4-5 control sin cuentas ausentes el reintento no deja rastro`() =
        runTest(testDispatcher) {
            val handle = handle()
            val muerto = viewModel(handle)
            advanceUntilIdle()
            parcialSobreLasDos(muerto)

            val vm = viewModel(handle)
            advanceUntilIdle()
            reintentar(vm)

            assertTrue(vm.state.value.registrada != null)
            assertEquals(emptyList<Int>(), avisosDeAusentes)
            assertTrue(
                telemetria.recorded.none {
                    it.name == VisitasTelemetria.CODE_VISITA_CUENTA_AUSENTE_AL_REINTENTAR
                }
            )
        }

    // ─── lo que resiste ──────────────────────────────────────────────────────

    /** Dos pantallas (dos entradas de navegación) no comparten lo congelado. */
    @Test
    fun `R4 lo congelado de una visita no se filtra a la de otra entrada`() =
        runTest(testDispatcher) {
            val primera = viewModel(handle())
            advanceUntilIdle()
            parcialSobreLasDos(primera)
            assertTrue(primera.state.value.seleccionCongelada)

            val otra = viewModel(handle())
            advanceUntilIdle()

            assertFalse(otra.state.value.seleccionCongelada)
            assertNull(otra.state.value.captura.resultado)
            assertFalse(otra.visitaId == primera.visitaId)
        }

    /** Doble toque: un solo juego de escrituras, la selección congelada una vez. */
    @Test
    fun `R4 doble toque de guardar escribe una sola vez`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
        vm.guardar()
        vm.guardar()
        advanceUntilIdle()

        assertEquals(2, puerto.llamadas.size)
        assertTrue(vm.state.value.registrada != null)
    }

    /**
     * Un parcial, y luego un reintento que tampoco escribe: la selección sigue
     * congelada y tras la muerte del proceso también.
     */
    @Test
    fun `R4 parcial y luego fallo total siguen congelados tras la muerte del proceso`() =
        runTest(testDispatcher) {
            val handle = handle()
            val vm = viewModel(handle)
            advanceUntilIdle()
            parcialSobreLasDos(vm)
            puerto.fallaSinEscribir = setOf(2, 3)
            vm.guardar()
            advanceUntilIdle()

            val restaurado = viewModel(handle)
            advanceUntilIdle()
            restaurado.onCuenta(VisitasFixtures.SALA)

            assertTrue(restaurado.state.value.seleccionCongelada)
            assertEquals(
                setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR),
                restaurado.state.value.captura.cuentas
            )
        }

    // ─── utilidades ──────────────────────────────────────────────────────────

    /** "Visité, vuelvo" sobre las dos con una foto: el ancla entra, la otra falla sin escribir. */
    private fun parcialSobreLasDos(vm: RegistrarVisitaViewModel) {
        vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
        tomarFoto(vm)
        puerto.fallaSinEscribir = setOf(puerto.llamadas.size + 1)
        vm.guardar()
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("el primer guardado fue parcial", vm.state.value.fallo != null)
        assertTrue(vm.state.value.seleccionCongelada)
    }

    private fun reintentar(vm: RegistrarVisitaViewModel) {
        puerto.escribeYContestaFallo = emptySet()
        puerto.fallaSinEscribir = emptySet()
        vm.guardar()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun tomarFoto(vm: RegistrarVisitaViewModel) {
        vm.onOrigen(OrigenDeLaFoto.CAMARA)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.fotoTomada()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun handle() = SavedStateHandle(
        mapOf(
            VisitasRutas.ARG_CLIENTE_ID to VisitasFixtures.VICTORIA,
            VisitasRutas.ARG_VENTA_ID to VisitasRutas.SIN_VENTA
        )
    )

    private fun viewModel(
        savedStateHandle: SavedStateHandle = handle(),
        ubicacion: UbicacionPort = FakeUbicacionPort(),
        registro: RegistroDeVisitaPort = puerto
    ) = RegistrarVisitaViewModel(
        savedStateHandle = savedStateHandle,
        abrirRegistro = AbrirRegistroDeVisita(contextoPort, recomendacionesPort, telemetria),
        registrarVisita = RegistrarVisita(registro, ubicacion, telemetria),
        camara = camaraPort,
        dictado = DictadoFalso(),
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher,
        cuentaAusente = CuentaAusentePort { avisosDeAusentes += it }
    )

    private val avisosDeAusentes: MutableList<Int> = mutableListOf()

    /**
     * Puerto con la tabla de fotos. Por índice de llamada (global):
     * [escribeYContestaFallo] deja la fila y la foto y contesta fallo;
     * [fallaSinEscribir] contesta fallo sin dejar nada.
     */
    private class PuertoConTabla : RegistroDeVisitaPort {
        val llamadas: MutableList<VisitaARegistrar> = mutableListOf()
        val tabla: MutableMap<String, String> = mutableMapOf()
        var escribeYContestaFallo: Set<Int> = emptySet()
        var fallaSinEscribir: Set<Int> = emptySet()

        override suspend fun registrar(visita: VisitaARegistrar): ResultadoDelRegistro {
            val indice = llamadas.size
            llamadas += visita
            if (indice in fallaSinEscribir) return ResultadoDelRegistro.FALLO_EL_GUARDADO
            visita.comprobantes.forEach { tabla[it.id] = visita.visitaId }
            return if (indice in escribeYContestaFallo) {
                ResultadoDelRegistro.FALLO_EL_GUARDADO
            } else {
                ResultadoDelRegistro.REGISTRADA
            }
        }
    }

    /**
     * E-APP-127: la ubicación ya no va antes de escribir, así que un GPS sin fix
     * no detiene la escritura. "El proceso muere antes o durante la escritura"
     * se modela con el puerto colgado: la escritura queda en vuelo ahí.
     */
    private object PuertoQueNoContesta : RegistroDeVisitaPort {
        override suspend fun registrar(visita: VisitaARegistrar): ResultadoDelRegistro =
            awaitCancellation()
    }

    private object UbicacionQueNoContesta : UbicacionPort {
        override suspend fun ubicacionActual(): UbicacionDeLaVisita? = awaitCancellation()
    }

    private companion object {
        val LAS_DOS = setOf(VisitasFixtures.SALA, VisitasFixtures.REFRIGERADOR)
    }
}
