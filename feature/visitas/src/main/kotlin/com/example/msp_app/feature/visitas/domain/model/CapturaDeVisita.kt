package com.example.msp_app.feature.visitas.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.LocalDate
import java.time.LocalTime

/**
 * Lo que el cobrador lleva capturado en la pantalla. Es el **estado de captura**
 * completo, no el registro: puede estar a medias, y por eso casi todo es
 * anulable.
 *
 * [cuentas] son las cuentas marcadas: "el cobrador toca una puerta, no una
 * venta" (§7 del expediente), así que el desenlace se dice UNA vez y se aplica a
 * las cuentas que toque. Vacío cuando el desenlace es de toda la puerta
 * ([ResultadoDeVisita.alcance] `CLIENTE`): ahí el estado se propaga solo, sin
 * escribir una fila por cuenta.
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
    /**
     * Las cuentas a las que aplica el desenlace, por `DOCTO_CC_ACR_ID`.
     *
     * Un `Set` y no una lista: marcar dos veces la misma cuenta no significa
     * nada, y el orden de la escritura no lo pone el dedo del cobrador sino
     * [com.example.msp_app.feature.visitas.domain.IdsDeLaVisita].
     */
    val cuentas: Set<Int> = emptySet(),
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
     * El desenlace es de una cuenta y no queda ninguna marcada.
     *
     * Las casillas nacen TODAS marcadas —"no te voy a pagar nada" es el caso
     * común y desmarcar es para el caso fino—, así que llegar aquí exige que el
     * cobrador las haya desmarcado a mano. Y sin una sola cuenta no hay nada que
     * escribir: un desenlace de alcance VENTA sin venta no toca ninguna fila y
     * se perdería entero.
     */
    SIN_CUENTAS("elige una cuenta"),

    /**
     * Prometió, pero no dijo cuándo. **Sin fecha no hay promesa**: es el único
     * camino que saca una cuenta del trabajo de la semana, y "no cae esta
     * semana" sin fecha es una afirmación que nada sostiene (regla de la Task
     * 16, aquí aplicada en la captura para que el dato nazca completo).
     */
    PROMESA_SIN_FECHA("falta la fecha"),

    /**
     * La fecha del compromiso ya pasó. Un compromiso hacia atrás no difiere nada
     * y envenena la medición de cumplimiento: nace vencido. **Hoy SÍ es
     * válido** — es justo el segmento *hoy* de la Task 17.
     *
     * Vale igual para la promesa y para la cita: es una sola regla sobre la
     * fecha que el desenlace elegido lleve, no dos que puedan despegarse.
     */
    COMPROMISO_EN_EL_PASADO("esa fecha ya pasó"),

    /**
     * La fecha del compromiso está más allá del horizonte
     * ([ReglasDeLaVisita.HORIZONTE_DIAS]). No es un compromiso: es un año mal
     * tecleado en el calendario.
     *
     * Importa por dinero y por espacio. Por dinero, porque una promesa a dos
     * años no se puede cruzar contra ningún pago y ensucia la medición. Por
     * espacio, porque la retención conserva la fila noventa días **después** de
     * la fecha del compromiso: sin techo, un dedazo pina una visita en el
     * teléfono durante décadas.
     */
    COMPROMISO_MUY_LEJANO("está demasiado lejos"),

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
