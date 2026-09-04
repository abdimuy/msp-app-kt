package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

/** El riel agrupa por mes de NEGOCIO, más reciente primero, y siempre visible. */
class RielDePagosTest {

    private fun pago(fechaIso: String, pesos: String) = PagoDelHistorial(
        pagoId = fechaIso,
        ventaId = 1,
        fecha = Instant.parse(fechaIso),
        importe = Money.of(BigDecimal(pesos)),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null
    )

    @Test
    fun `agrupa por mes con subtotal y del mas reciente al mas viejo`() {
        val meses = RielDePagos.de(
            listOf(
                pago("2026-08-03T17:10:00Z", "350"),
                pago("2026-07-27T16:40:00Z", "350"),
                pago("2026-07-20T17:05:00Z", "700")
            )
        )
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 7)), meses.map { it.mes })
        assertEquals(Money.of(BigDecimal("350.00")), meses.first().subtotal)
        assertEquals(Money.of(BigDecimal("1050.00")), meses.last().subtotal)
    }

    @Test
    fun `un pago de las 23 30 del ultimo dia del mes pertenece a ese mes en la zona de negocio`() {
        // 2026-09-01T05:30:00Z es 2026-08-31 23:30 en America/Mexico_City.
        val meses = RielDePagos.de(listOf(pago("2026-09-01T05:30:00Z", "350")))
        assertEquals(YearMonth.of(2026, 8), meses.single().mes)
    }

    @Test
    fun `sin pagos no hay meses`() {
        assertEquals(emptyList<Any>(), RielDePagos.de(emptyList()))
    }

    @Test
    fun `el nombre del mes va en minusculas`() {
        assertEquals("agosto", RielDePagos.nombreDe(YearMonth.of(2026, 8)))
    }
}
