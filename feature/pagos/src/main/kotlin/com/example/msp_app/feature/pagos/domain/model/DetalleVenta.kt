package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.LocalDate

/**
 * El detalle de UNA venta. Casi todo el dato de cobranza vive aquí (decisión
 * del `task-16-brief.md`): saldo, parcialidad, frecuencia, productos, fecha,
 * enganche, contado, vendedor, progreso, pagos y liquidación. Del cliente solo
 * viajan [clienteId] y [clienteNombre], que son la migaja de vuelta.
 *
 * [estado] es el catálogo de ocho de la Task 14, consumido tal cual.
 */
data class DetalleVenta(
    val ventaId: Int,
    val folio: String,
    val creditoId: Int,
    val clienteId: Int,
    val clienteNombre: String,
    val titulo: String,
    val fechaVenta: LocalDate?,
    val saldo: Money,
    val parcialidad: Money,
    val frecuencia: String,
    val abonosPagados: Int,
    val abonosTotales: Int,
    val avance: Float,
    val totalVenta: Money,
    val precioContado: Money,
    val enganche: Money,
    val abonado: Money,
    val vendedor: String,
    val estado: EstadoDelPeriodo,
    val productos: List<ProductoDeVenta>,
    val historial: HistorialDePagos,
    val liquidacion: Liquidacion?
)

/** Una línea de la sección "productos". */
data class ProductoDeVenta(
    val nombre: String,
    val importe: Money?
)
