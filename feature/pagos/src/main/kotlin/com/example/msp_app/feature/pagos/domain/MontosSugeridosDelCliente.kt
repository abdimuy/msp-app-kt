package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import java.time.LocalDate

/**
 * Las dos cifras que el detalle del cliente pone junto al saldo: **"suele dar"**
 * y **"pídele hoy"**.
 *
 * Dominio PURO, como [MontosSugeridos]: el "hoy" entra por parámetro.
 *
 * ## Lo que NO está aquí, y por qué
 *
 * **No hay "liquida todo con".** Liquidar es por venta, contra un
 * `DOCTO_CC_ACR_ID`: una suma a nivel cliente no corresponde a ninguna operación
 * que la app pueda ejecutar, así que el cobrador no puede cobrar esa cifra de una.
 * Vive donde siempre vivió, dentro del detalle de cada venta.
 *
 * ## Por qué se suma por venta y no se agrega el cliente
 *
 * Las fórmulas son las de [MontosSugeridos], aplicadas **cuenta por cuenta** y
 * sumadas al final. No es lo mismo que calcularlas sobre los totales del
 * cliente: cada venta tiene su propia fecha, su propia frecuencia y su propio
 * enganche, así que "periodos transcurridos" es distinto en cada una. Aplanarlas
 * daría un número que no es el de ninguna cuenta.
 *
 * Y cada sumando ya viene topado en el saldo de SU venta, así que el total nunca
 * puede exceder el saldo del cliente — la misma invariante de [MontosSugeridos],
 * heredada por construcción en vez de re-impuesta.
 */
object MontosSugeridosDelCliente {

    /**
     * Lo que este cliente **suele dar** cuando paga: la suma de
     * `IMPORTE_PAGO_PROMEDIO` de sus cuentas.
     *
     * Se suma porque cuando el cobrador pasa, el cliente abona a cada cuenta: lo
     * que entrega en la puerta es el total, no el de una.
     *
     * `null` cuando **ninguna** cuenta trae el dato — sin dato no se afirma un
     * promedio, y `$0` diría "no suele dar nada", que es otra cosa. Con algunas
     * cuentas con dato y otras sin él se suman las que lo traen: es la mejor
     * cifra cierta disponible, y la alternativa (esconder el renglón porque una
     * cuenta nueva todavía no tiene promedio) escondería el dato justo cuando el
     * cliente acaba de comprar otra cosa.
     */
    fun sueleDar(ventas: List<VentaDelCliente>): Money? {
        val conDato = ventas.mapNotNull { it.pagoPromedio }
        return if (conDato.isEmpty()) null else Money.sum(conDato)
    }

    /**
     * Lo que hay que **pedirle hoy** para dejarlo sin atraso, sumando sus
     * cuentas.
     *
     * Por cuenta se toma **el mayor** entre lo que falta de la cuota del periodo
     * abierto ([MontosSugeridos.esperadoHoy]) y lo que falta para no traer atraso
     * ([MontosSugeridos.alCorriente]).
     *
     * El mayor, y no uno de los dos, porque **ninguno contiene al otro**:
     *  - una venta vencida tiene el atraso por encima de la cuota, y pedir solo
     *    la cuota dejaría el atraso vivo;
     *  - una venta **estrenada hoy** no tiene periodos transcurridos, así que su
     *    atraso es `0` mientras su cuota ya corre. Pedir solo el atraso pediría
     *    `$0` de una cuenta que sí debe algo esta semana.
     *
     * Una cuenta ya cubierta aporta `0` y no resta: los sugeridos tienen piso en
     * cero, así que un cliente adelantado en una cuenta no "descuenta" del atraso
     * de otra. Eso sería cruzar dinero entre dos créditos distintos.
     */
    fun pideleHoy(ventas: List<VentaDelCliente>, hoy: LocalDate): Money = Money.sum(
        ventas.map { venta ->
            maxOf(MontosSugeridos.esperadoHoy(venta), MontosSugeridos.alCorriente(venta, hoy))
        }
    )
}
