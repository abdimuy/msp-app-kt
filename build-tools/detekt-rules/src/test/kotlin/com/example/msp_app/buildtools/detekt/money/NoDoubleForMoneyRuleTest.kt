package com.example.msp_app.buildtools.detekt.money

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD de la regla (Task 9, Plan 1). `lint(code)` solo parsea el snippet a
 * PSI y corre la regla — no requiere compilar ni resolver tipos, que es
 * justo lo que `NoDoubleForMoneyRule` necesita (es puramente sintáctica).
 */
class NoDoubleForMoneyRuleTest {

    private val rule = NoDoubleForMoneyRule()

    @Test
    fun `falla con Double explicito en propiedad de dinero`() {
        val findings = rule.lint(
            """
            class Pago {
                val montoTotal: Double = 100.0
            }
            """.trimIndent()
        )
        assertEquals(1, findings.size)
        assertEquals("NoDoubleForMoney", findings.first().id)
    }

    @Test
    fun `falla con Float explicito en parametro de dinero`() {
        val findings = rule.lint(
            """
            class Recibo(val importe: Float)
            """.trimIndent()
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun `falla con Double explicito en tipo de retorno de funcion de dinero`() {
        val findings = rule.lint(
            """
            class Cobro {
                fun calcularSaldo(): Double {
                    return 0.0
                }
            }
            """.trimIndent()
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun `falla con literal flotante inferido en propiedad de dinero`() {
        val findings = rule.lint(
            """
            class Venta {
                val precioUnitario = 19.99
            }
            """.trimIndent()
        )
        assertEquals(1, findings.size)
    }

    @Test
    fun `pasa con BigDecimal para dinero`() {
        val findings = rule.lint(
            """
            import java.math.BigDecimal

            class Pago {
                val montoTotal: BigDecimal = BigDecimal("100.00")
            }
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `pasa con Long centavos para dinero`() {
        val findings = rule.lint(
            """
            class Pago {
                val montoTotalCentavos: Long = 10000L
            }
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }

    @Test
    fun `pasa con Double cuando el nombre no sugiere dinero`() {
        val findings = rule.lint(
            """
            class Ubicacion {
                val latitud: Double = 19.4326
            }
            """.trimIndent()
        )
        assertTrue(findings.isEmpty())
    }
}
