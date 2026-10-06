package com.example.msp_app.feature.pagos.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [SemanaEnCurso.etiqueta]: "semana del D al D mes", con y sin cruce de mes.
 */
class SemanaEnCursoTest {

    @Test
    fun `martes dentro del mes da lunes y domingo del mismo mes, un solo nombre de mes`() {
        // Martes 1-sep-2026: su semana es lunes 31-ago a domingo 6-sep -> SÍ cruza mes.
        // Se usa un martes a media semana en vez para quedar dentro del mismo mes.
        val martes = LocalDate.of(2026, 9, 8) // martes, semana lunes 7 - domingo 13 de sep
        assertEquals("semana del 7 al 13 sep", SemanaEnCurso.etiqueta(martes))
    }

    @Test
    fun `semana que cruza de mes nombra los dos meses`() {
        val martes = LocalDate.of(2026, 9, 1) // martes; semana lunes 31-ago a domingo 6-sep
        assertEquals("semana del 31 ago al 6 sep", SemanaEnCurso.etiqueta(martes))
    }

    @Test
    fun `un lunes es el primer dia de su propia semana`() {
        val lunes = LocalDate.of(2026, 9, 7)
        assertEquals("semana del 7 al 13 sep", SemanaEnCurso.etiqueta(lunes))
    }

    @Test
    fun `un domingo es el ultimo dia de su propia semana`() {
        val domingo = LocalDate.of(2026, 9, 13)
        assertEquals("semana del 7 al 13 sep", SemanaEnCurso.etiqueta(domingo))
    }
}
