package com.example.msp_app.feature.visitas.domain

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los chips de fecha. Función pura del "hoy" que entra por parámetro: ni una
 * línea pregunta la hora del sistema, así que un golden no cambia según el día
 * en que corra.
 */
class DiasSugeridosTest {

    /** Martes 1-sep-2026. */
    private val martes = LocalDate.of(2026, 9, 1)

    @Test
    fun `la promesa ofrece manana y el proximo viernes`() {
        assertEquals(
            listOf(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 4)),
            DiasSugeridos.paraPromesa(martes)
        )
    }

    @Test
    fun `la cita ofrece hoy, manana y el proximo viernes`() {
        assertEquals(
            listOf(martes, LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 4)),
            DiasSugeridos.paraCita(martes)
        )
    }

    /**
     * En viernes, "el próximo viernes" es el de la semana que entra — no hoy.
     * Si fuera hoy, el chip diría dos veces lo mismo con dos nombres.
     */
    @Test
    fun `en viernes el proximo viernes es el de la semana que entra`() {
        val viernes = LocalDate.of(2026, 9, 4)

        assertEquals(LocalDate.of(2026, 9, 11), DiasSugeridos.proximoViernes(viernes))
        assertFalse(viernes in DiasSugeridos.paraPromesa(viernes))
    }

    /** En jueves, mañana Y el próximo viernes son el mismo día: no se duplica el chip. */
    @Test
    fun `en jueves manana y el viernes no producen dos chips iguales`() {
        val jueves = LocalDate.of(2026, 9, 3)

        val dias = DiasSugeridos.paraPromesa(jueves)

        assertEquals(listOf(LocalDate.of(2026, 9, 4)), dias)
    }

    @Test
    fun `las etiquetas relativas ganan al calendario`() {
        assertEquals("hoy", DiasSugeridos.etiquetaDe(martes, martes))
        assertEquals("mañana", DiasSugeridos.etiquetaDe(martes.plusDays(1), martes))
    }

    @Test
    fun `del tercer dia en adelante la etiqueta trae dia y mes`() {
        val etiqueta = DiasSugeridos.etiquetaDe(LocalDate.of(2026, 9, 4), martes)

        assertTrue("la etiqueta debe traer el dia del mes: $etiqueta", etiqueta.contains("4"))
        assertEquals("la etiqueta va en minusculas", etiqueta, etiqueta.lowercase())
    }

    @Test
    fun `las horas sugeridas van en formato de 24 horas`() {
        assertEquals("09:00", DiasSugeridos.etiquetaDe(LocalTime.of(9, 0)))
        assertEquals("19:00", DiasSugeridos.etiquetaDe(LocalTime.of(19, 0)))
        assertEquals(4, DiasSugeridos.HORAS_SUGERIDAS.size)
    }
}
