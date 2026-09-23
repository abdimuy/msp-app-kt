package com.example.msp_app.feature.pagos.application

import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.pagos.data.fake.FakePagosPort
import com.example.msp_app.feature.pagos.data.fake.FakeVentasPort
import com.example.msp_app.feature.pagos.domain.model.MetodoDeCobro
import com.example.msp_app.feature.pagos.domain.model.PagoDelHistorial
import com.example.msp_app.feature.pagos.ui.PagosFixtures
import java.math.BigDecimal
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El armado del ticket de pago a partir del `pagoId` de la ruta.
 *
 * Lo que estas pruebas fijan, en orden de gravedad:
 * 1. **El saldo anterior es `saldo actual + importe`** — se deriva, no se lee de
 *    otro lado. El `SALDO_REST` ya viene descontado por la escritura del abono.
 * 2. **El abono que se acaba de cobrar NO aparece en "últimos pagos"**: sería el
 *    mismo pago impreso dos veces en el mismo papel.
 * 3. Un `pagoId` que el teléfono no tiene devuelve `null`, no un ticket a medias.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CargarTicketDePagoTest {

    private val ventas = FakeVentasPort()
    private val pagos = FakePagosPort()
    private val cargar = CargarTicketDePago(pagos, ventas)

    private val venta = PagosFixtures.datosDeVenta(
        ventaId = PagosFixtures.VENTA_EN_PROMESA,
        folio = "V-5188",
        descripcion = "Refrigerador Mabe 14'",
        cifras = PagosFixtures.Cifras(
            total = dinero("6400"),
            restante = dinero("1450"),
            cuota = dinero("220"),
            cubierto = dinero("4950")
        )
    )

    private fun dinero(pesos: String) = Money.of(BigDecimal(pesos))

    private fun abono(
        id: String,
        fechaIso: String,
        pesos: String,
        cobrador: String = "Martín Salgado"
    ) = PagoDelHistorial(
        pagoId = id,
        ventaId = PagosFixtures.VENTA_EN_PROMESA,
        fecha = Instant.parse(fechaIso),
        importe = dinero(pesos),
        formaCobroId = MetodoDeCobro.EFECTIVO.formaCobroId,
        metodo = MetodoDeCobro.EFECTIVO,
        nota = null,
        cobrador = cobrador
    )

    private fun sembrar(vararg abonos: PagoDelHistorial) {
        ventas.ventas = listOf(venta)
        pagos.pagos = abonos.toList()
    }

    @Test
    fun `el saldo anterior es el actual mas el importe`() = runTest(StandardTestDispatcher()) {
        sembrar(abono("A-3", "2026-09-01T18:00:00Z", "350"))

        val ticket = cargar("A-3")
        advanceUntilIdle()

        assertEquals(dinero("1450"), ticket?.saldoActual)
        assertEquals(dinero("1800"), ticket?.saldoAnterior)
    }

    @Test
    fun `el abono recien cobrado no se repite en ultimos pagos`() =
        runTest(StandardTestDispatcher()) {
            sembrar(
                abono("A-3", "2026-09-01T18:00:00Z", "350"),
                abono("A-2", "2026-08-25T18:00:00Z", "220"),
                abono("A-1", "2026-08-18T18:00:00Z", "220")
            )

            val ticket = cargar("A-3")
            advanceUntilIdle()

            // Control positivo: los otros dos SÍ están, así que la ausencia del
            // tercero no es una lista que siempre sale vacía.
            assertEquals(2, ticket?.ultimosPagos?.size)
            assertEquals(dinero("220"), ticket?.ultimosPagos?.first()?.importe)
            assertTrue(ticket?.ultimosPagos?.none { it.importe == dinero("350") } == true)
        }

    @Test
    fun `los abonos previos salen del mas reciente al mas viejo`() =
        runTest(StandardTestDispatcher()) {
            sembrar(
                abono("A-3", "2026-09-01T18:00:00Z", "350"),
                abono("A-1", "2026-08-18T18:00:00Z", "100"),
                abono("A-2", "2026-08-25T18:00:00Z", "200")
            )

            val ticket = cargar("A-3")
            advanceUntilIdle()

            assertEquals(
                listOf(dinero("200"), dinero("100")),
                ticket?.ultimosPagos?.map { it.importe }
            )
        }

    @Test
    fun `nunca se imprimen mas de cuatro abonos previos`() = runTest(StandardTestDispatcher()) {
        val previos = (1..SEIS).map { abono("A-$it", "2026-08-0${it}T18:00:00Z", "100") }
        sembrar(*(previos + abono("A-9", "2026-09-01T18:00:00Z", "350")).toTypedArray())

        val ticket = cargar("A-9")
        advanceUntilIdle()

        assertEquals(CUATRO, ticket?.ultimosPagos?.size)
    }

    @Test
    fun `el conteo de abonos incluye el que se acaba de cobrar`() =
        runTest(StandardTestDispatcher()) {
            sembrar(
                abono("A-3", "2026-09-01T18:00:00Z", "350"),
                abono("A-2", "2026-08-25T18:00:00Z", "220")
            )

            val ticket = cargar("A-3")
            advanceUntilIdle()

            assertEquals(2, ticket?.abonosPagados)
            assertEquals(venta.abonosTotales, ticket?.abonosTotales)
        }

    @Test
    fun `quien cobro sale de la fila del pago, no de la sesion`() =
        runTest(StandardTestDispatcher()) {
            sembrar(abono("A-3", "2026-09-01T18:00:00Z", "350", cobrador = "Ana Lucía Beltrán"))

            val ticket = cargar("A-3")
            advanceUntilIdle()

            assertEquals("Ana Lucía Beltrán", ticket?.cobrador)
        }

    @Test
    fun `la fecha del cobro es la del abono, no la de hoy`() = runTest(StandardTestDispatcher()) {
        val cobradoEn = Instant.parse("2026-09-01T18:00:00Z")
        sembrar(abono("A-3", cobradoEn.toString(), "350"))

        val ticket = cargar("A-3")
        advanceUntilIdle()

        assertEquals(cobradoEn, ticket?.cobradoEn)
    }

    @Test
    fun `un pagoId que el telefono no tiene devuelve null`() = runTest(StandardTestDispatcher()) {
        sembrar(abono("A-3", "2026-09-01T18:00:00Z", "350"))

        val ticket = cargar("no-existe")
        advanceUntilIdle()

        assertNull(ticket)
    }

    @Test
    fun `un abono cuya venta ya no esta devuelve null`() = runTest(StandardTestDispatcher()) {
        pagos.pagos = listOf(abono("A-3", "2026-09-01T18:00:00Z", "350"))
        ventas.ventas = emptyList()

        val ticket = cargar("A-3")
        advanceUntilIdle()

        assertNull(ticket)
        // Control positivo: con la venta sembrada el MISMO id sí arma ticket.
        ventas.ventas = listOf(venta)
        assertEquals("V-5188", cargar("A-3")?.folio)
    }

    private companion object {
        const val SEIS = 6
        const val CUATRO = 4
    }
}
