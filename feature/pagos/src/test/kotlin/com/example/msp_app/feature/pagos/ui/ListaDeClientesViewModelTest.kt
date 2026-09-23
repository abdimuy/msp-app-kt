package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
 * El ViewModel de la lista.
 *
 * **`StandardTestDispatcher`, nunca `UnconfinedTestDispatcher`** — mismo
 * criterio que `DetalleClienteViewModelTest`: con Unconfined el `launch` del
 * `init` termina antes de que el test pueda ver `cargando = true`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListaDeClientesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val temaPort = FakeTemaDeLaAppPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = ListaFixtures.datosDeLaRuta()
        // Una visita real dentro de la ventana, para que el chip "vencidos"
        // tenga a alguien: el estado lo deriva `EstadoCuentaDeriver`, no el
        // fixture. Sin ella la ruta entera sale "sin tocar", que es la verdad.
        visitasPort.visitas = listOf(
            VisitaDelCliente(
                visitaId = "visita-guadalupe",
                clienteId = ListaFixtures.GUADALUPE,
                ventaId = 79115,
                fecha = Instant.parse("2026-09-01T16:00:00Z"),
                tipoVisita = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
                nota = null
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ListaDeClientesViewModel(
        savedStateHandle = SavedStateHandle(),
        reunirCartera = ReunirCartera(
            ventasPort = ventasPort,
            pagosPort = pagosPort,
            visitasPort = visitasPort,
            resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
            derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
            telemetry = telemetria
        ),
        tema = temaPort,
        privacidad = FakePrivacidadPort(),
        telemetry = telemetria,
        io = testDispatcher
    )

    /**
     * **La pantalla abre en *sin visitar*.**
     *
     * No es cosmética: con `TODOS` retirado la lista arranca siempre filtrada, y
     * el chip que la abre decide qué es lo primero que ve el cobrador al entrar.
     * *Sin visitar* es "las puertas que nadie ha tocado esta semana", que es el
     * trabajo con el que empieza el día.
     *
     * Se afirman las **dos** mitades —el chip y las filas que produce—: un default
     * puesto en el `UiState` pero no respetado por la proyección pintaría un chip
     * que no corresponde a la lista de abajo.
     */
    @Test
    fun `arranca cargando, abre en sin visitar y termina con la ruta ordenada`() =
        runTest(testDispatcher) {
            val vm = viewModel()
            assertTrue(vm.state.value.cargando)
            assertEquals(SegmentoDeCobranza.SIN_VISITAR, vm.state.value.segmento)

            advanceUntilIdle()
            val state = vm.state.value
            assertFalse(state.cargando)
            assertFalse(state.fallo)
            assertEquals(SegmentoDeCobranza.SIN_VISITAR, state.segmento)
            assertEquals(
                listOf(ListaFixtures.RICARDO, ListaFixtures.VICTORIA),
                state.clientes.map { it.clienteId }
            )
            // Control positivo del filtro: la ruta entera SÍ se cargó. Lo que
            // falta de la lista lo esconde el chip, no una lectura corta —
            // Guadalupe se negó y vive en *después*.
            assertEquals(1, state.conteos[SegmentoDeCobranza.YA_NO_ESTA_SEMANA])
        }

    @Test
    fun `buscar filtra sin volver a leer la ruta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.buscar("Zepeda")
        // `state` se DERIVA con `combine(...).stateIn(...)` desde Ruling BQ (el tema no se
        // guarda, se re-aplica en cada emisión), así que hay que dejar correr al dispatcher.
        // En producción no hay lag: `viewModelScope` es `Dispatchers.Main.immediate` y la
        // emisión sale inline; el `advanceUntilIdle` es contabilidad del
        // `StandardTestDispatcher`, que esta clase usa a propósito (ver su KDoc).
        advanceUntilIdle()
        assertEquals(listOf(ListaFixtures.RICARDO), vm.state.value.clientes.map { it.clienteId })
        assertEquals("Zepeda", vm.state.value.query)
        // La búsqueda es sobre lo ya cargado: nada de releer Room por tecla.
        assertEquals(1, ventasPort.lecturasDeTodas)
    }

    @Test
    fun `elegir un chip filtra y se recuerda en el estado`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirSegmento(SegmentoDeCobranza.YA_NO_ESTA_SEMANA)
        advanceUntilIdle() // ver el comentario del test de arriba (derivación de `state`)
        assertEquals(SegmentoDeCobranza.YA_NO_ESTA_SEMANA, vm.state.value.segmento)
        // Solo Guadalupe tiene una visita en la ventana, y dice que se negó.
        assertEquals(listOf(ListaFixtures.GUADALUPE), vm.state.value.clientes.map { it.clienteId })
        assertEquals(1, ventasPort.lecturasDeTodas)
    }

    @Test
    fun `el chip y la busqueda sobreviven en el SavedStateHandle`() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val vm = ListaDeClientesViewModel(
            savedStateHandle = handle,
            reunirCartera = ReunirCartera(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria),
                telemetry = telemetria
            ),
            tema = temaPort,
            privacidad = FakePrivacidadPort(),
            telemetry = telemetria,
            io = testDispatcher
        )
        advanceUntilIdle()
        // Control positivo: nada guardado todavía, así que el ordinal de abajo no
        // puede venir de un valor que ya estuviera ahí.
        assertEquals(null, handle.get<Int>("pagos_lista_segmento"))

        vm.buscar("Flores")
        // **No** `SIN_VISITAR`: es el chip por defecto y su `ordinal` es 0, así
        // que guardarlo no distinguiría "se guardó" de "nunca se guardó nada".
        vm.elegirSegmento(SegmentoDeCobranza.PAGADOS)

        assertEquals("Flores", handle.get<String>("pagos_lista_query"))
        assertEquals(
            SegmentoDeCobranza.PAGADOS.ordinal,
            handle.get<Int>("pagos_lista_segmento")
        )
    }

    @Test
    fun `si la lectura falla se reporta y la pantalla lo dice`() = runTest(testDispatcher) {
        ventasPort.falla = IOException("Room se cayó")
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.fallo)
        assertTrue(vm.state.value.clientes.isEmpty())
        val evento = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_LISTA_CLIENTES_FALLO
        }
        // Se emite el nombre de la clase, nunca el `message` crudo.
        assertEquals("IOException", evento.props[PagosTelemetria.PROP_EXCEPCION])
        assertTrue(evento.props.values.none { it.contains("Room se cayó") })
    }

    @Test
    fun `reintentar vuelve a leer`() = runTest(testDispatcher) {
        ventasPort.falla = IOException("Room se cayó")
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.fallo)

        ventasPort.falla = null
        vm.cargar()
        advanceUntilIdle()
        assertFalse(vm.state.value.fallo)
        // Dos: la ruta tiene tres puertas y el chip de arranque —*sin visitar*—
        // deja fuera a Guadalupe, que se negó.
        assertEquals(2, vm.state.value.clientes.size)
    }

    @Test
    fun `la pantalla se anuncia a telemetria sin PII`() = runTest(testDispatcher) {
        viewModel()
        advanceUntilIdle()
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.SCREEN_VIEW && it.name == "pagos_lista_clientes"
            }
        )
    }

    // --- recargar(): la que usa la pantalla al reanudarse --------------------

    /** Una puerta nueva, ajena a la ruta sembrada en `setUp`. */
    private fun ventaDeEsperanza() = ListaFixtures.datos(
        clienteId = ListaFixtures.ESPERANZA,
        nombre = "Esperanza Vargas Trejo",
        ventaId = 80233,
        folio = "V-8233",
        saldo = "2600",
        total = "7400",
        enganche = "900",
        fecha = "2026-07-08"
    )

    /**
     * **El corazón del arreglo**: después de registrar un abono o una visita, la
     * lista a la que se vuelve tiene que reflejarlo. `recargar()` no puede ser
     * un alias de "repintar lo que ya había en memoria" — tiene que volver a
     * leer la ruta completa. Se agrega una puerta real entre la carga inicial y
     * la recarga y se comprueba que la lista la refleja.
     */
    @Test
    fun `recargar vuelve a leer la ruta`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(2, vm.state.value.clientes.size)

        ventasPort.ventas = ventasPort.ventas + ventaDeEsperanza()

        vm.recargar()
        advanceUntilIdle()

        assertEquals(3, vm.state.value.clientes.size)
    }

    /**
     * `recargar()` no tira el chip ni el texto buscado que el cobrador ya había
     * elegido. A diferencia del detalle, acá no hace falta un `copy` explícito
     * para conservarlos: viven en el `SavedStateHandle`, no en el estado que
     * [leer]/[proyectar] reconstruyen, así que sobreviven por construcción —
     * este test lo cobra observando el resultado, no confiando en el porqué.
     */
    @Test
    fun `recargar conserva el chip y la busqueda elegidos por el usuario`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.buscar("Zepeda")
        vm.elegirSegmento(SegmentoDeCobranza.PAGADOS)
        advanceUntilIdle()

        vm.recargar()
        advanceUntilIdle()

        assertEquals("Zepeda", vm.state.value.query)
        assertEquals(SegmentoDeCobranza.PAGADOS, vm.state.value.segmento)
    }

    /**
     * **`recargar()` no parpadea.** A diferencia de
     * [ListaDeClientesViewModel.cargar] —que enciende `cargando` antes de
     * leer—, `recargar()` nunca lo hace. Se mide sobre la SECUENCIA de estados
     * emitidos y no solo sobre el valor final, porque un `cargando` que se
     * prendiera y apagara en el mismo tick no se vería mirando sólo
     * `state.value` al final.
     *
     * El contraste con `cargar()` es el control positivo: la MISMA forma de
     * medir sí ve el encendido cuando de verdad ocurre.
     */
    @Test
    fun `recargar no enciende cargando, a diferencia de cargar`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val estados = mutableListOf<Boolean>()
        backgroundScope.launch { vm.state.collect { estados += it.cargando } }
        // `advanceUntilIdle` y no `runCurrent`: con `runCurrent` el colector
        // todavía no había arrancado, así que las emisiones de la recarga no
        // llegaban a la lista y la prueba se caía por su propio control de
        // "no midió nada" — el control hizo exactamente su trabajo.
        advanceUntilIdle()
        estados.clear() // solo interesan las emisiones de aquí en adelante

        ventasPort.ventas = ventasPort.ventas + ventaDeEsperanza()
        vm.recargar()
        advanceUntilIdle()

        assertTrue(
            "recargar encendió cargando en algún momento observable: $estados",
            estados.none { it }
        )
        assertTrue(
            "recargar no produjo ninguna emisión nueva: esta prueba no midió nada",
            estados.isNotEmpty()
        )

        estados.clear()
        vm.cargar()
        advanceUntilIdle()

        assertTrue(
            "cargar ya no enciende cargando: el contraste de arriba no prueba nada",
            estados.any { it }
        )
    }
}
