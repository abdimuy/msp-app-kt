package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.ui.AbonoFixtures
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.Instant
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
            sugeridos.take(3).map { it.cual }
        )
        assertEquals(dinero("220"), sugeridos[0].importe)
        assertEquals(dinero("440"), sugeridos[1].importe)
        assertEquals(dinero("1290"), sugeridos[2].importe)
    }

    // --- Las dos fuentes nuevas ----------------------------------------------

    @Test
    fun `los redondos van despues de la deuda y en orden de frecuencia`() {
        // Sin historial no hay "lo de siempre", así que detrás de los tres de la
        // deuda quedan los redondos: $100 (37.5 %), $200 (24.9 %), $150
        // (18.4 %) — y el tope de cinco corta el último.
        val sugeridos = MontosSugeridos.de(AbonoFixtures.detalle(), hoy)
        assertEquals(MontosSugeridos.MAXIMO_DE_CHIPS, sugeridos.size)
        assertEquals(
            listOf(dinero("220"), dinero("440"), dinero("1290"), dinero("100"), dinero("200")),
            sugeridos.map { it.importe }
        )
    }

    @Test
    fun `lo de siempre sale del historial del cliente y va antes que los redondos`() {
        val sugeridos = MontosSugeridos.de(
            venta = AbonoFixtures.detalle(),
            hoy = hoy,
            historial = listOf(abono("300", 1), abono("300", 2), abono("500", 3))
        )
        assertEquals(
            listOf(
                MontosSugeridos.Sugerencia.ESPERADO_HOY,
                MontosSugeridos.Sugerencia.AL_CORRIENTE,
                MontosSugeridos.Sugerencia.LIQUIDAR,
                MontosSugeridos.Sugerencia.LO_DE_SIEMPRE,
                MontosSugeridos.Sugerencia.REDONDO
            ),
            sugeridos.map { it.cual }
        )
        assertEquals(dinero("300"), sugeridos[3].importe)
    }

    @Test
    fun `sin costumbre medida no se pinta lo de siempre`() {
        val sugeridos = MontosSugeridos.de(
            venta = AbonoFixtures.detalle(),
            hoy = hoy,
            historial = listOf(abono("300", 1), abono("300", 2))
        )
        assertTrue(
            sugeridos.none { it.cual == MontosSugeridos.Sugerencia.LO_DE_SIEMPRE }
        )
    }

    @Test
    fun `un redondo que no cabe en el saldo no se ofrece`() {
        // La invariante de siempre, ahora sobre las fuentes que NO salen de la
        // deuda: un chip que ofreciera un sobrepago sería la pantalla ofreciendo
        // lo que ella misma bloquea.
        val venta = AbonoFixtures.detalle().copy(saldo = dinero("120"), liquidacion = null)
        val sugeridos = MontosSugeridos.de(
            venta = venta,
            hoy = hoy,
            historial = listOf(abono("300", 1), abono("300", 2), abono("300", 3))
        )
        assertTrue(sugeridos.isNotEmpty())
        sugeridos.forEach {
            assertTrue("${it.clave} = ${it.importe.amount}", it.importe <= venta.saldo)
        }
        assertTrue(
            "ni 300 de costumbre ni 150 redondo caben en un saldo de 120",
            sugeridos.none { it.importe > venta.saldo }
        )
    }

    @Test
    fun `un redondo que repite a un chip de la deuda se colapsa`() {
        // Lo esperado hoy vale $200: el redondo de $200 no puede volver a
        // pintarse. La regla de de-duplicado de siempre, extendida.
        //
        // Se mueve la CUOTA y no sólo la parcialidad: desde que el esperado sale
        // de `CuentaCobrable.cuota` (la precedencia de tres escalones), copiar
        // sólo `parcialidad` deja el esperado donde estaba — y el test pasaría a
        // medir otra cosa sin avisar.
        val venta = AbonoFixtures.detalle().copy(
            parcialidad = dinero("200"),
            cuota = CuotaDeLaVenta(dinero("200"), OrigenDeLaCuota.PARCIALIDAD)
        )
        val sugeridos = MontosSugeridos.de(venta, hoy)
        assertEquals(1, sugeridos.count { it.importe == dinero("200") })
        assertEquals(
            MontosSugeridos.Sugerencia.ESPERADO_HOY,
            sugeridos.single { it.importe == dinero("200") }.cual
        )
    }

    @Test
    fun `nunca se pintan mas chips que el tope`() {
        val sugeridos = MontosSugeridos.de(
            venta = AbonoFixtures.detalle(),
            hoy = hoy,
            historial = listOf(abono("777", 1), abono("777", 2), abono("777", 3))
        )
        assertEquals(MontosSugeridos.MAXIMO_DE_CHIPS, sugeridos.size)
    }

    @Test
    fun `cada chip tiene su propia clave, aunque dos compartan tipo`() {
        // Tres chips REDONDO con el mismo `name` compartirían `testTag`, y dos
        // nodos con el mismo tag no son dos chips que se puedan tocar aparte.
        val sugeridos = MontosSugeridos.de(AbonoFixtures.detalle(), hoy)
        assertEquals(sugeridos.size, sugeridos.map { it.clave }.toSet().size)
        assertEquals("esperado_hoy", sugeridos[0].clave)
        assertEquals("redondo_100", sugeridos[3].clave)
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
            sugeridos.take(2).map { it.cual }
        )
        assertEquals(venta.saldo, sugeridos[1].importe)
        assertTrue(
            "liquidar valía lo mismo que al corriente y no se repite",
            sugeridos.none { it.cual == MontosSugeridos.Sugerencia.LIQUIDAR }
        )
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

    private fun abono(pesos: String, dia: Int) = AbonoPrevio(
        fecha = Instant.parse("2026-08-%02dT12:00:00Z".format(dia)),
        importe = dinero(pesos)
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
