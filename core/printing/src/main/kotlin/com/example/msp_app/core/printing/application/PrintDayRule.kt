package com.example.msp_app.core.printing.application

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.printing.domain.PrintRecord
import java.time.Instant

/**
 * What the collector is allowed to do with a ticket right now. Exhaustive so a
 * `when` in a ViewModel cannot forget a case.
 */
sealed interface PrintPermission {

    /** Nothing has been printed yet and today IS the collection day. */
    data object PrimeraImpresion : PrintPermission

    /**
     * Today is still the collection day, but this ticket already came out of a
     * printer. Printing again is allowed — the customer may have lost the
     * paper — and the copy is **marked** so the reprint is detectable.
     */
    data class Reimpresion(val previas: Int, val primeraVez: Instant) : PrintPermission

    /**
     * Today is not the collection day. **Nothing prints**, first copy or not.
     * This is the rule from the mock, and it is the case that makes the log
     * above only need to survive one day.
     */
    data object FueraDelDia : PrintPermission
}

/**
 * *El ticket solo se imprime el día del cobro.*
 *
 * Pure, and the whole rule in one place: both the payment ticket and the visit
 * ticket call this, so the two cannot drift apart about what "the same day"
 * means.
 *
 * "The same day" is the **business day** — the calendar date observed in
 * `America/Mexico_City` ([AppTime.BUSINESS_ZONE]), never the device's zone and
 * never a 24-hour window. A payment collected at 23:50 and reprinted at 00:10
 * is a different day and does not print; a payment collected at 00:10 and
 * printed at 23:50 is the same day and does. Using elapsed hours instead would
 * make the boundary depend on the hour of collection, which is not what the rule
 * says.
 *
 * The three cases the tests pin are the boundary itself: the collection day, the
 * day before it, and the day after it.
 */
object PrintDayRule {

    /**
     * @param cobradoEn when the money was taken (or the visit registered).
     * @param ahora now, from `AppClock` — the only date source.
     * @param registro what [com.example.msp_app.core.printing.domain.PrintLogStore]
     *   knows about this ticket, or `null` if it was never printed.
     */
    fun evaluar(cobradoEn: Instant, ahora: Instant, registro: PrintRecord?): PrintPermission {
        val diaDelCobro = AppTime.toBusinessDate(cobradoEn)
        val hoy = AppTime.toBusinessDate(ahora)
        if (hoy != diaDelCobro) return PrintPermission.FueraDelDia
        return if (registro == null || registro.prints <= 0) {
            PrintPermission.PrimeraImpresion
        } else {
            PrintPermission.Reimpresion(
                previas = registro.prints,
                primeraVez = registro.firstPrintedAt
            )
        }
    }

    /** `true` when a ticket collected at [cobradoEn] may be printed at [ahora]. */
    fun sePuedeImprimir(cobradoEn: Instant, ahora: Instant): Boolean =
        AppTime.toBusinessDate(cobradoEn) == AppTime.toBusinessDate(ahora)
}
