package com.example.msp_app.core.common.busqueda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-157: la normalización nueva (acentos, Ñ, diéresis, mayúsculas, espacios y puntuación). */
class NormalizadorDeBusquedaTest {

    private fun n(texto: String) = NormalizadorDeBusqueda.normalizar(texto)

    @Test fun `minusculas`() = assertEquals("maria lopez", n("MARIA LOPEZ"))

    @Test fun `quita acentos de las cinco vocales`() = assertEquals("aeiou", n("áéíóú"))

    @Test fun `quita acentos en mayuscula`() = assertEquals("aeiou", n("ÁÉÍÓÚ"))

    @Test fun `la eñe cuenta como ene`() = assertEquals("pena nunez", n("PEÑA NUÑEZ"))

    @Test fun `la dieresis se quita`() = assertEquals("guemes zuniga", n("GÜEMES ZÚÑIGA"))

    @Test fun `espacios dobles se colapsan`() = assertEquals("maria elena", n("maria    elena"))

    @Test fun `espacios al inicio y al final se recortan`() = assertEquals("maria", n("   maria  "))

    @Test fun `tabuladores y saltos de linea son espacios`() = assertEquals(
        "maria elena rios",
        n("maria\telena\nrios")
    )

    @Test fun `espacio de no separacion es espacio`() = assertEquals(
        "maria elena",
        n("maria elena")
    )

    @Test fun `el punto se ignora como separador`() = assertEquals("j guadalupe", n("J. GUADALUPE"))

    @Test fun `punto pegado a la siguiente palabra la separa`() = assertEquals(
        "j guadalupe",
        n("J.GUADALUPE")
    )

    @Test fun `la coma se ignora`() = assertEquals("hernandez maria", n("HERNANDEZ, MARIA"))

    @Test fun `el guion se ignora`() = assertEquals("perez garcia", n("PEREZ-GARCIA"))

    @Test fun `el guion largo y el medio tambien`() = assertEquals(
        "perez garcia ruiz",
        n("PEREZ—GARCIA–RUIZ")
    )

    @Test fun `el apostrofo recto se ignora`() = assertEquals("o connor", n("O'CONNOR"))

    @Test fun `el apostrofo tipografico se ignora`() = assertEquals("o connor", n("O’CONNOR"))

    @Test fun `las comillas dobles se ignoran`() = assertEquals(
        "adriana la chata",
        n("ADRIANA \"LA CHATA\"")
    )

    @Test fun `las comillas tipograficas se ignoran`() = assertEquals(
        "adriana la chata",
        n("ADRIANA “LA CHATA”")
    )

    @Test fun `la tilde invertida y el acento agudo suelto se ignoran`() = assertEquals(
        "o connor",
        n("O`CONNOR´")
    )

    @Test fun `solo puntuacion queda vacio`() = assertEquals("", n(".,-'\""))

    @Test fun `solo espacios queda vacio`() = assertEquals("", n("     "))

    @Test fun `vacio queda vacio`() = assertEquals("", n(""))

    @Test fun `el porcentaje es literal y no se ignora`() = assertEquals("100%", n("100%"))

    @Test fun `el guion bajo es literal y no se ignora`() = assertEquals("a_b", n("a_b"))

    @Test fun `la diagonal invertida es literal`() = assertEquals("a\\b", n("a\\b"))

    @Test fun `caracteres invisibles de ancho cero no parten palabras`() = assertEquals(
        "maria",
        n("ma​ria")
    )

    @Test fun `los emojis pasan intactos y no truenan`() = assertEquals("maria 😀", n("MARIA 😀"))

    @Test fun `caracteres chinos pasan intactos`() = assertEquals("李小龍", n("李小龍"))

    @Test fun `es idempotente`() {
        val una = n("  JOSÉ  Á. O'NEIL-ÑÚÑEZ ")
        assertEquals(una, n(una))
    }

    @Test fun `texto de 500 letras no truena`() = assertEquals(500, n("a".repeat(500)).length)

    @Test fun `normalizarGuardado acepta el texto viejo con puntuacion`() = assertEquals(
        "j guadalupe ramos",
        NormalizadorDeBusqueda.normalizarGuardado("j. guadalupe  ramos")
    )

    @Test fun `normalizarGuardado con texto no ASCII toma el camino completo`() =
        assertEquals("pena", NormalizadorDeBusqueda.normalizarGuardado("PEÑA"))

    @Test fun `normalizar y normalizarGuardado coinciden en ASCII`() {
        val texto = "Carlos  O'Brien-Ruiz, Jr."
        assertEquals(n(texto), NormalizadorDeBusqueda.normalizarGuardado(texto))
    }

    @Test fun `palabras de vacio es lista vacia`() = assertTrue(
        NormalizadorDeBusqueda.palabras("").isEmpty()
    )

    @Test fun `palabras parte por espacio`() =
        assertEquals(listOf("a", "bc", "d"), NormalizadorDeBusqueda.palabras("a bc d"))

    @Test fun `escapa porcentaje guion bajo y diagonal`() =
        assertEquals("\\%\\_\\\\", NormalizadorDeBusqueda.escaparParaLike("%_\\"))

    @Test fun `escapar no toca letras`() = assertEquals(
        "maria",
        NormalizadorDeBusqueda.escaparParaLike("maria")
    )

    @Test fun `escapar conserva comillas y punto y coma que no son comodines de LIKE`() =
        assertEquals("';\"", NormalizadorDeBusqueda.escaparParaLike("';\""))
}
