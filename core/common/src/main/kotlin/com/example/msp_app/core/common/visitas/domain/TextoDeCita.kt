package com.example.msp_app.core.common.visitas.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Cómo se dice una cita en pantalla y en el aviso (decisión del dueño,
 * 2026-10-30). A mano y sin `Locale`: el resultado no depende de la versión del
 * JDK ni del Android del teléfono (el espacio de "p. m." cambia entre CLDR).
 */
object TextoDeCita {
    private const val MEDIODIA = 12

    /** `4:30 p. m.`, `12:00 p. m.`, `12:05 a. m.`: 12 h, sin cero a la izquierda. */
    fun hora12(hora: LocalTime): String {
        val h12 = if (hora.hour % MEDIODIA == 0) MEDIODIA else hora.hour % MEDIODIA
        val sufijo = if (hora.hour < MEDIODIA) "a. m." else "p. m."
        return "%d:%02d %s".format(h12, hora.minute, sufijo)
    }

    /** `jueves`, en minúsculas. */
    fun diaDeLaSemana(fecha: LocalDate): String = when (fecha.dayOfWeek) {
        DayOfWeek.MONDAY -> "lunes"
        DayOfWeek.TUESDAY -> "martes"
        DayOfWeek.WEDNESDAY -> "miércoles"
        DayOfWeek.THURSDAY -> "jueves"
        DayOfWeek.FRIDAY -> "viernes"
        DayOfWeek.SATURDAY -> "sábado"
        DayOfWeek.SUNDAY -> "domingo"
    }

    /** `Cita: jueves 4:30 p. m.`; sin hora, `Cita: jueves`. Nunca inventa la hora. */
    fun etiquetaDelHistorial(fecha: LocalDate, hora: LocalTime?): String = if (hora == null) {
        "Cita: ${diaDeLaSemana(fecha)}"
    } else {
        "Cita: ${diaDeLaSemana(fecha)} ${hora12(hora)}"
    }

    /** `Cita con Josefa a las 4:30 p. m.`; sin hora, `Cita con Josefa`. */
    fun avisoDeCita(cliente: String, hora: LocalTime?): String =
        if (hora == null) "Cita con $cliente" else "Cita con $cliente a las ${hora12(hora)}"
}
