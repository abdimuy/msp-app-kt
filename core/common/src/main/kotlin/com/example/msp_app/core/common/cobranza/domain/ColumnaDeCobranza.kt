package com.example.msp_app.core.common.cobranza.domain

import java.time.LocalDate
import java.time.LocalTime

/**
 * Columna de la lista de cobranza (D18 del dueño, 2026-10-15; E-APP-070): la
 * misma regla que msp-api `internal/desempeno/domain/columna.go` (E-INF-018),
 * amarrada por el juego de casos compartido. La pregunta es "¿a qué puerta
 * voy?": sólo la promesa con fecha posterior a hoy se aparta; pagado y abono
 * parcial van a PAGADOS; todo lo demás, a POR_VISITAR.
 */
enum class ColumnaDeCobranza(val codigo: String) {
    POR_VISITAR("por_visitar"),
    NO_PAGA("no_paga"),
    PAGADOS("pagados")
}

fun columnaDe(
    estado: EstadoCuenta,
    fechaPromesa: LocalDate?,
    horaCita: LocalTime?,
    hoy: LocalDate
): ColumnaDeCobranza = when (TratoDeEstado.de(estado, fechaPromesa, horaCita)) {
    TratoDelEstado.PAGADO, TratoDelEstado.PARCIAL -> ColumnaDeCobranza.PAGADOS
    TratoDelEstado.DIFERIDO ->
        if (fechaPromesa != null && fechaPromesa.isAfter(hoy)) {
            ColumnaDeCobranza.NO_PAGA
        } else {
            ColumnaDeCobranza.POR_VISITAR
        }
    else -> ColumnaDeCobranza.POR_VISITAR
}
