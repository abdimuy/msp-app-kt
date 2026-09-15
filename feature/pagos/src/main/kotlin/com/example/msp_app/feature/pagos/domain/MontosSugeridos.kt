package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.CuentaCobrable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Los TRES montos sugeridos de la pantalla de abono (`registrar-abono.html`,
 * chips `.sug`): **esperado hoy**, **al corriente** y **liquidar**.
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
 * - **esperado hoy** = `parcialidad - abonoDelPeriodo`. Lo que falta para
 *   cubrir la cuota del periodo abierto. Se lee del estado YA derivado
 *   ([com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo]); esta
 *   pantalla no re-deriva nada del catálogo de ocho.
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
 *
 * Un sugerido en cero no se pinta, y dos sugeridos con el mismo importe se
 * colapsan al más significativo (el orden de la lista es la precedencia): tres
 * chips que dicen la misma cifra no son tres opciones, son ruido en una
 * pantalla de dinero.
 */
object MontosSugeridos {

    /** Cuál de los tres es. El color lo pone la UI desde la tabla del Task 2. */
    enum class Sugerencia(val etiqueta: String) {
        /** Lo que falta de la cuota del periodo. Verde `statusPaid`. */
        ESPERADO_HOY("esperado hoy"),

        /** Lo que falta para no traer atraso. Turquesa `statusTeal`. */
        AL_CORRIENTE("al corriente"),

        /** Cerrar la venta hoy. Violeta `promise`. */
        LIQUIDAR("liquidar")
    }

    /** Un chip: cuál es y cuánto ofrece. Siempre `0 < importe <= saldo`. */
    data class Sugerido(val cual: Sugerencia, val importe: Money)

    /** Los sugeridos de [venta] al día [hoy], en orden y sin repetidos. */
    fun de(venta: CuentaCobrable, hoy: LocalDate): List<Sugerido> {
        val saldo = venta.saldo
        if (saldo <= Money.ZERO) return emptyList()
        val candidatos = listOf(
            Sugerido(Sugerencia.ESPERADO_HOY, esperadoHoy(venta)),
            Sugerido(Sugerencia.AL_CORRIENTE, alCorriente(venta, hoy)),
            Sugerido(Sugerencia.LIQUIDAR, liquidar(venta))
        )
        val vistos = mutableListOf<Money>()
        return candidatos.filter { sugerido ->
            val nuevo = sugerido.importe > Money.ZERO && vistos.none { it == sugerido.importe }
            if (nuevo) vistos += sugerido.importe
            nuevo
        }
    }

    /** Lo que falta de la cuota del periodo, topado en el saldo. */
    fun esperadoHoy(venta: CuentaCobrable): Money = Money
        .of(venta.parcialidad.amount.subtract(venta.estado.abonoDelPeriodo.amount))
        .entre(Money.ZERO, venta.saldo)

    /**
     * El atraso en dinero: lo que el plan esperaba a estas alturas menos lo
     * realmente abonado (sin el enganche, que no es una cuota). Topado en el
     * saldo y con piso en cero — una venta adelantada no trae atraso negativo.
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

    private const val DIAS_SEMANAL = 7L
    private const val DIAS_QUINCENAL = 15L
    private const val DIAS_MENSUAL = 30L
}
