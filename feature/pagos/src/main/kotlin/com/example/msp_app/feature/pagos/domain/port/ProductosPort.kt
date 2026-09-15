package com.example.msp_app.feature.pagos.domain.port

import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta

/**
 * Los renglones de producto de una venta, con su importe REAL.
 *
 * ## Por qué existe, si la venta ya traía los nombres
 *
 * `SaleDao.getByClientId` concatena los artículos en una sola columna
 * (`GROUP_CONCAT(p.ARTICULO, ', ')`), así que
 * [com.example.msp_app.feature.pagos.application.CargarDetalleVenta] los partía
 * de vuelta por comas y **solo podía atribuir importe cuando había uno**: con
 * dos o más renglones, la columna de dinero quedaba en blanco.
 *
 * La tabla `products` sí tiene el importe por renglón (`PRECIO_TOTAL_NETO`) y el
 * DAO que la lee ya existía —`ProductDao.getProductsByFolio`—; lo que faltaba era
 * que `:feature:pagos` lo inyectara. Este puerto es esa inyección.
 *
 * **Justificación del puerto (Ruling BF, caso 3):** el consumidor vive en
 * `application/`, que tiene prohibido importar `data/adapter`. Sin el puerto no
 * hay forma legal de que el caso de uso llegue a Room.
 *
 * Contesta lista vacía cuando el teléfono no tiene los renglones de ese folio —
 * no es un error, es un folio cuyos productos no sincronizaron todavía, y el
 * llamador tiene que poder distinguirlo para no pintar una venta sin productos.
 */
interface ProductosPort {

    /** Los renglones del folio, en el orden en que los trae la venta. */
    suspend fun productosDe(folio: String): List<ProductoDeVenta>
}
