package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.BUSINESS_LOCALE
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

/**
 * La única regla que convierte `ARTICULO` en texto de usuario (`task-2-brief.md`).
 *
 * **Microsip lo guarda en MAYÚSCULAS**, y de ahí sale [ProductoDeVenta.nombre]
 * crudo — [com.example.msp_app.feature.pagos.data.adapter.RoomProductosAdapter]
 * no lo toca. La sección "productos" del detalle de venta sigue pintando ese
 * crudo (fuera de alcance de esta tarea): lo único que pasa por acá es el
 * nombre que identifica la CUENTA en la bitácora
 * ([ContactoDeCobranza.cuenta]), y pasa por un solo lugar para que la regla no
 * se reparta entre los llamadores.
 *
 * **Sentencia, no título.** Una mayúscula inicial y el resto en minúsculas —la
 * misma convención de texto de usuario del repo (`CLAUDE.md` §3: mayúscula
 * inicial, nunca "Title Case" por palabra, que capitalizaría también "King" y
 * "Chocolate" como si fueran nombres propios).
 */
object NombreDeProducto {
    fun normaliza(nombreCrudo: String): String {
        val sentencia = nombreCrudo.trim().lowercase(BUSINESS_LOCALE)
        return sentencia.replaceFirstChar { it.titlecase(BUSINESS_LOCALE) }
    }
}
