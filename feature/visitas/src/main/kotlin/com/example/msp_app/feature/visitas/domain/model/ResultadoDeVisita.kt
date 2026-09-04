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
    /** Título del renglón. Español, minúsculas, sin punto final, 2-4 palabras. */
    val titulo: String,
    /** La línea de abajo, en el mismo registro que [titulo]. */
    val detalle: String
) {
    /** No había quién atendiera. Se propaga a todas las cuentas del cliente. */
    NO_ESTABA(EstadoCuenta.NO_ESTABA, "no estaba", "nadie atendió"),

    /** Pasaste y no se resolvió. Sigue en la lista de esta semana. */
    VISITE_VUELVO(EstadoCuenta.VISITE_VUELVO, "visité, vuelvo", "no se resolvió"),

    /** Dijo cuándo y cuánto. **El único camino que difiere trabajo.** */
    PROMETIO(EstadoCuenta.PROMETIO_PROXIMA, "prometió pagar", "dijo cuándo y cuánto"),

    /** Quedaron de verse. Es del domicilio, así que aplica a todas sus cuentas. */
    CITA(EstadoCuenta.CITA_A_UNA_HORA, "cita a una hora", "quedaron de verse"),

    /** Se niega o hay conflicto: la cuenta se escala. */
    SE_NEGO(EstadoCuenta.SE_NEGO, "se negó", "hay conflicto");

    /** ¿El desenlace es del domicilio o de una deuda? Lo dice el catálogo. */
    val alcance: VisitScope get() = estado.alcance

    /** El badge del mock: `cliente` o `venta`, en minúsculas. */
    val etiquetaDeAlcance: String
        get() = when (alcance) {
            VisitScope.CLIENTE -> "cliente"
            VisitScope.VENTA -> "venta"
        }
}
