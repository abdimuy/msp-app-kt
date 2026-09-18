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
    override val fechaVenta: LocalDate?,
    override val saldo: Money,
    override val parcialidad: Money,
    override val frecuencia: String,
    val abonosPagados: Int,
    val abonosTotales: Int,
    val avance: Float,
    val totalVenta: Money,
    val precioContado: Money,
    override val enganche: Money,
    override val abonado: Money,
    val vendedor: String,
    override val estado: EstadoDelPeriodo,
    val productos: List<ProductoDeVenta>,
    val historial: HistorialDePagos,
    /**
     * **Todo lo que pasó con este cliente**, cobros y visitas, no sólo lo de
     * esta cuenta.
     *
     * El dueño lo pidió así, y no cuesta una consulta nueva: el caso de uso ya
     * reunía la cobranza COMPLETA para poder derivar el estado —una visita de
     * alcance cliente se propaga a todas sus ventas— y hasta ahora la angostaba
     * y tiraba el resto.
     *
     * Lo de ESTA venta se distingue por [ContactoDeCobranza.ventaId], que la
     * pantalla marca con una barra al borde. No se filtra aquí: quién decide
     * qué mirar es el cobrador, con las pastillas.
     */
    val contactos: List<ContactoDeCobranza> = emptyList(),
    override val liquidacion: Liquidacion?,
    val garantia: GarantiaDeLaVenta?
) : CuentaCobrable

/** Una línea de la sección "productos". */
data class ProductoDeVenta(
    val nombre: String,
    val importe: Money?
)
