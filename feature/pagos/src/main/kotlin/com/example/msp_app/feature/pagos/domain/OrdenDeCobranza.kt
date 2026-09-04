package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.time.LocalDate

/**
 * La posición de UNA venta en el orden de cobranza. Dos campos, en el orden en
 * que mandan.
 *
 * [sinAbonos] es el predicado que la lista vieja escribía así
 * (`SalesScreen.kt:201-203`, código que hoy corre en la calle):
 *
 * ```kotlin
 * compareByDescending<SaleWithProducts> { it.SALDO_REST == it.PRECIO_TOTAL - it.ENGANCHE }
 *     .thenBy { it.FECHA }
 * ```
 *
 * "El saldo sigue siendo exactamente lo financiado" = no ha entrado un solo
 * peso. `compareByDescending` sobre un `Boolean` pone `true` primero, así que
 * los que no han abonado nada encabezan la lista; después van los demás por
 * fecha de venta ascendente, las más viejas primero.
 */
data class RangoDeCobranza(
    /** El saldo sigue siendo exactamente lo financiado: cero abonos. */
    val sinAbonos: Boolean,
    /** `Sale.FECHA` ya parseada. `null` cuando no se pudo leer — ver [PRIMERO]. */
    val fechaVenta: LocalDate?
)

/**
 * El orden de la lista de cobranza. **Se copia del que ya existe**; no se
 * mejora.
 *
 * ## Qué cambió al portarlo, y qué no
 *
 * El **predicado** es idéntico: `saldo == totalVenta - enganche`. Lo que cambia
 * es el tipo en el que se evalúa. La pantalla vieja lo hacía sobre `Double`
 * crudo de Room; aquí llega como [Money] porque la REGLA DE DINERO
 * (`global-constraints.md`) prohíbe que un `Double` cruce el adaptador, y
 * `RoomVentasAdapter` ya envuelve `SALDO_REST`/`PRECIO_TOTAL`/`ENGANCHE` con
 * `Money.of(...)` en el borde.
 *
 * Sobre pesos enteros —que es lo que trae la base— las dos aritméticas dan
 * exactamente el mismo booleano, y eso está probado fila por fila en
 * `OrdenIgualAlDeSalesScreenTest`, que corre la expresión legada sobre los
 * `Double` originales y este orden sobre los mismos datos y compara las dos
 * secuencias. Donde SÍ pueden diferir es en centavos: `8400.10 - 900.05` en
 * binario no siempre es el `Double` exacto que Firebird guardó en `SALDO_REST`,
 * y ahí el `==` legado contesta `false` para una venta que no ha recibido un
 * peso. La resta decimal de [Money] contesta `true`, que es la respuesta que el
 * predicado quería. Es la única divergencia posible y está aislada en su propio
 * test.
 *
 * ## Las fechas ilegibles van al final
 *
 * `Sale.FECHA` es texto y puede no parsearse; ahí [RangoDeCobranza.fechaVenta]
 * llega en `null`. Una venta sin fecha no puede ordenarse por fecha, así que
 * cae al final de su grupo ([PRIMERO] usa `nullsLast`) en vez de colarse al
 * principio como si fuera la más vieja de la ruta. **No es un silencio:**
 * `ReunirCartera` cuenta cuántas hubo y lo emite con
 * `PagosTelemetria.CODE_VENTA_SIN_FECHA_LEGIBLE` — el orden de la lista es el
 * orden del día del cobrador, y que se mueva sin señal es justo lo que la NORMA
 * DE ERRORES prohíbe.
 */
object OrdenDeCobranza {

    /**
     * El orden entre ventas: primero las que no han abonado nada, después por
     * fecha de venta ascendente (las viejas primero), y las fechas ilegibles al
     * final.
     */
    val PRIMERO: Comparator<RangoDeCobranza> =
        compareByDescending<RangoDeCobranza> { it.sinAbonos }
            .thenBy(nullsLast()) { it.fechaVenta }

    /**
     * El rango de una venta. [saldo] es lo que falta, [totalVenta] el precio
     * financiado y [enganche] lo que se dio de entrada: si el saldo sigue
     * siendo la diferencia exacta, nadie ha abonado.
     */
    fun rangoDe(
        saldo: Money,
        totalVenta: Money,
        enganche: Money,
        fechaVenta: LocalDate?
    ): RangoDeCobranza = RangoDeCobranza(
        sinAbonos = saldo == totalVenta - enganche,
        fechaVenta = fechaVenta
    )

    /**
     * El rango que un CLIENTE hereda de sus ventas: el de su venta **mejor
     * rankeada**, o sea la que aparecería primero si la lista fuera por venta.
     *
     * Es la decisión que el brief pide tomar explícitamente. Con dos ventas un
     * cliente puede rankear distinto según cuál se mire, y se toma la mejor
     * porque el cobrador toca UNA puerta: si detrás de esa puerta hay una
     * cuenta que no ha recibido un peso, la puerta se visita temprano aunque la
     * otra cuenta esté al corriente. Tomar la peor escondería esa cuenta al
     * final del día; promediarlas inventaría un número que no existe.
     *
     * Devuelve `null` solo si [rangos] viene vacía, que es un cliente sin
     * ventas — no se pinta.
     */
    fun mejorDe(rangos: List<RangoDeCobranza>): RangoDeCobranza? = rangos.minWithOrNull(PRIMERO)
}
