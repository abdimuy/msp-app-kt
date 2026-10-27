package com.example.msp_app.core.common.cobranza.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tabla de `TratoDe` en msp-api (presentacion.go:25-51), fila por fila (E-APP-070). */
class TratoDeEstadoTest {
    private val fecha = LocalDate.parse("2026-10-15")
    private val hora = LocalTime.of(10, 0)

    private fun trato(e: EstadoCuenta, f: LocalDate?, h: LocalTime?) = TratoDeEstado.de(e, f, h)

    @Test
    fun `E-APP-070 los ocho estados con y sin fecha y con y sin hora`() {
        for (f in listOf(fecha, null)) {
            for (h in listOf(hora, null)) {
                val c = "fecha=$f hora=$h"
                assertEquals(c, TratoDelEstado.PAGADO, trato(EstadoCuenta.PAGO, f, h))
                assertEquals(c, TratoDelEstado.PARCIAL, trato(EstadoCuenta.ABONO_PARCIAL, f, h))
                assertEquals(c, TratoDelEstado.REGRESAS, trato(EstadoCuenta.VISITE_VUELVO, f, h))
                assertEquals(
                    c,
                    if (f == null) TratoDelEstado.REGRESAS else TratoDelEstado.DIFERIDO,
                    trato(EstadoCuenta.PROMETIO_PROXIMA, f, h)
                )
                assertEquals(c, TratoDelEstado.ESCALAR, trato(EstadoCuenta.SE_NEGO, f, h))
                assertEquals(
                    c,
                    if (h == null) TratoDelEstado.REGRESAS else TratoDelEstado.CITA,
                    trato(EstadoCuenta.CITA_A_UNA_HORA, f, h)
                )
                assertEquals(c, TratoDelEstado.NADIE, trato(EstadoCuenta.NO_ESTABA, f, h))
                assertEquals(c, TratoDelEstado.SIN_TRABAJAR, trato(EstadoCuenta.SIN_TOCAR, f, h))
            }
        }
    }
}
