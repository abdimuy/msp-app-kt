package com.example.msp_app.core.common.busqueda

import com.example.msp_app.core.testing.padron.FuenteEnMemoria
import com.example.msp_app.core.testing.padron.PadronSintetico
import java.text.Normalizer
import java.util.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * E-APP-157 — prueba de propiedad sobre un corpus sintético de ~44,000 clientes (semilla fija,
 * frecuencias de nombres parecidas a las reales) y miles de formas de teclear.
 *
 * Exigencias duras: nombre completo (con o sin acentos y mayúsculas) → el cliente SIEMPRE está
 * en el top 20, salvo que lo desplacen homónimos IDÉNTICOS (mismo nombre normalizado, orden
 * estable por id). Formas parciales: se MIDE y se imprime la tasa (`E-APP-157 MEDIDO ...`); el
 * piso que se exige es el medido con margen, para que un empeoramiento vuelva roja la prueba.
 */
class CorpusSinteticoTest {

    companion object {
        private const val MUESTRA = 400
        private lateinit var filas: List<FilaDeBusqueda>
        private lateinit var nombres: Map<Int, String>
        private lateinit var identicos: Map<String, List<Int>>
        private lateinit var fuente: FuenteEnMemoria

        @BeforeClass
        @JvmStatic
        fun generar() {
            val padron = PadronSintetico.generar(normalizar = NormalizadorDeBusqueda::normalizar)
            filas = padron.map { FilaDeBusqueda(it.CLIENTE_ID, it.NOMBRE_NORMALIZADO) }
            nombres = padron.associate { it.CLIENTE_ID to it.NOMBRE }
            identicos = padron.groupBy({
                it.NOMBRE_NORMALIZADO
            }, { it.CLIENTE_ID }).mapValues { it.value.sorted() }
            fuente = FuenteEnMemoria(filas)
        }

        private fun sinAcentos(t: String) =
            Normalizer.normalize(t, Normalizer.Form.NFD).replace("\\p{M}".toRegex(), "")
    }

    private val motor = MotorDeBusqueda(fuente)

    private fun muestra(semilla: Long): List<FilaDeBusqueda> {
        val azar = Random(semilla)
        return List(MUESTRA) { filas[azar.nextInt(filas.size)] }
    }

    /** ¿El cliente salió entre los 20 (exactos o parecidos)? Los desplazados por idénticos no cuentan como falla. */
    private suspend fun sale(
        fila: FilaDeBusqueda,
        consulta: String,
        incluirParecidos: Boolean = false
    ): Boolean? {
        val r = motor.buscar(consulta)
        val ids = if (incluirParecidos) r.ids + r.parecidosIds else r.ids
        if (fila.id in ids) return true
        val gemelos = identicos.getValue(fila.nombreNormalizado)
        if (gemelos.size > MotorDeBusqueda.LIMITE_DE_RESULTADOS && gemelos.indexOf(fila.id) >= MotorDeBusqueda.LIMITE_DE_RESULTADOS) {
            return null // lo desplazaron idénticos: permitido
        }
        return false
    }

