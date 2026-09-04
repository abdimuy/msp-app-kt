package com.example.msp_app.feature.pagos.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.printing.application.EvaluatePrintPermission
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.application.PrintTicketUseCase
import com.example.msp_app.core.printing.application.PrinterDirectory
import com.example.msp_app.core.printing.application.TicketPrinting
import com.example.msp_app.core.printing.domain.PrintError
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.pagos.application.CargarTicketDePago
import com.example.msp_app.feature.pagos.application.PagosTelemetria
import com.example.msp_app.feature.pagos.data.fake.FakeImpresoraPreferida
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakePrintLog
import com.example.msp_app.feature.pagos.data.fake.FakePrinterPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import java.math.BigDecimal
import java.time.Instant
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
 * **Las dos mitades de la regla del mock, de punta a punta.**
 *
 * 1. *El ticket solo se imprime el día del cobro* — con sus tres bordes: el día
 *    mismo, el día ANTERIOR y el día SIGUIENTE. El reloj es siempre [FakeClock];
 *    ningún camino de esta pantalla llama a `Instant.now()`.
 * 2. *Cada impresión queda registrada* — una segunda impresión se detecta como
 *    reimpresión, la pantalla lo dice y el papel lo lleva estampado.
 *
 * Fakes escritos a mano, sin MockK. `StandardTestDispatcher` +
 * `advanceUntilIdle()`, nunca `UnconfinedTestDispatcher`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por camino; juntarlas escondería cuál se rompió.
class TicketDePagoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val reloj = FakeClock(TicketFixtures.COBRADO_EN)
    private val telemetria = RecordingTelemetry(reloj)

    private val ventas = FakeVentasPort()
    private val pagos = FakePagosPort()
    private val printer = FakePrinterPort()
    private val preferida = FakeImpresoraPreferida()
    private val log = FakePrintLog()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        ventas.ventas = listOf(
            PagosFixtures.datosDeVenta(
                ventaId = PagosFixtures.VENTA_EN_PROMESA,
                folio = "V-5188",
                descripcion = "Refrigerador Mabe 14'",
                cifras = PagosFixtures.Cifras(
                    total = Money.of(BigDecimal("6400")),
                    restante = Money.of(BigDecimal("1450")),
                    cuota = Money.of(BigDecimal("220")),
                    cubierto = Money.of(BigDecimal("4950"))
                )
            )
        )
        pagos.pagos = listOf(
            PagoDelHistorial(
                pagoId = TicketFixtures.PAGO_ID,
                ventaId = PagosFixtures.VENTA_EN_PROMESA,
                fecha = TicketFixtures.COBRADO_EN,
                importe = Money.of(BigDecimal("350")),
                formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
                metodo = MetodoDeCobro.EFECTIVO,
                nota = null,
                cobrador = "Martín Salgado"
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(pagoId: String = TicketFixtures.PAGO_ID) = TicketDePagoViewModel(
        savedStateHandle = SavedStateHandle(mapOf(PagosRutas.ARG_PAGO_ID to pagoId)),
        cargarTicket = CargarTicketDePago(pagos, ventas),
        impresion = TicketPrinting(
            directorio = PrinterDirectory(printer, preferida),
            evaluar = EvaluatePrintPermission(log, reloj),
            imprimirYRegistrar = PrintTicketUseCase(printer, log, reloj)
        ),
        telemetry = telemetria,
        io = testDispatcher
    )

    // region — la regla del día del cobro, con sus tres bordes -------------------

    @Test
    fun `el dia del cobro se puede imprimir`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PrintPermission.PrimeraImpresion, vm.state.value.permiso)
        assertTrue(vm.state.value.sePuedeImprimir)
        assertFalse(vm.state.value.fueraDelDia)
    }

    @Test
    fun `el dia SIGUIENTE no se puede imprimir`() = runTest(testDispatcher) {
        reloj.advanceDays(1)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PrintPermission.FueraDelDia, vm.state.value.permiso)
        assertFalse(vm.state.value.sePuedeImprimir)
        assertTrue(vm.state.value.fueraDelDia)
    }

    @Test
    fun `el dia ANTERIOR tampoco se puede imprimir`() = runTest(testDispatcher) {
        reloj.advanceDays(-1)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PrintPermission.FueraDelDia, vm.state.value.permiso)
        assertFalse(vm.state.value.sePuedeImprimir)
    }

    @Test
    fun `fuera del dia el CTA no imprime aunque se toque`() = runTest(testDispatcher) {
        // CON impresora recordada a propósito: así, si la regla del día se
        // rompiera, este toque SÍ imprimiría — que es lo que hace que la
        // ausencia de abajo signifique algo.
        preferida.savePreferredAddress(FakePrinterPort.IMPRESORA.address)
        reloj.advanceDays(1)
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()

        // Control positivo: el grabador SÍ ve impresiones cuando las hay — lo
        // prueba `la primera impresion sale y queda registrada`, con el MISMO
        // fake. Esta ausencia significa algo.
        assertEquals(emptyList<Any>(), printer.impresos)
        assertEquals(emptyList<String>(), log.registrados)
    }

    @Test
    fun `casi medianoche del dia del cobro todavia imprime`() = runTest(testDispatcher) {
        // 23:59 CDMX del día del cobro; en UTC ya es el día siguiente.
        reloj.setNow(Instant.parse("2026-09-02T05:59:00Z"))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PrintPermission.PrimeraImpresion, vm.state.value.permiso)
    }

    @Test
    fun `pasada la medianoche del dia del cobro ya no imprime`() = runTest(testDispatcher) {
        reloj.setNow(Instant.parse("2026-09-02T06:01:00Z")) // 00:01 CDMX del día siguiente

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PrintPermission.FueraDelDia, vm.state.value.permiso)
    }

    // endregion

    // region — el registro de impresiones ---------------------------------------

    @Test
    fun `la primera impresion sale y queda registrada`() = runTest(testDispatcher) {
        preferida.savePreferredAddress(FakePrinterPort.IMPRESORA.address)
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()

        assertEquals(1, printer.impresos.size)
        assertEquals(listOf(TicketFixtures.PAGO_ID), log.registrados)
        assertEquals(FaseDeImpresion.IMPRESO, vm.state.value.impresion.fase)
    }

    @Test
    fun `la segunda impresion se detecta como reimpresion`() = runTest(testDispatcher) {
        preferida.savePreferredAddress(FakePrinterPort.IMPRESORA.address)
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()
        val trasLaPrimera = vm.state.value

        reloj.advanceMinutes(SEIS)
        vm.imprimir()
        advanceUntilIdle()

        // Tras la primera, la pantalla YA avisa que el papel siguiente es copia.
        assertEquals(
            PrintPermission.Reimpresion(previas = 1, primeraVez = TicketFixtures.COBRADO_EN),
            trasLaPrimera.permiso
        )
        assertTrue(trasLaPrimera.esReimpresion)
        assertEquals("copia 2 · 12:00", trasLaPrimera.detalleDeCopia)
        // Y tras la segunda el conteo sube, conservando la hora de la primera.
        assertEquals(
            PrintPermission.Reimpresion(previas = 2, primeraVez = TicketFixtures.COBRADO_EN),
            vm.state.value.permiso
        )
        assertEquals("copia 3 · 12:00", vm.state.value.detalleDeCopia)
    }

    @Test
    fun `el papel de la segunda copia lleva la marca de reimpresion`() = runTest(testDispatcher) {
        preferida.savePreferredAddress(FakePrinterPort.IMPRESORA.address)
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()
        vm.imprimir()
        advanceUntilIdle()

        val primerPapel = printer.impresos[0].toString()
        val segundoPapel = printer.impresos[1].toString()
        assertFalse(primerPapel.contains("REIMPRESION"))
        assertTrue(segundoPapel.contains("REIMPRESION"))
    }

    @Test
    fun `un ticket ya impreso en una sesion anterior abre como reimpresion`() =
        runTest(testDispatcher) {
            log.record(TicketFixtures.PAGO_ID, TicketFixtures.PRIMERA_COPIA)

            val vm = viewModel()
            advanceUntilIdle()

            assertTrue(vm.state.value.esReimpresion)
            assertTrue(vm.state.value.vistaPrevia.contains("REIMPRESION"))
            // Y aun así se puede imprimir: la regla del día lo permite.
            assertTrue(vm.state.value.sePuedeImprimir)
        }

    @Test
    fun `una impresion fallida NO deja registro`() = runTest(testDispatcher) {
        preferida.savePreferredAddress(FakePrinterPort.IMPRESORA.address)
        printer.resultadoDeImprimir = Result.failure(PrintError.BluetoothDisabled)
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), log.registrados)
        assertFalse(vm.state.value.esReimpresion)
        assertEquals(FaseDeImpresion.FALLO, vm.state.value.impresion.fase)
        assertEquals("activa el bluetooth", vm.state.value.impresion.mensaje)
    }

    // endregion

    // region — picker e incidencias ---------------------------------------------

    @Test
    fun `sin impresora recordada se abre el picker en vez de imprimir`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()

        assertEquals(FaseDeImpresion.ELIGIENDO, vm.state.value.impresion.fase)
        assertEquals(listOf(FakePrinterPort.IMPRESORA), vm.state.value.impresion.disponibles)
        assertEquals(emptyList<String>(), log.registrados)
    }

    @Test
    fun `elegir impresora la recuerda e imprime`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.imprimir()
        advanceUntilIdle()

        vm.elegirImpresora(FakePrinterPort.IMPRESORA)
        advanceUntilIdle()

        assertEquals(listOf(FakePrinterPort.IMPRESORA.address), preferida.guardadas)
        assertEquals(1, printer.impresos.size)
        assertEquals(listOf(TicketFixtures.PAGO_ID), log.registrados)
    }

    @Test
    fun `un fallo del puerto se reporta por telemetria con su codigo`() = runTest(testDispatcher) {
        preferida.savePreferredAddress(FakePrinterPort.IMPRESORA.address)
        printer.resultadoDeImprimir = Result.failure(PrintError.WriteFailed())
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()

        val error = telemetria.recorded.single {
            it.type == TelemetryEventType.ERROR &&
                it.name == PagosTelemetria.CODE_TICKET_PAGO_NO_SE_IMPRIMIO
        }
        // Anti-PII: la clase del fallo, nunca la MAC de la impresora.
        assertEquals("WriteFailed", error.props[PagosTelemetria.PROP_EXCEPCION])
        assertFalse(error.props.values.any { it.contains(FakePrinterPort.IMPRESORA.address) })
    }

    @Test
    fun `un abono que el telefono no tiene deja la pantalla en error y se reporta`() =
        runTest(testDispatcher) {
            val vm = viewModel(pagoId = "no-existe")
            advanceUntilIdle()

            assertEquals(ErrorDelTicket.PAGO_NO_ESTA, vm.state.value.error)
            assertFalse(vm.state.value.sePuedeImprimir)
            assertNotNull(
                telemetria.recorded.singleOrNull {
                    it.type == TelemetryEventType.ERROR &&
                        it.name == PagosTelemetria.CODE_TICKET_PAGO_SIN_ABONO
                }
            )
        }

    // endregion

    private companion object {
        const val SEIS = 6L
    }
}
