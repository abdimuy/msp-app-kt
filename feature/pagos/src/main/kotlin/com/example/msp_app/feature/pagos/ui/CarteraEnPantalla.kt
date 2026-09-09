package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.feature.pagos.domain.BusquedaDeClientes
import com.example.msp_app.feature.pagos.domain.OrdenDeCobranza
import com.example.msp_app.feature.pagos.domain.RangoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.model.VentaEnLista
import java.time.LocalDate

/** Lo que la pantalla pinta: la lista ya buscada, filtrada y ordenada, con sus conteos. */
data class CarteraProyectada(
    val clientes: List<ClienteEnLista>,
    /** Cuántos clientes caen en cada chip, sobre el resultado de la búsqueda. */
    val conteos: Map<SegmentoDeCobranza, Int>
)

/**
 * Buscar → filtrar por chip → ordenar. Función **pura**, fuera del Composable,
 * para que el orden se pueda probar sin levantar Compose y para que no se
 * recalcule dentro de una recomposición.
 *
 * ## A qué segmentos aplica el orden (decisión de esta tarea)
 *
 * A **los cuatro**. La pantalla vieja ordenaba solo la pestaña `POR VISITAR` y
 * dejaba `VISITADOS`/`PAGADOS` sin orden explícito, y con pestañas eso no se
 * notaba: eran tres listas distintas. Con chips es UNA lista que se filtra, y
 * las mismas filas aparecen bajo *todos*; si el orden dependiera del chip, una
 * fila cambiaría de lugar al tocar un chip sin que nada de esa fila hubiera
 * cambiado. Un chip filtra, no reordena.
 *
 * El conjunto que heredó la pestaña ordenada —`vencidos ∪ sin visitar`, ver
 * [SegmentoDeCobranza]— conserva exactamente el orden que ya tenía.
 *
 * ## La búsqueda apaga el orden (heredado, tal cual)
 *
 * `SalesScreen` (retirada por la Task 21) ordenaba solo `if (query.isBlank())`;
 * buscando, respetaba el orden natural de la fuente. Se copia: quien teclea un
 * nombre está buscando a UNA
 * persona, y reordenar el resultado por prioridad de cobranza mueve de lugar lo
 * que ya encontró.
 *
 * ## Y el cliente se queda con TODAS sus ventas
 *
 * El chip decide si el cliente aparece y de cuál de sus ventas hereda su
 * posición; no recorta lo que se pinta. Esconder la segunda cuenta del cliente
 * porque no cae en el filtro sería volver a la lista por venta con otra ropa.
 */
object CarteraEnPantalla {

    fun proyectar(
        clientes: List<ClienteEnLista>,
        segmento: SegmentoDeCobranza,
        query: String,
        hoy: LocalDate
    ): CarteraProyectada {
        val buscados = clientes.filter { BusquedaDeClientes.coincide(it.textoBuscable, query) }
        val delSegmento = buscados.filter { tieneVentasDe(it, segmento, hoy) }
        return CarteraProyectada(
            clientes = if (query.isBlank()) ordenar(delSegmento, segmento, hoy) else delSegmento,
            conteos = SegmentoDeCobranza.entries.associateWith { chip ->
                buscados.count { tieneVentasDe(it, chip, hoy) }
            }
        )
    }

    private fun tieneVentasDe(
        cliente: ClienteEnLista,
        segmento: SegmentoDeCobranza,
        hoy: LocalDate
    ): Boolean = cliente.ventas.any { segmento.contiene(it.venta.estado, hoy) }

    /**
     * Ordena por el rango heredado y, cuando dos clientes empatan en él, por
     * `CLIENTE_ID` ascendente.
     *
     * El desempate no es decorativo: dos clientes sin un solo abono y con
     * ventas del mismo día empatan de verdad, y sin un criterio estable
     * intercambiarían lugares entre cargas por el orden en que Room devolvió
     * las filas. `CLIENTE_ID` no cambia nunca, así que la lista tampoco.
     */
    private fun ordenar(
        clientes: List<ClienteEnLista>,
        segmento: SegmentoDeCobranza,
        hoy: LocalDate
    ): List<ClienteEnLista> = clientes.sortedWith(
        compareBy<ClienteEnLista, RangoDeCobranza>(OrdenDeCobranza.PRIMERO) {
            rangoDe(it, segmento, hoy)
        }
            .thenBy { it.clienteId }
    )

    /**
     * El rango que el cliente hereda: el de su venta mejor rankeada **de entre
     * las que lo metieron en este segmento**.
     *
     * Se miran solo las del segmento porque la posición de una fila tiene que
     * poderse justificar con lo que esa fila muestra bajo ese chip. Bajo *todos*
     * se miran todas sus ventas, que es el mismo conjunto.
     */
    private fun rangoDe(
        cliente: ClienteEnLista,
        segmento: SegmentoDeCobranza,
        hoy: LocalDate
    ): RangoDeCobranza {
        val delSegmento = cliente.ventas.filter { segmento.contiene(it.venta.estado, hoy) }
        return OrdenDeCobranza.mejorDe(rangos(delSegmento))
            ?: OrdenDeCobranza.mejorDe(rangos(cliente.ventas))
            ?: SIN_VENTAS
    }

    private fun rangos(ventas: List<VentaEnLista>): List<RangoDeCobranza> = ventas.map { it.rango }

    /**
     * Un cliente sin una sola venta no puede llegar a la lista —se agrupa desde
     * sus ventas— pero el tipo lo admite; va al final, nunca al principio.
     */
    private val SIN_VENTAS = RangoDeCobranza(sinAbonos = false, instanteDeVenta = null)
}
