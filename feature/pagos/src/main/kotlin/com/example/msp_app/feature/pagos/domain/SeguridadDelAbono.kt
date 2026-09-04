package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal

/**
 * Por qué un abono NO se puede registrar. Son **bloqueos, no advertencias**: si
 * hay uno, el guardado es imposible por diseño.
 *
 * El diseño de seguridad que esta pantalla conserva íntegro nació de un abono
 * de seis cifras aceptado sobre una venta de cuatro, porque nada validaba
 * `monto <= saldo`. El bloqueo duro es la respuesta a ese defecto: no se avisa,
 * se impide.
 */
enum class BloqueoDelAbono {
    /** El monto es cero, negativo o no se puede leer. No hay nada válido que registrar. */
    NO_ES_POSITIVO,

    /** El monto es estrictamente mayor al saldo. El bloqueo duro por sobrepago. */
    EXCEDE_EL_SALDO,

    /**
     * La venta ya no debe nada. "Liquidada" es un hecho del **saldo**, no del
     * estado: `EstadoCuenta` describe cómo se trabajó el periodo, no si la
     * cuenta sigue viva, y usarlo aquí confundiría "pagó esta semana" con "ya
     * no debe".
     */
    VENTA_SIN_SALDO
}

/**
 * Por qué un abono que SÍ se puede registrar merece un aviso. Una rareza
 * **nunca** bloquea.
 *
 * Solo se evalúan sobre un monto que no está bloqueado — un monto prohibido no
 * necesita además ruido de "¿seguro?" encima.
 *
 * ## Dos tonos, y la diferencia importa (Ruling AM, ronda 3 de arreglo)
 *
 * [escalaLaHoja] parte estas rarezas en dos, y no es una decisión de gusto:
 *
 * - **Las que escalan** son las anómalas: el dígito de más, el monto que no
 *   cuadra, el posible cobro duplicado. La hoja se pinta en rojo y el CTA cambia
 *   a "sí, el monto es correcto" — hay que **afirmar** el monto, no solo
 *   continuar.
 * - **Las que solo avisan** describen un desenlace que el dominio de este plan ya
 *   modela como **normal**: `EstadoCuenta` distingue *Pagó* de *Abonó parcial*, y
 *   el contrato de tests exige probar los tres casos alrededor de la
 *   `PARCIALIDAD`. Un desenlace que el dominio trata como de primera clase no
 *   puede pintar la pantalla de peligro.
 *
 * El daño de mezclarlas es concreto y va en la dirección contraria a la
 * intuición: **una alarma que suena en el caso común entrena al cobrador a
 * descartarla**, y se lleva por delante a la que sí importaba. Degradar el canal
 * del duplicado —que es raro y es caro— es peor que no haber avisado del abono
 * corto.
 *
 * @property escalaLaHoja si esta rareza sola basta para poner la confirmación en
 *   rojo. Toda rareza nueva tiene que declararlo: es la pregunta que este enum
 *   obliga a contestar.
 */
enum class RarezaDelAbono(val escalaLaHoja: Boolean) {
    /** Cinco veces o más lo esperado hoy. El dígito de más. */
    MUY_ARRIBA_DE_LO_ESPERADO(escalaLaHoja = true),

    /**
     * Menos de lo esperado hoy. **El hermano de arriba, en la otra dirección.**
     *
     * Repone el aviso que `NewPaymentDialog` pintaba en rojo ("el pago es menor
     * a la parcialidad acordada de $X") y que se perdió al retirarlo. Un abono
     * corto es legítimo y frecuente —por eso es rareza y no bloqueo—, pero tiene
     * que ser **deliberado**: es el único camino por el que una cuenta se atrasa
     * sin que nadie lo diga en voz alta.
     *
     * La pantalla nueva pre-carga lo esperado en el teclado, pero eso **no**
     * cubre este caso: `montoInicialDe` lo marca como sugerido y la primera
     * tecla lo reemplaza entero, así que el cobrador que teclea un monto corto
     * nunca vio la cifra que reemplazó. Éste es exactamente ese cobrador.
     *
     * **No escala la hoja** (Ruling AM): un abono parcial es un desenlace que el
     * dominio ya trata como normal. Se dice en ámbar —el mismo tono de
     * `EstadoCuenta.ABONO_PARCIAL`— y el CTA sigue siendo "confirmar y
     * registrar". Es la única rareza de este tono junto con... ninguna otra: las
     * demás sí son anómalas.
     */
    ABAJO_DE_LO_ESPERADO(escalaLaHoja = false),

    /**
     * No termina en 00 ni en 50. Se exenta cuando liquida la venta (== saldo) o
     * cuando es exactamente lo esperado hoy: esos dos montos son raros por
     * aritmética, no por error de dedo.
     */
    NO_TERMINA_EN_CINCUENTA(escalaLaHoja = true),

    /** Esta venta ya recibió dinero en el periodo abierto. El posible duplicado. */
    YA_ABONO_ESTE_PERIODO(escalaLaHoja = true)
}

/**
 * El veredicto sobre el monto tecleado: los [bloqueos] duros que impiden
 * registrarlo, las [rarezas] blandas que solo lo hacen ver inusual, y el
 * **saldo anterior → saldo nuevo** que la confirmación en dos pasos enseña
 * antes de tocar dinero.
 */
