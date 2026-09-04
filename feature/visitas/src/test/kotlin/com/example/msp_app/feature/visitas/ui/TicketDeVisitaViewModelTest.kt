package com.example.msp_app.feature.visitas.ui

import androidx.lifecycle.SavedStateHandle
import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.printing.application.EvaluatePrintPermission
import com.example.msp_app.core.printing.application.PrintPermission
import com.example.msp_app.core.printing.application.PrintTicketUseCase
import com.example.msp_app.core.printing.application.PrinterDirectory
import com.example.msp_app.core.printing.application.TicketPrinting
import com.example.msp_app.core.printing.domain.PrintError
import com.example.msp_app.core.telemetry.TelemetryEventType
import com.example.msp_app.core.testing.telemetry.RecordingTelemetry
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.feature.visitas.application.CargarTicketDeVisita
import com.example.msp_app.feature.visitas.application.VisitasTelemetria
import com.example.msp_app.feature.visitas.data.fake.FakeContextoDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeImpresoraPreferida
import com.example.msp_app.feature.visitas.data.fake.FakePrintLog
import com.example.msp_app.feature.visitas.data.fake.FakePrinterPort
import com.example.msp_app.feature.visitas.data.fake.FakeVisitaImpresaPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.model.VisitaRegistrada
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
 * **Las dos mitades de la regla del mock, de punta a punta, en el ticket de
 * visita** — gemelas de `TicketDePagoViewModelTest` porque la regla es una sola:
 *
 * 1. *Solo se imprime el día de la visita*, con sus tres bordes: el día mismo, el
 *    ANTERIOR y el SIGUIENTE. El reloj es siempre [FakeClock].
 * 2. *Cada impresión queda registrada*: la segunda se detecta como reimpresión y
 *    el papel la lleva estampada.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // una prueba por camino; juntarlas escondería cuál se rompió.
class TicketDeVisitaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val reloj = FakeClock(TicketDeVisitaFixtures.REGISTRADA_EN)
    private val telemetria = RecordingTelemetry(reloj)

    private val visitas = FakeVisitaImpresaPort()
    private val contextos = FakeContextoDeVisitaPort()
    private val printer = FakePrinterPort()
    private val preferida = FakeImpresoraPreferida()
    private val log = FakePrintLog()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        visitas.visitas = listOf(
            VisitaRegistrada(
                visitaId = TicketDeVisitaFixtures.VISITA_ID,
                clienteId = VisitasFixtures.VICTORIA,
                ventaId = null,
                registradaEn = TicketDeVisitaFixtures.REGISTRADA_EN,
                tipoVisita = TipoVisitaCatalogo.NO_SE_ENCONTRABA,
                cobrador = "Martín Salgado",
                nota = null,
                fechaPromesa = null,
                montoPrometido = null,
                fechaCita = null,
                horaCita = null
            )
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(visitaId: String = TicketDeVisitaFixtures.VISITA_ID) =
        TicketDeVisitaViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf(VisitasRutas.ARG_VISITA_ID to visitaId)
            ),
            cargarTicket = CargarTicketDeVisita(visitas, contextos),
            impresion = TicketPrinting(
                directorio = PrinterDirectory(printer, preferida),
                evaluar = EvaluatePrintPermission(log, reloj),
                imprimirYRegistrar = PrintTicketUseCase(printer, log, reloj)
            ),
            telemetry = telemetria,
            io = testDispatcher
        )

    // region — la regla del día de la visita, con sus tres bordes ---------------

    @Test
    fun `el dia de la visita se puede imprimir`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PrintPermission.PrimeraImpresion, vm.state.value.permiso)
        assertTrue(vm.state.value.sePuedeImprimir)
    }

    @Test
    fun `el dia SIGUIENTE no se puede imprimir`() = runTest(testDispatcher) {
        reloj.advanceDays(1)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PrintPermission.FueraDelDia, vm.state.value.permiso)
        assertFalse(vm.state.value.sePuedeImprimir)
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
        // prueba `la primera impresion sale y queda registrada` con el MISMO fake.
        assertEquals(emptyList<Any>(), printer.impresos)
        assertEquals(emptyList<String>(), log.registrados)
    }

    @Test
    fun `casi medianoche del dia de la visita todavia imprime`() = runTest(testDispatcher) {
        reloj.setNow(Instant.parse("2026-09-02T05:59:00Z")) // 23:59 CDMX del mismo día

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(PrintPermission.PrimeraImpresion, vm.state.value.permiso)
    }

    @Test
    fun `pasada la medianoche ya no imprime`() = runTest(testDispatcher) {
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
        assertEquals(listOf(TicketDeVisitaFixtures.VISITA_ID), log.registrados)
        assertEquals(FaseDeImpresionDeVisita.IMPRESO, vm.state.value.impresion.fase)
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

        assertTrue(trasLaPrimera.esReimpresion)
        assertEquals("copia 2 · 12:00", trasLaPrimera.detalleDeCopia)
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

        assertFalse(printer.impresos[0].toString().contains("REIMPRESION"))
        assertTrue(printer.impresos[1].toString().contains("REIMPRESION"))
    }

    @Test
    fun `un ticket impreso en una sesion anterior abre como reimpresion`() =
        runTest(testDispatcher) {
            log.record(TicketDeVisitaFixtures.VISITA_ID, TicketDeVisitaFixtures.PRIMERA_COPIA)

            val vm = viewModel()
            advanceUntilIdle()

            assertTrue(vm.state.value.esReimpresion)
            assertTrue(vm.state.value.vistaPrevia.contains("REIMPRESION"))
        }

    @Test
    fun `una impresion fallida NO deja registro`() = runTest(testDispatcher) {
        preferida.savePreferredAddress(FakePrinterPort.IMPRESORA.address)
        printer.resultadoDeImprimir = Result.failure(PrintError.NotPaired)
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), log.registrados)
        assertFalse(vm.state.value.esReimpresion)
        assertEquals("impresora no emparejada", vm.state.value.impresion.mensaje)
    }

    // endregion

    @Test
    fun `sin impresora recordada se abre el picker en vez de imprimir`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.imprimir()
        advanceUntilIdle()

        assertEquals(FaseDeImpresionDeVisita.ELIGIENDO, vm.state.value.impresion.fase)
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
        assertEquals(listOf(TicketDeVisitaFixtures.VISITA_ID), log.registrados)
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
                it.name == VisitasTelemetria.CODE_TICKET_VISITA_NO_SE_IMPRIMIO
        }
        assertEquals("WriteFailed", error.props[VisitasTelemetria.PROP_EXCEPCION])
        assertFalse(error.props.values.any { it.contains(FakePrinterPort.IMPRESORA.address) })
    }

    @Test
    fun `una visita que el telefono no tiene deja la pantalla en error y se reporta`() =
        runTest(testDispatcher) {
            val vm = viewModel(visitaId = "no-existe")
            advanceUntilIdle()

            assertEquals(ErrorDelTicketDeVisita.VISITA_NO_ESTA, vm.state.value.error)
            assertFalse(vm.state.value.sePuedeImprimir)
            assertNotNull(
                telemetria.recorded.singleOrNull {
                    it.type == TelemetryEventType.ERROR &&
                        it.name == VisitasTelemetria.CODE_TICKET_VISITA_SIN_VISITA
                }
            )
        }

    private companion object {
        const val SEIS = 6L
    }
}
