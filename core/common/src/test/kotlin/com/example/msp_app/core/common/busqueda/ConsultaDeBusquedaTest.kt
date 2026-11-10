package com.example.msp_app.core.common.busqueda

import com.example.msp_app.core.common.busqueda.ConsultaDeBusqueda.PorNombre
import com.example.msp_app.core.common.busqueda.ConsultaDeBusqueda.PorNumero
import com.example.msp_app.core.common.busqueda.ConsultaDeBusqueda.Vacia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-157: cómo se interpreta lo tecleado (vacío, número o nombre). */
class ConsultaDeBusquedaTest {

    private fun q(texto: String) = ConsultaDeBusqueda.interpretar(texto)

    @Test fun `cadena vacia es vacia`() = assertEquals(Vacia, q(""))

    @Test fun `solo espacios es vacia`() = assertEquals(Vacia, q("      "))

    @Test fun `una sola letra es vacia`() = assertEquals(Vacia, q("a"))

    @Test fun `una sola letra con espacios es vacia`() = assertEquals(Vacia, q("  a  "))

    @Test fun `dos letras sueltas separadas son ruido y son vacia`() = assertEquals(Vacia, q("a b"))

    @Test fun `solo puntuacion es vacia`() = assertEquals(Vacia, q(".,-'\""))

    @Test fun `dos letras juntas ya buscan`() = assertEquals(PorNombre("ma", listOf("ma")), q("MA"))

    @Test fun `una inicial y una palabra buscan`() =
        assertEquals(PorNombre("j guadalupe", listOf("j", "guadalupe")), q("J. Guadalupe"))

    @Test fun `el normalizado conserva todas las palabras`() {
        val consulta = q("  María   GUADALUPE ") as PorNombre
        assertEquals("maria guadalupe", consulta.normalizado)
    }

    @Test fun `un digito solo es vacia`() = assertEquals(Vacia, q("7"))

    @Test fun `dos digitos son numero`() = assertEquals(PorNumero("12"), q("12"))

    @Test fun `digitos con espacios son un solo numero`() = assertEquals(
        PorNumero("1234"),
        q("12 34")
    )

    @Test fun `digitos con espacios al inicio y al final`() = assertEquals(
        PorNumero("4567"),
        q("  4567  ")
    )

    @Test fun `digitos con guion son un numero`() = assertEquals(PorNumero("1234"), q("12-34"))

    @Test fun `ceros a la izquierda se quitan`() = assertEquals(PorNumero("123"), q("0123"))

    @Test fun `solo ceros es vacia`() = assertEquals(Vacia, q("000"))

    @Test fun `digitos de ancho completo no son numero`() = assertTrue(q("１２３４") is PorNombre)

    @Test fun `numero larguisimo no truena`() = assertEquals(
        PorNumero("9".repeat(40)),
        q("9".repeat(40))
    )

    @Test fun `letras mezcladas con digitos son nombre`() = assertTrue(q("maria 2") is PorNombre)

    @Test fun `digitos pegados a letras son nombre`() = assertTrue(q("a123") is PorNombre)

    @Test fun `mas de ocho palabras se recortan a ocho para la consulta`() {
        val consulta = q("uno dos tres cuatro cinco seis siete ocho nueve diez") as PorNombre
        assertEquals(8, consulta.palabras.size)
        assertEquals("nueve diez", consulta.normalizado.substringAfter("ocho "))
    }

    @Test fun `texto de 500 letras es una sola palabra`() {
        val consulta = q("a".repeat(500)) as PorNombre
        assertEquals(1, consulta.palabras.size)
    }

    @Test fun `porcentaje solo no es vacia pero buscara literal`() = assertEquals(
        PorNombre("%%", listOf("%%")),
        q("%%")
    )

    @Test fun `un porcentaje solo es vacia por corto`() = assertEquals(Vacia, q("%"))

    @Test fun `emoji solo cuenta como caracteres`() = assertTrue(q("😀😀") is PorNombre)
}
