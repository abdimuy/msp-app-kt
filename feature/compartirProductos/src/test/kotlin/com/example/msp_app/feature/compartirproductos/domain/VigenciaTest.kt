package com.example.msp_app.feature.compartirproductos.domain

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VigenciaTest {

    @Test fun `la fecha sale en zona del negocio con mes abreviado en minuscula`() {
        // 2026-11-10 18:00 UTC = 12:00 en Ciudad de México.
        assertEquals("10 nov 2026", FechaVigente.fecha(Instant.parse("2026-11-10T18:00:00Z")))
        assertEquals(
            "Precios vigentes al 10 nov 2026",
            FechaVigente.leyenda(Instant.parse("2026-11-10T18:00:00Z"))
        )
    }

    @Test fun `cambio de dia en la medianoche del negocio y no en la de UTC`() {
        // 05:59:59 UTC del 11 = 23:59:59 del 10 en CDMX (UTC-6, sin horario de verano desde 2022).
        assertEquals("10 nov 2026", FechaVigente.fecha(Instant.parse("2026-11-11T05:59:59Z")))
        assertEquals("11 nov 2026", FechaVigente.fecha(Instant.parse("2026-11-11T06:00:00Z")))
        // 03:00 UTC del 11 todavía es 21:00 del 10 en el negocio.
        assertEquals("10 nov 2026", FechaVigente.fecha(Instant.parse("2026-11-11T03:00:00Z")))
    }

    @Test fun `cambio de anio y de mes`() {
        assertEquals("31 dic 2026", FechaVigente.fecha(Instant.parse("2027-01-01T05:59:59Z")))
        assertEquals("1 ene 2027", FechaVigente.fecha(Instant.parse("2027-01-01T06:00:00Z")))
        assertEquals("1 mar 2028", FechaVigente.fecha(Instant.parse("2028-03-01T12:00:00Z")))
    }

    @Test fun `los doce meses`() {
        val esperados =
            listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
        esperados.forEachIndexed { i, mes ->
            val m = (i + 1).toString().padStart(2, '0')
            assertEquals("5 $mes 2026", FechaVigente.fecha(Instant.parse("2026-$m-05T18:00:00Z")))
        }
    }

    @Test fun `el dia no lleva cero a la izquierda`() {
        assertEquals("3 feb 2026", FechaVigente.fecha(Instant.parse("2026-02-03T18:00:00Z")))
    }

    private val ahora = Instant.parse("2026-11-10T18:00:00Z")

    @Test fun `mas de tres dias sin actualizar avisa`() {
        assertTrue(
            AvisoDeCatalogo.desactualizado(ahora.minus(Duration.ofDays(3)).minusSeconds(1), ahora)
        )
        assertTrue(AvisoDeCatalogo.desactualizado(ahora.minus(Duration.ofDays(30)), ahora))
    }

    @Test fun `tres dias exactos o menos no avisa`() {
        assertFalse(AvisoDeCatalogo.desactualizado(ahora.minus(Duration.ofDays(3)), ahora))
        assertFalse(AvisoDeCatalogo.desactualizado(ahora.minus(Duration.ofHours(1)), ahora))
        assertFalse(AvisoDeCatalogo.desactualizado(ahora, ahora))
    }

    @Test fun `sin marca de actualizacion no se inventa una antiguedad`() {
        assertFalse(AvisoDeCatalogo.desactualizado(null, ahora))
    }

    @Test fun `el texto del aviso`() {
        assertEquals("Los precios pueden no estar actualizados", AvisoDeCatalogo.MENSAJE)
    }
}
