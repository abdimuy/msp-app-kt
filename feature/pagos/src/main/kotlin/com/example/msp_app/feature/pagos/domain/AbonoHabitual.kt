package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.time.Instant

/**
 * Un abono que este cliente ya dio. Lo mínimo para saber **qué suele dar**:
 * cuánto y cuándo.
 *
 * Es un tipo de dominio y no `PagoDelHistorial` a propósito: aquél arrastra
 * método, cobrador, ubicación y dos identidades de sincronización, y ninguna de
 * las seis cambia cuál es el monto habitual. El adaptador de pantalla proyecta
 * lo que haga falta a estos dos campos, y la regla se prueba sin fabricar un
 * pago completo.
 */
data class AbonoPrevio(val fecha: Instant, val importe: Money)

/**
 * **"Lo de siempre"**: el monto que ESTE cliente entrega más seguido.
 *
 * Dominio PURO. No hay reloj, no hay Android y no hay consulta: recibe los
 * abonos que ya ocurrieron y devuelve la moda, o `null` cuando todavía no hay
 * costumbre que afirmar.
 *
 * ## Es la MODA, no el promedio — y no es
 * [MontosSugeridosDelCliente.promedioDeMicrosip]
 *
 * Ese otro número suma `IMPORTE_PAGO_PROMEDIO` de las cuentas del cliente:
 * es un **promedio**, calculado por Microsip, a nivel cuenta. Éste es la
 * **moda** del historial real de abonos. No son dos implementaciones de lo
 * mismo y por eso no se colapsan:
 *
 * - un cliente que da `$100, $100, $100, $600` promedia `$225` —una cifra que
 *   nunca entregó— y su moda es `$100`, que es la que el cobrador reconoce;
 * - el promedio existe aunque el cliente haya pagado una sola vez, y esta moda
 *   se niega a existir ahí (ver el mínimo, abajo).
 *
 * La moda es la que sirve para las dos cosas de esta tarea —ofrecer un chip y
 * medir "cuántas veces más que lo normal es este monto"—, porque las dos
 * necesitan **un monto que el cliente de verdad entrega**, no su centro de masa.
 *
 * ## El mínimo: tres abonos, y que el habitual se repita
 *
 * Medido sobre los 6,165 abonos de la ruta: **236 clientes tienen 5 o más
 * abonos, 22 tienen de 2 a 4 y sólo 3 tienen uno**. O sea que el piso se puede
 * poner alto sin perder cobertura.
 *
 * Se piden dos cosas a la vez, y las dos hacen falta:
 *
 * 1. **[MINIMO_DE_ABONOS] = 3.** Con dos abonos distintos la "moda" la decide
 *    el desempate por fecha, o sea que "lo de siempre" sería en realidad "el
 *    último pago" con otro nombre — un chip que miente sobre qué lo respalda.
 * 2. **[MINIMO_DE_REPETICIONES] = 2.** Tres montos distintos no son una
 *    costumbre; son tres pagos. Exigir que el ganador se repita es lo que
 *    convierte la moda en un hecho ("ya dio esta cifra más de una vez") en vez
 *    de un artefacto de conteo.
 *
 * Con esas dos, un cliente sin costumbre no pinta el chip **y tampoco alimenta
 * los avisos de "más del triple de lo que suele dar"**: es la misma función, así
 * que la pantalla no puede acusar a nadie de salirse de una costumbre que nunca
 * se midió.
 *
 * ## El desempate es por reciente, y sólo entre empatados
 *
 * Dos montos con la misma cuenta de repeticiones: gana el que tenga el abono
 * más nuevo. Es la regla del dueño, y describe lo que pasa cuando alguien
 * **cambia** de costumbre — `$100, $100, $200, $200` es un cliente que subió a
 * $200, no un empate sin información.
 */
object AbonoHabitual {

    /** Cuántos abonos hacen falta antes de afirmar una costumbre. Ver el KDoc. */
    const val MINIMO_DE_ABONOS: Int = 3

    /** Cuántas veces tiene que aparecer el monto ganador. Ver el KDoc. */
    const val MINIMO_DE_REPETICIONES: Int = 2

    /**
     * El monto que más se repite en [historial], o `null` si no hay costumbre.
     *
     * Los abonos en cero o negativos se descartan antes de contar: no son
     * dinero entregado y meterlos al denominador movería el mínimo sin mover el
     * hecho.
     */
    fun de(historial: List<AbonoPrevio>): Money? {
        val abonos = historial.filter { it.importe > Money.ZERO }
        if (abonos.size < MINIMO_DE_ABONOS) return null
        return abonos
            .groupBy { it.importe }
            .filterValues { it.size >= MINIMO_DE_REPETICIONES }
            .maxWithOrNull(
                compareBy<Map.Entry<Money, List<AbonoPrevio>>> { (_, mismos) -> mismos.size }
                    .thenBy { (_, mismos) -> mismos.maxOf { abono -> abono.fecha } }
            )
            ?.key
    }
}
