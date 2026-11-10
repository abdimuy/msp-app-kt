package com.example.msp_app.core.common.busqueda

import org.junit.Assert.assertEquals
import org.junit.Test

/** E-APP-157: cada llave del orden por separado, y su precedencia. */
class OrdenDeResultadosTest {

    private fun fila(id: Int, nombre: String) = FilaDeBusqueda(
        id,
        NormalizadorDeBusqueda.normalizar(nombre)
    )

    private fun orden(consulta: String, vararg filas: FilaDeBusqueda): List<Int> {
        val q = ConsultaDeBusqueda.interpretar(consulta) as ConsultaDeBusqueda.PorNombre
        return OrdenDeResultados.ordenar(filas.toList(), q).map { it.id }
    }

    @Test fun `el nombre exacto va antes que uno mas largo que lo contiene`() = assertEquals(
        listOf(2, 1),
        orden("ana lopez", fila(1, "ANA LOPEZ RUIZ"), fila(2, "ANA LOPEZ"))
    )

    @Test fun `el exacto gana aunque el id sea mayor y el nombre mas tarde en el alfabeto`() =
        assertEquals(
            listOf(9, 1),
            orden("zoe", fila(1, "ZOE ABAD"), fila(9, "ZOE"))
        )

    @Test fun `inicio de palabra va antes que subcadena en medio de palabra`() = assertEquals(
        listOf(2, 1),
        orden("ana", fila(1, "MARIANA RUIZ"), fila(2, "ANA RUIZ SOTO LARGO"))
    )

    @Test fun `todas las palabras como inicio de palabra`() = assertEquals(
        listOf(2, 1),
        orden("guada her", fila(1, "MAGUADA HERNANDEZ"), fila(2, "GUADALUPE HERNANDEZ SOTO LARGO"))
    )

    @Test fun `mismo orden de palabras va antes que orden distinto`() = assertEquals(
        listOf(2, 1),
        orden("maria guadalupe", fila(1, "GUADALUPE MARIA"), fila(2, "MARIA SOFIA GUADALUPE"))
    )

    @Test fun `entre iguales gana el nombre mas corto`() = assertEquals(
        listOf(2, 3, 1),
        orden("ruiz", fila(1, "ANA MARIA RUIZ SOTO"), fila(2, "ANA RUIZ"), fila(3, "ANA LUZ RUIZ"))
    )

    @Test fun `a igual largo manda el orden alfabetico`() = assertEquals(
        listOf(2, 1),
        orden("ruiz", fila(1, "LUZ RUIZ"), fila(2, "ANA RUIZ"))
    )

    @Test fun `homonimos identicos salen por CLIENTE_ID`() = assertEquals(
        listOf(3, 7, 8),
        orden("ana ruiz", fila(8, "ANA RUIZ"), fila(3, "ANA RUIZ"), fila(7, "ANA RUIZ"))
    )

    @Test fun `el orden de entrada no cambia el resultado`() {
        val filas =
            listOf(
                fila(1, "ANA RUIZ"),
                fila(2, "ANA RUIZ SOTO"),
                fila(3, "ROSA ANA RUIZ"),
                fila(4, "ANA")
            )
        val esperado = orden("ana ruiz", *filas.toTypedArray())
        repeat(5) { semilla ->
            assertEquals(
                esperado,
                orden(
                    "ana ruiz",
                    *filas.shuffled(java.util.Random(semilla.toLong())).toTypedArray()
                )
            )
        }
    }

    @Test fun `un padron guardado con la normalizacion vieja con puntuacion se ordena igual`() =
        assertEquals(
            listOf(1, 2),
            orden(
                "j guadalupe",
                FilaDeBusqueda(1, "j. guadalupe"),
                FilaDeBusqueda(2, "j. guadalupe ramos")
            )
        )

    @Test fun `texto guardado con acentos sin normalizar tambien se ordena`() = assertEquals(
        listOf(1),
        orden("pena", FilaDeBusqueda(1, "PEÑA"))
    )
}
