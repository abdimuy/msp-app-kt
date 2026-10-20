package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.telemetry.Telemetry
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
import com.example.msp_app.feature.visitas.domain.CatalogoDeResultados
import com.example.msp_app.feature.visitas.domain.IdsDeLaVisita
import com.example.msp_app.feature.visitas.domain.model.ResultadoDeVisita
import com.example.msp_app.feature.visitas.domain.port.RegistroDeVisitaPort
import com.example.msp_app.feature.visitas.domain.port.ResultadoDelRegistro
import com.example.msp_app.feature.visitas.domain.port.VisitaARegistrar
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Adversario ronda 5 sobre E-VIS-071/072 y H-r4-2 (delta `3773800d..1e566de1`).
 *
 * Mismo patrón que la ronda 4. H-r5-1 ya no es ESPERADA-ROJA: la arregló
 * E-VIS-074 y sus pruebas afirman el esperado bueno. El resto: el código
 * resiste y la prueba lo fija.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por ataque.
class RegistrarVisitaAdversarioR5Test {

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

    // ─── H-r5-1: una telemetría que lanza DESPUÉS del commit del ancla ───────

    /**
     * **E-VIS-074 (H-r5-1).** E-VIS-071 cerró la telemetría post-commit DENTRO
     * del adaptador, pero con varias cuentas "después del commit" es también
     * todo lo que corre tras el ancla. `RegistrarVisita.reportarParcial` iba
     * sin guarda: si `DurableTelemetry.error` lanzaba (inserta en Room con
     * `runBlocking`), la excepción salía del caso de uso, `aplicar` nunca
     * corría y la corrutina del `viewModelScope` moría sin manejador (en el
     * teléfono, un crash con el ancla y su foto ya escritas). Ahora el parcial
     * se pinta como cualquier fallo y la pantalla suelta "guardando".
     */
    @Test
    fun `E-VIS-074 H-r5-1 la telemetria del parcial que lanza no saca la excepcion de la VM`() =
        runTest(testDispatcher) {
            val lanza = TelemetriaQueLanzaEn(
                telemetria,
                VisitasTelemetria.CODE_VISITA_PARCIAL_POR_CUENTA
            )
            val vm = viewModel(telemetriaDelCaso = lanza)
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            tomarFoto(vm)
            val foto = vm.state.value.comprobantes.single().id
            puerto.fallaSinEscribir = setOf(puerto.llamadas.size + 1)
            vm.guardar()
            advanceUntilIdle()

            assertEquals("la telemetría sí se pidió (y lanzó)", 1, lanza.lanzadas)
            assertEquals(
                "el ancla quedó escrita con la foto",
                IdsDeLaVisita.idDe(vm.visitaId, VisitasFixtures.SALA),
                puerto.tabla[foto]
            )
            assertFalse("la pantalla suelta guardando", vm.state.value.guardando)
            assertNotNull("con banda de fallo", vm.state.value.fallo)
            assertTrue("lo congelado se sostiene", vm.state.value.seleccionCongelada)
        }

    /**
     * **E-VIS-074 (H-r5-1), segunda puerta.** `aplicar` registraba
     * [VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO] sin `try` después de soltar
     * `yaSeEncolo` y antes de publicar el estado. Ahora el estado se publica.
     */
    @Test
    fun `E-VIS-074 H-r5-1 la telemetria de aplicar que lanza tras un parcial no deja la pantalla guardando`() =
        runTest(testDispatcher) {
            val lanza = TelemetriaQueLanzaEn(
                telemetria,
                VisitasTelemetria.CODE_VISITA_NO_SE_GUARDO
            )
            val vm = viewModel(telemetriaDeLaVm = lanza)
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            tomarFoto(vm)
            puerto.fallaSinEscribir = setOf(puerto.llamadas.size + 1)
            vm.guardar()
            advanceUntilIdle()

            assertEquals("la telemetría sí se pidió (y lanzó)", 1, lanza.lanzadas)
            assertFalse(vm.state.value.guardando)
            assertNotNull(vm.state.value.fallo)
            assertTrue(vm.state.value.seleccionCongelada)
        }

    /** **H-r5-1, control positivo.** Sin telemetría rota, el mismo parcial pinta su fallo. */
    @Test
    fun `H-r5-1 control el mismo parcial con telemetria sana pinta el fallo y suelta guardando`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            vm.onResultado(ResultadoDeVisita.VISITE_VUELVO)
            tomarFoto(vm)
            puerto.fallaSinEscribir = setOf(puerto.llamadas.size + 1)
            vm.guardar()
            advanceUntilIdle()

