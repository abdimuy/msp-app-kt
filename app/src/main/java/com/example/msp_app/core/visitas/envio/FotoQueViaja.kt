package com.example.msp_app.core.visitas.envio

import com.example.msp_app.feature.visitas.domain.ComprobantesDeVisita

/**
 * El predicado ÚNICO de "esta foto sin subir todavía puede viajar": tipo
 * permitido (`ComprobantesDeVisita.permitido`, el mismo de
 * `partesDeComprobantesDeVisita`) Y archivo en el disco. Lo usan el barrido
 * de la reconciliación inversa (`RoomDeliveredVisitsStore`) y la poda
 * (`VisitsLocalDataSource`), para que no puedan divergir (adenda de
 * E-APP-079): una foto que el barrido nunca mandaría tampoco protege su
 * visita de la poda.
 */
object FotoQueViaja {
    fun viaja(mime: String?, uri: String, archivoExiste: (String) -> Boolean): Boolean =
        ComprobantesDeVisita.permitido(mime) && archivoExiste(uri)

    /** Tiene archivo pero su tipo nunca viaja: no protege nada y se cuenta en la telemetría. */
    fun conArchivoQueNoViaja(
        mime: String?,
        uri: String,
        archivoExiste: (String) -> Boolean
    ): Boolean = !ComprobantesDeVisita.permitido(mime) && archivoExiste(uri)
}
