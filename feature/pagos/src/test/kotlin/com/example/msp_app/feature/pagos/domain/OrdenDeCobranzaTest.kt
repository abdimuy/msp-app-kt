package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El borde exacto de "cero abonos".**
 *
 * `SALDO_REST == PRECIO_TOTAL - ENGANCHE` es todo lo que separa a la puerta
 * donde no ha entrado un peso —que encabeza el día del cobrador— de la que ya
 * abonó. Un peso decide la posición, así que se prueba el `==` exacto y el peso
 * de abajo.
 */
class OrdenDeCobranzaTest {

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))

    private val unDia = Instant.parse("2026-05-04T18:00:00Z")

    private fun rango(saldo: String, fecha: Instant? = unDia) = OrdenDeCobranza.rangoDe(
        saldo = dinero(saldo),
        totalVenta = dinero("8400"),
        enganche = dinero("900"),
        instanteDeVenta = fecha
    )

    @Test
    fun `el saldo exactamente igual a lo financiado es cero abonos`() {
        // 8400 - 900 = 7500
        assertTrue(rango("7500").sinAbonos)
    }

    @Test
    fun `un peso abajo ya es un abono`() {
        assertFalse(rango("7499").sinAbonos)
    }

    @Test
    fun `un peso arriba tampoco es cero abonos`() {
        assertFalse(rango("7501").sinAbonos)
    }

    @Test
    fun `un centavo abajo ya es un abono`() {
        assertFalse(rango("7499.99").sinAbonos)
    }

    @Test
    fun `los que no han abonado nada van primero`() {
        val sinAbonos = rango("7500")
        val conAbonos = rango("7499")
        assertEquals(
            listOf(sinAbonos, conAbonos),
            listOf(conAbonos, sinAbonos).sortedWith(OrdenDeCobranza.PRIMERO)
        )
    }

    @Test
    fun `dentro del mismo dia manda la venta de mas temprano`() {
        val temprano = rango("7499", Instant.parse("2026-05-04T14:00:00Z"))
        val tarde = rango("7499", Instant.parse("2026-05-04T22:00:00Z"))
        assertEquals(
            listOf(temprano, tarde),
            listOf(tarde, temprano).sortedWith(OrdenDeCobranza.PRIMERO)
        )
    }

    @Test
    fun `dentro del mismo grupo mandan las ventas mas viejas`() {
        val vieja = rango("7499", Instant.parse("2025-01-09T18:00:00Z"))
        val nueva = rango("7499", Instant.parse("2026-08-30T18:00:00Z"))
        assertEquals(
            listOf(vieja, nueva),
            listOf(nueva, vieja).sortedWith(OrdenDeCobranza.PRIMERO)
        )
    }

    @Test
    fun `la fecha manda despues del abono, nunca antes`() {
        // La vieja YA abonó; la nueva no ha abonado nada. Manda no haber abonado.
        val viejaConAbono = rango("7499", Instant.parse("2025-01-09T18:00:00Z"))
        val nuevaSinAbono = rango("7500", Instant.parse("2026-08-30T18:00:00Z"))
        assertEquals(
            listOf(nuevaSinAbono, viejaConAbono),
            listOf(viejaConAbono, nuevaSinAbono).sortedWith(OrdenDeCobranza.PRIMERO)
        )
    }

    @Test
    fun `una venta sin fecha legible cae al final de su grupo, no al principio`() {
        val sinFecha = rango("7499", fecha = null)
        val conFecha = rango("7499", Instant.parse("2026-08-30T18:00:00Z"))
        assertEquals(
            listOf(conFecha, sinFecha),
            listOf(sinFecha, conFecha).sortedWith(OrdenDeCobranza.PRIMERO)
        )
    }

    @Test
    fun `el cliente hereda el rango de su venta mejor rankeada`() {
        val yaAbono = rango("7499", Instant.parse("2026-08-30T18:00:00Z"))
        val sinAbonos = rango("7500", Instant.parse("2026-08-30T18:00:00Z"))
        assertEquals(sinAbonos, OrdenDeCobranza.mejorDe(listOf(yaAbono, sinAbonos)))
    }

    @Test
    fun `un cliente sin ventas no tiene rango`() {
        assertEquals(null, OrdenDeCobranza.mejorDe(emptyList()))
    }
}
