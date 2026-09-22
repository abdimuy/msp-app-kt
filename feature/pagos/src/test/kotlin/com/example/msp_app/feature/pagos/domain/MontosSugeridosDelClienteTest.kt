package com.example.msp_app.feature.pagos.domain

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.domain.model.EstadoDelPeriodo
import com.example.msp_app.feature.pagos.domain.model.Liquidacion
import com.example.msp_app.feature.pagos.domain.model.VentaDelCliente
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * "Suele dar" y "pídele hoy" del detalle del cliente.
 *
 * Los bordes que se defienden son de dinero: la suma **por cuenta** (no sobre
 * los totales del cliente), el tope en el saldo, el piso en cero y la diferencia
 * entre *no hay dato* y *es cero*.
 */
class MontosSugeridosDelClienteTest {

    private val hoy = LocalDate.of(2026, 9, 15)

    /**
     * **La suma es por cuenta, y en cada una gana el mayor de los dos.**
     *
     * Las dos ventas son del 4 de mayo y hoy es el 15 de septiembre: 134 días,
     * o sea **19 semanas** completas.
     *
     * | | cuota | esperaba | pagó en cuotas | atraso | falta de la cuota | pide |
     * |---|---|---|---|---|---|---|
     * | sala | 350 | 6 650 | 5 400 | **1 250** | 0 (ya pagó) | 1 250 |
     * | refri | 220 | 4 180 | 4 050 | 130 | **220** | 220 |
     *
     * Total **1 470**. Las dos filas muestran un lado distinto del `maxOf`: en la
     * sala manda el atraso, en el refri manda la cuota del periodo abierto. Los
     * números van escritos, no recalculados con la misma fórmula que se prueba —
     * un oráculo que repite la implementación no prueba nada.
     */
    @Test
    fun `pidele hoy suma lo que falta cuenta por cuenta`() {
        val sala = cuenta(
            saldo = "2100",
            parcialidad = "350",
            abonado = "6300",
            abonoDelPeriodo = "350",
            desde = LocalDate.of(2026, 5, 4)
        )
        val refri = cuenta(
            saldo = "1450",
            parcialidad = "220",
            abonado = "4950",
            abonoDelPeriodo = "0",
            desde = LocalDate.of(2026, 5, 4)
        )

        assertEquals(dinero("1250"), MontosSugeridosDelCliente.pideleHoy(listOf(sala), hoy))
        assertEquals(dinero("220"), MontosSugeridosDelCliente.pideleHoy(listOf(refri), hoy))
        assertEquals(dinero("1470"), MontosSugeridosDelCliente.pideleHoy(listOf(sala, refri), hoy))
    }

    /**
     * **Una cuenta adelantada aporta cero, no resta.** Los sugeridos tienen piso
     * en cero, así que el saldo a favor de una venta no se cruza contra el atraso
     * de otra: son dos créditos distintos y el dinero de uno no cubre al otro.
     */
    @Test
    fun `una cuenta adelantada no descuenta del atraso de la otra`() {
        val adelantada = cuenta(
            saldo = "2100",
            parcialidad = "350",
            // Pagó mucho más de lo que el plan esperaba a estas alturas.
            abonado = "9000",
            abonoDelPeriodo = "350",
            desde = LocalDate.of(2026, 5, 4)
        )
        val vencida = cuenta(
            saldo = "1450",
            parcialidad = "220",
            abonado = "4950",
            abonoDelPeriodo = "0",
            desde = LocalDate.of(2026, 5, 4)
        )

        assertEquals(Money.ZERO, MontosSugeridosDelCliente.pideleHoy(listOf(adelantada), hoy))
        assertEquals(
            dinero("220"),
            MontosSugeridosDelCliente.pideleHoy(listOf(adelantada, vencida), hoy)
        )
    }

    /**
     * **Una cuenta estrenada hoy aporta su cuota, no cero.** No lleva periodos
     * transcurridos, así que su atraso es `0`; si "pídele hoy" fuera solo el
     * atraso, pediría `$0` de una cuenta que sí debe esta semana.
     *
     * **Control de reversión:** cambiar el `maxOf` de `pideleHoy` por
     * `MontosSugeridos.alCorriente(...)` a secas pone este test en ROJO.
     */
    @Test
    fun `una venta de hoy pide su cuota aunque todavia no tenga atraso`() {
        val recienVendida = cuenta(
            saldo = "6400",
            parcialidad = "220",
            abonado = "900",
            abonoDelPeriodo = "0",
            desde = hoy
        )

        assertEquals(Money.ZERO, MontosSugeridos.alCorriente(recienVendida, hoy))
        assertEquals(
            dinero("220"),
            MontosSugeridosDelCliente.pideleHoy(listOf(recienVendida), hoy)
        )
    }

