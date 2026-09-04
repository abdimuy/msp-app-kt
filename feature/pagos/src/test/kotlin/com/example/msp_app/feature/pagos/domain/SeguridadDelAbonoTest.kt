package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El diseño de seguridad del abono, borde por borde.
 *
 * El caso central es **el borde exacto del sobrepago**: con saldo $1,450, el
 * abono de $1,450 se puede registrar y el de $1,451 no. No "alrededor de":
 * exactamente ahí. Ese `>` en vez de `>=` es la diferencia entre poder liquidar
 * una venta y no poder.
 */
class SeguridadDelAbonoTest {

    private val saldo = dinero("1450")
    private val esperado = dinero("220")

    // --- El borde exacto del sobrepago ---------------------------------------

    @Test
    fun `el saldo exacto se puede registrar`() {
        val veredicto = evaluar(saldo)
        assertTrue(veredicto.sePuedeRegistrar)
        assertFalse(BloqueoDelAbono.EXCEDE_EL_SALDO in veredicto.bloqueos)
        assertEquals("liquida: el saldo queda en cero", Money.ZERO, veredicto.saldoNuevo)
    }

    @Test
    fun `el saldo mas uno se bloquea`() {
        val veredicto = evaluar(dinero("1451"))
        assertFalse(veredicto.sePuedeRegistrar)
        assertTrue(BloqueoDelAbono.EXCEDE_EL_SALDO in veredicto.bloqueos)
    }

    @Test
    fun `el saldo menos uno se puede registrar`() {
        val veredicto = evaluar(dinero("1449"))
        assertTrue(veredicto.sePuedeRegistrar)
        assertEquals(dinero("1"), veredicto.saldoNuevo)
    }

    @Test
    fun `un centavo por encima del saldo tambien se bloquea`() {
        assertTrue(BloqueoDelAbono.EXCEDE_EL_SALDO in evaluar(dinero("1450.01")).bloqueos)
    }

    @Test
    fun `un centavo por debajo del saldo pasa`() {
        assertTrue(evaluar(dinero("1449.99")).sePuedeRegistrar)
    }

    // --- Los otros dos bloqueos ---------------------------------------------

    @Test
    fun `cero no es un abono`() {
        val veredicto = evaluar(Money.ZERO)
        assertTrue(BloqueoDelAbono.NO_ES_POSITIVO in veredicto.bloqueos)
        assertEquals("sin monto, el saldo no se mueve", saldo, veredicto.saldoNuevo)
    }

    @Test
    fun `un negativo no sube el saldo, se bloquea`() {
        val veredicto = evaluar(Money.of(BigDecimal("-100")))
        assertTrue(BloqueoDelAbono.NO_ES_POSITIVO in veredicto.bloqueos)
        assertEquals(saldo, veredicto.saldoNuevo)
    }

    @Test
    fun `una venta sin saldo no admite abonos`() {
        val veredicto = SeguridadDelAbono.evaluar(
            monto = dinero("100"),
            saldo = Money.ZERO,
            esperadoHoy = esperado,
            yaAbonoEstePeriodo = false
        )
        assertTrue(BloqueoDelAbono.VENTA_SIN_SALDO in veredicto.bloqueos)
        assertFalse(veredicto.sePuedeRegistrar)
    }

    @Test
    fun `un monto bloqueado no acumula rarezas encima`() {
        // 300,000 sobre un saldo de 1,450: excede Y seria "muy arriba de lo
        // esperado". El bloqueo manda; la alerta de raro no se pinta encima.
        val veredicto = evaluar(dinero("300000"))
        assertFalse(veredicto.sePuedeRegistrar)
        assertTrue(veredicto.rarezas.isEmpty())
        assertFalse(veredicto.esRaro)
    }

    // --- Las rarezas ---------------------------------------------------------

    @Test
    fun `cinco veces lo esperado es raro, cuatro no`() {
        assertTrue(
            RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO in evaluar(dinero("1100")).rarezas
        )
        assertFalse(
            RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO in evaluar(dinero("880")).rarezas
        )
    }

    @Test
    fun `sin esperado conocido nadie es muy alto`() {
        val veredicto = SeguridadDelAbono.evaluar(
            monto = dinero("1000"),
            saldo = saldo,
            esperadoHoy = Money.ZERO,
            yaAbonoEstePeriodo = false
        )
        assertFalse(RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO in veredicto.rarezas)
    }

    // --- El abono corto (Ruling AL, ronda 2 de arreglo) -----------------------

    /**
     * **El borde exacto.** Lo esperado justo NO es raro —cubrir la cuota es lo
     * normal—; un centavo menos sí. Es el hermano de
     * [RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO] en la otra dirección, y repone
     * el aviso que `NewPaymentDialog` pintaba ("el pago es menor a la
     * parcialidad acordada") y que se perdió al retirarlo.
     */
    @Test
    fun `un centavo menos de lo esperado es corto, lo esperado exacto no`() {
        assertTrue(RarezaDelAbono.ABAJO_DE_LO_ESPERADO in evaluar(dinero("219.99")).rarezas)
        assertFalse(RarezaDelAbono.ABAJO_DE_LO_ESPERADO in evaluar(dinero("220")).rarezas)
        assertFalse(RarezaDelAbono.ABAJO_DE_LO_ESPERADO in evaluar(dinero("220.01")).rarezas)
    }

