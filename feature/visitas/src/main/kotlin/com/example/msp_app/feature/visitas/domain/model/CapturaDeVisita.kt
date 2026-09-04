package com.example.msp_app.feature.visitas.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * Lo que el cobrador lleva capturado en la pantalla. Es el **estado de captura**
 * completo, no el registro: puede estar a medias, y por eso casi todo es
 * anulable.
 *
 * [ventaDeLaPromesa] es una columna aparte de la venta que el cobrador tenía
 * abierta: "el cobrador toca una puerta, no una venta" (§7 del expediente), así
 * que el cliente puede prometer sobre otra de sus cuentas, o sobre todas
 * (`null`).
 *
 * El monto viaja en [Money] —nunca `Double`— y solo cruza a centavos enteros en
 * el adaptador, que es la frontera que fija la REGLA DE DINERO.
 */
data class CapturaDeVisita(
    val resultado: ResultadoDeVisita? = null,
    /** El literal de `TIPO_VISITA` elegido dentro del resultado. */
    val etiqueta: String? = null,
    /** Texto libre, opcional. **Nunca** lleva la fecha ni la hora dentro. */
    val nota: String = "",
    val ventaDeLaPromesa: Int? = null,
    val fechaPromesa: LocalDate? = null,
    val montoPrometido: Money? = null,
    val fechaCita: LocalDate? = null,
    val horaCita: LocalTime? = null
)

/**
 * Por qué esta captura todavía no se puede guardar. Un enum y no un booleano:
 * la pantalla tiene que poder DECIR cuál falta, y el caso de uso tiene que
 * poder reportarlo con un nombre.
 *
 * [razon] es texto de pantalla: español, minúsculas, sin punto final.
 */
enum class BloqueoDeLaVisita(val razon: String) {
    /** Todavía no eligió qué pasó. El mock lo pinta con el CTA apagado. */
    SIN_RESULTADO("elige un resultado"),

    /**
     * Prometió, pero no dijo cuándo. **Sin fecha no hay promesa**: es el único
     * camino que saca una cuenta del trabajo de la semana, y "no cae esta
     * semana" sin fecha es una afirmación que nada sostiene (regla de la Task
     * 16, aquí aplicada en la captura para que el dato nazca completo).
     */
    PROMESA_SIN_FECHA("falta la fecha"),

    /**
     * La fecha prometida ya pasó. Una promesa hacia atrás no difiere nada y
     * envenena la medición de cumplimiento: nace vencida. Hoy SÍ es válido —
     * es justo el segmento *hoy* de la Task 17.
     */
    PROMESA_EN_EL_PASADO("esa fecha ya pasó"),

    /**
     * Prometió cero o menos. `null` es legítimo ("dijo cuándo pero no cuánto");
     * cero no lo es: significaría "prometió no pagar", que es otro resultado.
     */
    PROMESA_SIN_MONTO("el monto no sirve"),

    /**
     * Cita sin día. La hora puede faltar (el mock contempla "otro día sin
     * hora"), el día no: sin él la cita no cae en ningún segmento y no se puede
     * distinguir la de hoy de la del lunes pasado (Task 17).
     */
    CITA_SIN_DIA("falta el día")
}