    /**
     * **Ningún total puede exceder el saldo del cliente.** Cada sumando ya viene
     * topado en el saldo de su venta, así que la invariante se hereda.
     */
    @Test
    fun `el total nunca excede el saldo del cliente`() {
        // Una cuenta vieja y casi liquidada: el atraso calculado supera de largo
        // lo que queda por pagar.
        val casiLiquidada = cuenta(
            saldo = "50",
            parcialidad = "350",
            abonado = "8350",
            abonoDelPeriodo = "0",
            desde = LocalDate.of(2024, 1, 1)
        )

        val pedir = MontosSugeridosDelCliente.pideleHoy(listOf(casiLiquidada), hoy)

        assertEquals(dinero("50"), pedir)
    }

    /** Un cliente sin saldo no se le pide nada. */
    @Test
    fun `sin saldo no hay nada que pedir`() {
        val saldada = cuenta(
            saldo = "0",
            parcialidad = "350",
            abonado = "8400",
            abonoDelPeriodo = "0",
            desde = LocalDate.of(2026, 5, 4)
        )

        assertEquals(Money.ZERO, MontosSugeridosDelCliente.pideleHoy(listOf(saldada), hoy))
    }

    /** Sin ventas no hay cifra que sumar, y sumar nada es cero, no un error. */
    @Test
    fun `un cliente sin ventas pide cero`() {
        assertEquals(Money.ZERO, MontosSugeridosDelCliente.pideleHoy(emptyList(), hoy))
    }

    /**
     * **Una venta sin fecha no inventa periodos.** `FECHA` ilegible llega como
     * `null` y `periodosTranscurridos` contesta `0`: el atraso queda en cero y
     * solo se pide la cuota del periodo.
     */
    @Test
    fun `una venta sin fecha de venta no inventa atraso`() {
        val sinFecha = cuenta(
            saldo = "1450",
            parcialidad = "220",
            abonado = "4950",
            abonoDelPeriodo = "0",
            desde = null
        )

        assertEquals(dinero("220"), MontosSugeridosDelCliente.pideleHoy(listOf(sinFecha), hoy))
    }

    /** Sin `IMPORTE_PAGO_PROMEDIO` en ninguna cuenta no se afirma un promedio. */
    @Test
    fun `suele dar es nulo cuando ninguna cuenta trae el dato`() {
        val sinDato = cuenta(
            saldo = "1450",
            parcialidad = "220",
            abonado = "4950",
            abonoDelPeriodo = "0",
            desde = LocalDate.of(2026, 5, 4)
        )

        assertNull(MontosSugeridosDelCliente.promedioDeMicrosip(listOf(sinDato)))
        assertNull(MontosSugeridosDelCliente.promedioDeMicrosip(emptyList()))
    }

    /**
     * Con algunas cuentas con dato y otras sin él se suman las que lo traen: es
     * la mejor cifra cierta, y esconder el renglón porque una cuenta nueva aún no
     * tiene promedio lo escondería justo cuando el cliente acaba de comprar.
     */
    @Test
    fun `suele dar suma las cuentas que si traen promedio`() {
        val conDato = cuenta(
            saldo = "2100",
            parcialidad = "350",
            abonado = "6300",
            abonoDelPeriodo = "0",
            desde = LocalDate.of(2026, 5, 4),
            promedio = "150"
        )
        val sinDato = conDato.copy(ventaId = 2, pagoPromedio = null)
        val otroConDato = conDato.copy(ventaId = 3, pagoPromedio = dinero("80"))

        assertEquals(
            dinero("230"),
            MontosSugeridosDelCliente.promedioDeMicrosip(listOf(conDato, sinDato, otroConDato))
        )
    }

    private fun cuenta(
        saldo: String,
        parcialidad: String,
        abonado: String,
        abonoDelPeriodo: String,
        desde: LocalDate?,
        promedio: String? = null
    ): VentaDelCliente = VentaDelCliente(
        ventaId = 1,
        folio = "V-0001",
        descripcion = "Refrigerador Mabe 14'",
        saldo = dinero(saldo),
        parcialidad = dinero(parcialidad),
        abonosPagados = 0,
        abonosTotales = 20,
        avance = 0f,
        estado = EstadoDelPeriodo.sinTocar(dinero(parcialidad))
            .copy(abonoDelPeriodo = dinero(abonoDelPeriodo)),
        fechaVenta = desde,
        frecuencia = "semanal",
        totalVenta = dinero(abonado) + dinero(saldo),
        enganche = dinero("900"),
        abonado = dinero(abonado),
        liquidacion = Liquidacion(dinero("99999"), null, "precio a 4 meses"),
        pagoPromedio = promedio?.let { dinero(it) }
    )

    private fun dinero(pesos: String) = Money.of(BigDecimal(pesos))
}
