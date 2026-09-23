package com.example.msp_app.features.home.components.homenearbyclientssection

import androidx.compose.runtime.Immutable
import com.example.msp_app.core.common.location.SaleDistance
import com.example.msp_app.core.utils.Coord
import com.example.msp_app.core.utils.sortGroupsByClosestCentroid
import com.example.msp_app.data.models.payment.PaymentLocationsGroup
import com.example.msp_app.data.models.sale.SaleWithProducts

/** Cuántas puertas caben en la lista. Ver [nearbyClientsFrom]. */
const val NEARBY_CLIENTS_LIMIT: Int = 10

/**
 * Una puerta de la lista de **clientes cercanos** de la pantalla principal.
 *
 * [accounts] es cuántas cuentas de ese cliente entraron en esta fila — ver la
 * decisión de producto en [nearbyClientsFrom]. Se muestra para que el renglón no
 * mienta por omisión cuando colapsó más de una venta.
 */
@Immutable
data class NearbyClient(
    val clientId: Int,
    val name: String,
    val address: String,
    val accounts: Int,
    val distance: SaleDistance
)

/**
 * Arma la lista de clientes cercanos, de la puerta más próxima a la más lejana.
 *
 * ## Decisión de producto **revisable**: la venta más cercana manda
 *
 * La maquinaria de abajo razona en **ventas** —[PaymentLocationsGroup.saleId],
 * [com.example.msp_app.core.utils.SaleProximity.saleId],
 * `PaymentsViewModel.getCentroidsBySale()`— porque los centroides salen de
 * agrupar los pagos por cuenta. Esta pantalla, en cambio, habla de **clientes**.
 * Al colapsar, un cliente con dos ventas aparece **una sola vez**, a la
 * distancia de su venta **más cercana**, y el renglón lleva al **cliente**, no a
 * la venta — que es como navega el resto de la app desde la Task 21 ("desde la
 * lista y el mapa se entra al cliente").
 *
 * El motivo: el cobrador está decidiendo **a qué puerta ir**, y una puerta es un
 * domicilio, no una cuenta. Ver el mismo domicilio dos veces porque el cliente
 * debe dos ventas sería ruido en la única lista cuyo trabajo es ordenar el
 * recorrido.
 *
 * **Es una decisión de producto, no una restricción técnica**: los datos
 * permiten perfectamente una fila por venta. Si el dueño prefiere lo otro, lo
 * que cambia es este `groupBy` y el renglón que lo pinta; nada más.
 *
 * ## De qué venta sale el cliente
 *
 * [PaymentLocationsGroup.saleId] viene de agrupar `PaymentLocation` por
 * `DOCTO_CC_ACR_ID`, que es el **cargo acreditado** del pago. El id de venta que
 * casa con ése es `sales.DOCTO_CC_ID` —no la PK de Room—, por el mismo join que
 * el backend hace (`s.DOCTO_CC_ID = p.DOCTO_CC_ACR_ID`) y que
 * [com.example.msp_app.features.sales.SaleIdSpaces.forSalePayments] documenta.
 * Por eso el índice de abajo es por `DOCTO_CC_ID`, igual que el `salesMap` que
 * esta misma lista usaba antes de retirarse. De la venta salen `CLIENTE_ID`,
 * `CLIENTE` y la dirección: **no hay derivación inventada**, el cliente viaja en
 * la propia fila de `sales`.
 *
 * ## Los tres casos en que la lista sale vacía
 *
 * - [position] nula — **no hay permiso de ubicación**, o el proveedor no dio
 *   fix (GPS apagado, bajo techo). Sin un punto desde donde medir no hay nada
 *   que ordenar, y ordenar por un punto inventado sería peor que no ordenar.
 * - [centroidsBySale] vacío — todavía no hay pagos georreferenciados.
 * - ninguna venta del cobrador casa con los centroides.
 *
 * En los tres la sección **no se pinta** (ver `HomeNearbyClientsSection`), que
 * no es lo mismo que quedarse cargando para siempre.
 *
 * Las ventas sin centroide ([SaleDistance.Unknown]) siguen entrando y quedan al
 * final, como siempre: el tipo lo garantiza al ordenar. Sólo alcanzan la lista
 * cuando sobran lugares de los [limit] disponibles.
 */
fun nearbyClientsFrom(
    position: Coord?,
    centroidsBySale: List<PaymentLocationsGroup>,
    sales: List<SaleWithProducts>,
    limit: Int = NEARBY_CLIENTS_LIMIT
): List<NearbyClient> {
    if (position == null) return emptyList()

    // `DOCTO_CC_ID` es único en `sales` (es el mismo número que la PK, ver
    // `SaleIdSpaces`), así que este índice no pierde filas.
    val salesByCharge = sales.associateBy { it.DOCTO_CC_ID }

    return sortGroupsByClosestCentroid(centroidsBySale, position)
        .mapNotNull { proximity ->
            salesByCharge[proximity.saleId]?.let { sale -> sale to proximity.distance }
        }
        .groupBy { (sale, _) -> sale.CLIENTE_ID }
        .map { (clientId, rows) ->
            val (closest, distance) = rows.minBy { (_, distance) -> distance }
            NearbyClient(
                clientId = clientId,
                name = closest.CLIENTE,
                address = addressOf(closest),
                accounts = rows.size,
                distance = distance
            )
        }
        .sortedBy { it.distance }
        .take(limit)
}

/**
 * La dirección escrita de la puerta. Se arma con lo que haya: en el padrón real
 * hay filas con ciudad y sin calle, y un `", "` colgando se lee como un dato
 * perdido.
 */
private fun addressOf(sale: SaleWithProducts): String = listOf(sale.CALLE, sale.CIUDAD)
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .joinToString(", ")
