package com.example.msp_app.feature.visitas.data.fake

import com.example.msp_app.core.printing.domain.PreferredPrinterStore
import com.example.msp_app.core.printing.domain.PrintLogStore
import com.example.msp_app.core.printing.domain.PrintRecord
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterPort
import com.example.msp_app.core.printing.domain.PrinterProfile
import java.time.Instant

/**
 * Fakes del stack de impresión para `:feature:visitas`, escritos a mano
 * (fakes-only, sin MockK): estado público + listas públicas que graban las
 * llamadas.
 *
 * Se escriben aquí y no se importan de `:core:printing`: el `test` source set de
 * un módulo no cruza a otro módulo, y ese es el mismo motivo por el que
 * `VisitasScreenshotTest` replica el bring-up de `MspScreenshotTest` en vez de
 * heredarlo.
 */
class FakePrinterPort(
    var emparejadas: Result<List<PrinterDevice>> = Result.success(listOf(IMPRESORA)),
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

    companion object {
        /** La impresora de campo de las pruebas. */
        val IMPRESORA: PrinterDevice = PrinterDevice(
            address = "00:11:22:33:44:55",
            name = "PT-210"
        )
    }
}

/** [PreferredPrinterStore] en memoria, con el mismo self-healing que el real. */
class FakeImpresoraPreferida(private var direccion: String? = null) : PreferredPrinterStore {

    /** Cada dirección guardada, en orden. */
    val guardadas: MutableList<String> = mutableListOf()

    override fun readPreferredAddress(): String? = direccion

    override fun savePreferredAddress(address: String) {
        guardadas += address
        direccion = address
    }

    override fun clear() {
        direccion = null
    }

    override fun preferredPrinter(pairedPrinters: List<PrinterDevice>): PrinterDevice? {
        val guardada = direccion ?: return null
        val encontrada = pairedPrinters.firstOrNull { it.address == guardada }
        if (encontrada == null) clear()
        return encontrada
    }
}

/** [PrintLogStore] en memoria, con el MISMO contrato que el de disco. */
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

    /** Siembra un registro previo: el ticket ya se había impreso antes. */
    fun sembrar(registro: PrintRecord) {
        registros[registro.ticketId] = registro
    }
}
