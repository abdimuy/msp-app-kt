package com.example.msp_app.feature.pagos.application

import com.example.msp_app.feature.pagos.domain.model.DatosDeVenta
import com.example.msp_app.feature.pagos.domain.model.NombreDeProducto
import com.example.msp_app.feature.pagos.domain.model.ProductoDeVenta
import com.example.msp_app.feature.pagos.domain.port.ProductosPort

/**
 * `ventaId → sus renglones de producto`, para armar el mapa que
 * [com.example.msp_app.feature.pagos.domain.BitacoraDelCliente.de] necesita
 * (`task-2-brief.md`).
 *
 * **Por qué se resuelve en `application/` y no dentro del mezclador de
 * dominio.** [ProductosPort] es un puerto de esta capa; `domain/` no lo
 * conoce (`BitacoraDelCliente` es dominio puro, ver su KDoc). La mezcla recibe
 * el mapa YA resuelto, igual que ya recibe `visitas` y `pagos` resueltos.
 *
 * **UNA consulta por LOTE, nunca una por venta ni una por pago** (ronda de
 * arreglo 1 de la Task 2). Las tres pantallas que usan
 * [com.example.msp_app.feature.pagos.domain.BitacoraDelCliente.de] mezclan
 * TODOS los pagos (y visitas) del cliente, y `SaleDao.getByClientId` no filtra
 * por estado — trae TODA la historia de ventas del cliente, no solo las
 * abiertas. Un cliente con años de antigüedad podía significar un centenar de
 * consultas secuenciales a Room, una por venta, solo para nombres de cuenta.
 * [ProductosPort.productosDeVarios] resuelve TODOS los folios en una sola
 * consulta (troceada por el tope de SQLite, ver
 * [com.example.msp_app.feature.pagos.data.adapter.RoomProductosAdapter]), y
 * aquí solo se reparte el resultado por `ventaId`.
 */
internal suspend fun ProductosPort.productosPorVenta(
    ventas: List<DatosDeVenta>
): Map<Int, List<ProductoDeVenta>> {
    val porFolio = productosDeVarios(ventas.map { it.folio })
    return ventas.associate { it.ventaId to porFolio[it.folio].orEmpty() }
}

/**
 * `ventaId → nombre de cuenta`, derivado de [productosPorVenta].
 *
 * **Qué producto nombra una cuenta de varios artículos:** el PRIMERO de la
 * lista. [ProductosPort] ya promete devolverlos en `POSICION` — el orden de
 * captura, y por eso el mueble principal de la venta— así que no hace falta
 * un segundo criterio (elegir por [com.example.msp_app.core.common.money.Money]
 * traería su propio desempate cuando dos renglones cuestan lo mismo). Ver el
 * KDoc de [com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza.cuenta]
 * para la justificación completa, con el caso real que la originó.
 *
 * **Una venta sin renglones —folio que no sincronizó `products` todavía— no
 * entra al mapa.** La mezcla no encuentra la llave y
 * [com.example.msp_app.feature.pagos.domain.model.ContactoDeCobranza.cuenta]
 * queda en `null`: nunca un texto de relleno ni el folio de repuesto.
 *
 * El nombre normalizado sale de
 * [com.example.msp_app.feature.pagos.domain.model.NombreDeProducto.normaliza] —
 * la única regla que convierte el `ARTICULO` de Microsip, en MAYÚSCULAS, en
 * texto de usuario.
 */
internal fun Map<Int, List<ProductoDeVenta>>.aCuentas(): Map<Int, String> =
    mapNotNull { (ventaId, productos) ->
        productos.firstOrNull()?.let { primero -> ventaId to NombreDeProducto.normaliza(primero.nombre) }
    }.toMap()

/**
 * Atajo de [productosPorVenta] + [aCuentas] para el llamador que solo
 * necesita el mapa de cuentas y no los renglones completos —
 * [CargarBitacoraDelCliente]. [CargarDetalleCliente] y [CargarDetalleVenta] NO
 * lo usan: los dos piden [productosPorVenta] directo porque además necesitan
 * los renglones completos para su propia sección "productos", y derivar de
 * ahí evita pedirlos dos veces — ver el KDoc de `CargarDetalleVenta.invoke`
 * para el defecto que eso cerró (la misma venta se consultaba dos veces).
 */
internal suspend fun cuentasDeLasVentas(
    ventas: List<DatosDeVenta>,
    productosPort: ProductosPort
): Map<Int, String> = productosPort.productosPorVenta(ventas).aCuentas()
