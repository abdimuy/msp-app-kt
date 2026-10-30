package com.example.msp_app.core.common.visitas.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TextoDeCitaTest {
    @Test
    fun `la hora va en 12 h con a m y p m`() {
        assertEquals("4:30 p. m.", TextoDeCita.hora12(LocalTime.of(16, 30)))
        assertEquals("12:00 p. m.", TextoDeCita.hora12(LocalTime.of(12, 0)))
        assertEquals("12:05 a. m.", TextoDeCita.hora12(LocalTime.of(0, 5)))
        assertEquals("9:00 a. m.", TextoDeCita.hora12(LocalTime.of(9, 0)))
    }

    @Test
    fun `el historial dice Cita con el dia y la hora`() {
        val jueves = LocalDate.of(2026, 10, 29)
        assertEquals(
            "Cita: jueves 4:30 p. m.",
            TextoDeCita.etiquetaDelHistorial(jueves, LocalTime.of(16, 30))
        )
        assertEquals("Cita: jueves", TextoDeCita.etiquetaDelHistorial(jueves, null))
    }

    @Test
    fun `el aviso nombra al cliente y la hora`() {
        assertEquals(
            "Cita con Josefa Temoxtle a las 4:30 p. m.",
            TextoDeCita.avisoDeCita("Josefa Temoxtle", LocalTime.of(16, 30))
        )
        assertEquals("Cita con Josefa", TextoDeCita.avisoDeCita("Josefa", null))
    }
}
