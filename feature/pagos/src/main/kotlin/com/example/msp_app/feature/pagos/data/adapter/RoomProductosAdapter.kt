package com.example.msp_app.feature.pagos.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.dao.product.ProductDao
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.port.ProductosPort

/**
 * Adaptador Room de [ProductosPort] sobre [ProductDao.getProductsByFolio]
 * (schema inmutable, no se toca).
 *
 * **Frontera de la REGLA DE DINERO:** `PRECIO_TOTAL_NETO` es `Double` en el
 * schema y cruza a [Money] aquí y solo aquí, vía `Money.of(Double)` —que usa
 * `BigDecimal.valueOf`, nunca `BigDecimal(double)`—, igual que
 * [RoomVentasAdapter].
 *
 * **Se ordena por `POSICION`.** La `@Query` no lleva `ORDER BY`, así que el
 * orden de las filas es el que quiera darle SQLite; el renglón de arriba de
 * "productos" cambiaría entre dos corridas sin que cambiara un dato. `POSICION`
 * es el orden con el que se capturó la venta, rematado con `DOCTO_PV_DET_ID`
 * —`@PrimaryKey`, único y estable— para que el orden sea **total**. Es el mismo
 * argumento y el mismo último eslabón que `CargarDetalleCliente` usa para "sus
 * ventas": se ordena en el consumidor y no en el DAO, porque el DAO tiene otros
 * llamadores a los que un `ORDER BY` movería.
 */
class RoomProductosAdapter(
    private val productDao: ProductDao
) : ProductosPort {

    override suspend fun productosDe(folio: String): List<ProductoDeVenta> =
        productDao.getProductsByFolio(folio)
            .sortedWith(compareBy({ it.POSICION }, { it.DOCTO_PV_DET_ID }))
            .map { ProductoDeVenta(nombre = it.ARTICULO, importe = Money.of(it.PRECIO_TOTAL_NETO)) }
}
