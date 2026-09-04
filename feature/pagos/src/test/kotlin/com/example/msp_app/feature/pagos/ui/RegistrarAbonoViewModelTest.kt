package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarDetalleVenta
import com.example.msp_app.feature.pagos.application.DerivarEstadoDelPeriodo
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.application.RegistrarAbono
import com.example.msp_app.feature.pagos.application.ResolverVentanaDeCobro
import com.example.msp_app.feature.pagos.application.ReunirCobranzaDelCliente
import com.example.msp_app.feature.pagos.data.fake.FakeGarantiasPort
import com.example.msp_app.feature.pagos.data.fake.FakeLiquidacionPort
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePeriodoDeCobroPort
import com.example.msp_app.feature.pagos.data.fake.FakeRegistroDeAbonoPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.data.fake.FakeVisitasPort
import com.example.msp_app.feature.pagos.domain.MontosSugeridos
import com.example.msp_app.feature.pagos.domain.RarezaDelAbono
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.port.ResultadoDelAbono
import java.math.BigDecimal
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
 * El ViewModel de la pantalla del dinero.
 *
 * Lo que estas pruebas protegen, en orden de gravedad:
 *
 * 1. **Ninguna ruta guarda dos veces** — doble toque, rotación y muerte de
 *    proceso entre pasos.
 * 2. **Ningún monto mayor al saldo llega al puerto**, ni por el CTA ni saltándose
 *    la pantalla.
 * 3. **Abandonar en el paso dos no guarda nada.**
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por camino; juntarlas escondería cuál se rompió.
class RegistrarAbonoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val clock = FakeClock(PagosFixtures.AHORA)
    private val telemetria = RecordingTelemetry(clock)

    private val ventasPort = FakeVentasPort()
    private val pagosPort = FakePagosPort()
    private val visitasPort = FakeVisitasPort()
    private val liquidacionPort = FakeLiquidacionPort()
    private val garantiasPort = FakeGarantiasPort()
    private val periodoPort = FakePeriodoDeCobroPort()
    private val registroPort = FakeRegistroDeAbonoPort()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventasPort.ventas = listOf(AbonoFixtures.datosDeVenta())
        liquidacionPort.liquidaciones = mapOf(
            AbonoFixtures.VENTA_ID to Liquidacion(
                monto = AbonoFixtures.LIQUIDACION,
                vigenteHasta = null,
                categoria = "precio a 4 meses"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Carga ---------------------------------------------------------------

    @Test
    fun `arranca con lo esperado hoy puesto y los tres sugeridos`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val state = vm.state.value
        assertEquals(AbonoFixtures.SALDO, state.venta!!.saldo)
        assertEquals(AbonoFixtures.ESPERADO_HOY, state.monto.importe)
        assertTrue("el prellenado se reemplaza con la primera tecla", state.monto.sugerido)
        assertEquals(
            listOf(
                MontosSugeridos.Sugerencia.ESPERADO_HOY,
                MontosSugeridos.Sugerencia.AL_CORRIENTE,
                MontosSugeridos.Sugerencia.LIQUIDAR
            ),
            state.sugeridos.map { it.cual }
        )
        assertTrue(state.sePuedeRegistrar)
    }

    // --- El borde exacto del sobrepago ---------------------------------------

    @Test
    fun `el saldo exacto se registra`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        teclear(vm, "1450")
        assertTrue(vm.state.value.sePuedeRegistrar)
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1, registroPort.registrados.size)
        assertEquals(AbonoFixtures.SALDO, registroPort.registrados.single().importe)
    }

    @Test
    fun `el saldo mas uno apaga el CTA y nunca llega al puerto`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        teclear(vm, "1451")
        assertFalse(vm.state.value.sePuedeRegistrar)
        // Ni por el CTA...
        vm.pedirConfirmacion()
        assertNull("el paso dos no se abre sobre un monto bloqueado", vm.state.value.confirmacion)
        // ...ni saltándoselo.
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(0, registroPort.registrados.size)
    }

    @Test
    fun `un sobrepago que se cuela al paso dos tampoco escribe`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        // Paso uno con un monto sano...
        vm.pedirConfirmacion()
        assertNotNull(vm.state.value.confirmacion)
        // ...y la venta cambia bajo los pies: el saldo baja por debajo del monto.
        ventasPort.ventas = listOf(AbonoFixtures.datosDeVenta().copy(saldo = dinero("10")))
        vm.cargar()
        advanceUntilIdle()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(0, registroPort.registrados.size)
    }

    // --- Los dos pasos -------------------------------------------------------

    @Test
    fun `el CTA no escribe, solo abre el paso dos`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        advanceUntilIdle()
        assertNotNull(vm.state.value.confirmacion)
        assertEquals(0, registroPort.registrados.size)
    }

    @Test
    fun `abandonar en el paso dos no guarda nada`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.descartarConfirmacion()
        advanceUntilIdle()
        assertNull(vm.state.value.confirmacion)
        assertEquals(0, registroPort.registrados.size)
        assertNull(vm.state.value.registrado)
    }

    @Test
    fun `confirmar sin haber pasado por el paso uno no guarda`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(0, registroPort.registrados.size)
    }

    @Test
    fun `el paso dos congela lo que se va a registrar`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        val congelado = vm.state.value.confirmacion!!
        // Teclear con la hoja arriba no mueve lo confirmado.
        vm.onDigito(9)
        assertEquals(congelado, vm.state.value.confirmacion)
        assertEquals(AbonoFixtures.ESPERADO_HOY, vm.state.value.confirmacion!!.importe)
    }

    // --- Ninguna ruta guarda dos veces ---------------------------------------

    @Test
    fun `un doble toque rapido guarda UNA vez`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        vm.confirmar()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1, registroPort.registrados.size)
    }

    @Test
    fun `confirmar despues de un registro exitoso no guarda otra vez`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(vm.abonoId, vm.state.value.registrado)
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1, registroPort.registrados.size)
    }

    @Test
    fun `una rotacion despues de guardar no vuelve a guardar`() = runTest(testDispatcher) {
        val handle = handle()
        val vm = viewModel(handle)
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        aterrizarEnRoom(registroPort.registrados.single().abonoId)

        // El ViewModel se recrea con el MISMO SavedStateHandle: es lo que pasa al
        // rotar el teléfono y al volver de la cámara con el proceso muerto.
        val recreado = viewModel(handle)
        advanceUntilIdle()
        assertEquals("la clave de idempotencia sobrevive", vm.abonoId, recreado.abonoId)
        assertEquals(
            "la pantalla vuelve en su final, no en captura",
            vm.abonoId,
            recreado.state.value.registrado
        )
        assertFalse(recreado.state.value.sePuedeRegistrar)
        recreado.pedirConfirmacion()
        recreado.confirmar()
        advanceUntilIdle()
        assertEquals(1, registroPort.registrados.size)
    }

    @Test
    fun `muerte de proceso entre los dos pasos no deja un abono a medias`() = runTest(
        testDispatcher
    ) {
        val handle = handle()
        val vm = viewModel(handle)
        advanceUntilIdle()
        // Paso uno dado, paso dos NO: el proceso muere aquí.
        vm.pedirConfirmacion()
        val recreado = viewModel(handle)
        advanceUntilIdle()
        assertNull(
            "el paso dos no sobrevive: hay que volver a confirmar",
            recreado.state.value.confirmacion
        )
        recreado.confirmar()
        advanceUntilIdle()
        assertEquals(0, registroPort.registrados.size)
    }

    @Test
    fun `el guard puesto sin abono en el historial se libera y se reporta`() = runTest(
        testDispatcher
    ) {
        // El proceso murió entre el toque y la transacción: el guard quedó puesto
        // y el abono no existe. Declararlo registrado perdería dinero de verdad.
        val handle = handle(guardPuesto = true)
        val vm = viewModel(handle)
        advanceUntilIdle()
        assertNull(vm.state.value.registrado)
        assertTrue(vm.state.value.sePuedeRegistrar)
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.ERROR &&
                    it.name == PagosTelemetria.CODE_ABONO_GUARD_SIN_ABONO
            }
        )
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(
            "el abono perdido sí se puede capturar de nuevo",
            1,
            registroPort.registrados.size
        )
    }

    @Test
    fun `el guard puesto CON abono en el historial deja la pantalla en su final`() = runTest(
        testDispatcher
    ) {
        aterrizarEnRoom(ABONO_FIJO)
        val vm = viewModel(handle(guardPuesto = true))
        advanceUntilIdle()
        assertEquals(ABONO_FIJO, vm.state.value.registrado)
        assertFalse(vm.state.value.sePuedeRegistrar)
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(0, registroPort.registrados.size)
    }

    // --- Fallos --------------------------------------------------------------

    @Test
    fun `un fallo libera el guard, se reporta, y el reintento usa la MISMA clave`() = runTest(
        testDispatcher
    ) {
        registroPort.resultado = ResultadoDelAbono.FALLO_EL_GUARDADO
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(FalloDelAbono.NO_SE_PUDO_GUARDAR, vm.state.value.fallo)
        assertNull(vm.state.value.registrado)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_NO_QUEDO_REGISTRADO
        }
        assertEquals(
            ResultadoDelAbono.FALLO_EL_GUARDADO.name,
            error.props[PagosTelemetria.PROP_RESULTADO]
        )
        assertTrue(
            "la pantalla NO reusa el codigo del adaptador: una falla se cuenta una vez",
            telemetria.recorded.none { it.name == PagosTelemetria.CODE_ABONO_NO_SE_GUARDO }
        )

        registroPort.resultado = ResultadoDelAbono.REGISTRADO
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(2, registroPort.registrados.size)
        assertEquals(
            "un reintento con la misma clave no puede volverse un segundo cobro",
            registroPort.registrados[0].abonoId,
            registroPort.registrados[1].abonoId
        )
    }

    @Test
    fun `un fallo cuyo abono SI aterrizo no libera el guard ni ofrece otro cobro`() = runTest(
        testDispatcher
    ) {
        // El puerto reporta fallo, pero la escritura sí aterrizó (el resultado se
        // perdió en el camino). Soltar el guard aquí sería ofrecer un SEGUNDO
        // cobro por el mismo abono.
        registroPort.resultado = ResultadoDelAbono.FALLO_EL_GUARDADO
        registroPort.alRegistrar = { aterrizarEnRoom(it.abonoId) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()

        assertEquals("la pantalla queda en su final", vm.abonoId, vm.state.value.registrado)
        assertNull(vm.state.value.fallo)
        assertFalse(vm.state.value.sePuedeRegistrar)
        assertTrue(
            telemetria.recorded.any {
                it.type == TelemetryEventType.ERROR &&
                    it.name == PagosTelemetria.CODE_ABONO_FALLO_PERO_SI_QUEDO
            }
        )

        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals("y no se cobra de nuevo", 1, registroPort.registrados.size)
    }

    @Test
    fun `si no se puede comprobar, el guard NO se libera`() = runTest(testDispatcher) {
        // Nada se sabe: la relectura truena. Un "no se sabe" no es un "no quedó",
        // y soltar el guard sobre esa suposición es el defecto entero.
        registroPort.resultado = ResultadoDelAbono.FALLO_EL_GUARDADO
        registroPort.alRegistrar = { ventasPort.falla = IllegalStateException("room caido") }
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()

        assertEquals(FalloDelAbono.NO_SE_PUDO_VERIFICAR, vm.state.value.fallo)
        assertNull(vm.state.value.registrado)
        // El CTA se APAGA: un botón vivo que no hace nada es como un cobrador
        // decide que la app está rota. La salida es el reintento de la banda.
        assertFalse("el CTA no puede quedar vivo y mudo", vm.state.value.sePuedeRegistrar)
        assertTrue("y la banda ofrece volver a revisar", vm.state.value.sePuedeRevisar)
        // Y NO se afirma lo que se acaba de declarar incognoscible.
        assertTrue(
            "no se dice 'no quedo registrado' despues de concluir que no se sabe",
            telemetria.recorded.none {
                it.name == PagosTelemetria.CODE_ABONO_NO_QUEDO_REGISTRADO
            }
        )
        val sinVerificar = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_SIN_VERIFICAR
        }
        assertEquals(
            "el error no se traga: viaja la clase de la excepcion",
            "IllegalStateException",
            sinVerificar.props[PagosTelemetria.PROP_EXCEPCION]
        )
        assertFalse(sinVerificar.props.values.any { it.contains("room caido") })

        ventasPort.falla = null
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(
            "el guard sigue puesto: nadie cobra dos veces",
            1,
            registroPort.registrados.size
        )
    }

    @Test
    fun `el unico evento de la duda es el suyo, con su causa y su resultado`() = runTest(
        testDispatcher
    ) {
        registroPort.resultado = ResultadoDelAbono.FALLO_EL_GUARDADO
        registroPort.alRegistrar = { ventasPort.falla = IllegalStateException("room caido") }
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()

        val evento = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_ABONO_SIN_VERIFICAR
        }
        assertEquals(
            ResultadoDelAbono.FALLO_EL_GUARDADO.name,
            evento.props[PagosTelemetria.PROP_RESULTADO]
        )
        assertEquals("IllegalStateException", evento.props[PagosTelemetria.PROP_EXCEPCION])
        assertEquals(
            "una sola falla, un solo evento",
            1,
            telemetria.recorded.count { it.type == TelemetryEventType.ERROR }
        )
    }

    @Test
    fun `volver a revisar resuelve la duda sin salir de la pantalla`() = runTest(testDispatcher) {
        // El reintento REAL: `cargar()` vuelve a leer y `resolverGuard` contesta
        // la pregunta mirando el historial. Aquí el abono SÍ había aterrizado.
        registroPort.resultado = ResultadoDelAbono.FALLO_EL_GUARDADO
        registroPort.alRegistrar = {
            aterrizarEnRoom(it.abonoId)
            ventasPort.falla = IllegalStateException("room caido")
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(FalloDelAbono.NO_SE_PUDO_VERIFICAR, vm.state.value.fallo)
        assertFalse(vm.state.value.sePuedeRegistrar)

        // La banda es tocable y hace algo de verdad.
        ventasPort.falla = null
        vm.cargar()
        advanceUntilIdle()

        assertEquals("la duda se resolvio: el abono estaba", vm.abonoId, vm.state.value.registrado)
        assertNull(vm.state.value.fallo)
        assertEquals("y no se cobro de nuevo", 1, registroPort.registrados.size)
    }

    @Test
    fun `volver a revisar tambien libera el guard cuando el abono NO estaba`() = runTest(
        testDispatcher
    ) {
        registroPort.resultado = ResultadoDelAbono.FALLO_EL_GUARDADO
        registroPort.alRegistrar = { ventasPort.falla = IllegalStateException("room caido") }
        val vm = viewModel()
        advanceUntilIdle()
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()

        ventasPort.falla = null
        vm.cargar()
        advanceUntilIdle()

        assertNull(vm.state.value.registrado)
        assertTrue("el cobrador puede capturar de nuevo", vm.state.value.sePuedeRegistrar)
        registroPort.resultado = ResultadoDelAbono.REGISTRADO
        registroPort.alRegistrar = {}
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(vm.abonoId, vm.state.value.registrado)
    }

    @Test
    fun `una venta que no esta no deja capturar`() = runTest(testDispatcher) {
        ventasPort.ventas = emptyList()
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ErrorDeDetalle.NO_ESTA_EN_EL_TELEFONO, vm.state.value.error)
        assertFalse(vm.state.value.sePuedeRegistrar)
    }

    @Test
    fun `un puerto que falla degrada a error y lo reporta sin PII`() = runTest(testDispatcher) {
        ventasPort.falla = IllegalStateException("room caido con datos de Victoria Flores")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(ErrorDeDetalle.FALLO_LA_CARGA, vm.state.value.error)
        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR && it.name == PagosTelemetria.CODE_ABONO_VENTA_FALLO
        }
        assertEquals("IllegalStateException", error.props[PagosTelemetria.PROP_EXCEPCION])
        assertFalse(error.props.values.any { it.contains("Victoria") })
    }

    // --- Rarezas y método ----------------------------------------------------

    /**
     * La regla de soberanía, con control positivo y negativo sobre la MISMA
     * carga: una visita del periodo SÍ se lee y SÍ mueve el estado de la cuenta,
     * y aun así **no** enciende la rareza de duplicado. Lo único que la enciende
     * es dinero.
     *
     * (Antes esto afirmaba `visitasPort.visitas.isEmpty()` — la semilla del
     * fake, cierta por construcción. No probaba nada.)
     */
    @Test
    fun `la rareza de duplicado sale del dinero del periodo, no de una visita`() = runTest(
        testDispatcher
    ) {
        // Hay visita en el periodo, y no hay dinero.
        visitasPort.visitas = listOf(
            PagosFixtures.visita(TipoVisitaCatalogo.NO_SE_ENCONTRABA)
        )
        val sinDinero = viewModel()
        advanceUntilIdle()

        assertTrue(
            "la visita SI se leyó: el puerto registró la consulta",
            visitasPort.clientesConsultados.contains(PagosFixtures.CLIENTE_ID)
        )
        assertEquals(
            "y SI movió el estado de la cuenta",
            EstadoCuenta.NO_ESTABA,
            sinDinero.state.value.venta!!.estado.estado
        )
        assertEquals(Money.ZERO, sinDinero.state.value.venta!!.estado.abonoDelPeriodo)
        assertFalse(
            "pero NO enciende el duplicado: eso lo decide el dinero",
            RarezaDelAbono.YA_ABONO_ESTE_PERIODO in sinDinero.state.value.veredicto.rarezas
        )

        // Ahora el dinero, con la MISMA visita puesta.
        pagosPort.pagos = listOf(AbonoFixtures.abonoDeEstaSemana())
        val conDinero = viewModel()
        advanceUntilIdle()
        assertTrue(conDinero.state.value.venta!!.estado.abonoDelPeriodo > Money.ZERO)
        assertTrue(
            RarezaDelAbono.YA_ABONO_ESTE_PERIODO in conDinero.state.value.veredicto.rarezas
        )
        assertTrue("una rareza nunca bloquea", conDinero.state.value.sePuedeRegistrar)
    }

    @Test
    fun `un monto raro sigue siendo registrable tras afirmarlo`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        teclear(vm, "1100")
        assertTrue(RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO in vm.state.value.veredicto.rarezas)
        vm.pedirConfirmacion()
        assertTrue(vm.state.value.confirmacion!!.veredicto.esRaro)
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(1, registroPort.registrados.size)
    }

    @Test
    fun `el metodo viaja al puerto y solo son efectivo y transferencia`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(MetodoDeCobro.EFECTIVO, vm.state.value.metodo)
        vm.onMetodo(MetodoDeCobro.TRANSFERENCIA)
        vm.pedirConfirmacion()
        vm.confirmar()
        advanceUntilIdle()
        assertEquals(MetodoDeCobro.TRANSFERENCIA, registroPort.registrados.single().metodo)
    }

    @Test
    fun `un chip sugerido nunca puede llenar el teclado con un sobrepago`() = runTest(
        testDispatcher
    ) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.state.value.sugeridos.forEach { sugerido ->
            vm.onSugerido(sugerido.importe)
            assertTrue(
                "${sugerido.cual} dejó la pantalla bloqueada",
                vm.state.value.sePuedeRegistrar
            )
        }
    }

    // --- Plomería ------------------------------------------------------------

    private fun teclear(vm: RegistrarAbonoViewModel, pesos: String) {
        vm.onBorrar()
        pesos.forEach { caracter ->
            if (caracter == '.') vm.onPunto() else vm.onDigito(caracter.digitToInt())
        }
    }

    /** Simula que el abono SÍ aterrizó en Room: aparece en el historial de la venta. */
    private fun aterrizarEnRoom(abonoId: String) {
        pagosPort.pagos = pagosPort.pagos + PagoDelHistorial(
            pagoId = abonoId,
            ventaId = AbonoFixtures.VENTA_ID,
            fecha = PagosFixtures.AHORA,
            importe = AbonoFixtures.ESPERADO_HOY,
            formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
            metodo = MetodoDeCobro.EFECTIVO,
            nota = null
        )
    }

    /**
     * El `SavedStateHandle` del destino. [guardPuesto] simula volver con el guard
     * anti-duplicado ya escrito; la clave del abono se fija para poder sembrar el
     * historial con ella.
     */
    private fun handle(guardPuesto: Boolean = false) = SavedStateHandle(
        mapOf(
            PagosRutas.ARG_VENTA_ID to AbonoFixtures.VENTA_ID,
            CLAVE_ABONO_ID to ABONO_FIJO,
            CLAVE_YA_SE_ENCOLO to guardPuesto
        )
    )

    private fun viewModel(savedStateHandle: SavedStateHandle = handle()) = RegistrarAbonoViewModel(
        savedStateHandle = savedStateHandle,
        cargarDetalleVenta = CargarDetalleVenta(
            ventasPort = ventasPort,
            garantiasPort = garantiasPort,
            reunirCobranzaDelCliente = ReunirCobranzaDelCliente(
                ventasPort = ventasPort,
                pagosPort = pagosPort,
                visitasPort = visitasPort,
                liquidacionPort = liquidacionPort,
                resolverVentanaDeCobro = ResolverVentanaDeCobro(periodoPort, clock),
                derivarEstadoDelPeriodo = DerivarEstadoDelPeriodo(telemetria)
            ),
            clock = clock
        ),
        registrarAbono = RegistrarAbono(registroPort, telemetria),
        telemetry = telemetria,
        clock = clock,
        io = testDispatcher
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private companion object {
        /**
         * Las MISMAS llaves que el ViewModel persiste. Escritas a mano a
         * propósito: son el contrato que tiene que sobrevivir a la muerte del
         * proceso, y un test que las tomara de la constante privada no notaría
         * que alguien las renombró.
         */
        const val CLAVE_ABONO_ID = "pagos_abono_id"
        const val CLAVE_YA_SE_ENCOLO = "pagos_abono_ya_se_encolo"
        const val ABONO_FIJO = "abono-de-prueba-0001"
    }
}
