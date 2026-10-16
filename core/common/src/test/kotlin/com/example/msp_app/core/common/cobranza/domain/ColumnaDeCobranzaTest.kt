package com.example.msp_app.core.common.cobranza.domain

import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class ColumnaDeCobranzaTest {
    private val hoy = LocalDate.parse("2026-10-15")
    private val fechas = mapOf(
        "vencida" to LocalDate.parse("2026-10-14"),
        "hoy" to hoy,
        "futura" to LocalDate.parse("2026-10-16"),
        "nula" to null
    )

    @Test
    fun `E-APP-070 la tabla del spec estado por estado y fecha por fecha`() {
        for ((nombre, f) in fechas) {
            for (e in EstadoCuenta.entries) {
                val obtenido =
                    columnaDe(e, fechaPromesa = f, horaCita = LocalTime.of(10, 0), hoy = hoy)
                val esperado = when {
                    e == EstadoCuenta.PAGO || e == EstadoCuenta.ABONO_PARCIAL -> ColumnaDeCobranza.PAGADOS
                    e == EstadoCuenta.PROMETIO_PROXIMA && nombre == "futura" -> ColumnaDeCobranza.NO_PAGA
                    else -> ColumnaDeCobranza.POR_VISITAR
                }
                assertEquals("$e, fecha $nombre", esperado, obtenido)
            }
        }
    }

    @Test
    fun `E-APP-070 lo que cambia contra los cuatro segmentos`() {
        assertEquals(
            ColumnaDeCobranza.PAGADOS,
            columnaDe(EstadoCuenta.ABONO_PARCIAL, null, null, hoy)
        )
        assertEquals(
            ColumnaDeCobranza.POR_VISITAR,
            columnaDe(EstadoCuenta.SE_NEGO, null, null, hoy)
        )
        assertEquals(
            ColumnaDeCobranza.POR_VISITAR,
            columnaDe(EstadoCuenta.CITA_A_UNA_HORA, null, LocalTime.of(16, 30), hoy)
        )
        assertEquals(
            ColumnaDeCobranza.POR_VISITAR,
            columnaDe(EstadoCuenta.CITA_A_UNA_HORA, null, null, hoy)
        )
        assertEquals(
            ColumnaDeCobranza.NO_PAGA,
            columnaDe(EstadoCuenta.PROMETIO_PROXIMA, LocalDate.parse("2026-10-20"), null, hoy)
        )
        assertEquals(
            ColumnaDeCobranza.POR_VISITAR,
            columnaDe(EstadoCuenta.PROMETIO_PROXIMA, null, null, hoy)
        )
    }

    @Test
    fun `E-APP-070 total y disjunta con entradas al azar`() {
        val rng = Random(20261016)
        repeat(20_000) {
            val e = EstadoCuenta.entries[rng.nextInt(EstadoCuenta.entries.size)]
            val f = if (rng.nextInt(4) == 0) null else hoy.plusDays((rng.nextInt(21) - 10).toLong())
            val h = if (rng.nextBoolean()) LocalTime.of(9, 30) else null
            val c = columnaDe(e, f, h, hoy)
            val trato = TratoDeEstado.de(e, f, h)
            val enPagados = trato == TratoDelEstado.PAGADO || trato == TratoDelEstado.PARCIAL
            val enNoPaga = trato == TratoDelEstado.DIFERIDO && f!!.isAfter(hoy)
            // Exactamente una columna: el conteo de predicados verdaderos por columna es 1.
            val aciertos = ColumnaDeCobranza.entries.count { col ->
                when (col) {
                    ColumnaDeCobranza.PAGADOS -> enPagados
                    ColumnaDeCobranza.NO_PAGA -> enNoPaga
                    ColumnaDeCobranza.POR_VISITAR -> !enPagados && !enNoPaga
                }
            }
            assertEquals("$e $f $h", 1, aciertos)
            assertEquals("$e $f $h", enPagados, c == ColumnaDeCobranza.PAGADOS)
            assertEquals("$e $f $h", enNoPaga, c == ColumnaDeCobranza.NO_PAGA)
        }
    }

    @Test
    fun `los codigos son los del contrato v2_2`() {
        assertEquals(
            listOf("por_visitar", "no_paga", "pagados"),
            ColumnaDeCobranza.entries.map {
                it.codigo
            }
        )
    }
}
