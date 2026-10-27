package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.feature.pagos.domain.BusquedaDeClientes
import com.example.msp_app.feature.pagos.domain.OrdenDeCobranza
import com.example.msp_app.feature.pagos.domain.OrdenDeLaLista
import com.example.msp_app.feature.pagos.domain.RangoDeCobranza
import com.example.msp_app.feature.pagos.domain.model.ClienteEnLista
import com.example.msp_app.feature.pagos.domain.model.VentaEnLista
import java.text.Collator
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

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
 * A **las tres** (E-APP-070). La pantalla vieja ordenaba solo la pestaña `POR VISITAR` y
 * dejaba `VISITADOS`/`PAGADOS` sin orden explícito, y con pestañas eso no se
 * notaba: eran tres listas distintas. Con chips es UNA lista que se filtra, y
 * las mismas filas aparecen bajo *todos*; si el orden dependiera del chip, una
 * fila cambiaría de lugar al tocar un chip sin que nada de esa fila hubiera
 * cambiado. Un chip filtra, no reordena.
 *
 * Por visitar —el conjunto que heredó la pestaña ordenada, ver
 * [SegmentoDeCobranza]— conserva el orden de cobranza de siempre.
 *
 * ## La búsqueda apaga el orden (heredado, tal cual)
 *
 * `SalesScreen` (retirada por la Task 21) ordenaba solo `if (query.isBlank())`;
 * buscando, respetaba el orden natural de la fuente. Se copia: quien teclea un
 * nombre está buscando a UNA
 * persona, y reordenar el resultado por prioridad de cobranza mueve de lugar lo
 * que ya encontró. Esto aplica a los SIETE órdenes, no solo a cobranza.
 *
 * ## Y el cliente se queda con TODAS sus ventas
 *
 * El chip decide si el cliente aparece y de cuál de sus ventas hereda su
 * posición; no recorta lo que se pinta. Esconder la segunda cuenta del cliente
 * porque no cae en el filtro sería volver a la lista por venta con otra ropa.
 *
 * ## Los siete órdenes (hoja "Ordenar por", decisión del dueño 2026-10-05)
 *
 * [OrdenDeLaLista.COBRANZA] es el de siempre —el único que existía antes de
 * esta tarea— y no cambia ni un bit. Los otros seis ([OrdenDeLaLista]) ponen su
 * propia clave primero y, en empate, caen al comparador de cobranza y luego a
 * `clienteId`: el desempate estable no es exclusivo de un orden, es de todos.
 */
@Suppress(
    "TooManyFunctions"
) // clientesPorVisitar (Task 8) cruzó el umbral; son helpers de UN solo algoritmo (orden/filtro)
object CarteraEnPantalla {

    fun proyectar(
        clientes: List<ClienteEnLista>,
        segmento: SegmentoDeCobranza,
        query: String,
        hoy: LocalDate,
        orden: OrdenDeLaLista = OrdenDeLaLista.COBRANZA,
        distanciasPorVenta: Map<Int, Double> = emptyMap()
    ): CarteraProyectada {
        val buscados = clientes.filter { BusquedaDeClientes.coincide(it.textoBuscable, query) }
        val delSegmento = buscados.filter { tieneVentasDe(it, segmento, hoy) }
        return CarteraProyectada(
            clientes = if (query.isBlank()) {
                ordenar(delSegmento, segmento, hoy, orden, distanciasPorVenta)
            } else {
                delSegmento
            },
            conteos = SegmentoDeCobranza.entries.associateWith { chip ->
                buscados.count { tieneVentasDe(it, chip, hoy) }
            }
        )
    }

    /**
     * Cuántos clientes caen en la pastilla "Por visitar" con la búsqueda vacía:
     * el contador "N por visitar" del menú lateral (spec §4; decisión del dueño
     * 2026-10-17, E-APP-070). Es [proyectar] mismo,
     * no una copia de la regla — así el menú y la lista no pueden diferir.
     */
    fun clientesPorVisitar(clientes: List<ClienteEnLista>, hoy: LocalDate): Int =
        proyectar(clientes, SegmentoDeCobranza.POR_VISITAR, query = "", hoy = hoy)
            .conteos[SegmentoDeCobranza.POR_VISITAR] ?: 0

