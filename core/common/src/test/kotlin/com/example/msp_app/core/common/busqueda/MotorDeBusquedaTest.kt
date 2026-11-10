package com.example.msp_app.core.common.busqueda

import com.example.msp_app.core.testing.padron.FuenteEnMemoria
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-157: el flujo completo del motor (número, nombre exacto, parecidos, homónimos masivos). */
class MotorDeBusquedaTest {

    private fun filas(vararg pares: Pair<Int, String>) = FuenteEnMemoria.deNombres(*pares)

    // --- número de cliente ---

    private val numeros = filas(
        123 to "ANA RUIZ",
        1234 to "LUIS SOTO",
        12345 to "ROSA DIAZ",
        12399 to "PAUL REYES",
        4567 to "JUAN MORA",
        100200 to "ELSA NAVA",
        2123 to "ALMA PAZ"
    )

    @Test fun `numero exacto va primero y luego los que empiezan igual`() = runTest {
        assertEquals(listOf(1234, 12345), MotorDeBusqueda(numeros).buscar("1234").ids)
    }

    @Test fun `numero corto trae todos los que empiezan igual en orden de id`() = runTest {
        assertEquals(listOf(123, 1234, 12345, 12399), MotorDeBusqueda(numeros).buscar("123").ids)
    }

    @Test fun `numero con espacios`() = runTest {
        assertEquals(listOf(1234, 12345), MotorDeBusqueda(numeros).buscar("12 34").ids)
    }

    @Test fun `numero inexistente no trae nada`() = runTest {
        assertEquals(ResultadoDeBusqueda.VACIO, MotorDeBusqueda(numeros).buscar("9999"))
    }

    @Test fun `un digito busca por numero y trae los que empiezan con el`() = runTest {
        val fuente = numeros
        val r = MotorDeBusqueda(fuente).buscar("1")
        assertEquals(listOf(123, 1234, 12345, 12399, 100200), r.ids)
        assertEquals(1, fuente.llamadasPorNumero)
    }

    @Test fun `numero de mas digitos que cualquier id no truena`() = runTest {
        assertEquals(ResultadoDeBusqueda.VACIO, MotorDeBusqueda(numeros).buscar("1".repeat(40)))
    }

    @Test fun `ceros a la izquierda se ignoran`() = runTest {
        assertEquals(listOf(4567), MotorDeBusqueda(numeros).buscar("004567").ids)
    }

    @Test fun `un numero no se busca como nombre ni dispara la segunda pasada`() = runTest {
        val fuente = numeros
        MotorDeBusqueda(fuente).buscar("4567")
        assertEquals(0, fuente.llamadasPorPalabras)
        assertEquals(0, fuente.llamadasATodas)
    }

    @Test fun `el prefijo no es subcadena en medio del numero`() = runTest {
        assertFalse(2123 in MotorDeBusqueda(numeros).buscar("123").ids)
    }

    // --- segunda pasada solo si la exacta da 0 ---

    private val padron = filas(
        1 to "MARIA GUADALUPE HERNANDEZ LOPEZ",
        2 to "GUADALUPE HERNANDEZ"
    )

    @Test fun `la segunda pasada no corre si la exacta encontro algo`() = runTest {
        val fuente = padron
        MotorDeBusqueda(fuente).buscar("guadalupe hernandez")
        assertEquals(0, fuente.llamadasATodas)
    }

    @Test fun `la segunda pasada corre si la exacta dio cero y hay palabras largas`() = runTest {
        val fuente = padron
        MotorDeBusqueda(fuente).buscar("guadalupe hernandes")
        assertEquals(1, fuente.llamadasATodas)
    }

    @Test fun `la segunda pasada no corre si todas las palabras son cortas`() = runTest {
        val fuente = padron
        MotorDeBusqueda(fuente).buscar("xx yy")
        assertEquals(0, fuente.llamadasATodas)
    }

    @Test fun `con parecidos la seccion principal queda vacia`() = runTest {
        val resultado = MotorDeBusqueda(padron).buscar("guadalupe hernandes")
        assertTrue(resultado.ids.isEmpty())
        assertEquals(0, resultado.total)
        assertEquals(listOf(2, 1), resultado.parecidosIds)
        assertEquals(2, resultado.totalParecidos)
    }

    @Test fun `una consulta vacia no consulta la fuente`() = runTest {
        val fuente = padron
        MotorDeBusqueda(fuente).buscar("   ")
        assertEquals(
            0,
            fuente.llamadasPorPalabras + fuente.llamadasPorNumero + fuente.llamadasATodas
        )
    }

    // --- homónimos masivos ---

