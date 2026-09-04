package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrintError
import com.example.msp_app.core.printing.domain.PrintRecord
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.TicketLine
import com.example.msp_app.core.printing.fake.FakePrintLog
import com.example.msp_app.core.printing.fake.FakePrinterPort
import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El par indivisible "imprimir + registrar": que una impresión exitosa **siempre**
 * deje su registro, y que una fallida **nunca** lo deje — un ticket que no salió
 * de la impresora no es una copia, y contarlo haría que la siguiente impresión
 * real se marcara como reimpresión sobre un papel que el cliente nunca recibió.
 *
 * `StandardTestDispatcher` + `advanceUntilIdle()`, nunca `UnconfinedTestDispatcher`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrintTicketUseCaseTest {

    private val impresora = PrinterDevice(address = "00:11:22:33:44:55", name = "PT-210")
    private val ticket = listOf(TicketLine.Header("TICKET DE PAGO"), TicketLine.Blank)
    private val ahora = Instant.parse("2026-09-04T16:30:00Z")

    @Test
    fun `una impresion exitosa deja su registro`() = runTest(StandardTestDispatcher()) {
        val port = FakePrinterPort()
        val log = FakePrintLog()
        val caso = PrintTicketUseCase(port, log, FakeClock(ahora))

        val resultado = caso(impresora, "abono-1", ahora, ticket)
        advanceUntilIdle()

        assertEquals(
            PrintRecord("abono-1", prints = 1, firstPrintedAt = ahora),
            resultado.getOrNull()
        )
        assertEquals(listOf("abono-1"), log.registrados)
        assertEquals(listOf(ticket), port.impresos)
    }

    @Test
    fun `una impresion fallida NO deja registro`() = runTest(StandardTestDispatcher()) {
        val port = FakePrinterPort(resultadoDeImprimir = Result.failure(PrintError.NotPaired))
        val log = FakePrintLog()
        val caso = PrintTicketUseCase(port, log, FakeClock(ahora))

        val resultado = caso(impresora, "abono-1", ahora, ticket)
        advanceUntilIdle()

        assertTrue(resultado.isFailure)
        assertEquals(PrintError.NotPaired, resultado.exceptionOrNull())
        // Control positivo: el grabador SÍ ve el registro cuando lo hay — lo
        // prueba el test de arriba con el MISMO fake. Esta ausencia significa algo.
        assertEquals(emptyList<String>(), log.registrados)
        assertNull(log.find("abono-1"))
    }

    @Test
    fun `la segunda impresion del mismo ticket incrementa el conteo`() =
        runTest(StandardTestDispatcher()) {
            val port = FakePrinterPort()
            val log = FakePrintLog()
            val reloj = FakeClock(ahora)
            val caso = PrintTicketUseCase(port, log, reloj)

            caso(impresora, "abono-1", ahora, ticket)
            reloj.advanceMinutes(SEIS)
            val segunda = caso(impresora, "abono-1", ahora, ticket)
            advanceUntilIdle()

            assertEquals(2, segunda.getOrNull()?.prints)
            assertEquals(ahora, segunda.getOrNull()?.firstPrintedAt)
        }

    @Test
    fun `evaluar el permiso lee el reloj y el registro`() {
        val log = FakePrintLog()
        val reloj = FakeClock(ahora)
        val evaluar = EvaluatePrintPermission(log, reloj)

        assertEquals(PrintPermission.PrimeraImpresion, evaluar("abono-1", ahora))

        log.sembrar(PrintRecord("abono-1", prints = 1, firstPrintedAt = ahora))
        assertEquals(
            PrintPermission.Reimpresion(previas = 1, primeraVez = ahora),
            evaluar("abono-1", ahora)
        )

        reloj.advanceDays(1)
        assertEquals(PrintPermission.FueraDelDia, evaluar("abono-1", ahora))
    }

    @Test
    fun `el reloj se lee EN EL MOMENTO de imprimir, no antes`() =
        runTest(StandardTestDispatcher()) {
            val port = FakePrinterPort()
            val log = FakePrintLog()
            val reloj = FakeClock(ahora)
            val caso = PrintTicketUseCase(port, log, reloj)

            // El caso de uso se construye el día del cobro —como lo haría una
            // pantalla abierta a las 23:58— y el día cambia DESPUÉS.
            reloj.advanceDays(1)
            val resultado = caso(impresora, "abono-1", ahora, ticket)
            advanceUntilIdle()

            assertTrue(resultado.isFailure)
            assertSame(ImpresionFueraDelDia, resultado.exceptionOrNull())
            // Ni se tocó la impresora ni se registró nada: un rechazo no es una
            // impresión. Control positivo: el primer test de esta clase, con los
            // MISMOS fakes, sí graba en las dos listas.
            assertEquals(emptyList<Any>(), port.impresos)
            assertEquals(emptyList<String>(), log.registrados)
        }

    @Test
    fun `el dia ANTERIOR al cobro tampoco imprime desde el caso de uso`() =
        runTest(StandardTestDispatcher()) {
            val port = FakePrinterPort()
            val log = FakePrintLog()
            val reloj = FakeClock(ahora)
            val caso = PrintTicketUseCase(port, log, reloj)

            reloj.advanceDays(-1)
            val resultado = caso(impresora, "abono-1", ahora, ticket)
            advanceUntilIdle()

            assertSame(ImpresionFueraDelDia, resultado.exceptionOrNull())
            assertEquals(emptyList<Any>(), port.impresos)
        }

    @Test
    fun `una reimpresion del mismo dia SI pasa el rechazo`() = runTest(StandardTestDispatcher()) {
        val port = FakePrinterPort()
        val log = FakePrintLog()
        val reloj = FakeClock(ahora)
        val caso = PrintTicketUseCase(port, log, reloj)
        log.sembrar(PrintRecord("abono-1", prints = 1, firstPrintedAt = ahora))

        // Control positivo del rechazo: el mismo camino, mismo día, con el
        // ticket ya impreso, NO se rechaza — la regla es del día, no del conteo.
        val resultado = caso(impresora, "abono-1", ahora, ticket)
        advanceUntilIdle()

        assertEquals(2, resultado.getOrNull()?.prints)
    }

    private companion object {
        const val SEIS = 6L
    }
}
