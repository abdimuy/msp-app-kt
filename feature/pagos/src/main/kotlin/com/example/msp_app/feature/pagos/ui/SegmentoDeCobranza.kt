package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.core.common.cobranza.domain.ColumnaDeCobranza
import com.example.msp_app.core.common.cobranza.domain.columnaDe
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import java.time.LocalDate

/**
 * Las tres pastillas de la lista de clientes (D18 del dueño, 2026-10-15;
 * E-APP-070): las mismas columnas del escritorio.
 *
 * La pregunta que la lista responde sigue siendo "¿a qué puerta voy?":
 *
 * | pastilla | quiere decir |
 * |---|---|
 * | **Por visitar** | hay que pasar: sin tocar, volver, no estaba, se negó, cita, promesa vencida o de hoy |
 * | **No paga** | prometió pagar en una fecha posterior a hoy |
 * | **Pagados** | pagó o abonó parcial: ya hubo dinero esta semana |
 *
 * La regla NO vive aquí: es [columnaDe] de `:core:common`, amarrada con msp-api
 * por el juego de casos compartido. Esta clase sólo pinta. Las tres columnas
 * son disjuntas y cubren los ocho estados: toda cuenta cae en exactamente una.
 */
enum class SegmentoDeCobranza(val etiqueta: String, val columna: ColumnaDeCobranza) {

    /** Hay que pasar por la puerta. */
    POR_VISITAR("Por visitar", ColumnaDeCobranza.POR_VISITAR),

    /** Promesa con fecha posterior a hoy: no vuelvas ahora. */
    NO_PAGA("No paga", ColumnaDeCobranza.NO_PAGA),

    /** Pagó o abonó parcial. */
    PAGADOS("Pagados", ColumnaDeCobranza.PAGADOS);

    /** ¿Cae [estado] en esta pastilla? Delega en [columnaDe]; no duplica la regla. */
    fun contiene(estado: EstadoDelPeriodo, hoy: LocalDate): Boolean =
        columnaDe(estado.estado, estado.fechaPromesa, estado.horaCita, hoy) == columna
}
