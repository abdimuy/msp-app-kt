package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.ControladorDeOrden
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCartera
import com.example.msp_app.feature.pagos.application.TogglesDeCabecera
import com.example.msp_app.feature.pagos.data.fake.FakeCercaniaPort
import com.example.msp_app.feature.pagos.data.fake.FakeOrdenGuardadoPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakePrivacidadPort
import com.example.msp_app.feature.pagos.data.fake.FakeTemaDeLaAppPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.model.VisitaDelCliente
import com.example.msp_app.feature.pagos.domain.port.OrdenGuardado
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
    private val cercaniaPort = FakeCercaniaPort()
    private val ordenGuardadoPort = FakeOrdenGuardadoPort()

    /**
     * Un [ControladorDeOrden] nuevo por llamada — como en producción, donde
     * Hilt arma uno por instancia de ViewModel (sin `@Singleton`). Los fakes
     * que envuelve SÍ son compartidos ([cercaniaPort], [ordenGuardadoPort]):
     * son ellos quienes sostienen el estado que las pruebas afirman.
     */
    private fun controladorDeOrden() = ControladorDeOrden(
        cercaniaPort,
        ordenGuardadoPort,
        testDispatcher
    )

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
        toggles = TogglesDeCabecera(temaPort, FakePrivacidadPort()),
        controladorDeOrden = controladorDeOrden(),
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
            toggles = TogglesDeCabecera(temaPort, FakePrivacidadPort()),
            controladorDeOrden = controladorDeOrden(),
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

    // --- Orden (hoja "Ordenar por", decisión del dueño 2026-10-05) -----------

    /**
     * Bajo *sin visitar* cobranza pone a Ricardo primero (su venta es la más
     * vieja sin abonos). Victoria debe $7,600 contra los $4,300 de Ricardo, así
     * que "Mayor saldo" la pone primero — el control positivo de que SÍ
     * reproyectó con el orden nuevo, y no sólo cambió el campo del estado.
     */
    @Test
    fun `elegir un orden reproyecta la lista`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(OrdenDeLaLista.COBRANZA, vm.state.value.orden)
        assertEquals(
            listOf(ListaFixtures.RICARDO, ListaFixtures.VICTORIA),
            vm.state.value.clientes.map { it.clienteId }
        )

        vm.elegirOrden(OrdenDeLaLista.MAYOR_SALDO)
        advanceUntilIdle()

        assertEquals(OrdenDeLaLista.MAYOR_SALDO, vm.state.value.orden)
        assertEquals(
            listOf(ListaFixtures.VICTORIA, ListaFixtures.RICARDO),
            vm.state.value.clientes.map { it.clienteId }
        )
    }

    @Test
    fun `un orden guardado hoy se restaura al cargar`() = runTest(testDispatcher) {
        ordenGuardadoPort.guardado = OrdenGuardado(OrdenDeLaLista.MAYOR_SALDO, ListaFixtures.HOY)
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(OrdenDeLaLista.MAYOR_SALDO, vm.state.value.orden)
    }

    /**
     * **Control positivo de [restaurarOrden][ListaDeClientesViewModel]**: un
     * orden guardado un día distinto de `hoy` NO se restaura — cae a
     * [OrdenDeLaLista.COBRANZA], el default. Sin este caso, el test de arriba
     * no distingue "se restauró porque el día coincide" de "nunca se
     * comparó el día".
     */
    @Test
    fun `un orden guardado ayer cae a Cobranza`() = runTest(testDispatcher) {
        ordenGuardadoPort.guardado = OrdenGuardado(
            OrdenDeLaLista.MAYOR_SALDO,
            ListaFixtures.HOY.minusDays(1)
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(OrdenDeLaLista.COBRANZA, vm.state.value.orden)
    }

    @Test
    fun `quitar el orden vuelve a Cobranza y lo persiste`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.elegirOrden(OrdenDeLaLista.MAYOR_SALDO)
        advanceUntilIdle()
        assertEquals(OrdenDeLaLista.MAYOR_SALDO, vm.state.value.orden)

        vm.quitarOrden()
        advanceUntilIdle()

        assertEquals(OrdenDeLaLista.COBRANZA, vm.state.value.orden)
        assertEquals(OrdenDeLaLista.COBRANZA, ordenGuardadoPort.guardado?.orden)
    }

    /**
     * **Hallazgo 4 de la revisión final** (`.superpowers/sdd/2026-10-06-menu-lateral/final-fix-report.md`):
     * sólo [ListaDeClientesViewModel.cargar] restauraba el orden persistido —
     * `recargar()` nunca lo volvía a hacer, así que si la app se quedaba
     * abierta de un día para el otro, el orden de AYER seguía vigente después
     * de medianoche en vez de caer a cobranza como marca la regla "durante el
     * día".
     */
    @Test
    fun `recargar con dia distinto al restaurado vuelve a aplicar la regla del dia`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.elegirOrden(OrdenDeLaLista.MAYOR_SALDO)
        advanceUntilIdle()
        assertEquals(OrdenDeLaLista.MAYOR_SALDO, vm.state.value.orden)

        // "La app quedó abierta de noche": el reloj avanza un día sin que
        // nadie vuelva a entrar a la pantalla ni a invocar cargar().
        clock.advanceDays(1)
        vm.recargar()
        advanceUntilIdle()

        // Nada se guardó para el día nuevo, así que debe caer a Cobranza —
        // igual que ya hace `cargar()` con un orden de ayer.
        assertEquals(OrdenDeLaLista.COBRANZA, vm.state.value.orden)
    }

    /**
     * Control positivo del hallazgo 4: el mismo día, `recargar()` NO debe
     * pisar una elección reciente con la persistencia — ver el KDoc de
     * [ListaDeClientesViewModel.recargar]. Sin este caso, el test de arriba no
     * distingue "restaura porque cambió el día" de "restaura siempre".
     */
    @Test
    fun `recargar el mismo dia no repite la restauracion ni pisa el orden elegido`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.elegirOrden(OrdenDeLaLista.MAYOR_SALDO)
        advanceUntilIdle()

        vm.recargar()
        advanceUntilIdle()

        assertEquals(OrdenDeLaLista.MAYOR_SALDO, vm.state.value.orden)
    }

    @Test
    fun `elegir Mas cerca pide distancias`() = runTest(testDispatcher) {
        cercaniaPort.mapa = mapOf(78400 to 120.0)
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirOrden(OrdenDeLaLista.MAS_CERCA)
        advanceUntilIdle()

        assertEquals(OrdenDeLaLista.MAS_CERCA, vm.state.value.orden)
        assertEquals(1, cercaniaPort.lecturas)
        assertFalse(vm.state.value.avisoSinUbicacion)
    }

    /**
     * Mapa vacío de [FakeCercaniaPort] (su default): "Más cerca" no puede
     * tronar ni dejar la lista en un orden que nadie pidió. `CarteraEnPantalla`
     * ya garantiza la caída a cobranza; acá se mide que el aviso se enciende.
     */
    @Test
    fun `Mas cerca sin ubicaciones deja la lista en orden de cobranza y avisa`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.elegirOrden(OrdenDeLaLista.MAS_CERCA)
        advanceUntilIdle()

        assertTrue(vm.state.value.avisoSinUbicacion)
        assertEquals(
            listOf(ListaFixtures.RICARDO, ListaFixtures.VICTORIA),
            vm.state.value.clientes.map { it.clienteId }
        )
    }

    @Test
    fun `recargar con Mas cerca activo vuelve a pedir distancias`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.elegirOrden(OrdenDeLaLista.MAS_CERCA)
        advanceUntilIdle()
        assertEquals(1, cercaniaPort.lecturas)

        vm.recargar()
        advanceUntilIdle()

        assertEquals(2, cercaniaPort.lecturas)
    }

    /**
     * **Hallazgo 3 de la revisión final** (`.superpowers/sdd/2026-10-06-menu-lateral/final-fix-report.md`):
     * con "Más cerca" restaurado, `cargar()` esperaba a [CercaniaPort] ANTES de
     * proyectar la cartera recién leída — un GPS lento (o que nunca contesta)
     * dejaba la tarjeta vieja en pantalla indefinidamente, aunque la cartera
     * fresca ya estuviera en memoria. Riesgo de dinero: al volver de registrar
     * un abono, la tarjeta sigue vieja hasta que llega la posición.
     *
     * [FakeCercaniaPort.espera] fuerza exactamente eso: una lectura de
     * ubicación que se queda suspendida hasta que el test la suelta.
     */
    @Test
    fun `con Mas Cerca la cartera se proyecta antes de esperar las distancias`() = runTest(
        testDispatcher
    ) {
        ordenGuardadoPort.guardado = OrdenGuardado(OrdenDeLaLista.MAS_CERCA, ListaFixtures.HOY)
        val espera = kotlinx.coroutines.CompletableDeferred<Map<Int, Double>>()
        cercaniaPort.espera = espera

        val vm = viewModel()
        // `runCurrent()`, no `advanceUntilIdle()`: este último avanza el reloj
        // virtual hasta agotar TODO lo programado, incluido el
        // `withTimeoutOrNull` de 8s de `ControladorDeOrden` — y eso resolvería
        // el timeout antes de que el test alcance a soltar `espera`, dejando
        // esta prueba en verde por la razón equivocada. `runCurrent()` corre
        // el trabajo YA listo sin tocar el reloj, así que la corrutina queda
        // de verdad suspendida en `espera.await()`.
        testDispatcher.scheduler.runCurrent()

        // La cartera YA se proyectó: no está cargando y ya hay clientes,
        // aunque el GPS (el `espera` sin soltar) no haya contestado.
        assertFalse("no debe quedarse 'cargando' esperando al GPS", vm.state.value.cargando)
        assertEquals(
            listOf(ListaFixtures.RICARDO, ListaFixtures.VICTORIA),
            vm.state.value.clientes.map { it.clienteId }
        )

        // Distancia sólo para la venta de Victoria (77021): si el reorden de
        // verdad ocurre, Victoria pasa al frente — distinto del orden de
        // cobranza [Ricardo, Victoria] de arriba, así que el cambio no es casualidad.
        espera.complete(mapOf(77021 to 10.0))
        testDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.avisoSinUbicacion)
        assertEquals(1, cercaniaPort.lecturas)
        assertEquals(
            listOf(ListaFixtures.VICTORIA, ListaFixtures.RICARDO),
            vm.state.value.clientes.map { it.clienteId }
        )
    }
}
