package com.example.msp_app.core.printing.fake

import com.example.msp_app.core.printing.domain.PrintLogStore
import com.example.msp_app.core.printing.domain.PrintRecord
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import java.time.Instant

/**
 * Fakes escritos a mano (fakes-only, sin MockK): estado público + lista pública
 * que graba las llamadas, para poder afirmar lo que pasó y no solo lo que se
 * devolvió.
 */
class FakePrinterPort(
    var emparejadas: Result<List<PrinterDevice>> = Result.success(emptyList()),
    var resultadoDeImprimir: Result<Unit> = Result.success(Unit)
) : PrinterPort {

    /** Cada ticket enviado, en orden. Vacía = nunca se imprimió nada. */
    val impresos: MutableList<PrintableTicket> = mutableListOf()

    /** El dispositivo de cada impresión, en el mismo orden que [impresos]. */
    val destinos: MutableList<PrinterDevice> = mutableListOf()

    override suspend fun listPairedPrinters(): Result<List<PrinterDevice>> = emparejadas

    override suspend fun testConnection(device: PrinterDevice): Result<Unit> = resultadoDeImprimir

    override suspend fun print(
        device: PrinterDevice,
        ticket: PrintableTicket,
        profile: PrinterProfile
    ): Result<Unit> {
        impresos += ticket
        destinos += device
        return resultadoDeImprimir
    }
}

/** [PrintLogStore] en memoria, con el mismo contrato que el de disco. */
class FakePrintLog : PrintLogStore {

    private val registros = mutableMapOf<String, PrintRecord>()

    /** Cada `record(...)` recibido, en orden — para afirmar que NO se llamó. */
    val registrados: MutableList<String> = mutableListOf()

    override fun find(ticketId: String): PrintRecord? = registros[ticketId]

    override fun record(ticketId: String, at: Instant): PrintRecord {
        registrados += ticketId
        val previo = registros[ticketId]
        val nuevo = PrintRecord(
            ticketId = ticketId,
            prints = (previo?.prints ?: 0) + 1,
            // Mismo contrato que el de disco: "sin registro" sella la hora,
            // "registro sin fecha" la deja desconocida.
            firstPrintedAt = if (previo == null) at else previo.firstPrintedAt
        )
        registros[ticketId] = nuevo
        return nuevo
    }

    /** Siembra un registro previo (el ticket ya se había impreso). */
    fun sembrar(registro: PrintRecord) {
        registros[registro.ticketId] = registro
    }
}
