package com.example.msp_app.core.datos

import com.example.msp_app.core.testing.time.FakeClock
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistroDeUsoTest {
    private val clock = FakeClock.at("2026-11-10T18:00:00Z") // 12:00 en CDMX
    private val registro = RegistroDeUso(clock)

    @Test fun `acumula por endpoint en el dia de negocio`() {
        registro.peticion("GET /ping")
        registro.bytes("GET /ping", 100, 10)
        registro.bytes("GET /ping", 50, 0)
        val dia = registro.instantanea().single()
        assertEquals("2026-11-10", dia.fecha)
        assertEquals(UsoDeEndpoint(rx = 150, tx = 10, peticiones = 1), dia.endpoints["GET /ping"])
    }

    @Test fun `cambio de dia en zona de negocio y no en UTC`() {
        // 05:59Z = 23:59 del 9 en CDMX (UTC-6); 06:01Z = 00:01 del 10.
        clock.setNow(Instant.parse("2026-11-10T05:59:00Z"))
        registro.bytes("A", 1, 0)
        clock.setNow(Instant.parse("2026-11-10T06:01:00Z"))
        registro.bytes("A", 2, 0)
        val dias = registro.instantanea().associateBy { it.fecha }
        assertEquals(1L, dias.getValue("2026-11-09").endpoints.getValue("A").rx)
        assertEquals(2L, dias.getValue("2026-11-10").endpoints.getValue("A").rx)
    }

    @Test fun `cambio de hora del telefono hacia atras no pierde bytes`() {
        clock.setNow(Instant.parse("2026-11-10T18:00:00Z"))
        registro.bytes("A", 10, 0)
        clock.setNow(Instant.parse("2026-11-09T18:00:00Z")) // reloj atrasado un dia
        registro.bytes("A", 5, 0)
        assertEquals(15L, registro.instantanea().sumOf { it.endpoints.values.sumOf { e -> e.rx } })
    }

    @Test fun `trafico se reparte por red y plano`() {
        registro.trafico(rx = 100, tx = 50, red = TipoDeRed.MOVIL, primerPlano = true)
        registro.trafico(rx = 10, tx = 5, red = TipoDeRed.WIFI, primerPlano = false)
        registro.trafico(rx = 1, tx = 1, red = TipoDeRed.SIN_RED, primerPlano = false)
        val d = registro.instantanea().single()
        assertEquals(167L, d.movil + d.wifi + d.otraRed)
        assertEquals(150L, d.movil)
        assertEquals(15L, d.wifi)
        assertEquals(2L, d.otraRed)
        assertEquals(150L, d.primerPlano)
        assertEquals(17L, d.segundoPlano)
        assertEquals(111L, d.rxTotal)
        assertEquals(56L, d.txTotal)
        assertEquals(167L, d.total)
    }

    @Test fun `retiene solo 30 dias`() {
        registro.restaurar(
            (0..40).map {
                UsoDelDia.vacio(
                    java.time.LocalDate.of(2026, 11, 10).minusDays(it.toLong()).toString()
                )
            }
        )
        registro.podar()
        val fechas = registro.instantanea().map { it.fecha }
        assertEquals(30, fechas.size)
        assertTrue("2026-11-10" in fechas)
        assertTrue("2026-10-12" in fechas)
        assertTrue("2026-10-11" !in fechas)
    }

    @Test fun `restaurar suma sobre lo ya contado`() {
        registro.bytes("A", 5, 0)
        registro.restaurar(
            listOf(
                UsoDelDia.vacio(
                    "2026-11-10"
                ).copy(endpoints = mapOf("A" to UsoDeEndpoint(10, 1, 2)), wifi = 7)
            )
        )
        val d = registro.instantanea().single()
        assertEquals(UsoDeEndpoint(15, 1, 2), d.endpoints["A"])
        assertEquals(7L, d.wifi)
    }

    @Test fun `mas de 200 endpoints distintos caen en otros`() {
        repeat(260) { registro.bytes("GET /x$it", 1, 0) }
        val d = registro.instantanea().single()
        assertTrue(d.endpoints.size <= 201)
        assertEquals(260L, d.endpoints.values.sumOf { it.rx })
        assertTrue(d.endpoints.containsKey(ClasificadorDeServicio.CLAVE_OTROS))
    }

    @Test fun `sin datos no hay dias`() {
        assertNull(registro.instantanea().firstOrNull())
    }
}