            assertFalse(vm.state.value.guardando)
            assertNotNull(vm.state.value.fallo)
            assertTrue(vm.state.value.seleccionCongelada)
        }

    // ─── 2af527f5: la lista blanca sí suelta lo que debe ─────────────────────

    /**
     * Control de E-VIS-071: con un desenlace previo a escribir y cero
     * escrituras, la selección se suelta y la foto (nada la escribió) se puede
     * quitar y su archivo se borra. Sin esto, la lista blanca podría estar
     * vacía y las pruebas de "sigue congelada" pasarían igual.
     */
    @Test
    fun `R5 cliente que no esta en el telefono al primer intento suelta la seleccion y la foto`() =
        runTest(testDispatcher) {
            for (desenlace in listOf(
                ResultadoDelRegistro.CLIENTE_NO_ESTA_EN_EL_TELEFONO,
                ResultadoDelRegistro.SIN_COBRADOR
            )) {
                val vm = viewModel()
                advanceUntilIdle()
                vm.onResultado(ResultadoDeVisita.NO_ESTABA)
                tomarFoto(vm)
                val comprobante = vm.state.value.comprobantes.single()
                puerto.contestaSinEscribir = desenlace
                vm.guardar()
                advanceUntilIdle()
                puerto.contestaSinEscribir = null

                assertFalse("$desenlace suelta", vm.state.value.seleccionCongelada)
                assertEquals(emptySet<String>(), vm.state.value.fotosFijas)
                vm.quitarFoto(comprobante.id)
                advanceUntilIdle()
                assertTrue(vm.state.value.comprobantes.isEmpty())
                assertTrue(comprobante.archivo in camaraPort.descartados)
            }
        }

    /** Tras un parcial, un reintento que contesta "cliente no está" NO suelta (yaEstabaCongelada). */
    @Test
    fun `R5 tras un parcial un reintento sin cliente no suelta la seleccion ni la foto`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            parcialSobreLasDos(vm)
            val foto = vm.state.value.comprobantes.single()
            puerto.contestaSinEscribir = ResultadoDelRegistro.CLIENTE_NO_ESTA_EN_EL_TELEFONO
            vm.guardar()
            advanceUntilIdle()
            puerto.contestaSinEscribir = null

            assertTrue(vm.state.value.seleccionCongelada)
            assertEquals(setOf(foto.id), vm.state.value.fotosFijas)
            vm.quitarFoto(foto.id)
            advanceUntilIdle()
            assertFalse(foto.archivo in camaraPort.descartados)
        }

    // ─── 4f150ab4: fotos fijas ───────────────────────────────────────────────

    /**
     * Foto agregada tras el parcial y un reintento que falla sin escribir: la
     * nueva también queda fija (se la pudo llevar) y la vieja sigue en el ancla.
     */
    @Test
    fun `R5 la foto agregada tras el parcial queda fija en cuanto un reintento se la pudo llevar`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            advanceUntilIdle()
            parcialSobreLasDos(vm)
            val vieja = vm.state.value.comprobantes.single()
            tomarFoto(vm)
            val nueva = vm.state.value.comprobantes.last()
            puerto.fallaSinEscribir = setOf(puerto.llamadas.size, puerto.llamadas.size + 1)
            vm.guardar()
            advanceUntilIdle()

            assertEquals(setOf(vieja.id, nueva.id), vm.state.value.fotosFijas)
            vm.quitarFoto(nueva.id)
            advanceUntilIdle()
            assertFalse(nueva.archivo in camaraPort.descartados)

            reintentar(vm)
            val ancla = IdsDeLaVisita.idDe(vm.visitaId, VisitasFixtures.SALA)
            assertEquals(ancla, puerto.tabla[vieja.id])
            assertEquals(ancla, puerto.tabla[nueva.id])
            assertNotNull(vm.state.value.registrada)
        }

    /** Las fotos fijas y lo congelado sobreviven a dos muertes de proceso seguidas. */
    @Test
    fun `R5 fotos fijas sobreviven dos restauraciones y un reintento fallido entre ellas`() =
        runTest(testDispatcher) {
            val handle = handle()
            val primero = viewModel(handle)
            advanceUntilIdle()
            parcialSobreLasDos(primero)
            val vieja = primero.state.value.comprobantes.single()

            val segundo = viewModel(handle)
            advanceUntilIdle()
            tomarFoto(segundo)
            val nueva = segundo.state.value.comprobantes.last()
            puerto.fallaSinEscribir = setOf(puerto.llamadas.size, puerto.llamadas.size + 1)
            segundo.guardar()
            advanceUntilIdle()

            val tercero = viewModel(handle)
            advanceUntilIdle()
            assertEquals(setOf(vieja.id, nueva.id), tercero.state.value.fotosFijas)
            assertTrue(tercero.state.value.seleccionCongelada)
            tercero.quitarFoto(vieja.id)
            tercero.quitarFoto(nueva.id)
            advanceUntilIdle()
            assertEquals(2, tercero.state.value.comprobantes.size)
        }

    // ─── 7cee3942 + formato de 4 líneas ──────────────────────────────────────

    /**
     * Propiedad sobre el catálogo cerrado completo: cada (desenlace, etiqueta)
     * congelado vuelve idéntico tras la muerte del proceso, y la etiqueta no se
     * puede cambiar. Si un literal llevara salto de línea, `decodificar`
     * devolvería `null` y la selección se soltaría en silencio.
     */
    @Test
    fun `R5 todo desenlace y etiqueta del catalogo sobrevive el handle de cuatro lineas`() =
        runTest(testDispatcher) {
            var casos = 0
            for ((resultado, etiquetas) in CatalogoDeResultados.ETIQUETAS) {
                for (etiqueta in etiquetas) {
                    val handle = handle()
                    val vm = viewModel(handle)
                    advanceUntilIdle()
                    vm.onResultado(resultado)
                    vm.onEtiqueta(etiqueta)
                    vm.onFechaPromesa(MANANA)
                    vm.onFechaCita(MANANA)
                    tomarFoto(vm)
                    assertTrue(
                        "$resultado/$etiqueta se puede guardar",
                        vm.state.value.sePuedeGuardar
                    )
                    puerto.todoFallaSinEscribir = true
                    vm.guardar()
                    advanceUntilIdle()
                    puerto.todoFallaSinEscribir = false

                    val restaurado = viewModel(handle)
                    advanceUntilIdle()
                    val captura = restaurado.state.value.captura
                    assertTrue(
                        "$resultado/$etiqueta congelada",
                        restaurado.state.value.seleccionCongelada
                    )
                    assertEquals(resultado, captura.resultado)
                    assertEquals(etiqueta, captura.etiqueta)
                    assertEquals(
                        vm.state.value.comprobantes.map { it.id }.toSet(),
                        restaurado.state.value.fotosFijas
                    )
                    val otra = etiquetas.firstOrNull { it != etiqueta }
                    if (otra != null) {
                        restaurado.onEtiqueta(otra)
                        assertEquals(etiqueta, restaurado.state.value.captura.etiqueta)
                    }
                    casos++
                }
            }
            assertTrue(
                "se recorrió el catálogo entero ($casos)",
                casos >= CatalogoDeResultados.ETIQUETAS.size
            )
        }

    /**
     * Congelada, editar lo que NO es selección sigue abierto: la nota y la
     * fecha de la promesa. Si `sostiene` comparara de más (H-r4-2), el
     * cobrador perdería la nota del reintento.
     */
    @Test
    fun `R5 congelada la nota y la fecha siguen editables`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onResultado(ResultadoDeVisita.PROMETIO)
        vm.onFechaPromesa(MANANA)
        puerto.todoFallaSinEscribir = true
        vm.guardar()
        advanceUntilIdle()
        puerto.todoFallaSinEscribir = false

        vm.onNota("dijo que el viernes sin falta")
        vm.onFechaPromesa(MANANA.plusDays(1))
        assertEquals("dijo que el viernes sin falta", vm.state.value.captura.nota)
        assertEquals(MANANA.plusDays(1), vm.state.value.captura.fechaPromesa)
    }

    // ─── utilidades ──────────────────────────────────────────────────────────

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
        telemetriaDeLaVm: Telemetry = telemetria,
        telemetriaDelCaso: Telemetry = telemetria
    ) = RegistrarVisitaViewModel(
        savedStateHandle = savedStateHandle,
        abrirRegistro = AbrirRegistroDeVisita(contextoPort, recomendacionesPort, telemetria),
        registrarVisita = RegistrarVisita(puerto, FakeUbicacionPort(), telemetriaDelCaso),
        camara = camaraPort,
        dictado = DictadoFalso(),
        tema = FakeTemaDeLaAppPort(),
        telemetry = telemetriaDeLaVm,
        clock = clock,
        io = testDispatcher
    )

    /** La telemetría durable cuyo `error()` lanza con un código dado (Room no pudo insertar). */
    private class TelemetriaQueLanzaEn(
        private val real: RecordingTelemetry,
        private val codigo: String
    ) : Telemetry by real {
        var lanzadas = 0

        override fun error(code: String, message: String, props: Map<String, String>) {
            if (code == codigo) {
                lanzadas++
                throw IllegalStateException(
                    "la cola de telemetria no pudo insertar"
                )
            }
            real.error(code, message, props)
        }
    }

    /** Igual que en la ronda 4, más un desenlace previo a escribir y "todo falla". */
    private class PuertoConTabla : RegistroDeVisitaPort {
        val llamadas: MutableList<VisitaARegistrar> = mutableListOf()
        val tabla: MutableMap<String, String> = mutableMapOf()
        var fallaSinEscribir: Set<Int> = emptySet()
        var todoFallaSinEscribir: Boolean = false
        var contestaSinEscribir: ResultadoDelRegistro? = null

        override suspend fun registrar(visita: VisitaARegistrar): ResultadoDelRegistro {
            val indice = llamadas.size
            llamadas += visita
            contestaSinEscribir?.let { return it }
            if (todoFallaSinEscribir || indice in fallaSinEscribir) {
                return ResultadoDelRegistro.FALLO_EL_GUARDADO
            }
            visita.comprobantes.forEach { tabla[it.id] = visita.visitaId }
            return ResultadoDelRegistro.REGISTRADA
        }
    }

    private companion object {
        val MANANA: LocalDate = LocalDate.of(2026, 9, 2)
    }
}