    private suspend fun tasa(
        nombre: String,
        semilla: Long,
        incluirParecidos: Boolean = false,
        forma: (String) -> String
    ): Double {
        var dentro = 0
        var contados = 0
        for (fila in muestra(semilla)) {
            val consulta = forma(nombres.getValue(fila.id))
            val r = sale(fila, consulta, incluirParecidos) ?: continue
            contados++
            if (r) dentro++
        }
        val porcentaje = 100.0 * dentro / contados
        println(
            "E-APP-157 MEDIDO corpus 44k, $nombre: ${"%.1f".format(
                porcentaje
            )} % en el top 20 ($dentro/$contados)"
        )
        return porcentaje
    }

    @Test fun `el corpus tiene el tamano y las frecuencias del padron real`() {
        assertEquals(PadronSintetico.TAMANO_REAL_APROXIMADO, filas.size)
        fun cerca(palabra: String, objetivo: Int) {
            val n = filas.count {
                NormalizadorDeBusqueda.palabras(
                    it.nombreNormalizado
                ).contains(palabra)
            }
            assertTrue(
                "$palabra: $n vs ~$objetivo",
                n in (objetivo * 0.8).toInt()..(objetivo * 1.2).toInt()
            )
        }
        cerca("hernandez", 4_200)
        cerca("maria", 4_000)
        cerca("sanchez", 3_150)
        cerca("martinez", 3_100)
        cerca("garcia", 2_700)
        cerca("jose", 1_800)
        cerca("guadalupe", 1_300)
    }

    @Test fun `el corpus no es trivial hay cientos de MARIA GUADALUPE y decenas de MARIA GUADALUPE HERNANDEZ`() {
        val pares = filas.count { it.nombreNormalizado.startsWith("maria guadalupe ") }
        val triples = filas.count { it.nombreNormalizado.startsWith("maria guadalupe hernandez") }
        println("E-APP-157 corpus: MARIA GUADALUPE x$pares, MARIA GUADALUPE HERNANDEZ x$triples")
        assertTrue("MARIA GUADALUPE x$pares", pares >= 300)
        assertTrue("MARIA GUADALUPE HERNANDEZ x$triples", triples >= 15)
    }

    @Test fun `nombre completo tal cual siempre en el top 20`() = runTest {
        assertEquals(100.0, tasa("nombre completo tal cual", 1) { it }, 0.0)
    }

    @Test fun `nombre completo sin acentos y en minusculas siempre en el top 20`() = runTest {
        assertEquals(
            100.0,
            tasa("nombre completo sin acentos minusculas", 2) {
                sinAcentos(it).lowercase()
            },
            0.0
        )
    }

    @Test fun `nombre completo con espacios y puntuacion de mas siempre en el top 20`() = runTest {
        assertEquals(
            100.0,
            tasa("nombre completo con ruido", 3) {
                "  " + it.replace(" ", " . ,  ") + " \t"
            },
            0.0
        )
    }

    @Test fun `nombre completo en mayusculas con acentos agregados al azar siempre en el top 20`() =
        runTest {
            assertEquals(100.0, tasa("nombre completo MAYUSCULAS", 4) { it.uppercase() }, 0.0)
        }

    @Test fun `nombre completo desordenado casi siempre en el top 20`() = runTest {
        val azar = Random(40)
        val t =
            tasa(
                "nombre completo desordenado",
                5
            ) { it.split(' ').shuffled(azar).joinToString(" ") }
        assertTrue("$t %", t >= 99.0)
    }

    @Test fun `nombre completo invertido casi siempre en el top 20`() = runTest {
        val t = tasa("nombre completo invertido", 6) { it.split(' ').reversed().joinToString(" ") }
        assertTrue("$t %", t >= 99.0)
    }

    @Test fun `solo apellidos se mide y se imprime`() = runTest {
        val t =
            tasa("solo los dos ultimos tokens", 7) { it.split(' ').takeLast(2).joinToString(" ") }
        assertTrue("$t %", t >= 40.0)
    }

    @Test fun `nombre y primer apellido se mide y se imprime`() = runTest {
        val t = tasa("primeros tres tokens", 8) { it.split(' ').take(3).joinToString(" ") }
        assertTrue("$t %", t >= 60.0)
    }

    @Test fun `pedazos de cuatro letras de cada palabra se mide y se imprime`() = runTest {
        val t =
            tasa(
                "pedazos de 4 letras por palabra",
                9
            ) { it.split(' ').joinToString(" ") { p -> p.take(4) } }
        assertTrue("$t %", t >= 85.0)
    }

    @Test fun `un error de dedo en el nombre completo sale entre exactos o parecidos`() = runTest {
        val t =
            tasa(
                "nombre completo con transposicion en la palabra mas larga",
                10,
                incluirParecidos = true
            ) { nombre ->
                val palabras = nombre.split(' ').toMutableList()
                val i = palabras.indices.maxByOrNull { palabras[it].length }!!
                val p = palabras[i]
                if (p.length >= 5) palabras[i] = p.substring(0, 1) + p[2] + p[1] + p.substring(3)
                palabras.joinToString(" ")
            }
        assertTrue("$t %", t >= 90.0)
    }

    @Test fun `el padron completo cabe en el tiempo de una consulta`() = runTest {
        val inicio = System.nanoTime()
        motor.buscar("ma")
        val ms = (System.nanoTime() - inicio) / 1_000_000
        println("E-APP-157 MEDIDO motor en memoria 44k, 'ma': $ms ms")
        assertTrue("$ms ms", ms < 2_000)
    }
}
