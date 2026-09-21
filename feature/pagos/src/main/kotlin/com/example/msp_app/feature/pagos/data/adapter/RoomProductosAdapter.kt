package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.dao.product.ProductDao
import com.example.msp_app.core.database.entities.ProductEntity
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.port.ProductosPort

/**
 * Adaptador Room de [ProductosPort] sobre [ProductDao.getProductsByFolio] y
 * [ProductDao.getProductsByFolios] (schema inmutable, no se toca).
 *
 * **Frontera de la REGLA DE DINERO:** `PRECIO_TOTAL_NETO` es `Double` en el
 * schema y cruza a [Money] aquí y solo aquí, vía `Money.of(Double)` —que usa
 * `BigDecimal.valueOf`, nunca `BigDecimal(double)`—, igual que
 * [RoomVentasAdapter].
 *
 * **Se ordena por `POSICION`.** Ninguna de las dos `@Query` lleva `ORDER BY`,
 * así que el orden de las filas es el que quiera darle SQLite; el renglón de
 * arriba de "productos" cambiaría entre dos corridas sin que cambiara un dato.
 * `POSICION` es el orden con el que se capturó la venta, rematado con
 * `DOCTO_PV_DET_ID` —`@PrimaryKey`, único y estable— para que el orden sea
 * **total**. Es el mismo argumento y el mismo último eslabón que
 * `CargarDetalleCliente` usa para "sus ventas": se ordena en el consumidor y
 * no en el DAO, porque el DAO tiene otros llamadores a los que un `ORDER BY`
 * movería.
 */
class RoomProductosAdapter(
    private val productDao: ProductDao
) : ProductosPort {

    override suspend fun productosDe(folio: String): List<ProductoDeVenta> =
        productDao.getProductsByFolio(folio).aProductosDeVenta()

    /**
     * Una consulta por LOTE de folios, no una por folio — ver el KDoc de
     * [ProductosPort.productosDeVarios] para el defecto que esto cierra
     * (un centenar de viajes a Room en un cliente con años de historial).
     *
     * **Troceado por debajo de `SQLITE_MAX_VARIABLE_NUMBER` (999).** El mismo
     * tope y el mismo margen que `RoomPendingVisitsStore` y
     * `CobranzaReconciler` ya documentan en este repo: sin trocear, un `IN`
     * con más de 999 parámetros lanza "too many SQL variables". Un cliente
     * real tiene unas pocas decenas de ventas, así que esto casi nunca
     * trocea de verdad — se deja puesto porque el tope es real y barato de
     * cubrir, no porque se espere alcanzarlo.
     */
    override suspend fun productosDeVarios(
        folios: List<String>
    ): Map<String, List<ProductoDeVenta>> = folios.distinct()
        .chunked(SQLITE_MAX_IN_PARAMS)
        .flatMap { chunk -> productDao.getProductsByFolios(chunk) }
        .groupBy { it.FOLIO }
        .mapValues { (_, renglones) -> renglones.aProductosDeVenta() }

    private fun List<ProductEntity>.aProductosDeVenta(): List<ProductoDeVenta> =
        sortedWith(compareBy({ it.POSICION }, { it.DOCTO_PV_DET_ID }))
            .map { ProductoDeVenta(nombre = it.ARTICULO, importe = Money.of(it.PRECIO_TOTAL_NETO)) }

    private companion object {
        /** Debajo de `SQLITE_MAX_VARIABLE_NUMBER` (999), con margen. */
        const val SQLITE_MAX_IN_PARAMS: Int = 900
    }
}