    private val apellidos = listOf(
        "LOPEZ", "RUIZ", "SOTO", "MORA", "NAVA", "PAZ", "REYES", "DIAZ", "CRUZ", "ROSAS",
        "VEGA", "LUNA", "ORTIZ", "BRAVO", "ROJAS", "SALAS", "TREJO", "ZAMORA", "IBARRA", "MENDOZA"
    )

    private fun masivo(cantidad: Int): List<Pair<Int, String>> = List(cantidad) { i ->
        (10_000 + i) to "MARIA GUADALUPE HERNANDEZ ${apellidos[i % apellidos.size]} ${apellidos[(i / 7) % apellidos.size]}" +
            " ${"X".repeat(1 + i % 3)}${i / 400}"
    }

    @Test fun `tres mil homonimos con nombre completo el buscado sale en los primeros 20`() = runTest {
        val datos = masivo(3_000)
        val motor = MotorDeBusqueda(filas(*datos.toTypedArray()))
        val azar = java.util.Random(157)
        repeat(300) {
            val (id, nombre) = datos[azar.nextInt(datos.size)]
            val resultado = motor.buscar(nombre)
            assertTrue("$nombre debe salir en los 20", id in resultado.ids)
        }
    }

    @Test fun `con homonimos masivos y nombre incompleto se informa el total y se muestran 20`() =
        runTest {
            val resultado = MotorDeBusqueda(
                filas(*masivo(3_000).toTypedArray())
            ).buscar("maria guadalupe hernandez")
            assertEquals(20, resultado.ids.size)
            assertEquals(3_000, resultado.total)
            assertTrue(resultado.hayMasDeLosMostrados)
        }

    @Test fun `con 20 o menos resultados no hay aviso de mas`() = runTest {
        val resultado = MotorDeBusqueda(filas(*masivo(15).toTypedArray())).buscar("maria guadalupe")
        assertEquals(15, resultado.ids.size)
        assertFalse(resultado.hayMasDeLosMostrados)
    }

    @Test fun `exactamente 20 resultados no dice que hay mas`() = runTest {
        val resultado = MotorDeBusqueda(filas(*masivo(20).toTypedArray())).buscar("maria guadalupe")
        assertEquals(20, resultado.ids.size)
        assertFalse(resultado.hayMasDeLosMostrados)
    }

    @Test fun `veintiun resultados si dicen que hay mas`() = runTest {
        val resultado = MotorDeBusqueda(filas(*masivo(21).toTypedArray())).buscar("maria guadalupe")
        assertTrue(resultado.hayMasDeLosMostrados)
        assertEquals(21, resultado.total)
    }

    // --- idénticos ---

    @Test fun `veinticinco nombres identicos salen en orden estable de id`() = runTest {
        val datos = (0 until 25).map { (900 + 24 - it) to "ANA MARIA RUIZ" }
        val resultado = MotorDeBusqueda(filas(*datos.toTypedArray())).buscar("ana maria ruiz")
        assertEquals((900..919).toList(), resultado.ids)
        assertEquals(25, resultado.total)
    }

    @Test fun `el resultado no depende del orden de las filas en la fuente`() = runTest {
        val datos = masivo(400)
        val a = MotorDeBusqueda(filas(*datos.toTypedArray())).buscar("hernandez lopez")
        val b = MotorDeBusqueda(
            filas(*datos.shuffled(java.util.Random(3)).toTypedArray())
        ).buscar("hernandez lopez")
        assertEquals(a, b)
    }

    @Test fun `un nombre identico con y sin acentos cuenta como identico`() = runTest {
        val resultado = MotorDeBusqueda(
            filas(5 to "JOSÉ PÉREZ", 3 to "JOSE PEREZ")
        ).buscar("José Pérez")
        assertEquals(listOf(3, 5), resultado.ids)
    }

    // --- padrón vacío y fuente con filas viejas ---

    @Test fun `padron vacio no truena`() = runTest {
        assertEquals(ResultadoDeBusqueda.VACIO, MotorDeBusqueda(filas()).buscar("maria lopez"))
    }

    @Test fun `una fila vieja con puntuacion se encuentra con la consulta nueva`() = runTest {
        val fuente =
            FuenteEnMemoria(
                listOf(FilaDeBusqueda(1, "j. guadalupe ramos"), FilaDeBusqueda(2, "o'connor ruiz"))
            )
        val motor = MotorDeBusqueda(fuente)
        assertEquals(listOf(1), motor.buscar("J Guadalupe").ids)
        assertEquals(listOf(2), motor.buscar("o connor").ids)
    }

    @Test fun `el limite de resultados es configurable`() = runTest {
        val resultado = MotorDeBusqueda(
            filas(*masivo(50).toTypedArray()),
            limite = 5
        ).buscar("maria")
        assertEquals(5, resultado.ids.size)
        assertEquals(50, resultado.total)
    }
}
