package com.example.msp_app.feature.visitas.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.VentaParaVisitar
import com.example.msp_app.feature.visitas.domain.port.ContextoDeVisitaPort
import java.math.BigDecimal

/**
 * Adaptador Room de [ContextoDeVisitaPort] sobre [SaleDao.getByClientId]
 * (schema inmutable, no se toca).
 *
 * Es la MISMA consulta que usa la lista y el detalle (`RoomVentasAdapter` de
 * `:feature:pagos`): agrupa por `DOCTO_CC_ID`, así que devuelve una fila por
 * venta con sus artículos concatenados en `PRODUCTOS`. Se reusa a propósito —
 * dos proyecciones distintas del mismo cliente es cómo dos pantallas empiezan a
 * decir cosas diferentes de la misma puerta.
 *
 * **Frontera de la REGLA DE DINERO:** `SALDO_REST` es `Double` en el schema
 * heredado y cruza aquí con [Money.of], que internamente usa
 * `BigDecimal.valueOf` (representación decimal, nunca los bits del flotante).
 * Del otro lado de esta línea no hay un solo `Double` de dinero.
 */
class RoomContextoDeVisitaAdapter(
    private val saleDao: SaleDao
) : ContextoDeVisitaPort {

    override suspend fun contexto(clienteId: Int): ContextoDeVisita? {
        val ventas = saleDao.getByClientId(clienteId)
        // Sin una sola cuenta no hay contra qué registrar: el teléfono ya no
        // tiene a ese cliente. Se contesta `null` en vez de una ficha vacía, que
        // dejaría al cobrador guardando una visita que no se puede atribuir.
        val primera = ventas.firstOrNull() ?: return null
        return ContextoDeVisita(
            clienteId = clienteId,
            nombre = primera.CLIENTE,
            direccion = listOf(primera.CALLE, primera.CIUDAD)
                .filter { it.isNotBlank() }
                .joinToString(", "),
            saldoTotal = Money.sum(ventas.map { Money.of(it.SALDO_REST) }),
            // ORDENADAS por `DOCTO_CC_ACR_ID`, y no es cosmética: la consulta
            // **no lleva `ORDER BY`** (`SaleDao.getByClientId`), así que el orden
            // en que SQLite emite las filas no está garantizado entre corridas.
            // De ese orden dependen ahora dos cosas visibles: cuál cuenta pinta
            // primero la pantalla y cuál es el ancla de la captura
            // (`IdsDeLaVisita.ancla`), que es la visita que se lleva las fotos y
            // abre el ticket. Es el mismo desempate explícito, y por la misma
            // razón, que `RegistroDeVisitaAdapter.cuentaDeAtribucion`.
            ventas = ventas
                .sortedBy { it.DOCTO_CC_ACR_ID }
                .map {
                    VentaParaVisitar(
                        ventaId = it.DOCTO_CC_ACR_ID,
                        folio = it.FOLIO,
                        descripcion = it.PRODUCTOS.orEmpty(),
                        saldo = Money.of(it.SALDO_REST),
                        // `PARCIALIDAD` es `Int` en el schema heredado: cruza con
                        // `BigDecimal.valueOf` sobre el `Long`, NUNCA con el
                        // constructor de `double`, que mete centavos fantasma en un
                        // número que era exacto. Misma frontera que `RoomVentasAdapter`.
                        parcialidad = Money.of(BigDecimal.valueOf(it.PARCIALIDAD.toLong())),
                        // `sales.FECHA` es wire RFC3339 UTC y llega del
                        // servidor, así que se lee con el parser TOLERANTE
                        // —mismo criterio que `ReunirCartera` en
                        // `:feature:pagos`—: una fecha ilegible es un dato que
                        // falta, no una excepción con la impresora en la mano.
                        fechaVenta = AppTime.parseWireFormatOrNull(it.FECHA)
                            ?.let(AppTime::toBusinessDate),
                        plazoMeses = it.TIEMPO_A_CORTO_PLAZOMESES,
                        // Misma frontera de la REGLA DE DINERO que `SALDO_REST`:
                        // `PRECIO_TOTAL` es `Double` en el schema heredado y
                        // cruza con `Money.of`, que va por `BigDecimal.valueOf`.
                        totalDeCompra = Money.of(it.PRECIO_TOTAL),
                        // `overdue_payments_view` entra por `LEFT JOIN`: sin
                        // fila para la cuenta la columna llega nula, y eso es
                        // "sin atraso", no "no se sabe".
                        pagosVencidos = it.NUM_PAGOS_ATRASADOS ?: 0
                    )
                }
        )
    }
}
