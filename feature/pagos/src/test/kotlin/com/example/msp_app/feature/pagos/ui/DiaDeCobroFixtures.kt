package com.example.msp_app.feature.pagos.ui

import com.example.msp_app.feature.pagos.domain.DiaAcordado
import com.example.msp_app.feature.pagos.domain.DiaDeCobroDeLaVenta
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.JUEVES
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.MARTES
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.SABADO
import com.example.msp_app.feature.pagos.domain.DiaDeLaSemana.VIERNES
import com.example.msp_app.feature.pagos.domain.EstimacionDelDia
import com.example.msp_app.feature.pagos.domain.PagosDelDia
import java.time.Instant

/** Los siete renglones de la tabla del spec §7, como fixtures reutilizables. */
internal object DiaDeCobroFixtures {

    /** 2026-11-11 18:40 UTC = 12:40 en la oficina. */
    val CALCULADO: Instant = Instant.parse("2026-11-11T18:40:00Z")

    fun estimacion(principal: DiaDeLaSemana?, vararg dias: Pair<DiaDeLaSemana, Int>) =
        EstimacionDelDia(
            pagosConsiderados = dias.sumOf { it.second },
            principal = principal,
            dias = dias.map { PagosDelDia(it.first, it.second) }
        )

    private fun semana(dia: DiaDeLaSemana) = DiaAcordado.DeLaSemana(dia)

    private fun venta(acordado: DiaAcordado?, estimacion: EstimacionDelDia?) =
        DiaDeCobroDeLaVenta(acordado, estimacion, CALCULADO)

    /** Acordado = el día que más paga: `✓ Acordado`. */
    fun coincide() = venta(
        semana(SABADO),
        estimacion(SABADO, SABADO to 5, MARTES to 3, JUEVES to 2)
    )

    /** Acordó sábado, paga más el martes: borde naranja. Con un pago suelto en "otros". */
    fun distinto() = venta(
        semana(SABADO),
        estimacion(MARTES, MARTES to 4, VIERNES to 3, SABADO to 2, DiaDeLaSemana.LUNES to 1)
    )

    /** Venta nueva: solo el día acordado, sin barra. */
    fun soloAcordado() = venta(semana(SABADO), null)

    /** Venta vieja sin acordado: sin etiqueta, con barra. */
    fun soloEstimacion() = venta(null, estimacion(MARTES, MARTES to 6, VIERNES to 2, JUEVES to 1))

    /** Empate en el primer lugar, con acordado: barra sin tramo fuerte. */
    fun empateConAcordado() =
        venta(semana(SABADO), estimacion(null, MARTES to 3, VIERNES to 3, JUEVES to 2))

    /** Empate sin acordado: "Distintos días". */
    fun empateSinAcordado() = venta(null, estimacion(null, MARTES to 3, VIERNES to 3, JUEVES to 2))

    /** Mensual: solo el acordado, "Día 15". */
    fun mensual() = venta(DiaAcordado.DelMes(15), null)
}
