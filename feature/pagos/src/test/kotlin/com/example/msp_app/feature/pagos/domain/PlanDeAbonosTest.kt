package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Bordes exactos del plan de abonos — `==`, `-1`, `+1` — porque la frase "18 de
 * 24 abonos" es dinero contado en pantalla.
 */
class PlanDeAbonosTest {

    private fun dinero(pesos: String) = Money.of(BigDecimal(pesos))

    @Test
    fun `abonado exactamente n parcialidades cuenta n abonos`() {
        val plan = PlanDeAbonos.de(
            totalVenta = dinero("8400"),
            abonado = dinero("6300"),
            parcialidad = dinero("350")
        )
        assertEquals(18, plan.pagados)
        assertEquals(24, plan.totales)
    }

    @Test
    fun `un peso menos que n parcialidades cuenta n menos uno`() {
        val plan = PlanDeAbonos.de(
            totalVenta = dinero("8400"),
            abonado = dinero("6299"),
            parcialidad = dinero("350")
        )
        assertEquals(17, plan.pagados)
    }

    @Test
    fun `un peso mas que n parcialidades sigue contando n`() {
        val plan = PlanDeAbonos.de(
            totalVenta = dinero("8400"),
            abonado = dinero("6301"),
            parcialidad = dinero("350")
        )
        assertEquals(18, plan.pagados)
    }

    @Test
    fun `parcialidad cero no inventa plan ni divide entre cero`() {
        val plan = PlanDeAbonos.de(
            totalVenta = dinero("8400"),
            abonado = dinero("6300"),
            parcialidad = Money.ZERO
        )
        assertEquals(0, plan.pagados)
        assertEquals(0, plan.totales)
        assertEquals(0f, plan.avance, 0f)
    }

    @Test
    fun `parcialidad negativa se trata igual que cero`() {
        val plan = PlanDeAbonos.de(
            totalVenta = dinero("8400"),
            abonado = dinero("6300"),
            parcialidad = dinero("-350")
        )
        assertEquals(0, plan.totales)
    }

    @Test
    fun `el total redondea hacia arriba cuando no cabe entero`() {
        val plan = PlanDeAbonos.de(
            totalVenta = dinero("1000"),
            abonado = Money.ZERO,
            parcialidad = dinero("300")
        )
        assertEquals(4, plan.totales)
    }

    @Test
    fun `sobrepago se topa en el total y el avance en uno`() {
        val plan = PlanDeAbonos.de(
            totalVenta = dinero("8400"),
            abonado = dinero("9000"),
            parcialidad = dinero("350")
        )
        assertEquals(24, plan.pagados)
        assertEquals(1f, plan.avance, 0f)
    }

    @Test
    fun `abonado negativo no produce abonos negativos`() {
        val plan = PlanDeAbonos.de(
            totalVenta = dinero("8400"),
            abonado = dinero("-100"),
            parcialidad = dinero("350")
        )
        assertEquals(0, plan.pagados)
        assertEquals(0f, plan.avance, 0f)
    }
}
