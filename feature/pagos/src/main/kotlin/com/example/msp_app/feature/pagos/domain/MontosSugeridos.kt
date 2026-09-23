package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.CuentaCobrable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Los montos sugeridos de la pantalla de abono (`registrar-abono.html`, chips
 * `.sug`): **esperado hoy**, **al corriente**, **liquidar**, **lo de siempre**
 * y **los redondos**.
 *
 * Dominio PURO. El "hoy" entra por parámetro, como en [RitmoDePagos]: un borde
 * de periodo es justo donde alguien alcanza el reloj del sistema.
 *
 * ## La invariante que los hace parte del diseño de seguridad
 *
 * **Ningún sugerido puede exceder el saldo.** Es el hermano blando del bloqueo
 * duro de [SeguridadDelAbono]: si un chip pudiera ofrecer un sobrepago, el
 * cobrador lo tocaría y se toparía con el bloqueo — una pantalla que ofrece lo
 * que ella misma prohíbe. Todos se topan en `saldo`.
 *
 * ## De dónde sale cada cifra
 *
 * - **esperado hoy** = `cuota afirmable - abonoDelPeriodo`. Lo que falta para
 *   cubrir la cuota del periodo abierto. Se lee del estado YA derivado
 *   ([com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo]); esta
 *   pantalla no re-deriva nada del catálogo de ocho.
 *
 *   **La cuota afirmable no es la `PARCIALIDAD` capturada** ([CuotaDeLaVenta]):
 *   sale del comportamiento de la cuenta cuando hay tres pagos o más, y no sale
 *   de ningún lado cuando el dato se ve mal. El chip y el aviso beben de la
 *   MISMA fuente a propósito — si el chip ofreciera la parcialidad y el aviso
 *   midiera contra otra cosa, la pantalla propondría una cifra y después la
 *   cuestionaría, que es justo lo que la regla "la app no interroga lo que
 *   propuso" prohíbe.
 * - **al corriente** = `periodos transcurridos x parcialidad - lo abonado sin
 *   contar el enganche`. Es la MISMA fórmula del atraso que la app ya usa en
 *   producción (`overdue_payments_view`, `NUM_PAGOS_ATRASADOS`), en dinero en
 *   vez de en número de cuotas. Se recalcula aquí y no se lee de esa vista por
 *   una razón concreta: la vista une `sales.DOCTO_CC_ID` con
 *   `payment.DOCTO_CC_ACR_ID` —dos llaves distintas, el mismo cruce que ya
 *   costó el defecto del entonces vigente `NewVisitDialog` (commit
 *   `721c5551`; el diálogo lo retiró después la Task 21)—, así que su
 *   número no es confiable para alimentar un monto de dinero. La fórmula sí lo
 *   es; el `JOIN` es lo que no. **No se toca esa vista**: se reporta.
 * - **liquidar** = "hoy liquida con" cuando existe (viene de
 *   `SettlementCalculator`, lógica de dinero de producción que NO se
 *   reimplementa), o el saldo completo cuando no hay oferta de liquidación.
 * - **lo de siempre** = la moda del historial de abonos de ESTE cliente, ver
 *   [AbonoHabitual]. Es la única fuente que **no** sale de la deuda: las otras
 *   cuatro dicen lo que la venta pide, ésta dice lo que el cliente da.
 * - **los redondos** = $100, $200 y $150, en ese orden. Medido sobre 6,165
 *   abonos reales de la ruta: **$100 el 37.5 %, $200 el 24.9 %, $150 el
 *   18.4 %** — los tres juntos son el **81 %** de todo lo cobrado. El orden es
 *   el de esa frecuencia, no el numérico.
 *
 * Un sugerido en cero no se pinta, y dos sugeridos con el mismo importe se
 * colapsan al más significativo (el orden de la lista es la precedencia): tres
 * chips que dicen la misma cifra no son tres opciones, son ruido en una
 * pantalla de dinero.
 *
 * ## El tope de chips, y por qué es 5
 *
 * Con cinco fuentes puede haber siete candidatos, y siete chips no caben en una
 * pantalla de dinero. [MAXIMO_DE_CHIPS] los corta en **cinco**, por el tamaño
 * de letra grande y no por el normal: en `NORMAL` la fila se desplaza en
 * horizontal y siete chips sólo serían incómodos, pero en `GRANDE` y
 * `MUY_GRANDE` los chips se **apilan** (`EnFilaOApiladas`) y cada uno mide al
 * menos 56dp — siete son ~400dp de columna, o sea el teclado empujado fuera de
 * la pantalla justo para el cobrador que peor ve. Cinco caben con el teclado a
 * la vista.
 *
 * El corte es por la cola, así que lo que se pierde primero son los redondos, y
 * eso está asumido: los tres primeros son los que dicen algo de ESTA cuenta y
 * son los únicos que cierran un periodo. En la práctica el corte muerde poco,
 * porque el de-duplicado suele liberar lugares — lo esperado hoy coincide con
 * $100, $150 o $200 en buena parte de la ruta, y ahí el redondo desaparece por
 * repetido antes de que el tope tenga que actuar.
 */
