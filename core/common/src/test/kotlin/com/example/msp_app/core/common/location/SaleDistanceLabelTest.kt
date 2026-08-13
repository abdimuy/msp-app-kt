package com.example.msp_app.core.common.location

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaleDistanceLabelTest {

    private lateinit var localeOriginal: Locale

    @Before
    fun guardarLocale() {
        localeOriginal = Locale.getDefault()
    }

    @After
    fun restaurarLocale() {
        Locale.setDefault(localeOriginal)
    }

    @Test
    fun `sin ubicacion muestra el guion, nunca un numero`() {
        assertEquals("—", SaleDistance.Unknown.label())
        assertEquals(NO_DISTANCE_LABEL, SaleDistance.Unknown.label())
    }

    @Test
    fun `el centinela heredado nunca se pinta como distancia`() {
        assertEquals(NO_DISTANCE_LABEL, SaleDistance.of(Long.MAX_VALUE).label())
    }

    @Test
    fun `cero metros`() {
        assertEquals("0 m", SaleDistance.of(0.0).label())
    }

    @Test
    fun `distancias cortas se redondean a metros enteros`() {
        assertEquals("1 m", SaleDistance.of(1.0).label())
        assertEquals("13 m", SaleDistance.of(12.7).label())
        assertEquals("850 m", SaleDistance.of(850.4).label())
        assertEquals("999 m", SaleDistance.of(999.4).label())
    }

    @Test
    fun `el borde metros-kilometros no deja un 1000 m`() {
        assertEquals("1 km", SaleDistance.of(999.6).label())
        assertEquals("1 km", SaleDistance.of(1000.0).label())
    }

    @Test
    fun `kilometros con un decimal debajo de diez`() {
        assertEquals("1.2 km", SaleDistance.of(1234.0).label())
        assertEquals("5.7 km", SaleDistance.of(5678.0).label())
    }

    @Test
    fun `no arrastra decimales inutiles`() {
        assertEquals("2 km", SaleDistance.of(2000.0).label())
        assertEquals("9 km", SaleDistance.of(9000.0).label())
        assertEquals("10 km", SaleDistance.of(9999.0).label())
    }

    @Test
    fun `desde diez kilometros el decimal desaparece`() {
        assertEquals("10 km", SaleDistance.of(10_000.0).label())
        assertEquals("25 km", SaleDistance.of(25_400.0).label())
        assertEquals("120 km", SaleDistance.of(119_900.0).label())
    }

    @Test
    fun `la distancia maxima sigue siendo un texto corto`() {
        assertEquals("20000 km", SaleDistance.of(SaleDistance.MAX_PLAUSIBLE_METERS).label())
    }

    @Test
    fun `el separador decimal no depende del idioma del dispositivo`() {
        // es-MX y fr-FR difieren en el separador decimal; el texto no puede
        // cambiar de equipo a equipo.
        Locale.setDefault(Locale.FRANCE)
        assertEquals("1.2 km", SaleDistance.of(1234.0).label())

        Locale.setDefault(Locale.forLanguageTag("es-MX"))
        assertEquals("1.2 km", SaleDistance.of(1234.0).label())
    }

    @Test
    fun `ningun valor produce notacion cientifica ni un numero gigante`() {
        val entradas = listOf(
            Long.MAX_VALUE.toDouble(),
            Long.MIN_VALUE.toDouble(),
            Double.MAX_VALUE,
            Double.MIN_VALUE,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            -1.0,
            0.0,
            0.4,
            1.0,
            999.9,
            1000.0,
            123_456.0,
            SaleDistance.MAX_PLAUSIBLE_METERS,
            SaleDistance.MAX_PLAUSIBLE_METERS + 1.0
        )

        entradas.forEach { metros ->
            val texto = SaleDistance.of(metros).label()
            assertTrue(
                "notacion cientifica para $metros: $texto",
                !texto.contains("E") && !texto.contains("e")
            )
            assertTrue(
                "texto demasiado largo para $metros: $texto",
                texto.length <= MAX_LABEL_LENGTH
            )
        }
    }

    @Test
    fun `el texto nunca supera el ancho de 20000 km en todo el rango`() {
        // Barrido del rango completo: el techo de longitud es una propiedad del
        // tipo, no de los casos que se le ocurrieron a quien escribio el test.
        var metros = 0.0
        while (metros <= SaleDistance.MAX_PLAUSIBLE_METERS) {
            val texto = SaleDistance.of(metros).label()
            assertTrue(
                "texto demasiado largo para $metros: $texto",
                texto.length <= MAX_LABEL_LENGTH
            )
            metros += PASO_BARRIDO_METROS
        }
    }

    private companion object {
        /** Longitud de "20000 km", el texto mas largo que el tipo puede producir. */
        const val MAX_LABEL_LENGTH = 8
        const val PASO_BARRIDO_METROS = 997.0
    }
}
