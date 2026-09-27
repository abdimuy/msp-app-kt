package com.example.msp_app.feature.ubicacion.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Las fechas y horas como las lee el cobrador: "20 sep", "desde jun",
 * "feb–may", "Sáb 10–12". En español, sin depender del `Locale` del teléfono
 * (un aparato en inglés no debe decir "Sep").
 */
object Textos {
    private val MESES =
        listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    private val DIAS = mapOf(
        DayOfWeek.MONDAY to "Lun",
        DayOfWeek.TUESDAY to "Mar",
        DayOfWeek.WEDNESDAY to "Mié",
        DayOfWeek.THURSDAY to "Jue",
        DayOfWeek.FRIDAY to "Vie",
        DayOfWeek.SATURDAY to "Sáb",
        DayOfWeek.SUNDAY to "Dom"
    )

    private fun z(i: Instant, zona: ZoneId): ZonedDateTime = i.atZone(zona)

    /** "jun", o "jun 2025" si no es del año de [ahora]. */
    fun mes(i: Instant, zona: ZoneId, ahora: Instant): String {
        val f = z(i, zona)
        val base = MESES[f.monthValue - 1]
        return if (f.year == z(ahora, zona).year) base else "$base ${f.year}"
    }

    /** "20 sep", o "20 sep 2025" si no es del año de [ahora]. */
    fun dia(i: Instant, zona: ZoneId, ahora: Instant): String {
        val f = z(i, zona)
        val base = "${f.dayOfMonth} ${MESES[f.monthValue - 1]}"
        return if (f.year == z(ahora, zona).year) base else "$base ${f.year}"
    }

    /** "14:12". */
    fun hora(i: Instant, zona: ZoneId): String {
        val f = z(i, zona)
        return "%02d:%02d".format(f.hour, f.minute)
    }

    /** "feb–may" (o un solo mes si empieza y acaba en el mismo). */
    fun rango(desde: Instant, hasta: Instant, zona: ZoneId, ahora: Instant): String {
        val a = mes(desde, zona, ahora)
        val b = mes(hasta, zona, ahora)
        return if (a == b) a else "$a–$b"
    }

    /** "Sáb 10–12". */
    fun suelePagar(s: SuelePagar): String = "${DIAS.getValue(s.dia)} ${s.horaInicio}–${s.horaFin}"

    /** "1 cobro" / "21 cobros". */
    fun cobros(n: Int): String = if (n == 1) "1 cobro" else "$n cobros"

    /** Nombre del cobrador en forma de título: "MARISOL VEGA" → "Marisol Vega". */
    fun nombre(crudo: String): String = crudo.trim().lowercase().split(Regex("\\s+"))
        .joinToString(" ") { p -> p.replaceFirstChar { it.titlecase() } }

    /** "Efectivo", "Cheque", "Transferencia" por el `FORMA_COBRO_ID` real (ver `E-COB-017`). */
    fun forma(formaCobroId: Int?): String = when (formaCobroId) {
        FORMA_EFECTIVO -> "Efectivo"
        FORMA_CHEQUE -> "Cheque"
        FORMA_TRANSFERENCIA -> "Transferencia"
        else -> "Abono"
    }

    private const val FORMA_EFECTIVO = 157
    private const val FORMA_CHEQUE = 158
    private const val FORMA_TRANSFERENCIA = 52569
}