object MontosSugeridos {

    /** Cuál es. El color lo pone la UI desde la tabla del Task 2. */
    enum class Sugerencia(val etiqueta: String) {
        /** Lo que falta de la cuota del periodo. Verde `statusPaid`. */
        ESPERADO_HOY("Esperado hoy"),

        /**
         * Lo mismo, pero cuando la cuota salió del **comportamiento** de la
         * cuenta y no de la parcialidad capturada
         * ([OrigenDeLaCuota.COMPORTAMIENTO]).
         *
         * Es una entrada aparte **sólo por el rótulo**: el importe, el color y
         * el lugar en la fila son los mismos. Existe porque un "esperado" que a
         * veces es el dato de la venta y a veces la costumbre de la cuenta, sin
         * decir cuál, es peor que uno equivocado pero predecible.
         */
        ESPERADO_POR_COSTUMBRE("Lo que paga"),

        /** Lo que falta para no traer atraso. Turquesa `statusTeal`. */
        AL_CORRIENTE("Al corriente"),

        /** Cerrar la venta hoy. Violeta `promise`. */
        LIQUIDAR("Liquidar"),

        /** Lo que este cliente entrega de costumbre. Azul `brand`. */
        LO_DE_SIEMPRE("Lo de siempre"),

        /**
         * Uno de los tres montos que se cobran todo el tiempo en la ruta.
         * Neutro: no dice nada de ESTA cuenta, así que no lleva color de estado.
         *
         * Es **una** entrada para los tres, y por eso [Sugerido.clave] existe.
         */
        REDONDO("Común")
    }

    /**
     * Un chip: cuál es y cuánto ofrece. Siempre `0 < importe <= saldo`.
     *
     * [clave] y no [Sugerencia.name] es lo que distingue a los chips cuando dos
     * comparten [cual]: los tres redondos lo hacen, y dos nodos con el mismo
     * `testTag` no son dos chips que se puedan tocar por separado.
     */
    data class Sugerido(val cual: Sugerencia, val importe: Money) {
        /** El sufijo del `testTag`. Único aunque dos chips compartan [cual]. */
        val clave: String
            get() = if (cual == Sugerencia.REDONDO) {
                "redondo_" + importe.amount.toBigInteger()
            } else {
                cual.name.lowercase()
            }
    }

    /**
     * Los sugeridos de [venta] al día [hoy], en orden, sin repetidos y como
     * mucho [MAXIMO_DE_CHIPS].
     *
     * [historial] son los abonos que este CLIENTE ya dio — de ahí sale "lo de
     * siempre". Vacío es lo normal en las pantallas que no lo cargan: el chip
     * simplemente no se pinta.
     */
    fun de(
        venta: CuentaCobrable,
        hoy: LocalDate,
        historial: List<AbonoPrevio> = emptyList()
    ): List<Sugerido> {
        val saldo = venta.saldo
        if (saldo <= Money.ZERO) return emptyList()
        val candidatos = listOf(
            Sugerido(cualEsperado(venta), esperadoHoy(venta)),
            Sugerido(Sugerencia.AL_CORRIENTE, alCorriente(venta, hoy)),
            Sugerido(Sugerencia.LIQUIDAR, liquidar(venta))
        ) +
            listOfNotNull(AbonoHabitual.de(historial)?.let { Sugerido(Sugerencia.LO_DE_SIEMPRE, it) }) +
            REDONDOS.map { Sugerido(Sugerencia.REDONDO, it) }
        val vistos = mutableListOf<Money>()
        return candidatos.filter { sugerido ->
            // El tope en el saldo se aplica AQUÍ para las cinco fuentes, y no
            // sólo dentro de cada fórmula: las tres primeras ya venían topadas,
            // pero "lo de siempre" y los redondos vienen de fuera de la deuda y
            // podrían exceder el saldo de una cuenta chica. Un solo lugar
            // guarda la invariante para todos.
            val nuevo = sugerido.importe > Money.ZERO &&
                sugerido.importe <= saldo &&
                vistos.none { it == sugerido.importe }
            if (nuevo) vistos += sugerido.importe
            nuevo
        }.take(MAXIMO_DE_CHIPS)
    }

