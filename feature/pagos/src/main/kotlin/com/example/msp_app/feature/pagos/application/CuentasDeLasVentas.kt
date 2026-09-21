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
 * **Una consulta por VENTA, no por pago.** Las tres pantallas que usan
 * [com.example.msp_app.feature.pagos.domain.BitacoraDelCliente.de] mezclan
 * TODOS los pagos (y visitas) del cliente, pero una cuenta tiene un solo
 * producto que la nombra — no uno por abono. Pedir `productosDe(folio)` una
 * vez por pago repetiría la misma consulta tantas veces como abonos tenga esa
 * cuenta; aquí se pide UNA vez por venta, indexado por `ventaId` para que la
 * mezcla solo tenga que preguntar.
 */
internal suspend fun ProductosPort.productosPorVenta(
    ventas: List<DatosDeVenta>
): Map<Int, List<ProductoDeVenta>> = ventas.associate { it.ventaId to productosDe(it.folio) }

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
 * Atajo de [productosPorVenta] + [aCuentas] para los llamadores que solo
 * necesitan el mapa de cuentas y no los renglones completos —
 * [CargarBitacoraDelCliente] y [CargarDetalleVenta]. [CargarDetalleCliente] NO
 * lo usa: ya pide los renglones completos para su sección "productos" y
 * derivar de ahí evita pedirlos dos veces.
 */
internal suspend fun cuentasDeLasVentas(
    ventas: List<DatosDeVenta>,
    productosPort: ProductosPort
): Map<Int, String> = productosPort.productosPorVenta(ventas).aCuentas()
