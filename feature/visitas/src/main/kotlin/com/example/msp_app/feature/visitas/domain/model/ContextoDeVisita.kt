package com.example.msp_app.feature.visitas.domain.model

import com.example.msp_app.core.common.money.Money
import java.time.LocalDate

/**
 * La puerta a la que el cobrador está parado: el cliente y sus cuentas.
 *
 * La pantalla es **ligera a propósito** — el contexto ya se leyó en el detalle
 * (Task 16), así que aquí solo entra lo que la captura necesita: a quién se
 * visita, cuántas cuentas toca el desenlace, y de cuál venta puede ser la
 * promesa.
 */
data class ContextoDeVisita(
    val clienteId: Int,
    val nombre: String,
    val direccion: String,
    val saldoTotal: Money,
    val ventas: List<VentaParaVisitar>
) {
    /** Las iniciales del avatar del mock. Nunca más de dos letras. */
    val iniciales: String
        get() = nombre.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")

    /** "2 cuentas · C. Hidalgo 214" — la línea bajo el nombre. */
    val resumen: String
        get() {
            val cuentas = if (ventas.size == 1) "1 cuenta" else "${ventas.size} cuentas"
            return listOf(cuentas, direccion).filter { it.isNotBlank() }.joinToString(" · ")
        }
}

/** Una cuenta del cliente, con lo justo para marcarla o desmarcarla. */
data class VentaParaVisitar(
    val ventaId: Int,
    val folio: String,
    val descripcion: String,
    val saldo: Money,
    /**
     * `PARCIALIDAD` — lo que le toca dar por esta cuenta cada periodo.
     *
     * **Se lee, no se deriva.** Es la misma columna cruda que pinta
     * `HojaDeAbono` a la derecha de cada cuenta en `:feature:pagos`; recalcular
     * aquí "lo esperado hoy" —que es `parcialidad - abonoDelPeriodo`, fórmula de
     * `MontosSugeridos`— sería una segunda fórmula de dinero en un módulo que no
     * tiene la mitad de las entradas, y las dos pantallas dirían cifras
     * distintas de la misma cuenta.
     */
    val parcialidad: Money,
    /**
     * `sales.FECHA` —el alta del crédito— **ya parseada**, o `null` cuando el
     * texto no se pudo leer.
     *
     * Se parsea en el adaptador y con el parser tolerante del repo
     * (`AppTime.parseWireFormatOrNull`), igual que `ReunirCartera` en
     * `:feature:pagos`: la columna es texto que viene del servidor, y este
     * código corre con la impresora en la mano frente al cliente. Una fecha
     * ilegible es un dato que falta, nunca una excepción.
     */
    val fechaVenta: LocalDate?,
    /**
     * `TIEMPO_A_CORTO_PLAZOMESES` crudo. **Se lee, no se interpreta**: quién
     * decide qué significa cada valor es [VencimientoDelCredito].
     */
    val plazoMeses: Int,
    /**
     * `PRECIO_TOTAL` — lo que **costó** la compra, que no es lo que falta por
     * pagar. Eso último es [saldo]; son dos cifras distintas y el papel las
     * nombra por separado, igual que las nombraba el ticket viejo.
     */
    val totalDeCompra: Money,
    /**
     * `NUM_PAGOS_ATRASADOS` de `overdue_payments_view`, que
     * `SaleDao.getByClientId` ya trae con su `LEFT JOIN`.
     *
     * **Cero cuando la vista no tiene fila para la cuenta**: ahí no hay atraso
     * que reportar. El papel no distingue ese cero del cero literal porque en
     * los dos casos no imprime nada.
     */
    val pagosVencidos: Int
) {
    /**
     * Cómo se llama esta cuenta **para el cobrador**: el producto.
     *
     * El folio es el último recurso, no el primero. "V-5021" no le dice nada a
     * nadie parado en una puerta; "Refrigerador Mabe 14 pies" sí. El fallback
     * existe porque `PRODUCTOS` sale de un `LEFT JOIN` con `GROUP_CONCAT` y
     * puede venir vacío, y una cuenta sin nombre visible sería imposible de
     * elegir.
     */
    val nombre: String get() = descripcion.ifBlank { folio }
}
