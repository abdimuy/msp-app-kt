package com.example.msp_app.feature.collectionreport.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Aritmética de calendario que respalda [CobranzaPorcentaje.vencimientosVencidos]/
 * [CobranzaPorcentaje.aplicaEnVentana] (puerto fiel de `rutasdomain` en `calendario.go`,
 * `msp-api`) — separada a su propio archivo SOLO para mantener [CobranzaPorcentaje] bajo el
 * umbral `TooManyFunctions` de detekt (convención del proyecto: dividir, no suprimir). No es
 * un puerto público independiente: sus funciones son detalle de implementación de
 * `CobranzaPorcentaje`, de ahí `internal`.
 */
internal object CobranzaCalendario {

    private const val MONTHS_IN_YEAR = 12
    private const val DAYS_PER_WEEK = 7
    private const val DIA_QUINCENA = 15

    /** SEMANAL/default de `VencimientosVencidos`: `floor(daysBetween(cargo, inicio)/7)`, mín 0. */
    fun weeklyVencidos(fechaCargo: LocalDate, fechaInicio: LocalDate): Int {
        val d = ChronoUnit.DAYS.between(fechaCargo, fechaInicio).toInt()
        if (d < 0) return 0
        return d / DAYS_PER_WEEK
    }

    /** MENSUAL de `VencimientosVencidos`: día-1 candidatos, gracia aplicada. */
    fun contarVencidosMensual(cargo: LocalDate, inicio: LocalDate, graceDias: Int): Int {
        var count = 0
        var candidate = LocalDate.of(cargo.year, cargo.monthValue, 1)
        if (!candidate.isAfter(cargo)) {
            candidate = candidate.plusMonths(1)
        }
        while (!candidate.isAfter(inicio)) {
            val withGrace = candidate.plusDays(graceDias.toLong())
            if (withGrace.isBefore(inicio)) count++
            candidate = candidate.plusMonths(1)
        }
        return count
    }

    /** QUINCENAL de `VencimientosVencidos`: día-15/último-día-de-mes candidatos, gracia aplicada. */
    fun contarVencidosQuincenal(cargo: LocalDate, inicio: LocalDate, graceDias: Int): Int {
        var count = 0
        var idx = cargo.year * MONTHS_IN_YEAR + cargo.monthValue - 1
        val idxEnd = inicio.year * MONTHS_IN_YEAR + inicio.monthValue - 1
        while (idx <= idxEnd) {
            count += countQuincenalCandidatesInMonth(idx, cargo, inicio, graceDias)
            idx++
        }
        return count
    }

    private fun countQuincenalCandidatesInMonth(
        idx: Int,
        cargo: LocalDate,
        inicio: LocalDate,
        graceDias: Int
    ): Int = quincenalCandidates(idx).count { v ->
        v.isAfter(cargo) && !v.isAfter(inicio) && v.plusDays(graceDias.toLong()).isBefore(inicio)
    }

    /** SEMANAL/default de `AplicaEnVentana`: existe múltiplo de 7 desde cargo en `[lo, hi]`. */
    fun aplicaSemanalEnVentana(cargo: LocalDate, lo: LocalDate, hi: LocalDate): Boolean {
        val offsetLo = ChronoUnit.DAYS.between(cargo, lo).toInt()
        val offsetHi = ChronoUnit.DAYS.between(cargo, hi).toInt()
        if (offsetHi < DAYS_PER_WEEK) return false
        val minOffset = maxOf(offsetLo, DAYS_PER_WEEK)
        val firstMult = ((minOffset + (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK) * DAYS_PER_WEEK
        return firstMult <= offsetHi
    }

    /** MENSUAL de `AplicaEnVentana`: existe día-1 en `[lo, hi]` posterior a cargo. */
    fun aplicaMensualEnVentana(cargo: LocalDate, lo: LocalDate, hi: LocalDate): Boolean {
        var y = lo.year
        var m = lo.monthValue
        val yEnd = hi.year
        val mEnd = hi.monthValue
        while (true) {
            val candidate = LocalDate.of(y, m, 1)
            if (candidate.isAfter(hi)) return false
            if (!candidate.isBefore(lo) && candidate.isAfter(cargo)) return true
            if (y == yEnd && m == mEnd) return false
            m++
            if (m > MONTHS_IN_YEAR) {
                m = 1
                y++
            }
        }
    }

    /** QUINCENAL de `AplicaEnVentana`: existe día-15/último-día-de-mes en `[lo, hi]` posterior a cargo. */
    fun aplicaQuincenalEnVentana(cargo: LocalDate, lo: LocalDate, hi: LocalDate): Boolean {
        var idx = lo.year * MONTHS_IN_YEAR + lo.monthValue - 1
        val idxEnd = hi.year * MONTHS_IN_YEAR + hi.monthValue - 1
        while (idx <= idxEnd) {
            if (quincenalCandidates(
                    idx
                ).any { !it.isAfter(hi) && !it.isBefore(lo) && it.isAfter(cargo) }
            ) {
                return true
            }
            idx++
        }
        return false
    }

    /** Día-15 y último-día-de-mes del mes codificado por el índice monotónico [idx] (año*12+mes-1). */
    private fun quincenalCandidates(idx: Int): List<LocalDate> {
        val yy = idx / MONTHS_IN_YEAR
        val mm = idx % MONTHS_IN_YEAR + 1
        val day15 = LocalDate.of(yy, mm, DIA_QUINCENA)
        val lastDay = LocalDate.of(yy, mm, 1).plusMonths(1).minusDays(1)
        return listOf(day15, lastDay)
    }
}