    /**
     * **Corto avisa, nunca bloquea.** Un abono parcial es legítimo y frecuente:
     * el aviso existe para que sea deliberado, no para impedirlo. Si esta
     * afirmación se pusiera roja, la rareza se habría convertido en bloqueo y el
     * cobrador no podría cobrar lo que el cliente sí trae.
     */
    @Test
    fun `el abono corto avisa pero NUNCA bloquea`() {
        val veredicto = evaluar(dinero("150"))
        assertTrue(RarezaDelAbono.ABAJO_DE_LO_ESPERADO in veredicto.rarezas)
        assertTrue("una rareza NUNCA bloquea", veredicto.sePuedeRegistrar)
        assertTrue(veredicto.esRaro)
        assertTrue(veredicto.bloqueos.isEmpty())
    }

    /**
     * Sin ventana de cobro no se sabe qué toca, y avisar "es menor a lo
     * esperado" contra un `ZERO` sería inventar el esperado. Misma regla que
     * [RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO], que ya la tenía.
     */
    @Test
    fun `sin esperado conocido nadie es corto`() {
        val veredicto = SeguridadDelAbono.evaluar(
            monto = dinero("1"),
            saldo = saldo,
            esperadoHoy = Money.ZERO,
            yaAbonoEstePeriodo = false
        )
        assertFalse(RarezaDelAbono.ABAJO_DE_LO_ESPERADO in veredicto.rarezas)
    }

    /**
     * **Los dos avisos contra lo esperado son mutuamente excluyentes**, y no por
     * casualidad: uno mira `>= 5x` y el otro `< 1x`. Sin esta afirmación, un
     * cambio de umbral podría encender los dos a la vez y la hoja pintaría "es
     * mucho mayor" sobre un abono corto.
     */
    @Test
    fun `corto y muy arriba no pueden encenderse juntos`() {
        listOf("0.01", "1", "219.99", "220", "220.01", "1099", "1100", "1450").forEach { pesos ->
            val rarezas = evaluar(dinero(pesos)).rarezas
            assertFalse(
                "los dos avisos contra lo esperado en $pesos",
                RarezaDelAbono.ABAJO_DE_LO_ESPERADO in rarezas &&
                    RarezaDelAbono.MUY_ARRIBA_DE_LO_ESPERADO in rarezas
            )
        }
    }

    @Test
    fun `un monto que no termina en 00 ni en 50 es raro`() {
        assertTrue(RarezaDelAbono.NO_TERMINA_EN_CINCUENTA in evaluar(dinero("317")).rarezas)
        assertFalse(RarezaDelAbono.NO_TERMINA_EN_CINCUENTA in evaluar(dinero("350")).rarezas)
        assertFalse(RarezaDelAbono.NO_TERMINA_EN_CINCUENTA in evaluar(dinero("300")).rarezas)
    }

    @Test
    fun `liquidar exento aunque el saldo no sea redondo`() {
        // Con un saldo que NO es multiplo de 50, liquidarlo exacto sigue sin ser raro.
        val saldoRaro = dinero("1233")
        val veredicto = SeguridadDelAbono.evaluar(
            monto = saldoRaro,
            saldo = saldoRaro,
            esperadoHoy = esperado,
            yaAbonoEstePeriodo = false
        )
        assertFalse(RarezaDelAbono.NO_TERMINA_EN_CINCUENTA in veredicto.rarezas)
    }

    @Test
    fun `lo esperado exacto exento aunque no sea redondo`() {
        val veredicto = SeguridadDelAbono.evaluar(
            monto = dinero("317"),
            saldo = saldo,
            esperadoHoy = dinero("317"),
            yaAbonoEstePeriodo = false
        )
        assertFalse(RarezaDelAbono.NO_TERMINA_EN_CINCUENTA in veredicto.rarezas)
    }

    @Test
    fun `ya abono este periodo es una rareza, no un bloqueo`() {
        val veredicto = SeguridadDelAbono.evaluar(
            monto = dinero("200"),
            saldo = saldo,
            esperadoHoy = esperado,
            yaAbonoEstePeriodo = true
        )
        assertTrue(RarezaDelAbono.YA_ABONO_ESTE_PERIODO in veredicto.rarezas)
        assertTrue("una rareza NUNCA bloquea", veredicto.sePuedeRegistrar)
        assertTrue(veredicto.esRaro)
    }

    // --- El saldo anterior -> saldo nuevo de la confirmacion -----------------

    @Test
    fun `el saldo nuevo es la consecuencia exacta del abono`() {
        val veredicto = evaluar(dinero("220"))
        assertEquals(saldo, veredicto.saldoAnterior)
        assertEquals(dinero("1230"), veredicto.saldoNuevo)
    }

    // --- El cinturon que reusa `application/` --------------------------------

    @Test
    fun `bloqueosDe y evaluar no pueden discrepar`() {
        listOf("0", "1", "220", "1449", "1450", "1451", "300000").forEach { pesos ->
            val monto = dinero(pesos)
            assertEquals(
                "los dos cinturones ven lo mismo en $pesos",
                SeguridadDelAbono.bloqueosDe(monto, saldo),
                SeguridadDelAbono.evaluar(monto, saldo, esperado, false).bloqueos
            )
        }
    }

    private fun evaluar(monto: Money) = SeguridadDelAbono.evaluar(
        monto = monto,
        saldo = saldo,
        esperadoHoy = esperado,
        yaAbonoEstePeriodo = false
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
