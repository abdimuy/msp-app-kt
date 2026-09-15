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
    /**
     * A quién llama el cobrador cuando el cliente no contesta.
     *
     * **`null` hoy, siempre, y no es un olvido:** no existe la columna. `sales`
     * trae `AVAL_O_RESPONSABLE` (el nombre) y `TELEFONO` (el del CLIENTE), y el
     * DTO de cobranza (`VentaDto.aval_o_responsable`) tampoco trae teléfono —
     * verificado con `grep -rn "AVAL\|aval"` sobre `:core:database` y sobre
     * `data/api/services/cobranza`, que es la misma consulta que SÍ encontró el
     * nombre. La fila no se pinta mientras el dato no exista, en vez de
     * rellenarla con el teléfono del cliente, que ya está en el encabezado y no
     * es a quien se llama.
     */
    val telefonoAval: String?,
    val saldoTotal: Money,
    val ventas: List<VentaDelCliente>,
    val contactos: List<ContactoDeCobranza>,
    val totalContactos: Int,
    /**
     * Lo que trae la VENTA en su campo `NOTAS`, tal como llega del servidor.
     *
     * **No es la ficha del cliente y por eso ya no se llama así.** La Task 16 lo
     * nombró `ficha` porque era lo único parecido que existía; es dato del
     * servidor, se reescribe en cada sincronización de `sales` y el cobrador no
     * lo puede editar. La ficha de verdad —conocimiento local, editable,
     * persistente— es [ficha], y confundirlas era exactamente el riesgo:
     * escribir en una creyendo escribir en la otra.
     */
    val notaDeLaVenta: String?,
    /**
     * La ficha del cliente: el catálogo cerrado y la nota libre.
     *
     * **`null` significa "no se pudo leer", NO "no tiene".** Un cliente sin
     * ficha llega como [FichaDelCliente] vacía. La distinción es la que impide
     * que una lectura fallida se pinte como ficha en blanco y el cobrador
     * escriba encima del conocimiento que sí estaba guardado — ver
     * [com.example.msp_app.feature.pagos.domain.port.FichaDelClientePort.fichaDe].
     */
    val ficha: FichaDelCliente?,
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
    val estado: EstadoDelPeriodo,
    /**
     * Cuántos pagos lleva atrasados — leído de `NUM_PAGOS_ATRASADOS`, no
     * derivado. Ver el KDoc de [DatosDeVenta.atrasos] para el porqué.
     */
    val atrasos: Int = 0
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
