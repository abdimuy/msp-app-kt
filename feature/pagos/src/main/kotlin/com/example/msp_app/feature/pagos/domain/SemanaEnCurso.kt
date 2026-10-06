package com.example.msp_app.feature.pagos.domain

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * La línea de contexto bajo el título grande de la lista de clientes:
 * "semana del D al D mes" (mock "Título" §1, decisión del dueño 2026-10-05).
 *
 * ## Por qué no lleva zona/ruta
 *
 * El mock pide "<zona/ruta> · semana del D al D mes" y cae a "sólo la semana"
 * cuando no hay un dato confiable de ruta. Es justo este caso: a nivel de
 * [com.example.msp_app.feature.pagos.application.Cartera] no existe una
 * "ruta" única — cada [com.example.msp_app.feature.pagos.domain.model.ClienteEnLista]
 * trae su propia [com.example.msp_app.feature.pagos.domain.model.ClienteEnLista.zona],
 * y nada garantiza que todas las puertas de una carga compartan la misma (las
 * zonas además tienen `ESTADO_ID` equivocado en cinco ciudades — ver
 * `project_entrega_garantizada_banderas`). Inventar una "ruta" leyendo la
 * primera zona de la lista sería afirmar algo que el dato no sostiene; se
 * prefiere la semana sola, que sí se puede calcular sin adivinar.
 *
 * ## De dónde sale la semana
 *
 * De [hoy], el mismo "hoy" de negocio que ya usa toda la pantalla
 * ([com.example.msp_app.feature.pagos.application.Cartera.hoy]) — nunca de un
 * reloj propio. La semana es `[lunes, domingo]` que contiene a [hoy], el mismo
 * criterio que [com.example.msp_app.core.common.time.AppTime.isThisWeek] usa
 * para "esta semana es business week".
 */
object SemanaEnCurso {

    /** "semana del 29 sep al 5 oct" (cruza mes) o "semana del 1 al 7 oct" (un solo mes). */
    fun etiqueta(hoy: LocalDate): String {
        val lunes = hoy.with(DayOfWeek.MONDAY)
        val domingo = lunes.plusDays(DIAS_DE_LA_SEMANA - 1L)
        val mesFin = MESES[domingo.monthValue - 1]
        return if (lunes.month == domingo.month) {
            "semana del ${lunes.dayOfMonth} al ${domingo.dayOfMonth} $mesFin"
        } else {
            val mesInicio = MESES[lunes.monthValue - 1]
            "semana del ${lunes.dayOfMonth} $mesInicio al ${domingo.dayOfMonth} $mesFin"
        }
    }

    private const val DIAS_DE_LA_SEMANA = 7

    private val MESES = listOf(
        "ene", "feb", "mar", "abr", "may", "jun",
        "jul", "ago", "sep", "oct", "nov", "dic"
    )
}
