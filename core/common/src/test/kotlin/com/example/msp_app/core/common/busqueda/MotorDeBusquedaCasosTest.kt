package com.example.msp_app.core.common.busqueda

import com.example.msp_app.core.testing.padron.FuenteEnMemoria
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * E-APP-157: tabla de casos del buscador sobre un padrón chico de nombres inventados. Cada fila
 * es UN caso (aparece como prueba aparte en el reporte).
 *
 * - `exactos`: ids esperados en la sección principal, EN ORDEN (lista completa).
 * - `parecidos`: ids esperados en "Parecidos", en orden.
 */
@RunWith(Parameterized::class)
class MotorDeBusquedaCasosTest(
    @Suppress("unused") private val titulo: String,
    private val consulta: String,
    private val exactos: List<Int>,
    private val parecidos: List<Int>
) {

    companion object {
        private val PADRON = arrayOf(
            1 to "MARÍA GUADALUPE HERNÁNDEZ LÓPEZ",
            2 to "MARIA GUADALUPE HERNANDEZ LOPEZ",
            3 to "JOSÉ LUIS MARTÍNEZ CRUZ",
            4 to "JOSE LUIS MARTINEZ",
            5 to "ANA KAREN DE LA CRUZ PEÑA",
            6 to "JUAN CARLOS DEL ÁNGEL RUIZ",
            7 to "ROSA MARÍA DE LOS SANTOS VÁZQUEZ",
            8 to "MARÍA DEL CARMEN NÚÑEZ IBARRA",
            9 to "FRANCISCO JAVIER O'CONNOR RAMÍREZ",
            10 to "JOSEFINA PÉREZ-GARCÍA",
            11 to "LUIS ÁNGEL MUÑOZ ZÚÑIGA",
            12 to "J. GUADALUPE RAMOS SOLÍS",
            13 to "GUADALUPE HERNÁNDEZ",
            14 to "HERNÁNDEZ GUADALUPE MARÍA",
            15 to "MARTHA PATRICIA GÓMEZ ÁLVAREZ",
            16 to "ROBERTO CARLOS SÁNCHEZ TORRES",
            17 to "BERNARDINO ESCAMILLA VARGAS",
            18 to "ÚRSULA GÜEMES BRAVO",
            19 to "MIGUEL   ANGEL   REYES",
            20 to "ADRIANA JIMÉNEZ \"LA CHATA\""
        )

        private fun caso(
            titulo: String,
            consulta: String,
            exactos: List<Int>,
            parecidos: List<Int> = emptyList()
        ) = arrayOf<Any>(titulo, consulta, exactos, parecidos)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun casos(): List<Array<Any>> =
            grupo1() + grupo2() + grupo3() + grupo4() + grupo5() + grupo6() + grupo7() + grupo8() + grupo9()

        // nombre completo, orden, partes
        private fun grupo1(): List<Array<Any>> = listOf(
            caso(
                "nombre completo exacto con acentos",
                "MARÍA GUADALUPE HERNÁNDEZ LÓPEZ",
                listOf(1, 2)
            ),
            caso(
                "nombre completo exacto sin acentos",
                "maria guadalupe hernandez lopez",
                listOf(1, 2)
            ),
            caso(
                "nombre completo en minusculas con acentos",
                "maría guadalupe hernández lópez",
                listOf(1, 2)
            ),
            caso("orden cambiado completo", "lopez hernandez guadalupe maria", listOf(1, 2)),
            caso("orden cambiado de dos nombres", "guadalupe maria", listOf(14, 1, 2)),
            caso("solo apellidos", "hernandez lopez", listOf(1, 2)),
            caso("solo un apellido comun", "hernandez", listOf(13, 14, 1, 2)),
            caso("solo nombres", "maria guadalupe", listOf(1, 2, 14)),
            caso("pedazos de palabra", "guada her", listOf(13, 1, 2, 14)),
            caso("pedazo dentro de la palabra", "uadalu", listOf(13, 12, 14, 1, 2)),
            caso("inicial y nombre", "j guadalupe", listOf(12)),
            caso("inicial con punto", "J. Guadalupe", listOf(12)),
            caso("inicial con punto pegado", "J.Guadalupe", listOf(12))
        )

        // particulas
        private fun grupo2(): List<Array<Any>> = listOf(
            caso("de la", "de la cruz", listOf(5)),
            caso("del", "del angel", listOf(6)),
            caso("de los", "de los santos", listOf(7)),
            caso("maria del carmen", "maria del carmen", listOf(8)),
            caso("apellido despues de DEL", "carmen nunez", listOf(8))
        )

        // acentos, enie, dieresis
        private fun grupo3(): List<Array<Any>> = listOf(
            caso("enie escrita como n", "pena", listOf(5)),
            caso("enie escrita como enie", "peña", listOf(5)),
            caso("enie en mayuscula", "NUÑEZ", listOf(8)),
            caso("dos enies", "muñoz zúñiga", listOf(11)),
            caso("sin enies para dos enies", "munoz zuniga", listOf(11)),
            caso("dieresis en la consulta", "güemes", listOf(18)),
            caso("dieresis y acento", "ÚRSULA GÜEMES", listOf(18)),
            caso("sin dieresis", "ursula guemes", listOf(18))
        )

        // espacios
        private fun grupo4(): List<Array<Any>> = listOf(
            caso("espacios dobles en la consulta", "maria   guadalupe", listOf(1, 2, 14)),
            caso("espacios al inicio y al final", "   maria guadalupe   ", listOf(1, 2, 14)),
            caso("tabulador entre palabras", "maria\tguadalupe", listOf(1, 2, 14)),
            caso("espacios dobles en el dato", "miguel angel", listOf(19)),
            caso("espacios dobles en dato y consulta", "  miguel    angel reyes ", listOf(19))
        )

        // puntuacion
        private fun grupo5(): List<Array<Any>> = listOf(
            caso("guion en la consulta", "perez-garcia", listOf(10)),
            caso("guion cambiado por espacio", "perez garcia", listOf(10)),
            caso("coma de apellido", "pérez, garcía", listOf(10)),
            caso("apostrofo", "o'connor", listOf(9)),
            caso("apostrofo como espacio", "o connor", listOf(9)),
            caso("comillas en la consulta", "\"la chata\"", listOf(20)),
            caso("comillas tipograficas", "“la chata” adriana", listOf(20)),
            caso("dato con puntuacion y consulta sin ella", "j guadalupe ramos", listOf(12))
        )

        // errores de dedo: solo si la exacta da 0
        private fun grupo6(): List<Array<Any>> = listOf(
            caso(
                "sustitucion al final de un apellido",
                "guadalupe hernandes",
                emptyList(),
                listOf(13, 14, 1, 2)
            ),
            caso("omision en un apellido", "guadalupe hernandz", emptyList(), listOf(13, 14, 1, 2)),
            caso(
                "insercion en un apellido",
                "guadalupe hernandeez",
                emptyList(),
                listOf(13, 14, 1, 2)
            ),
            caso(
                "transposicion en un apellido",
                "guadalupe hernadnez",
                emptyList(),
                listOf(13, 14, 1, 2)
            ),
            caso(
                "sustitucion al inicio de la palabra",
                "guadalupe bernandez",
                emptyList(),
                listOf(13, 14, 1, 2)
            ),
            caso("omision en el nombre", "guadalpe hernandez", emptyList(), listOf(13, 14, 1, 2)),
            caso(
                "transposicion en el nombre",
                "gaudalupe hernandez",
                emptyList(),
                listOf(13, 14, 1, 2)
            )
        ) + grupo6b()

        private fun grupo6b(): List<Array<Any>> = listOf(
            caso(
                "insercion en el nombre",
                "guadalupee hernandez",
                emptyList(),
                listOf(13, 14, 1, 2)
            ),
            caso(
                "error en el segundo apellido",
                "maria guadalupe hernandez lopes",
                emptyList(),
                listOf(1, 2)
            ),
            caso(
                "error en dos palabras distintas",
                "guadalpe hernandes",
                emptyList(),
                listOf(13, 14, 1, 2)
            ),
            caso("palabra de exactamente 5 letras con error", "karem", emptyList(), listOf(5)),
            caso("palabra de 5 letras con transposicion", "kraen", emptyList(), listOf(5)),
            caso("O'CONNOR tecleado pegado", "oconnor", emptyList(), listOf(9)),
            caso(
                "error en la ultima letra de un nombre",
                "guadalupx",
                emptyList(),
                listOf(13, 12, 14, 1, 2)
            ),
            caso("la busqueda exacta con resultados no muestra parecidos", "karen", listOf(5))
        )

        // palabras cortas con error: NO se toleran
        private fun grupo7(): List<Array<Any>> = listOf(
            caso("palabra de 4 letras con error", "jusn carlos", emptyList()),
            caso("palabra de 3 letras con error", "anq karen", emptyList()),
            caso("palabra de 4 letras con transposicion", "juna carlos", emptyList()),
            caso("palabra corta con error y larga correcta", "jusn hernandez", emptyList()),
            caso("solo palabras cortas con error", "luiz", emptyList()),
            caso("dos errores en una palabra larga", "guadelope hernandez", emptyList()),
            caso("control de la palabra corta correcta", "juan carlos", listOf(6))
        )

        // ruido y entradas hostiles: no truena y no trae basura
        private fun grupo8(): List<Array<Any>> = listOf(
            caso("porcentaje solo", "%%", emptyList()),
            caso("guion bajo solo", "__", emptyList()),
            caso("diagonal invertida", "\\\\", emptyList()),
            caso("una comilla", "''", emptyList()),
            caso("punto y coma", ";;", emptyList()),
            caso("inyeccion SQL clasica", "'; DROP TABLE cliente;--", emptyList()),
            caso("inyeccion con OR", "maria' OR '1'='1", emptyList()),
            caso("inyeccion con UNION", "x' UNION SELECT * FROM cliente --", emptyList()),
            caso("emojis", "😀😀", emptyList()),
            caso("caracteres chinos", "李小龍", emptyList()),
            caso("500 letras", "a".repeat(500), emptyList()),
            caso("500 letras en palabras", "maria ".repeat(100), listOf(14, 8, 1, 2, 7)),
            caso("solo espacios", "        ", emptyList()),
            caso("una sola letra", "m", emptyList()),
            caso("cadena vacia", "", emptyList()),
            caso("letra suelta con puntuacion", ". a .", emptyList()),
            caso("secuencia de control", "maria\u0000guadalupe", listOf(1, 2, 14)),
            caso("espacios de ancho cero", "ma​ria guadalupe", listOf(1, 2, 14)),
            caso("texto arabe", "مريم", emptyList()),
            caso("combinacion de emojis y nombre", "maria 😀", emptyList())
        )

        // homonimos
        private fun grupo9(): List<Array<Any>> = listOf(
            caso("MARIA sola en cinco posiciones distintas", "maria", listOf(14, 8, 1, 2, 7)),
            caso("nombre cortado a la mitad", "hernan", listOf(13, 14, 1, 2)),
            caso("apellido que no existe", "zzzzzz", emptyList()),
            caso("nombre sin ningun parecido", "xochitl", emptyList())
        )
    }

    @Test
    fun caso() = runTest {
        val motor = MotorDeBusqueda(FuenteEnMemoria.deNombres(*PADRON))
        val resultado = motor.buscar(consulta)
        assertEquals("exactos de «$consulta»", exactos, resultado.ids)
        assertEquals("total de «$consulta»", exactos.size, resultado.total)
        assertEquals("parecidos de «$consulta»", parecidos, resultado.parecidosIds)
        assertTrue(resultado.ids.size <= 20)
    }
}
