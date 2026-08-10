package com.example.msp_app.feature.collectionreport.domain.model

import com.example.msp_app.core.designsystem.component.formatMoneyMxn
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Robustez SUPREMA del VO de dinero: invariante de escala 2, aritmética exacta
 * (sin `Double`), igualdad por valor consistente, y render consistente con
 * `formatMoneyMxn` (HALF_UP). Cero dependencia de Android — unit test JVM puro.
 */
class MoneyTest {

    // region — escala 2 invariante

    @Test
    fun `ZERO tiene escala 2 y valor cero`() {
        assertEquals(BigDecimal("0.00"), Money.ZERO.amount)
        assertEquals(2, Money.ZERO.amount.scale())
    }

    @Test
    fun `of BigDecimal normaliza a escala 2`() {
        assertEquals(2, Money.of(BigDecimal("5")).amount.scale())
        assertEquals(2, Money.of(BigDecimal("5.1")).amount.scale())
        assertEquals(2, Money.of(BigDecimal("5.129")).amount.scale())
        assertEquals(BigDecimal("18300.00"), Money.of(BigDecimal("18300")).amount)
    }

    @Test
    fun `of Double normaliza a escala 2`() {
        assertEquals(BigDecimal("18300.00"), Money.of(18300.0).amount)
        assertEquals(2, Money.of(0.0).amount.scale())
    }

    @Test
    fun `plus preserva escala 2`() {
        assertEquals(2, (Money.of(1.0) + Money.of(2.5)).amount.scale())
    }

    @Test
    fun `minus preserva escala 2`() {
        assertEquals(2, (Money.of(1.0) - Money.of(2.5)).amount.scale())
    }

    // region — puente Double -> money con HALF_UP

    @Test
    fun `of Double redondea HALF_UP igual que formatMoneyMxn`() {
        assertEquals(BigDecimal("2.68"), Money.of(2.675).amount)
        assertEquals(BigDecimal("2.67"), Money.of(2.674).amount)
        assertEquals(BigDecimal("0.01"), Money.of(0.005).amount)
    }

    @Test
    fun `of Double soporta negativos`() {
        assertEquals(BigDecimal("-850.00"), Money.of(-850.0).amount)
        assertEquals(BigDecimal("-1234567.89"), Money.of(-1234567.89).amount)
    }

    // region — aritmética exacta sin Double

    @Test
    fun `suma de decimales es exacta donde Double fallaria`() {
        // 0.1 + 0.2 == 0.3 exacto (con Double daria 0.30000000000000004).
        assertEquals(Money.of(0.3), Money.of(0.1) + Money.of(0.2))
    }

    @Test
    fun `plus y minus`() {
        assertEquals(Money.of(100.75), Money.of(100.50) + Money.of(0.25))
        assertEquals(Money.of(99.70), Money.of(100.00) - Money.of(0.30))
    }

    @Test
    fun `plus es asociativo`() {
        val a = Money.of(10.10)
        val b = Money.of(20.20)
        val c = Money.of(30.30)
        assertEquals((a + b) + c, a + (b + c))
    }

    @Test
    fun `plus es conmutativo`() {
        assertEquals(Money.of(5.55) + Money.of(4.45), Money.of(4.45) + Money.of(5.55))
    }

    @Test
    fun `plus con ZERO es identidad`() {
        val m = Money.of(1234.56)
        assertEquals(m, m + Money.ZERO)
        assertEquals(m, Money.ZERO + m)
    }

    // region — sum de listas

    @Test
    fun `sum de lista vacia es ZERO`() {
        assertEquals(Money.ZERO, Money.sum(emptyList()))
    }

    @Test
    fun `sum de lista suma todos los elementos`() {
        val items = listOf(Money.of(100.00), Money.of(250.50), Money.of(0.49))
        assertEquals(Money.of(350.99), Money.sum(items))
    }

    @Test
    fun `sum con negativos se cancela a ZERO`() {
        assertEquals(Money.ZERO, Money.sum(listOf(Money.of(100.0), Money.of(-100.0))))
    }

    // region — igualdad por valor y comparacion

    @Test
    fun `igualdad es por valor entre distintos caminos de construccion`() {
        assertEquals(Money.ZERO, Money.of(0.0))
        assertEquals(Money.ZERO, Money.of(BigDecimal("0")))
        assertEquals(Money.of(5.0), Money.of(BigDecimal("5")))
        assertEquals(Money.of(5.0), Money.of(BigDecimal("5.00")))
    }

    @Test
    fun `valores distintos no son iguales`() {
        assertNotEquals(Money.of(5.0), Money.of(5.01))
    }

    @Test
    fun `compareTo ordena por monto`() {
        assertTrue(Money.of(1.00) < Money.of(2.00))
        assertTrue(Money.of(-1.00) < Money.ZERO)
        assertEquals(0, Money.of(2.50).compareTo(Money.of(2.50)))
    }

    @Test
    fun `lista ordenable por compareTo`() {
        val sorted = listOf(Money.of(3.0), Money.of(1.0), Money.of(2.0)).sorted()
        assertEquals(listOf(Money.of(1.0), Money.of(2.0), Money.of(3.0)), sorted)
    }

    // region — sin perdida de precision en magnitudes grandes

    @Test
    fun `sin perdida de precision en montos grandes`() {
        val big = Money.of(BigDecimal("12345678901234.56"))
        assertEquals(BigDecimal("12345678901234.56"), big.amount)
    }

    // region — render consistente con el design system

    @Test
    fun `render via formatMoneyMxn es consistente`() {
        // formatMoneyMxn redondea a peso entero para DISPLAY (decisión de negocio: sin
        // centavos); el `Money` que le entra sigue siendo exacto a escala 2 — el
        // redondeo ocurre SOLO en el string de salida, nunca en el VO.
        assertEquals("$1,234,568", formatMoneyMxn(Money.of(1234567.89).amount))
        assertEquals("$0", formatMoneyMxn(Money.ZERO.amount))
        assertEquals("-$850", formatMoneyMxn(Money.of(-850.0).amount))
        assertEquals(
            "$351",
            formatMoneyMxn(Money.sum(listOf(Money.of(350.50), Money.of(0.49))).amount)
        )
    }
}
