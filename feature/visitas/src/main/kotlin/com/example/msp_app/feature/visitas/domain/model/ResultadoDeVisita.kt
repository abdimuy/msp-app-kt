package com.example.msp_app.feature.visitas.domain.model

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.cobranza.domain.VisitScope

/**
 * Los **cinco desenlaces que una visita puede producir**, en el vocabulario del
 * catálogo de ocho de la Task 14 (`registrar-visita.html`, estado 1).
 *
 * No es una taxonomía nueva: cada uno **es** un [EstadoCuenta]. Los otros tres
 * estados del catálogo no nacen de una visita —[EstadoCuenta.PAGO] y
 * [EstadoCuenta.ABONO_PARCIAL] salen del dinero (Task 18) y
 * [EstadoCuenta.SIN_TOCAR] es la ausencia de todo—, así que lo que el cobrador
 * registra aquí es exactamente lo que después pinta la lista.
 *
 * ## Los dos que necesitan un dato para existir
 *
 * [PROMETIO] y [CITA] son los dos estados que hasta hoy no se podían derivar, y
 * por la misma razón: **el estado no lo sostiene la etiqueta, lo sostiene un
 * dato**. Una promesa sin fecha no difiere nada (Task 16 la pinta *regresas*) y
 * una cita sin día no cae en ninguna parte (Task 17). Por eso [PROMETIO] exige
 * fecha y [CITA] exige día — ver `CapturaDeVisita`.
 *
 * [alcance] es el badge "cliente"/"venta" del mock: no se decide aquí, se lee
 * de [EstadoCuenta.alcance], que es la única clasificación del repo.
 */
enum class ResultadoDeVisita(
    val estado: EstadoCuenta,
    /** Título del renglón. Español, mayúscula inicial, sin punto final, 2-4 palabras. */
    val titulo: String,
    /** La línea de abajo, en el mismo registro que [titulo]. */
    val detalle: String
) {
    /** No había quién atendiera. Se propaga a todas las cuentas del cliente. */
    NO_ESTABA(EstadoCuenta.NO_ESTABA, "No estaba", "Nadie atendió"),

    /** Pasaste y no se resolvió. Sigue en la lista de esta semana. */
    VISITE_VUELVO(EstadoCuenta.VISITE_VUELVO, "Visité, vuelvo", "No se resolvió"),

    /** Dijo cuándo y cuánto. **El único camino que difiere trabajo.** */
    PROMETIO(EstadoCuenta.PROMETIO_PROXIMA, "Prometió pagar", "Dijo cuándo y cuánto"),

    /** Quedaron de verse. Es del domicilio, así que aplica a todas sus cuentas. */
    CITA(EstadoCuenta.CITA_A_UNA_HORA, "Cita a una hora", "Quedaron de verse"),

    /** Se niega o hay conflicto: la cuenta se escala. */
    SE_NEGO(EstadoCuenta.SE_NEGO, "Se negó", "Hay conflicto");

    /** ¿El desenlace es del domicilio o de una deuda? Lo dice el catálogo. */
    val alcance: VisitScope get() = estado.alcance

    /**
     * El badge del renglón, **en el vocabulario del cobrador**.
     *
     * Decía `CLIENTE` y `VENTA`, que son los nombres de dos ramas de un `when`.
     * Nadie parado en una puerta sabe qué es "una venta" y sí sabe qué es una
     * cuenta y qué es la puerta entera. La regla es exactamente la misma; lo que
     * cambia es que ahora está dicha para quien la usa.
     */
    val etiquetaDeAlcance: String
        get() = when (alcance) {
            VisitScope.CLIENTE -> "toda la puerta"
            VisitScope.VENTA -> "una cuenta"
        }

    /**
     * ¿Se puede marcar en VARIAS cuentas a la vez?
     *
     * Sí para "visité, vuelvo" y "se negó": el cliente lo dice UNA vez sobre
     * todo lo que debe, y hasta hoy había que registrar dos visitas para
     * capturar una sola frase.
     *
     * **No para [PROMETIO]**, y no por simetría: una promesa lleva UNA fecha y
     * UN monto. Repartir "$220 el viernes" entre dos cuentas escribiría $440
     * prometidos, que es dinero que el cliente no prometió; y dejar el monto en
     * una sola de las dos dejaría a la otra con una promesa a medias. El
     * desenlace que difiere trabajo es el que menos puede mentir sobre a qué
     * cuenta lo difiere.
     */
    val admiteVariasCuentas: Boolean
        get() = alcance == VisitScope.VENTA && this != PROMETIO
}
