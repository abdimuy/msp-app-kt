package com.example.msp_app.feature.visitas.data.adapter

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.feature.visitas.domain.model.ContextoDeVisita
import com.example.msp_app.feature.visitas.domain.model.VentaParaVisitar
import com.example.msp_app.feature.visitas.domain.port.ContextoDeVisitaPort

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
            ventas = ventas.map {
                VentaParaVisitar(
                    ventaId = it.DOCTO_CC_ACR_ID,
                    folio = it.FOLIO,
                    descripcion = it.PRODUCTOS.orEmpty(),
                    saldo = Money.of(it.SALDO_REST)
                )
            }
        )
    }
}