    /**
     * Lo que falta de la cuota del periodo, topado en el saldo.
     *
     * Sale de [CuentaCobrable.cuota] y no de `parcialidad`: **cero cuando la
     * cuota es dudosa**, que es el mismo valor que [SeguridadDelAbono] y
     * [AvisosDelAbono] ya interpretan como "no se sabe qué toca". Por eso una
     * parcialidad que se ve mal apaga sola el chip, el prellenado del teclado y
     * el aviso de abono corto, sin tocar ninguna de las tres piezas.
     */
    fun esperadoHoy(venta: CuentaCobrable): Money = Money
        .of(venta.cuota.esperado.amount.subtract(venta.estado.abonoDelPeriodo.amount))
        .entre(Money.ZERO, venta.saldo)

    /** Con qué rótulo se pinta el esperado: el dato de la venta o su costumbre. */
    fun cualEsperado(venta: CuentaCobrable): Sugerencia =
        if (venta.cuota.origen == OrigenDeLaCuota.COMPORTAMIENTO) {
            Sugerencia.ESPERADO_POR_COSTUMBRE
        } else {
            Sugerencia.ESPERADO_HOY
        }

    /**
     * El atraso en dinero: lo que el plan esperaba a estas alturas menos lo
     * realmente abonado (sin el enganche, que no es una cuota). Topado en el
     * saldo y con piso en cero — una venta adelantada no trae atraso negativo.
     *
     * ## Sigue usando `parcialidad`, y eso es deliberado
     *
     * Ésta es la **misma fórmula del atraso que usa producción**
     * (`overdue_payments_view`, `NUM_PAGOS_ATRASADOS`), en dinero en vez de en
     * número de cuotas. Cambiarle la base a [CuentaCobrable.cuota] cambiaría lo
     * que la app llama "atraso" —no sólo en esta pantalla— y eso no se hace sin
     * decirlo antes.
     *
     * **Lo que sí hay que saber:** con una parcialidad inflada este número se
     * dispara y se topa en el saldo, así que el chip "al corriente" termina
     * ofreciendo el saldo completo. Y como el de-duplicado colapsa al más
     * significativo, el chip que sobrevive se rotula AL CORRIENTE cuando en
     * realidad es liquidar. Está medido y reportado; no se arregla aquí.
     */
    fun alCorriente(venta: CuentaCobrable, hoy: LocalDate): Money {
        val esperadoAcumulado = venta.parcialidad.amount
            .multiply(BigDecimal(periodosTranscurridos(venta, hoy)))
        val cuotasPagadas = venta.abonado.amount.subtract(venta.enganche.amount)
        return Money.of(esperadoAcumulado.subtract(cuotasPagadas)).entre(Money.ZERO, venta.saldo)
    }

    /** "Hoy liquida con", o el saldo completo si esta venta no tiene oferta. */
    fun liquidar(venta: CuentaCobrable): Money =
        (venta.liquidacion?.monto ?: venta.saldo).entre(Money.ZERO, venta.saldo)

    /**
     * Cuántos periodos completos han pasado desde la venta. **Completos**: no
     * se debe una fracción de cuota, y truncar es el lado conservador (nunca
     * sugiere de más).
     *
     * Los divisores son los mismos que ya usa la app para contar cuotas
     * transcurridas (`overdue_payments_view`): 7 / 15 / 30 días, y 1 para
     * cualquier otra frecuencia.
     */
    fun periodosTranscurridos(venta: CuentaCobrable, hoy: LocalDate): Long {
        val inicio = venta.fechaVenta ?: return 0
        val dias = ChronoUnit.DAYS.between(inicio, hoy)
        if (dias <= 0) return 0
        return dias / diasPorPeriodo(venta.frecuencia)
    }

    private fun diasPorPeriodo(frecuencia: String): Long = when (frecuencia.trim().lowercase()) {
        "semanal" -> DIAS_SEMANAL
        "quincenal" -> DIAS_QUINCENAL
        "mensual" -> DIAS_MENSUAL
        else -> 1L
    }

    private fun Money.entre(minimo: Money, maximo: Money): Money =
        if (this < minimo) minimo else if (this > maximo) maximo else this

    /** Cuántos chips caben sin empujar el teclado fuera. Ver el KDoc de la clase. */
    const val MAXIMO_DE_CHIPS: Int = 5

    /**
     * Los tres montos que son el **81 %** de lo cobrado en esta ruta, en orden
     * de frecuencia medida: $100 (37.5 %), $200 (24.9 %), $150 (18.4 %).
     *
     * El orden es el de la medición y no el numérico: cuando el tope corta, lo
     * que sobrevive tiene que ser lo que más se cobra.
     */
    val REDONDOS: List<Money> = listOf(
        Money.of(BigDecimal(100)),
        Money.of(BigDecimal(200)),
        Money.of(BigDecimal(150))
    )

    private const val DIAS_SEMANAL = 7L
    private const val DIAS_QUINCENAL = 15L
    private const val DIAS_MENSUAL = 30L
}
