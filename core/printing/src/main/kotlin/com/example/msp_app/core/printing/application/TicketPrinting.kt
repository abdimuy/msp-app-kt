package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrintRecord
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterProfile
import java.time.Instant
import javax.inject.Inject

/**
 * **The whole printing surface a ticket screen needs**, behind one dependency:
 * may I print this ticket, which printers are there, remember this one, and
 * print-and-log.
 *
 * ## Why a facade and not three injections
 *
 * `:feature:pagos` and `:feature:visitas` were injecting
 * [EvaluatePrintPermission], [PrintTicketUseCase] and [PrinterDirectory]
 * separately and *always all three together* — a ticket screen that only had two
 * of them could print without checking the day, or print without logging. detekt
 * caught the smell as a parameter count; the real finding is that these three
 * are one collaboration, and splitting them across call sites is what would let
 * a future screen forget one.
 *
 * It carries no state and takes no decisions of its own: every method delegates.
 * The rule still lives in [PrintDayRule] and the print/log pairing still lives in
 * [PrintTicketUseCase]; this only stops the two features from wiring them
 * differently.
 */
class TicketPrinting
@Inject
constructor(
    private val directorio: PrinterDirectory,
    private val evaluar: EvaluatePrintPermission,
    private val imprimirYRegistrar: PrintTicketUseCase
) {

    /** ¿Se puede imprimir este ticket ahora mismo, y sería copia? */
    fun permiso(ticketId: String, cobradoEn: Instant): PrintPermission =
        evaluar(ticketId, cobradoEn)

    /** Las impresoras emparejadas, o el fallo tipado del puerto. */
    suspend fun emparejadas(): Result<List<PrinterDevice>> = directorio.emparejadas()

    /** La impresora recordada, revalidada contra [entre]; `null` si ya no está. */
    fun recordada(entre: List<PrinterDevice>): PrinterDevice? = directorio.recordada(entre)

    /** Recuerda [device] como la de la próxima vez. */
    fun recordar(device: PrinterDevice) = directorio.recordar(device)

    /**
     * Imprime **y registra** en una sola operación. No hay ningún otro camino
     * hacia el `PrinterPort` desde un feature: es lo que hace cierta la mitad
     * "cada impresión queda registrada" de la regla del mock.
     */
    suspend fun imprimir(
        device: PrinterDevice,
        ticketId: String,
        ticket: PrintableTicket,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): Result<PrintRecord> = imprimirYRegistrar(device, ticketId, ticket, profile)
}
