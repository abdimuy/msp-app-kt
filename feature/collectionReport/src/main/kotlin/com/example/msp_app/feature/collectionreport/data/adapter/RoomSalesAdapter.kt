package com.example.msp_app.feature.collectionreport.data.adapter

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.database.dao.sale.SaleDao
import com.example.msp_app.core.database.entities.SaleCobranzaRow
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje
import com.example.msp_app.feature.collectionreport.domain.model.DateRange
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.SaleForCobranza
import com.example.msp_app.feature.collectionreport.domain.port.SalesPort
import java.math.BigDecimal

/**
 * Adapter Room de [SalesPort] sobre [SaleDao.getCobranzaRows] (schema v27, inmutable).
 *
 * `PARCIALIDAD` es `Int` en el schema (pesos enteros, sin centavos) — se envuelve en [Money]
 * vía [BigDecimal] directo (exacto, sin el puente `Double`). `PRECIO_TOTAL`/`SALDO_REST` SÍ
 * cruzan por el puente `Double` -> [Money] (mismo borde que usa [RoomPaymentsAdapter] para
 * `IMPORTE`). `FECHA` se parsea con [AppTime.parseWireFormat] (estricto: una fila con fecha
 * corrupta debe fallar ruidosamente, no colarse silenciosamente en el cálculo de "Meta de la
 * semana" con una fecha inventada).
 */
class RoomSalesAdapter(
    private val saleDao: SaleDao
) : SalesPort {

    override suspend fun nonContadoActiveSales(range: DateRange): List<SaleForCobranza> =
        saleDao.getCobranzaRows(range.startIso, range.endExclusiveIso)
            .map { it.toSaleForCobranza() }
}

private fun SaleCobranzaRow.toSaleForCobranza(): SaleForCobranza = SaleForCobranza(
    doctoCcAcrId = doctoCcAcrId,
    parcialidad = Money.of(BigDecimal(parcialidad)),
    totalImporte = Money.of(precioTotal),
    saldoHoy = Money.of(saldoRest),
    frecuencia = CobranzaPorcentaje.Frecuencia.fromWire(frecPago),
    fechaCargo = AppTime.parseWireFormat(fecha)
)
