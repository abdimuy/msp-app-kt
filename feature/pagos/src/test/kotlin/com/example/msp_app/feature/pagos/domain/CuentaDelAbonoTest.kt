package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **A qué cuenta va el abono.**
 *
 * Este test reemplaza a `CuentaQueEncabezaTest`, que probaba —y por lo tanto
 * blindaba— el defecto: `ventas.firstOrNull()`, o sea el dinero entrando a la
 * primera venta de la lista sin que nadie lo dijera. Aquel test estaba bien
 * escrito y afirmaba lo equivocado; por eso no se ajustó, se retiró.
 */
class CuentaDelAbonoTest {

    /**
     * **Con una sola cuenta cobrable no se pregunta.** El flujo común —un
     * cliente, un mueble, un abono— no paga ningún toque extra por el arreglo.
     */
    @Test
    fun `una sola cuenta cobrable entra directo`() {
        val sala = cuenta(ventaId = 10, saldo = "2100")
        assertEquals(10, CuentaDelAbono.unica(listOf(sala)))
    }

    /**
     * **Con dos o más NO adivina.** Es el corazón del arreglo: contestar aquí
     * cualquier cosa sería volver a elegir por el cobrador.
     *
     * **Control de reversión:** cambiar `singleOrNull()` por `firstOrNull()` en
     * `CuentaDelAbono.unica` pone este test en ROJO.
     */
    @Test
    fun `con dos cuentas no adivina, contesta que hay que preguntar`() {
        val sala = cuenta(ventaId = 10, saldo = "2100")
        val refri = cuenta(ventaId = 20, saldo = "1450")
        assertNull(CuentaDelAbono.unica(listOf(sala, refri)))
    }

    /**
     * Una venta saldada no cuenta: un cliente con una cuenta viva y tres
     * liquidadas sigue entrando directo, que es el caso de quien lleva años
     * comprando.
     */
    @Test
    fun `las cuentas saldadas no cuentan para decidir si hay que preguntar`() {
        val viva = cuenta(ventaId = 10, saldo = "2100")
        val liquidada = cuenta(ventaId = 20, saldo = "0")
        assertEquals(10, CuentaDelAbono.unica(listOf(viva, liquidada)))
        assertEquals(
            listOf(10),
            CuentaDelAbono.cobrables(listOf(viva, liquidada)).map { it.ventaId }
        )
    }

    /** Sin ninguna cuenta cobrable no hay a dónde mandar el dinero. */
    @Test
    fun `sin cuentas cobrables no hay cuenta`() {
        val liquidada = cuenta(ventaId = 20, saldo = "0")
        assertNull(CuentaDelAbono.unica(listOf(liquidada)))
        assertNull(CuentaDelAbono.preseleccionada(listOf(liquidada)))
        assertNull(CuentaDelAbono.unica(emptyList()))
    }

    /**
     * **Viene marcada la de atrasos, NO la primera.** Ésta es la diferencia
     * concreta contra el comportamiento anterior: la sala va primera en la lista
     * y el refri es el que trae atraso, así que el refri es el preseleccionado.
     *
     * **Control de reversión:** cambiar `maxByOrNull { it.atrasos }` por
     * `firstOrNull()` pone este test en ROJO.
     */
    @Test
    fun `viene marcada la cuenta con atrasos, no la primera de la lista`() {
        val sala = cuenta(ventaId = 10, saldo = "2100", atrasos = 0)
        val refri = cuenta(ventaId = 20, saldo = "1450", atrasos = 2)

        assertEquals(20, CuentaDelAbono.preseleccionada(listOf(sala, refri)))
    }

    /**
     * Sin atrasos en ninguna —el caso normal— gana la de arriba, que es el orden
     * en que se pintan. Lo preseleccionado es lo que el dedo espera.
     */
    @Test
    fun `sin atrasos gana la de arriba, que es la que se pinta primero`() {
        val sala = cuenta(ventaId = 10, saldo = "2100", atrasos = 0)
        val refri = cuenta(ventaId = 20, saldo = "1450", atrasos = 0)

        assertEquals(10, CuentaDelAbono.preseleccionada(listOf(sala, refri)))
    }

    /** Empate de atrasos: también gana la de arriba. */
    @Test
    fun `empate de atrasos gana la de arriba`() {
        val sala = cuenta(ventaId = 10, saldo = "2100", atrasos = 3)
        val refri = cuenta(ventaId = 20, saldo = "1450", atrasos = 3)

        assertEquals(10, CuentaDelAbono.preseleccionada(listOf(sala, refri)))
    }

    /** Una cuenta saldada no se preselecciona aunque traiga el atraso más alto. */
    @Test
    fun `una cuenta saldada no se preselecciona ni con atrasos`() {
        val liquidadaConAtraso = cuenta(ventaId = 10, saldo = "0", atrasos = 9)
        val viva = cuenta(ventaId = 20, saldo = "1450", atrasos = 1)

        assertEquals(20, CuentaDelAbono.preseleccionada(listOf(liquidadaConAtraso, viva)))
    }

    private fun cuenta(ventaId: Int, saldo: String, atrasos: Int = 0) = VentaDelCliente(
        ventaId = ventaId,
        folio = "V-$ventaId",
        descripcion = "Mueble $ventaId",
        saldo = Money.of(BigDecimal(saldo)),
        parcialidad = Money.of(BigDecimal("220")),
        abonosPagados = 3,
        abonosTotales = 20,
        avance = 0.15f,
        estado = EstadoDelPeriodo.sinTocar(Money.of(BigDecimal("220"))),
        atrasos = atrasos
    )
}
