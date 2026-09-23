package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.domain.model.RitmoDeSemana
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El ritmo de doce semanas y su borde exacto entre *a tiempo* y *tarde*: el
 * corte es `>=`, el mismo de `EstadoCuentaDeriver.estadoPorDinero`, porque
 * pagar la parcialidad exacta es haber pagado.
 */
class RitmoDePagosTest {

    private val hoy = LocalDate.of(2026, 9, 1)
    private val parcialidad = Money.of(BigDecimal("350"))

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
    fun `siempre devuelve doce semanas terminando en la de hoy`() {
        val semanas = RitmoDePagos.de(emptyList(), parcialidad, hoy)
        assertEquals(RitmoDePagos.SEMANAS, semanas.size)
        assertEquals(LocalDate.of(2026, 8, 31), semanas.last().inicio)
        assertEquals(LocalDate.of(2026, 6, 15), semanas.first().inicio)
    }

    @Test
    fun `la parcialidad exacta es a tiempo`() {
        val semanas = RitmoDePagos.de(listOf(pago("2026-08-31T18:00:00Z", "350")), parcialidad, hoy)
        assertEquals(RitmoDeSemana.A_TIEMPO, semanas.last().ritmo)
    }

    @Test
    fun `un peso menos que la parcialidad es tarde`() {
        val semanas = RitmoDePagos.de(listOf(pago("2026-08-31T18:00:00Z", "349")), parcialidad, hoy)
        assertEquals(RitmoDeSemana.TARDE, semanas.last().ritmo)
    }

    @Test
    fun `un peso mas que la parcialidad sigue siendo a tiempo`() {
        val semanas = RitmoDePagos.de(listOf(pago("2026-08-31T18:00:00Z", "351")), parcialidad, hoy)
        assertEquals(RitmoDeSemana.A_TIEMPO, semanas.last().ritmo)
    }

    @Test
    fun `sin dinero la semana es sin pago`() {
        val semanas = RitmoDePagos.de(emptyList(), parcialidad, hoy)
        assertTrue(semanas.all { it.ritmo == RitmoDeSemana.SIN_PAGO })
    }

    @Test
    fun `parcialidad no positiva manda todo abono a tarde y nunca a a tiempo`() {
        val semanas = RitmoDePagos.de(
            listOf(pago("2026-08-31T18:00:00Z", "1000")),
            Money.ZERO,
            hoy
        )
        assertEquals(RitmoDeSemana.TARDE, semanas.last().ritmo)
    }

    @Test
    fun `dos abonos de la misma semana se suman antes de decidir`() {
        val semanas = RitmoDePagos.de(
            listOf(pago("2026-08-31T18:00:00Z", "200"), pago("2026-09-02T18:00:00Z", "150")),
            parcialidad,
            hoy
        )
        assertEquals(RitmoDeSemana.A_TIEMPO, semanas.last().ritmo)
    }

    @Test
    fun `el promedio ignora las semanas sin dinero`() {
        val semanas = RitmoDePagos.de(
            listOf(pago("2026-08-31T18:00:00Z", "300"), pago("2026-08-24T18:00:00Z", "500")),
            parcialidad,
            hoy
        )
        val resumen = RitmoDePagos.resumen(semanas)
        assertEquals(Money.of(BigDecimal("400.00")), resumen.promedio)
        assertEquals(10, resumen.semanasSinPago)
        assertEquals(1, resumen.cumplidas)
    }

    @Test
    fun `sin ningun abono el promedio es cero y no truena`() {
        val resumen = RitmoDePagos.resumen(RitmoDePagos.de(emptyList(), parcialidad, hoy))
        assertEquals(Money.ZERO, resumen.promedio)
    }

    @Test
    fun `un pago fuera de la ventana de doce semanas no entra`() {
        val semanas = RitmoDePagos.de(
            listOf(pago("2026-01-05T18:00:00Z", "5000")),
            parcialidad,
            hoy
        )
        assertTrue(semanas.all { it.cobrado == Money.ZERO })
    }
}
