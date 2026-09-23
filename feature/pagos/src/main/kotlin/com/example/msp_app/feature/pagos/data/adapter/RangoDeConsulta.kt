package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.cobranza.domain.VentanaCobro
import com.example.msp_app.core.common.time.AppTime
import java.time.temporal.ChronoUnit

/**
 * Traduce una [VentanaCobro] al par de cadenas que esperan
 * `PaymentDao.getPaymentsByDate` y `VisitDao.getVisitsByDate`.
 *
 * ## Por qué se ensancha un segundo de cada lado
 *
 * Dos desajustes, ninguno inventado aquí:
 *
 * 1. **Los DAOs son medio-abiertos (`>= :start AND < :end`) y [VentanaCobro] es
 *    cerrada en los dos extremos** (copia el `FECHA >= ? AND FECHA <= ?` del
 *    servidor). Sin holgura, un pago capturado exactamente en `fin` se caería
 *    de la consulta.
 * 2. **La comparación es de TEXTO, no de instantes.** `DateTimeFormatter.
 *    ISO_INSTANT` omite la fracción de segundo cuando es cero, y `'.'` (0x2E)
 *    ordena ANTES que `'Z'` (0x5A): `"…T18:00:00.500Z"` es lexicográficamente
 *    MENOR que `"…T18:00:00Z"` aunque sea medio segundo posterior. Un borde
 *    partido al segundo exacto perdería filas con milisegundos.
 *
 * Los dos extremos se truncan a segundos antes de moverse, así que ninguno de
 * los dos sale con fracción y el borde queda lejos de cualquier fila real.
 *
 * Ensanchar es seguro y recortar no lo sería: `EstadoCuentaDeriver` vuelve a
 * filtrar con `ventana.contiene(...)` sobre instantes de verdad, así que una
 * fila de más no cambia ningún estado y una de menos sí.
 */
internal object RangoDeConsulta {

    /** `[inicio - 1s, fin + 1s)` en formato de cable. */
    fun de(ventana: VentanaCobro): Pair<String, String> {
        val desde = ventana.inicio.truncatedTo(ChronoUnit.SECONDS).minusSeconds(HOLGURA_SEGUNDOS)
        val hasta = ventana.fin.truncatedTo(ChronoUnit.SECONDS).plusSeconds(HOLGURA_SEGUNDOS)
        return AppTime.toWireFormat(desde) to AppTime.toWireFormat(hasta)
    }

    private const val HOLGURA_SEGUNDOS = 1L
}
