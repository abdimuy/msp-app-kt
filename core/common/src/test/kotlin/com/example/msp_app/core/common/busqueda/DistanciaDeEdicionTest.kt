package com.example.msp_app.core.common.busqueda

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-157: distancia de edición ≤ 1 (sustitución, omisión, inserción, transposición). */
class DistanciaDeEdicionTest {

    private fun d1(a: String, b: String) = DistanciaDeEdicion.dentroDeUno(a, b)

    @Test fun `iguales`() = assertTrue(d1("guadalupe", "guadalupe"))

    @Test fun `ambas vacias`() = assertTrue(d1("", ""))

    @Test fun `sustitucion al inicio`() = assertTrue(d1("buadalupe", "guadalupe"))

    @Test fun `sustitucion en medio`() = assertTrue(d1("guadalope", "guadalupe"))

    @Test fun `sustitucion al final`() = assertTrue(d1("guadalupa", "guadalupe"))

    @Test fun `omision al inicio`() = assertTrue(d1("uadalupe", "guadalupe"))

    @Test fun `omision en medio`() = assertTrue(d1("guadalpe", "guadalupe"))

    @Test fun `omision al final`() = assertTrue(d1("guadalup", "guadalupe"))

    @Test fun `insercion al inicio`() = assertTrue(d1("gguadalupe", "guadalupe"))

    @Test fun `insercion en medio`() = assertTrue(d1("guadalupie", "guadalupe"))

    @Test fun `insercion al final`() = assertTrue(d1("guadalupee", "guadalupe"))

    @Test fun `transposicion al inicio`() = assertTrue(d1("ugadalupe", "guadalupe"))

    @Test fun `transposicion en medio`() = assertTrue(d1("guadaulpe", "guadalupe"))

    @Test fun `transposicion al final`() = assertTrue(
        d1("guadalupe".dropLast(2) + "ep", "guadalupe")
    )

    @Test fun `dos sustituciones no cuentan`() = assertFalse(d1("guadelope", "guadalupe"))

    @Test fun `dos omisiones no cuentan`() = assertFalse(d1("guadlpe", "guadalupe"))

    @Test fun `dos transposiciones no cuentan`() = assertFalse(d1("ugadaulpe", "guadalupe"))

    @Test fun `una transposicion y una sustitucion no cuentan`() = assertFalse(
        d1("ugadalope", "guadalupe")
    )

    @Test fun `diferencia de largo mayor a uno no cuenta`() = assertFalse(d1("guadal", "guadalupe"))

    @Test fun `palabras sin relacion`() = assertFalse(d1("hernandez", "guadalupe"))

    @Test fun `una letra contra vacio`() = assertTrue(d1("a", ""))

    @Test fun `tramo de un texto mayor`() = assertTrue(
        DistanciaDeEdicion.dentroDeUno("hernandez", 0, 7, "hernand")
    )

    @Test fun `tramo con un error`() = assertTrue(
        DistanciaDeEdicion.dentroDeUno("hernandez", 0, 7, "hernamd")
    )

    @Test fun `es simetrica`() {
        listOf(
            "guadalupe" to "guadaulpe",
            "maria" to "mria",
            "ana" to "anna",
            "luis" to "lius"
        ).forEach { (a, b) ->
            assertEquals("$a/$b", d1(a, b), d1(b, a))
        }
    }

    /** Referencia: distancia de alineación óptima (Damerau-Levenshtein restringida) completa. */
    private fun osa(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            val costo = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + costo)
            val transpuestas = i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]
            if (transpuestas) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
        }
        return d[a.length][b.length]
    }

    @Test fun `coincide con la distancia completa en 20000 pares al azar con alfabeto chico`() {
        val azar = Random(157)
        repeat(20_000) {
            val a = String(CharArray(azar.nextInt(7)) { 'a' + azar.nextInt(3) })
            val b = String(CharArray(azar.nextInt(7)) { 'a' + azar.nextInt(3) })
            assertEquals("$a / $b", osa(a, b) <= 1, d1(a, b))
        }
    }

    @Test fun `coincide con la distancia completa en pares derivados de un error`() {
        val azar = Random(1570)
        repeat(5_000) {
            val base = String(CharArray(5 + azar.nextInt(6)) { 'a' + azar.nextInt(26) })
            val i = azar.nextInt(base.length - 1)
            val roto = when (azar.nextInt(4)) {
                0 -> base.substring(0, i) + 'z' + base.substring(i + 1)
                1 -> base.removeRange(i, i + 1)
                2 -> base.substring(0, i) + 'q' + base.substring(i)
                else -> base.substring(0, i) + base[i + 1] + base[i] + base.substring(i + 2)
            }
            assertTrue("$base / $roto", d1(base, roto))
            assertTrue(osa(base, roto) <= 1)
        }
    }
}
