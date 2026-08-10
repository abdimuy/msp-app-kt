package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import com.example.msp_app.core.printing.domain.ReportTicket
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Orchestration test for [PrintReportTicketUseCase] with a fake [PrinterPort]:
 * asserts the formatted ticket and chosen profile reach the port verbatim,
 * and that the port's [Result] is returned unmodified (mirrors the contract
 * [PrintPaymentReceiptUseCase] already has, though undocumented by its own test).
 */
class PrintReportTicketUseCaseTest {
    private val ticket =
        ReportTicket(
            negocio = "Mueblería Bonanza",
            sucursal = "",
            title = "REPORTE DEL DÍA",
            rangeLabel = "Jueves 3 de julio",
            collector = "C. Chávez",
            methodRows = emptyList(),
            paymentCount = 0,
            totalLabel = "TOTAL COBRADO",
            totalAmount = "$0.00"
        )
    private val device = PrinterDevice(address = "00:11:22:33:44:55", name = "Printer A")

    @Test
    fun `formats the ticket and prints it to the given device and profile`() = runTest {
        val port = FakePrinterPort(result = Result.success(Unit))
        val useCase = PrintReportTicketUseCase(port = port, formatter = ReportTicketFormatter())

        val result = useCase(device, ticket, PrinterProfile.PROFILE_80MM)

        assertTrue(result.isSuccess)
        assertEquals(device, port.lastDevice)
        assertEquals(PrinterProfile.PROFILE_80MM, port.lastProfile)
        assertEquals(
            ReportTicketFormatter().format(ticket, PrinterProfile.PROFILE_80MM),
            port.lastTicket
        )
    }

    @Test
    fun `returns the port's failure verbatim`() = runTest {
        val failure = Result.failure<Unit>(IllegalStateException("boom"))
        val port = FakePrinterPort(result = failure)
        val useCase = PrintReportTicketUseCase(port = port, formatter = ReportTicketFormatter())

        val result = useCase(device, ticket)

        assertEquals(failure, result)
    }

    private class FakePrinterPort(private val result: Result<Unit>) : PrinterPort {
        var lastDevice: PrinterDevice? = null
        var lastTicket: PrintableTicket? = null
        var lastProfile: PrinterProfile? = null

        override suspend fun listPairedPrinters(): Result<List<PrinterDevice>> = Result.success(
            emptyList()
        )

        override suspend fun testConnection(device: PrinterDevice): Result<Unit> = Result.success(
            Unit
        )

        override suspend fun print(
            device: PrinterDevice,
            ticket: PrintableTicket,
            profile: PrinterProfile
        ): Result<Unit> {
            lastDevice = device
            lastTicket = ticket
            lastProfile = profile
            return result
        }
    }
}
