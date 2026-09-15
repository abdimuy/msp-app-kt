package com.example.msp_app.feature.pagos.domain.model

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.RangoDeCobranza
import java.time.LocalDate

/**
 * Un CLIENTE en la lista de cobranza, con sus ventas dentro.
 *
 * ## El defecto que este tipo existe para cerrar
 *
 * La lista de cobranza de hoy itera con `key = { it.DOCTO_CC_ID }` y pinta una
 * `SaleItem` por fila: **es una lista de ventas, no de clientes**, así que un
 * cliente con dos ventas aparece dos veces, como si fueran dos personas. El
 * cobrador toca una puerta, no una venta. Aquí la unidad es la puerta y las
 * deudas van adentro, cada una con su propio estado del catálogo de ocho.
 *
 * [ventas] trae **todas** las cuentas del cliente, siempre — incluso cuando un
 * chip de segmento fue lo que lo trajo a la lista. Esconder la otra cuenta
 * porque no cae en el filtro sería el mismo defecto con otra ropa: el cobrador
 * ya está frente a la puerta y necesita ver todo lo que puede cobrar ahí.
 *
 * Todo importe es [Money] (REGLA DE DINERO): este tipo lo consumen
 * `application/` y `ui/`, así que ya cruzó la frontera.
 */
data class ClienteEnLista(
    val clienteId: Int,
    val nombre: String,
    val telefono: String,
    val direccion: String,
    val zona: String,
    /** La suma de los saldos de [ventas]. Es del cliente, no de un segmento. */
    val saldoTotal: Money,
    val ventas: List<VentaEnLista>,
    /**
     * El último día en que este cliente pagó ALGO, en cualquiera de sus ventas
     * — la más reciente de sus `FECHA_ULT_PAGO`, o `null` si nunca pagó.
     *
     * Es el único dato de la tarjeta que contesta "¿hace cuánto que este no da
     * nada?"; el resto habla de la semana en curso.
     */
    val ultimoPago: LocalDate?,
    /**
     * Nombre, folios, calle, ciudad y teléfono concatenados y normalizados —
     * lo que mira la búsqueda. Se arma una vez por carga, no por tecla.
     */
    val textoBuscable: String
) {
    /** Cuántas cuentas tiene — el badge "N cuentas" de la fila. */
    val cuentas: Int get() = ventas.size

    /** Las iniciales del avatar. Dos letras; una sola si el nombre es de una palabra. */
    val iniciales: String get() = nombre
        .split(' ')
        .filter { it.isNotBlank() }
        .take(LETRAS_DEL_AVATAR)
        .map { it.first().uppercaseChar() }
        .joinToString("")

    private companion object {
        const val LETRAS_DEL_AVATAR = 2
    }
}

/**
 * Una venta dentro de la fila del cliente: lo que se pinta ([venta], el MISMO
 * tipo que ya usa el detalle de cliente, con el MISMO componente `FilaDeVenta`)
 * más lo que la ordena ([rango]).
 *
 * Van juntos y no en dos listas paralelas porque el orden por cliente se hereda
 * de una venta concreta, y con dos listas nada garantiza que sea la misma.
 */
data class VentaEnLista(
    val venta: VentaDelCliente,
    val rango: RangoDeCobranza
)
