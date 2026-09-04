package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.ui.AbonoFixtures
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los tres montos sugeridos.
 *
 * La invariante que importa es la de dinero: **ninguno puede exceder el
 * saldo**. Un chip que ofreciera un sobrepago sería una pantalla ofreciendo lo
 * que ella misma bloquea.
 */
class MontosSugeridosTest {

    private val hoy = PagosFixtures.HOY

    @Test
    fun `los tres del mock, con las cifras del mock`() {
        val sugeridos = MontosSugeridos.de(AbonoFixtures.detalle(), hoy)
        assertEquals(
            listOf(
                MontosSugeridos.Sugerencia.ESPERADO_HOY,
                MontosSugeridos.Sugerencia.AL_CORRIENTE,
                MontosSugeridos.Sugerencia.LIQUIDAR
            ),
            sugeridos.map { it.cual }
        )
        assertEquals(dinero("220"), sugeridos[0].importe)
        assertEquals(dinero("440"), sugeridos[1].importe)
        assertEquals(dinero("1290"), sugeridos[2].importe)
    }

    @Test
    fun `ninguno excede el saldo, ni siquiera con una liquidacion mayor`() {
        // Una liquidacion mal calculada por encima del saldo NO puede convertirse
        // en un chip de sobrepago: se topa.
        val venta = AbonoFixtures.detalle().copy(
            liquidacion = Liquidacion(
                monto = dinero("99999"),
                vigenteHasta = null,
                categoria = "rota"
            )
        )
        val sugeridos = MontosSugeridos.de(venta, hoy)
        assertTrue(sugeridos.isNotEmpty())
        sugeridos.forEach {
            assertTrue("${it.cual} = ${it.importe.amount}", it.importe <= venta.saldo)
        }
        assertEquals(
            venta.saldo,
            sugeridos.single {
                it.cual == MontosSugeridos.Sugerencia.LIQUIDAR
            }.importe
        )
    }

    @Test
    fun `lo esperado descuenta lo que ya entro en el periodo`() {
        val venta = AbonoFixtures.detalle(AbonoFixtures.estadoConAbonoParcial())
        assertEquals(dinero("120"), MontosSugeridos.esperadoHoy(venta))
    }

    @Test
    fun `un periodo ya cubierto no ofrece esperado hoy`() {
        val venta = AbonoFixtures.detalle(
            AbonoFixtures.estadoSinTocar().copy(abonoDelPeriodo = AbonoFixtures.PARCIALIDAD)
        )
        assertEquals(Money.ZERO, MontosSugeridos.esperadoHoy(venta))
        assertTrue(
            MontosSugeridos.de(venta, hoy).none {
                it.cual == MontosSugeridos.Sugerencia.ESPERADO_HOY
            }
        )
    }

    @Test
    fun `una venta al dia no ofrece ponerse al corriente`() {
        // Vendida hace una semana: una cuota transcurrida, y ya abono de sobra.
        val venta = AbonoFixtures.detalle().copy(fechaVenta = hoy.minusDays(7))
        assertEquals(Money.ZERO, MontosSugeridos.alCorriente(venta, hoy))
        assertTrue(
            MontosSugeridos.de(venta, hoy).none {
                it.cual == MontosSugeridos.Sugerencia.AL_CORRIENTE
            }
        )
    }

    @Test
    fun `sin saldo no hay nada que sugerir`() {
        val venta = AbonoFixtures.detalle().copy(saldo = Money.ZERO)
        assertEquals(emptyList<MontosSugeridos.Sugerido>(), MontosSugeridos.de(venta, hoy))
    }

    @Test
    fun `dos sugeridos con el mismo importe se colapsan al mas significativo`() {
        // Sin liquidacion, "liquidar" es el saldo; con un atraso igual al saldo,
        // "al corriente" valdria lo mismo y solo debe quedar el primero.
        val venta = AbonoFixtures.detalle().copy(
            liquidacion = null,
            enganche = Money.ZERO,
            abonado = Money.ZERO,
            fechaVenta = hoy.minusDays(700)
        )
        val sugeridos = MontosSugeridos.de(venta, hoy)
        assertEquals(
            listOf(
                MontosSugeridos.Sugerencia.ESPERADO_HOY,
                MontosSugeridos.Sugerencia.AL_CORRIENTE
            ),
            sugeridos.map { it.cual }
        )
        assertEquals(venta.saldo, sugeridos[1].importe)
    }

    @Test
    fun `sin fecha de venta no se inventan periodos transcurridos`() {
        val venta = AbonoFixtures.detalle().copy(fechaVenta = null)
        assertEquals(0L, MontosSugeridos.periodosTranscurridos(venta, hoy))
        assertEquals(Money.ZERO, MontosSugeridos.alCorriente(venta, hoy))
    }

    @Test
    fun `los periodos se cuentan completos, y por frecuencia`() {
        val base = AbonoFixtures.detalle().copy(fechaVenta = LocalDate.of(2026, 8, 1))
        // 31 dias: 4 semanas completas, 2 quincenas, 1 mes.
        assertEquals(4L, MontosSugeridos.periodosTranscurridos(base, hoy))
        assertEquals(
            2L,
            MontosSugeridos.periodosTranscurridos(base.copy(frecuencia = "quincenal"), hoy)
        )
        assertEquals(
            1L,
            MontosSugeridos.periodosTranscurridos(base.copy(frecuencia = "mensual"), hoy)
        )
    }

    @Test
    fun `una venta futura no trae periodos negativos`() {
        val venta = AbonoFixtures.detalle().copy(fechaVenta = hoy.plusDays(30))
        assertEquals(0L, MontosSugeridos.periodosTranscurridos(venta, hoy))
    }

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
