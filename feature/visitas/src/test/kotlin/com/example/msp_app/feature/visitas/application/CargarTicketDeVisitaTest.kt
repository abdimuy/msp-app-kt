package com.example.msp_app.feature.visitas.application

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.money.Money
import com.example.msp_app.feature.visitas.data.fake.FakeContextoDeVisitaPort
import com.example.msp_app.feature.visitas.data.fake.FakeVisitaImpresaPort
import com.example.msp_app.feature.visitas.data.fake.VisitasFixtures
import com.example.msp_app.feature.visitas.domain.model.DesenlaceImpreso
import com.example.msp_app.feature.visitas.domain.model.VisitaRegistrada
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * **El desenlace se deriva de lo registrado, no se elige a mano.**
 *
 * El ticket viejo pedía al cobrador escoger entre tres cartas en un menú, sin
 * ninguna relación con lo que acababa de capturar: nada impedía dejar la carta
 * de cobranza dura en una puerta donde el cliente acababa de prometer pagar.
 * Estas pruebas fijan la derivación que lo hace imposible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CargarTicketDeVisitaTest {

    private val visitas = FakeVisitaImpresaPort()
    private val contextos = FakeContextoDeVisitaPort()
    private val cargar = CargarTicketDeVisita(visitas, contextos)

    private val registradaEn = Instant.parse("2026-09-01T18:00:00Z")

    private fun visita(
        tipo: String = TipoVisitaCatalogo.NO_SE_ENCONTRABA,
        ventaId: Int? = null,
        fechaPromesa: LocalDate? = null,
        montoPrometido: Money? = null,
        fechaCita: LocalDate? = null,
        horaCita: LocalTime? = null
    ) = VisitaRegistrada(
        visitaId = VISITA,
        clienteId = VisitasFixtures.VICTORIA,
        ventaId = ventaId,
        registradaEn = registradaEn,
        tipoVisita = tipo,
        cobrador = "Martín Salgado",
        nota = "preguntar por la mañana",
        fechaPromesa = fechaPromesa,
        montoPrometido = montoPrometido,
        fechaCita = fechaCita,
        horaCita = horaCita
    )

    private fun sembrar(v: VisitaRegistrada) {
        visitas.visitas = listOf(v)
    }

    @Test
    fun `no estaba deriva la carta suave`() = runTest(StandardTestDispatcher()) {
        sembrar(visita())

        val ticket = cargar(VISITA)
        advanceUntilIdle()

        assertEquals(DesenlaceImpreso.NO_ESTABA, ticket?.desenlace)
    }

    @Test
    fun `se nego deriva el aviso de cobranza`() = runTest(StandardTestDispatcher()) {
        sembrar(visita(tipo = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO))

        val ticket = cargar(VISITA)
        advanceUntilIdle()

        assertEquals(DesenlaceImpreso.SE_NEGO, ticket?.desenlace)
    }

    @Test
    fun `la fecha de promesa manda sobre el literal`() = runTest(StandardTestDispatcher()) {
        // El literal dice "no se encontraba" pero la fila trae PROMESA_FECHA: el
        // dato es el que sostiene el estado, no la etiqueta.
        sembrar(visita(fechaPromesa = LocalDate.of(2026, 9, 4)))

        val ticket = cargar(VISITA)
        advanceUntilIdle()

        assertEquals(DesenlaceImpreso.PROMETIO, ticket?.desenlace)
        assertEquals(LocalDate.of(2026, 9, 4), ticket?.promesa?.fecha)
    }

    @Test
    fun `la fecha de cita manda sobre todo lo demas`() = runTest(StandardTestDispatcher()) {
        sembrar(
            visita(
                tipo = TipoVisitaCatalogo.NO_VA_A_DAR_PAGO,
                fechaPromesa = LocalDate.of(2026, 9, 4),
                fechaCita = LocalDate.of(2026, 9, 3),
                horaCita = LocalTime.of(16, 0)
            )
        )

        val ticket = cargar(VISITA)
        advanceUntilIdle()

        assertEquals(DesenlaceImpreso.CITA, ticket?.desenlace)
        assertEquals(LocalTime.of(16, 0), ticket?.cita?.hora)
    }

    @Test
    fun `un literal desconocido cae en la carta suave, nunca en la dura`() =
        runTest(StandardTestDispatcher()) {
            sembrar(visita(tipo = "un literal que el servidor invento ayer"))

            val ticket = cargar(VISITA)
            advanceUntilIdle()

            // Ante la duda no se deja una carta de cobranza dura en la puerta.
            assertEquals(DesenlaceImpreso.VISITE_VUELVO, ticket?.desenlace)
        }

    @Test
    fun `una promesa sin monto llega como null, nunca como cero`() =
        runTest(StandardTestDispatcher()) {
            sembrar(visita(fechaPromesa = LocalDate.of(2026, 9, 4), montoPrometido = null))

            val ticket = cargar(VISITA)
            advanceUntilIdle()

            assertNull(ticket?.promesa?.monto)
        }

    @Test
    fun `una visita ligada a una venta imprime SOLO esa cuenta`() =
        runTest(StandardTestDispatcher()) {
            sembrar(visita(ventaId = VisitasFixtures.REFRIGERADOR))

            val ticket = cargar(VISITA)
            advanceUntilIdle()

            assertEquals(listOf("V-5188"), ticket?.cuentas?.map { it.folio })
        }

    @Test
    fun `una visita del domicilio imprime todas las cuentas`() = runTest(StandardTestDispatcher()) {
        sembrar(visita(ventaId = null))

        val ticket = cargar(VISITA)
        advanceUntilIdle()

        assertEquals(listOf("V-5021", "V-5188"), ticket?.cuentas?.map { it.folio })
        assertEquals(Money.of(BigDecimal("3550")), ticket?.saldoTotal)
    }

    @Test
    fun `la fecha del ticket es la de la visita, no la de hoy`() =
        runTest(StandardTestDispatcher()) {
            sembrar(visita())

            val ticket = cargar(VISITA)
            advanceUntilIdle()

            assertEquals(registradaEn, ticket?.registradaEn)
        }

    @Test
    fun `quien visito sale de la fila, no de la sesion`() = runTest(StandardTestDispatcher()) {
        sembrar(visita().copy(cobrador = "Ana Lucía Beltrán"))

        val ticket = cargar(VISITA)
        advanceUntilIdle()

        assertEquals("Ana Lucía Beltrán", ticket?.cobrador)
    }

    @Test
    fun `un visitaId que el telefono no tiene devuelve null`() = runTest(StandardTestDispatcher()) {
        sembrar(visita())

        assertNull(cargar("no-existe"))
        // Control positivo: el MISMO camino con el id bueno SÍ arma ticket.
        assertEquals("Victoria Flores Olmedo", cargar(VISITA)?.cliente)
    }

    @Test
    fun `un cliente que ya no esta en el telefono devuelve null`() =
        runTest(StandardTestDispatcher()) {
            sembrar(visita())
            contextos.contexto = null

            assertNull(cargar(VISITA))
        }

    private companion object {
        const val VISITA = "vis-2026-09-01-victoria"
    }
}
