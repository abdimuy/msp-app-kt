package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.ResumenDeRitmo
import com.example.msp_app.feature.pagos.domain.model.RitmoDeSemana
import com.example.msp_app.feature.pagos.domain.model.SemanaDeRitmo
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * El "ritmo": las últimas [SEMANAS] semanas — *a tiempo / tarde / sin pago* —
 * más el pie cumple / promedio / sin pago. La UI las pinta como los doce
 * cuadros iguales de kollect; este objeto solo clasifica.
 *
 * Dominio PURO: cero `android.*`, cero Room, cero `Instant.now()`. El "hoy" se
 * recibe por parámetro ([hoy]) porque un borde de semana es justo donde alguien
 * alcanza el reloj del sistema.
 *
 * **El corte a tiempo/tarde es el mismo de la Task 14** —`abono >= parcialidad`
 * es haber pagado, `>` no— y por la misma razón: `internal/rutas/domain/
 * aporte.go:71` cuenta una cuota completa si y solo si `AbonoSemana >=
 * Parcialidad`. No se re-deriva el estado de la cuenta aquí: esto es el
 * histórico semana a semana, otro eje que el estado del periodo en curso.
 */
object RitmoDePagos {

    /** Las doce semanas del mock. */
    const val SEMANAS: Int = 12

    /**
     * Construye el ritmo de las últimas [SEMANAS] semanas terminando en la
     * semana de [hoy].
     *
     * La semana arranca en lunes, en la zona de negocio de `AppTime` — no en la
     * zona del teléfono. [parcialidad] es el objetivo semanal; si viene en cero
     * o negativa (dato sucio de Microsip) no se puede distinguir *a tiempo* de
     * *tarde*, así que todo abono positivo cae en [RitmoDeSemana.TARDE], que es
     * el lado conservador — el mismo que toma
     * `EstadoCuentaDeriver.estadoPorDinero`.
     */
    fun de(pagos: List<PagoDelHistorial>, parcialidad: Money, hoy: LocalDate): List<SemanaDeRitmo> {
        val finDeVentana = hoy.with(DayOfWeek.MONDAY)
        val inicioDeVentana = finDeVentana.minusWeeks((SEMANAS - 1).toLong())
        val cobradoPorSemana = pagos.groupingBy { semanaDe(it.fecha) }
            .fold(Money.ZERO) { acumulado, pago -> acumulado + pago.importe }
        return (0 until SEMANAS).map { indice ->
            val inicio = inicioDeVentana.plusWeeks(indice.toLong())
            val cobrado = cobradoPorSemana[inicio] ?: Money.ZERO
            SemanaDeRitmo(inicio = inicio, cobrado = cobrado, ritmo = ritmoDe(cobrado, parcialidad))
        }
    }

    /**
     * El pie del ritmo. [ResumenDeRitmo.promedio] promedia SOLO las semanas con
     * dinero — ver el KDoc de ese campo para el porqué.
     */
    fun resumen(semanas: List<SemanaDeRitmo>): ResumenDeRitmo {
        val conDinero = semanas.filter { it.cobrado > Money.ZERO }
        val promedio = if (conDinero.isEmpty()) {
            Money.ZERO
        } else {
            Money.of(
                Money.sum(conDinero.map { it.cobrado })
                    .amount
                    .divide(BigDecimal(conDinero.size), 2, RoundingMode.HALF_UP)
            )
        }
        return ResumenDeRitmo(
            cumplidas = semanas.count { it.ritmo == RitmoDeSemana.A_TIEMPO },
            totalSemanas = semanas.size,
            promedio = promedio,
            semanasSinPago = semanas.count { it.ritmo == RitmoDeSemana.SIN_PAGO }
        )
    }

    // `alturaDe` vivía aquí: la altura relativa de cada barra dentro del ciclo.
    // Se fue con las barras. El dueño eligió los DOCE CUADROS IGUALES de kollect
    // sobre las barras de altura variable del mock (ver el KDoc de
    // `RitmoDeSemanas`), así que ya no hay altura que calcular — y dejar la
    // función sin llamador sería dejar dominio muerto que ninguna prueba cubría.

    private fun semanaDe(instante: java.time.Instant): LocalDate =
        AppTime.toBusinessDate(instante).with(DayOfWeek.MONDAY)

    private fun ritmoDe(cobrado: Money, parcialidad: Money): RitmoDeSemana = when {
        cobrado <= Money.ZERO -> RitmoDeSemana.SIN_PAGO
        parcialidad <= Money.ZERO -> RitmoDeSemana.TARDE
        cobrado >= parcialidad -> RitmoDeSemana.A_TIEMPO
        else -> RitmoDeSemana.TARDE
    }

    /** Cuántas semanas cubre la ventana, para documentar el rango en la UI. */
    fun semanasEntre(desde: LocalDate, hasta: LocalDate): Long =
        ChronoUnit.WEEKS.between(desde, hasta)
}
