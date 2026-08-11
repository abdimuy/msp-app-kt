package com.example.msp_app.feature.collectionreport.ui

import com.example.msp_app.core.common.time.AppClock
import com.example.msp_app.feature.collectionreport.domain.CobranzaPorcentaje
import com.example.msp_app.feature.collectionreport.domain.model.CollectionPayment
import com.example.msp_app.feature.collectionreport.domain.model.Money
import com.example.msp_app.feature.collectionreport.domain.model.PaymentMethod
import java.time.Instant
import java.time.ZoneOffset

/**
 * Calcula "Meta de la semana" ([CobranzaPorcentaje]) a partir de [CollectionReportStateBuilder.LoadedPorts]
 * — separado de [CollectionReportStateBuilder] SOLO para mantenerlo bajo el umbral
 * `TooManyFunctions` de detekt (convención del proyecto: dividir, no suprimir).
 */
internal object CollectionReportMetaBuilder {

    /**
     * Ventas activas del cobrador ([CollectionReportStateBuilder.LoadedPorts.sales]) + el
     * `abonoSemana` por venta agrupando los pagos YA cargados
     * ([CollectionReportStateBuilder.LoadedPorts.payments], mismo lote que alimenta el resto
     * del tablero — sin una segunda consulta a los puertos). `null` cuando el cobrador no tiene
     * ciclo ([CollectionReportStateBuilder.LoadedPorts.fechaCargaInicial] ausente) -> pcts
     * nulos, contadores en 0.
     *
     * **Fechas en día civil UTC** (no zona de negocio): [CobranzaPorcentaje] es un puerto fiel
     * del Go, que trabaja en `dateUTC` — ver su KDoc de clase.
     */
    fun cobranzaSemanal(
        ports: CollectionReportStateBuilder.LoadedPorts,
        clock: AppClock
    ): CobranzaPorcentaje.CobranzaSemanal {
        val fechaCargaInicial = ports.fechaCargaInicial
            ?: return CobranzaPorcentaje.CobranzaSemanal(null, null, 0, 0)
        val abonoPorVenta = abonoSemanaPorVenta(ports.payments)
        val ventas = ports.sales.map { sale ->
            CobranzaPorcentaje.VentaCobranzaInput(
                parcialidad = sale.parcialidad.amount,
                frecuencia = sale.frecuencia,
                fechaCargo = sale.fechaCargo.toUtcDate(),
                totalImporte = sale.totalImporte.amount,
                abonoSemana = (abonoPorVenta[sale.doctoCcAcrId] ?: Money.ZERO).amount,
                saldoHoy = sale.saldoHoy.amount
            )
        }
        return CobranzaPorcentaje.calcular(
            ventas,
            fechaInicio = fechaCargaInicial.toUtcDate(),
            hoy = clock.now().toUtcDate()
        )
    }

    /** `abonoSemana` por venta: suma de pagos (excluida condonación) agrupados por [CollectionPayment.saleId]. */
    private fun abonoSemanaPorVenta(payments: List<CollectionPayment>): Map<Int, Money> =
        payments.asSequence()
            .filter { it.method != PaymentMethod.CONDONACION }
            .groupBy { it.saleId }
            .mapValues { (_, ps) -> Money.sum(ps.map { it.amount }) }

    private fun Instant.toUtcDate() = atZone(ZoneOffset.UTC).toLocalDate()
}
