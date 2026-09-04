package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.cobranza.domain.EstadoCuenta
import com.example.msp_app.core.common.money.Money
import java.time.Instant
import java.time.LocalDate

/**
 * El detalle de UN cliente con sus ventas dentro.
 *
 * **Por qué por cliente y no por venta** (decisión del `task-16-brief.md`, no
 * se relitiga): un cliente puede estar vencido en una venta y al corriente en
 * otra, y agregar todo a nivel cliente pierde exactamente ese caso — que es el
 * que decide si el cobrador vuelve o no. Por eso [ventas] es una lista y cada
 * una carga su propio [VentaDelCliente.estado].
 *
 * Todo importe es [Money]: este tipo vive en `domain/model` y lo consumen
 * `application/` y `ui/`, así que ya cruzó la frontera de la REGLA DE DINERO
 * (`global-constraints.md`). El `BigDecimal` pelado de
 * [com.example.msp_app.core.common.cobranza.domain.CuentaDelPeriodo] se queda
 * del otro lado, en `cobranza/domain`.
 */
data class DetalleCliente(
    val clienteId: Int,
    val nombre: String,
    val telefono: String,
    val direccion: String,
    val zona: String,
    val aval: String,
    val saldoTotal: Money,
    val ventas: List<VentaDelCliente>,
    val contactos: List<ContactoDeCobranza>,
    val totalContactos: Int,
    val ficha: String?,
    val liquidacion: Liquidacion?,
    val ultimaVisita: Instant?
) {
    /** Cuántas cuentas tiene — el badge "N cuentas" del encabezado. */
    val cuentas: Int get() = ventas.size
}

/**
 * Una venta del cliente, tal como se pinta en la lista "sus ventas".
 *
 * [estado] es el resultado de
 * [com.example.msp_app.core.common.cobranza.domain.EstadoCuentaDeriver] — el
 * catálogo de ocho de la Task 14, envuelto en [EstadoDelPeriodo]. La UI lo
 * **consume**; no lo vuelve a derivar, ni siquiera parcialmente.
 */
data class VentaDelCliente(
    val ventaId: Int,
    val folio: String,
    val descripcion: String,
    val saldo: Money,
    val parcialidad: Money,
    val abonosPagados: Int,
    val abonosTotales: Int,
    val avance: Float,
    val estado: EstadoDelPeriodo
)

/**
 * Una línea de la bitácora "últimos contactos". [estado] viene del catálogo de
 * ocho aplicado al literal de `TIPO_VISITA` — otra vez, consumido, no derivado
 * en la pantalla.
 */
data class ContactoDeCobranza(
    val fecha: Instant,
    val etiqueta: String,
    val nota: String?,
    val estado: EstadoCuenta,
    val importe: Money?
)

/**
 * "Hoy liquida con": cuánto cierra la deuda hoy y hasta cuándo vale esa cifra.
 *
 * Existe a nivel cliente **y** a nivel venta y **no es redundancia** (decisión
 * del brief): en el cliente cierra la conversación completa, en la venta cierra
 * ese mueble.
 *
 * El cálculo NO se reimplementa aquí: vive en `:app`
 * (`features/sales/domain/models/SettlementCalculator.kt`) y llega por
 * [com.example.msp_app.feature.pagos.domain.port.LiquidacionPort]. Reescribirlo
 * sería tocar lógica de dinero que esta tarea no vino a tocar.
 */
data class Liquidacion(
    val monto: Money,
    val vigenteHasta: LocalDate?,
    val categoria: String
)
