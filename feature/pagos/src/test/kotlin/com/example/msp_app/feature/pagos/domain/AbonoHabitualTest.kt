package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import java.math.BigDecimal
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **"Lo de siempre"**: la moda del historial del cliente.
 *
 * Lo que estas pruebas fijan es el **mínimo** —tres abonos y que el ganador se
 * repita— porque es la decisión que separa "lo que este cliente suele dar" de
 * "su último pago con otro nombre". Y el mínimo no es cosmético: la misma
 * función alimenta los avisos de "más del triple de lo que suele dar", así que
 * un mínimo flojo haría que la pantalla acuse a alguien de salirse de una
 * costumbre que nunca se midió.
 */
class AbonoHabitualTest {

    @Test
    fun `sin abonos no hay costumbre`() {
        assertNull(AbonoHabitual.de(emptyList()))
    }

    @Test
    fun `con dos abonos iguales todavia no hay costumbre`() {
        // Hay moda (los dos son $100) pero no llega al piso de tres: la frontera
        // exacta del mínimo, por abajo.
        val historial = listOf(abono("100", dia = 1), abono("100", dia = 2))
        assertNull(AbonoHabitual.de(historial))
    }

    @Test
    fun `con tres abonos y uno repetido si hay costumbre`() {
        // La frontera exacta del mínimo, por arriba.
        val historial = listOf(abono("100", dia = 1), abono("100", dia = 2), abono("250", dia = 3))
        assertEquals(dinero("100"), AbonoHabitual.de(historial))
    }

    @Test
    fun `tres montos distintos no son una costumbre`() {
        // Tres abonos, pero ninguno se repite: son tres pagos, no un hábito.
        val historial = listOf(abono("100", dia = 1), abono("250", dia = 2), abono("300", dia = 3))
        assertNull(AbonoHabitual.de(historial))
    }

    @Test
    fun `gana el que mas se repite, no el mas grande ni el mas nuevo`() {
        val historial = listOf(
            abono("100", dia = 1),
            abono("100", dia = 2),
            abono("100", dia = 3),
            abono("900", dia = 4)
        )
        assertEquals(dinero("100"), AbonoHabitual.de(historial))
    }

    @Test
    fun `empatados gana el mas reciente, que es el cliente que cambio de costumbre`() {
        val historial = listOf(
            abono("100", dia = 1),
            abono("100", dia = 2),
            abono("200", dia = 3),
            abono("200", dia = 4)
        )
        assertEquals(dinero("200"), AbonoHabitual.de(historial))
    }

    @Test
    fun `un abono en cero no cuenta para el minimo`() {
        // Tres filas, pero una no es dinero entregado: quedan dos, y dos no
        // alcanzan. Si el cero contara, el piso se cruzaría sin que el cliente
        // haya pagado tres veces.
        val historial = listOf(abono("100", dia = 1), abono("100", dia = 2), abono("0", dia = 3))
        assertNull(AbonoHabitual.de(historial))
    }

    @Test
    fun `la moda es la moda y no el promedio`() {
        // El promedio de estos cuatro es $225, una cifra que el cliente nunca
        // entregó. La moda es $100, la que el cobrador reconoce.
        val historial = listOf(
            abono("100", dia = 1),
            abono("100", dia = 2),
            abono("100", dia = 3),
            abono("600", dia = 4)
        )
        assertEquals(dinero("100"), AbonoHabitual.de(historial))
    }

    private fun abono(pesos: String, dia: Int): AbonoPrevio = AbonoPrevio(
        fecha = Instant.parse("2026-08-%02dT12:00:00Z".format(dia)),
        importe = dinero(pesos)
    )

    private fun dinero(pesos: String): Money = Money.of(BigDecimal(pesos))
}
