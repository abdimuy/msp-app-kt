package com.example.msp_app.feature.visitas.domain

import com.example.msp_app.core.common.time.BUSINESS_LOCALE
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * Los días que la pantalla ofrece de un toque para una promesa o una cita
 * (`.chips` del fold "¿Cuándo?" del mock).
 *
 * Función PURA del "hoy" que entra por parámetro: nada de `LocalDate.now()`.
 * Así el mismo cálculo se prueba con `FakeClock` y los goldens no cambian según
 * el día en que corran.
 *
 * ## Por qué mañana y el viernes
 *
 * Son las dos fechas que un cobrador teclearía casi siempre: *"mañana paso"* y
 * *"el viernes que cobre mi esposo"* — la segunda es literal del mock. El
 * viernes es el día de raya de la zona, así que concentra las promesas. Todo lo
 * demás entra por [OTRO_DIA], que abre el calendario: el catálogo corto no
 * pretende cubrir el mundo, solo ahorrar el 90% de los toques.
 *
 * **Hoy no está** entre las sugerencias de la promesa: prometer para hoy es
 * válido (`ReglasDeLaVisita` lo acepta y cae en el segmento *hoy* de la Task
 * 17) pero es raro — si va a pagar hoy, el cobrador cobra, no promete. Para la
 * cita sí está primero: "quedaron de verse hoy" es el caso del mock.
 */
object DiasSugeridos {

    /** El chip que abre el calendario. No es una fecha: es una puerta. */
    const val OTRO_DIA: String = "otro día"

    /** El chip que deja la cita sin hora — el tercer caso del mock. */
    const val SIN_HORA: String = "sin hora"

    private val DIA_Y_MES: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE d MMM", BUSINESS_LOCALE)

    private val HORA: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", BUSINESS_LOCALE)

    /** Las horas que se ofrecen de un toque para una cita. Mañana, mediodía y tarde. */
    val HORAS_SUGERIDAS: List<LocalTime> = listOf(
        LocalTime.of(9, 0),
        LocalTime.of(12, 0),
        LocalTime.of(16, 0),
        LocalTime.of(19, 0)
    )

    /** Los días que se ofrecen para una **promesa**: mañana y el próximo viernes. */
    fun paraPromesa(hoy: LocalDate): List<LocalDate> =
        listOf(hoy.plusDays(1), proximoViernes(hoy)).distinct()

    /** Los días que se ofrecen para una **cita**: hoy, mañana y el próximo viernes. */
    fun paraCita(hoy: LocalDate): List<LocalDate> =
        listOf(hoy, hoy.plusDays(1), proximoViernes(hoy)).distinct()

    /**
     * El viernes que viene. Si hoy ES viernes devuelve el de la semana que
     * entra, nunca hoy: [TemporalAdjusters.next] es estrictamente posterior, y
     * eso evita que el chip "vie 4 sep" y el chip "hoy" sean el mismo día con
     * dos nombres.
     */
    fun proximoViernes(hoy: LocalDate): LocalDate =
        hoy.with(TemporalAdjusters.next(DayOfWeek.FRIDAY))

    /**
     * El texto del chip de [fecha]: "hoy", "mañana" o "vie 4 sep".
     *
     * Los dos nombres relativos ganan al calendario porque es como el cobrador
     * piensa la fecha; a partir del tercer día ya no hay nombre corto que no se
     * preste a confusión ("pasado mañana" es largo y ambiguo en voz alta).
     */
    fun etiquetaDe(fecha: LocalDate, hoy: LocalDate): String = when (fecha) {
        hoy -> "hoy"
        hoy.plusDays(1) -> "mañana"
        else -> DIA_Y_MES.format(fecha).lowercase(BUSINESS_LOCALE)
    }

    /** `HH:mm` — la hora tal como se guarda en `CITA_HORA`. */
    fun etiquetaDe(hora: LocalTime): String = HORA.format(hora)
}
