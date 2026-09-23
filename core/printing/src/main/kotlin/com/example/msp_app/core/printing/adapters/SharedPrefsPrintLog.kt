package com.example.msp_app.core.printing.adapters

import android.content.Context
import com.example.msp_app.core.printing.domain.PrintLogStore
import com.example.msp_app.core.printing.domain.PrintRecord
import com.example.msp_app.core.telemetry.Telemetry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/** Telemetry codes of the print log. Named constants so they can be grepped. */
object PrintLogTelemetry {

    /** The counter could not be written to disk; the paper already came out. */
    const val CODE_PRINT_LOG_NO_SE_GUARDO: String = "print_log_no_se_guardo"

    /** A stored first-print timestamp is unreadable; the entry is rebuilt. */
    const val CODE_PRINT_LOG_FECHA_ILEGIBLE: String = "print_log_fecha_ilegible"

    /** The exception class name — never its message (anti-PII). */
    const val PROP_EXCEPCION: String = "excepcion"
}

/**
 * [PrintLogStore] on `SharedPreferences`. See the port's KDoc for why this is
 * not a Room table and exactly where its durability stops.
 *
 * Its own prefs file (`print_log_prefs`), not the printer's: the preferred
 * printer is one hint that is fine to lose, this is a per-ticket ledger that
 * grows: sharing a file would make one `clear()` wipe the other's data.
 *
 * ## Why `commit = true` and not `apply()`
 *
 * `apply()` returns before the write reaches disk. The write happens right after
 * paper physically came out of the printer, and the process can be killed at any
 * moment — a lost counter there turns a reprint into an undetectable first copy,
 * which is the one thing this class exists to prevent. A few milliseconds of
 * synchronous I/O is the cheaper side of that trade. It runs on the caller's
 * dispatcher, which is the I/O dispatcher of the print use case.
 *
 * ## Totality
 *
 * Nothing here throws at the caller: a print must never fail because the ledger
 * did. But nothing is swallowed either (NORMA DE ERRORES) — a failed write and
 * an unreadable stored date each emit their own grep-able
 * [Telemetry] code, with only the exception's class name in `props`.
 */
@Singleton
class SharedPrefsPrintLog
@Inject
constructor(
    @ApplicationContext context: Context,
    private val telemetry: Telemetry
) : PrintLogStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Lo que se sabe de [ticketId].
     *
     * **Una fecha ilegible NO borra el conteo.** Se devuelve el registro con
     * `firstPrintedAt = null`: la copia se sigue detectando y se sigue marcando,
     * solo pierde la hora. Descartar la entrada entera —lo que hacía la primera
     * versión— costaba exactamente una copia **indetectable**: el siguiente
     * papel salía limpio, sin banda.
     */
    override fun find(ticketId: String): PrintRecord? {
        val prints = prefs.getInt(claveConteo(ticketId), 0)
        if (prints <= 0) return null
        return PrintRecord(
            ticketId = ticketId,
            prints = prints,
            firstPrintedAt = leerInstante(ticketId)
        )
    }

    override fun record(ticketId: String, at: Instant): PrintRecord {
        val previo = find(ticketId)
        val nuevo = PrintRecord(
            ticketId = ticketId,
            prints = (previo?.prints ?: 0) + 1,
            // La PRIMERA vez nunca se pisa: es la que dice qué copia salió antes.
            // Y "no había registro" no es lo mismo que "el registro perdió su
            // fecha": en el segundo caso la hora se queda DESCONOCIDA, porque
            // sellar la de ahora afirmaría que esta copia fue la primera.
            firstPrintedAt = if (previo == null) at else previo.firstPrintedAt
        )
        guardar(nuevo)
        return nuevo
    }

    /**
     * Escribe el registro de forma síncrona. Un `commit` en `false` significa
     * que el conteo NO quedó en disco: el ticket ya se imprimió, así que no se
     * puede deshacer nada, pero tampoco se calla — se reporta con su código.
     */
    private fun guardar(registro: PrintRecord) {
        val editor = prefs.edit()
        editor.putInt(claveConteo(registro.ticketId), registro.prints)
        val primera = registro.firstPrintedAt
        if (primera == null) {
            // Primera vez desconocida: se limpia la cadena corrupta en vez de
            // sellar la de AHORA, que afirmaría que esta copia fue la primera.
            editor.remove(clavePrimera(registro.ticketId))
        } else {
            editor.putString(clavePrimera(registro.ticketId), primera.toString())
        }
        if (!editor.commit()) {
            telemetry.error(
                code = PrintLogTelemetry.CODE_PRINT_LOG_NO_SE_GUARDO,
                message = "el conteo de impresiones no quedo en disco; una reimpresion podria " +
                    "verse como primera copia",
                props = emptyMap()
            )
        }
    }

    /**
     * Lee la fecha de la primera impresión. Una cadena ilegible (disco corrupto,
     * un formato viejo) degrada a `null` — el CONTEO se conserva, así que la
     * reimpresión se sigue detectando y solo se pierde la hora. No es silencioso:
     * emite su propio código. Anti-PII: viaja el nombre de la clase de la
     * excepción, nunca su texto.
     */
    private fun leerInstante(ticketId: String): Instant? {
        val crudo = prefs.getString(clavePrimera(ticketId), null) ?: return null
        return try {
            Instant.parse(crudo)
        } catch (invalida: DateTimeParseException) {
            telemetry.error(
                code = PrintLogTelemetry.CODE_PRINT_LOG_FECHA_ILEGIBLE,
                message = "la fecha de la primera impresion no se pudo leer; el registro se " +
                    "reconstruye en la siguiente impresion",
                props = mapOf(PrintLogTelemetry.PROP_EXCEPCION to invalida.javaClass.simpleName)
            )
            null
        }
    }

    private fun claveConteo(ticketId: String) = "$ticketId$SUFIJO_CONTEO"

    private fun clavePrimera(ticketId: String) = "$ticketId$SUFIJO_PRIMERA"

    private companion object {
        const val PREFS = "print_log_prefs"
        const val SUFIJO_CONTEO = ".conteo"
        const val SUFIJO_PRIMERA = ".primera"
    }
}
