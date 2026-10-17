package com.example.msp_app.core.common.visitas.envio

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Límites de retención local de visitas (spec visitas DA-4, §4.2-8). */
class ReglaDePodaTest {
    private val ahora = Instant.parse("2026-10-17T15:30:00Z")

    @Test fun `las confirmadas se conservan 30 dias tras confirmar`() =
        assertEquals("2026-09-17T15:30:00Z", ReglaDePoda.limiteConfirmadas(ahora))

    @Test fun `las resguardadas se conservan 90 dias tras resguardar`() =
        assertEquals("2026-07-19T15:30:00Z", ReglaDePoda.limiteResguardadas(ahora))

    @Test fun `los dias son los de DA-4`() {
        assertEquals(30, ReglaDePoda.CONFIRMADAS_DIAS)
        assertEquals(90, ReglaDePoda.RESGUARDADAS_DIAS)
    }

    /** El formato de cable es de ancho fijo: la comparación de texto en SQLite ordena igual que el tiempo. */
    @Test fun `los limites conservan el formato de cable con segundos aunque el instante no los tenga`() {
        val redondo = Instant.parse("2026-10-17T00:00:00Z")
        assertEquals("2026-09-17T00:00:00Z", ReglaDePoda.limiteConfirmadas(redondo))
        assertTrue(ReglaDePoda.limiteResguardadas(redondo) < ReglaDePoda.limiteConfirmadas(redondo))
    }
}