data class VeredictoDelAbono(
    val bloqueos: Set<BloqueoDelAbono>,
    val rarezas: Set<RarezaDelAbono>,
    val saldoAnterior: Money,
    val saldoNuevo: Money
) {
    /** Se puede registrar si y solo si nada lo bloquea. */
    val sePuedeRegistrar: Boolean get() = bloqueos.isEmpty()

    /**
     * Registrable pero **anómalo**: la confirmación se pone roja y el CTA obliga
     * a afirmar el monto.
     *
     * No es "hay alguna rareza" sino "hay alguna que escale" (Ruling AM). Un
     * aviso de tono suave —hoy solo [RarezaDelAbono.ABAJO_DE_LO_ESPERADO]— se
     * pinta igual, en su banda ámbar, sin encender esto. Y basta **una** grave
     * para escalar: con un abono corto encima de un posible duplicado, gana la
     * más grave.
     */
    val esRaro: Boolean get() = rarezas.any { it.escalaLaHoja }

    companion object {
        /**
         * El veredicto mientras la venta no ha cargado: bloqueado por
         * [BloqueoDelAbono.NO_ES_POSITIVO], que apaga el CTA sin fingir que se
         * conoce un saldo.
         */
        val SIN_VENTA: VeredictoDelAbono = VeredictoDelAbono(
            bloqueos = setOf(BloqueoDelAbono.NO_ES_POSITIVO),
            rarezas = emptySet(),
            saldoAnterior = Money.ZERO,
            saldoNuevo = Money.ZERO
        )
    }
}

/**
 * El núcleo de seguridad de dinero de la pantalla de abono. Dominio PURO: cero
 * `android.*`, cero Room, cero reloj. Toda la aritmética es [Money] (BigDecimal
 * escala 2) — nunca `Double`.
 *
 * [bloqueosDe] se expone aparte de [evaluar] a propósito: es el cinturón que
 * `application/` vuelve a abrochar antes de escribir, y para eso solo necesita
 * el monto y el saldo. Que los dos caminos llamen a la MISMA función es lo que
 * hace imposible que la pantalla y el caso de uso discrepen sobre qué es
 * sobrepago.
 */
object SeguridadDelAbono {

    /** Los bloqueos duros de [monto] contra [saldo]. La frontera del sobrepago. */
    fun bloqueosDe(monto: Money, saldo: Money): Set<BloqueoDelAbono> = buildSet {
        if (monto <= Money.ZERO) add(BloqueoDelAbono.NO_ES_POSITIVO)
        // Estrictamente mayor: pagar EXACTAMENTE el saldo es liquidar, y liquidar
        // se permite. El borde exacto (saldo pasa, saldo + 1 no) lo prueba
        // `SeguridadDelAbonoTest`.
        if (monto > saldo) add(BloqueoDelAbono.EXCEDE_EL_SALDO)
        if (saldo <= Money.ZERO) add(BloqueoDelAbono.VENTA_SIN_SALDO)
    }

    /**
     * El veredicto completo.
     *
     * @param monto lo que el cobrador tecleó.
     * @param saldo lo que la venta debe hoy — el techo del bloqueo duro.
     * @param esperadoHoy la cuota que toca en el periodo abierto (`ZERO` si no se sabe).
     * @param yaAbonoEstePeriodo lo dice el estado del periodo YA derivado
     *   (`EstadoDelPeriodo.abonoDelPeriodo > 0`); esta función no lo deduce ni
     *   consulta nada.
     */
    fun evaluar(
        monto: Money,
        saldo: Money,
        esperadoHoy: Money,
        yaAbonoEstePeriodo: Boolean
    ): VeredictoDelAbono {
        val bloqueos = bloqueosDe(monto, saldo)
        val saldoNuevo = if (monto <= Money.ZERO) {
            saldo.coerceAtLeast(Money.ZERO)
        } else {
            (saldo - monto).coerceAtLeast(Money.ZERO)
        }
        val rarezas = if (bloqueos.isEmpty()) {
            rarezasDe(monto, saldo, esperadoHoy, yaAbonoEstePeriodo)
        } else {
            emptySet()
        }
        return VeredictoDelAbono(
            bloqueos = bloqueos,
            rarezas = rarezas,
            saldoAnterior = saldo,
            saldoNuevo = saldoNuevo
        )
    }

    private fun rarezasDe(
        monto: Money,
        saldo: Money,
        esperadoHoy: Money,
        yaAbonoEstePeriodo: Boolean
    ): Set<RarezaDelAbono> = buildSet {
        if (esperadoHoy > Money.ZERO && monto.amount >= esperadoHoy.amount.multiply(CINCO)) {
            add(RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO)
        }
        // Estrictamente menor: pagar EXACTAMENTE lo esperado es cubrir la cuota,
        // y eso no tiene nada de raro. `esperadoHoy == ZERO` significa "no se
        // sabe qué toca" (sin ventana de cobro), y ahí no hay contra qué
        // comparar: avisar sería inventar un esperado. El borde exacto
        // (esperado pasa, un centavo menos no) lo prueba `SeguridadDelAbonoTest`.
        if (esperadoHoy > Money.ZERO && monto < esperadoHoy) {
            add(RarezaDelAbono.ABAJO_DE_LO_ESPERADO)
        }
        val redondo = monto.amount.remainder(CINCUENTA).signum() == 0
        val liquida = monto.amount.compareTo(saldo.amount) == 0
        val esLoEsperado = monto.amount.compareTo(esperadoHoy.amount) == 0
        if (!redondo && !liquida && !esLoEsperado) add(RarezaDelAbono.NO_TERMINA_EN_CINCUENTA)
        if (yaAbonoEstePeriodo) add(RarezaDelAbono.YA_ABONO_ESTE_PERIODO)
    }

    private val CINCO: BigDecimal = BigDecimal(5)
    private val CINCUENTA: BigDecimal = BigDecimal(50)
}