    private fun tieneVentasDe(
        cliente: ClienteEnLista,
        segmento: SegmentoDeCobranza,
        hoy: LocalDate
    ): Boolean = cliente.ventas.any { segmento.contiene(it.venta.estado, hoy) }

    /**
     * Ordena por la clave del [orden] elegido y, en empate, por el orden de
     * cobranza heredado del segmento y luego por `CLIENTE_ID` ascendente.
     *
     * El desempate no es decorativo: dos clientes que empatan en la clave
     * principal (dos "nunca pagó", dos con el mismo saldo, dos sin ubicación)
     * intercambiarían lugares entre cargas por el orden en que Room devolvió
     * las filas si no hubiera un criterio estable. `CLIENTE_ID` no cambia
     * nunca, así que la lista tampoco.
     */
    private fun ordenar(
        clientes: List<ClienteEnLista>,
        segmento: SegmentoDeCobranza,
        hoy: LocalDate,
        orden: OrdenDeLaLista,
        distanciasPorVenta: Map<Int, Double>
    ): List<ClienteEnLista> {
        val cobranza = cobranzaComparator(segmento, hoy)
        val clave: Comparator<ClienteEnLista> = when (orden) {
            OrdenDeLaLista.COBRANZA -> cobranza
            OrdenDeLaLista.VENTAS_NUEVAS ->
                compareByDescending<ClienteEnLista> { instanteMasRecienteDe(it) }.then(cobranza)
            OrdenDeLaLista.MAS_ATRASOS ->
                compareByDescending<ClienteEnLista> { mayorAtrasoDe(it) }.then(cobranza)
            OrdenDeLaLista.SIN_PAGAR_HACE_MAS ->
                compareBy<ClienteEnLista> { it.ultimoPago }.then(cobranza)
            OrdenDeLaLista.MAYOR_SALDO ->
                compareByDescending<ClienteEnLista> { it.saldoTotal }.then(cobranza)
            OrdenDeLaLista.NOMBRE -> nombreComparator().then(cobranza)
            OrdenDeLaLista.MAS_CERCA ->
                compareBy<ClienteEnLista, Double?>(nullsLast()) {
                    distanciaDe(it, distanciasPorVenta)
                }.then(cobranza)
        }
        return clientes.sortedWith(clave.thenBy { it.clienteId })
    }

    /** El orden de cobranza de siempre: el rango heredado del segmento, sin tocar. */
    private fun cobranzaComparator(
        segmento: SegmentoDeCobranza,
        hoy: LocalDate
    ): Comparator<ClienteEnLista> = compareBy<ClienteEnLista, RangoDeCobranza>(
        OrdenDeCobranza.PRIMERO
    ) {
        rangoDe(it, segmento, hoy)
    }

    /** El `instanteDeVenta` más reciente entre las ventas del cliente; `null` si ninguna lo tiene. */
    private fun instanteMasRecienteDe(cliente: ClienteEnLista): Instant? =
        cliente.ventas.mapNotNull { it.rango.instanteDeVenta }.maxOrNull()

    /** El máximo `atrasos` entre las ventas del cliente. */
    private fun mayorAtrasoDe(cliente: ClienteEnLista): Int =
        cliente.ventas.maxOfOrNull { it.venta.atrasos } ?: 0

    /** La distancia a la venta más cercana del cliente; `null` si ninguna de sus ventas la tiene. */
    private fun distanciaDe(
        cliente: ClienteEnLista,
        distanciasPorVenta: Map<Int, Double>
    ): Double? = cliente.ventas.mapNotNull { distanciasPorVenta[it.venta.ventaId] }.minOrNull()

    /**
     * Comparador por nombre con fuerza `PRIMARY`: ignora acentos y mayúsculas,
     * como pide la decisión del dueño. Se crea por llamada —no es un recurso
     * costoso y así no hay un `Collator` mutable compartido entre hilos.
     */
    private fun nombreComparator(): Comparator<ClienteEnLista> {
        val collator = Collator.getInstance(
            Locale("es", "MX")
        ).apply { strength = Collator.PRIMARY }
        return Comparator { a, b -> collator.compare(a.nombre, b.nombre) }
    }

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
