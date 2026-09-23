package com.example.msp_app.core.printing.application

import com.example.msp_app.core.printing.domain.PrintRecord
import com.example.msp_app.core.printing.domain.PrintableTicket
import com.example.msp_app.core.printing.domain.PrinterDevice
import com.example.msp_app.core.printing.domain.PrinterProfile
import java.time.Instant
import javax.inject.Inject

/**
 * **The whole printing surface a ticket screen needs**, behind one dependency:
 * how should I paint myself, which printers are there, remember this one, and
 * check-the-day + print + log.
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
     * **Comprueba el día, imprime y registra**, en una sola operación.
     *
     * [cobradoEn] es obligatorio a propósito: no hay forma de llegar al
     * `PrinterPort` desde un feature sin darle a la regla el dato que necesita,
     * y la regla se evalúa con una lectura FRESCA del reloj dentro de
     * [PrintTicketUseCase] — no con el veredicto que la pantalla usó para pintar
     * el botón. Devuelve `Result.failure(ImpresionFueraDelDia)` si el día ya
     * pasó, sin tocar la impresora.
     */
    suspend fun imprimir(
        device: PrinterDevice,
        ticketId: String,
        cobradoEn: Instant,
        ticket: PrintableTicket,
        profile: PrinterProfile = PrinterProfile.PROFILE_58MM
    ): Result<PrintRecord> = imprimirYRegistrar(device, ticketId, cobradoEn, ticket, profile)
}
