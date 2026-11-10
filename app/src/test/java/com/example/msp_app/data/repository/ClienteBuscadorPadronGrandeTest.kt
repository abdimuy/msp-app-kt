package com.example.msp_app.data.repository

import androidx.test.core.app.ApplicationProvider
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.padron.PadronSintetico
import java.text.Normalizer
import java.util.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * E-APP-157: el defecto medido el 2026-11-10 (dev, 43,837 clientes) — con el nombre completo
 * exacto el cliente quedaba FUERA de los resultados en 29.6 % de 1,500 muestras, porque
 * `ClienteDao.searchByNormalizedWord` traía `LIMIT 300` filas de la palabra MÁS LARGA antes de
 * filtrar las demás palabras.
 *
 * Estas pruebas usan solo la API que YA existía en la base (`ClienteRepository.searchClientes`),
 * para poder correrlas en 5a881ca6 y verlas ROJAS antes del arreglo (ver el reporte del
 * cambio). Padrón sintético y determinista de ~44,000 clientes con las frecuencias de nombres
 * del padrón real.
 */
class ClienteBuscadorPadronGrandeTest : RoomTestBase() {

    private lateinit var repository: ClienteRepository
    private lateinit var padron: List<ClienteEntity>

    /** La normalización que ya tienen guardada los teléfonos (la de la base 5a881ca6). */
    private fun normalizacionVieja(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()

    @Before
    fun sembrarPadron() = runTest {
        repository = ClienteRepository(ApplicationProvider.getApplicationContext())
        padron = PadronSintetico.generar(normalizar = ::normalizacionVieja)
        padron.chunked(2_000).forEach { db.clienteDao().insertAll(it) }
    }

    @Test
    fun `el padron sintetico se parece al real en frecuencias`() {
        fun cerca(palabra: String, objetivo: Int) {
            val n = PadronSintetico.frecuencia(padron, palabra)
            assertTrue(
                "$palabra: $n vs ~$objetivo",
                n in (objetivo * 0.8).toInt()..(objetivo * 1.2).toInt()
            )
        }
        cerca("HERNANDEZ", 4_200)
        cerca("MARIA", 4_000)
        cerca("SANCHEZ", 3_150)
        cerca("MARTINEZ", 3_100)
        cerca("GARCIA", 2_700)
        cerca("JOSE", 1_800)
        cerca("GUADALUPE", 1_300)
    }

    @Test
    fun `nombre completo exacto de 150 clientes al azar siempre sale en los primeros 20`() =
        runTest {
            val azar = Random(7)
            val muestra = List(150) { padron[azar.nextInt(padron.size)] }
            val fuera = muestra.filter { cliente ->
                val resultado = repository.searchClientes(cliente.NOMBRE)
                // Los idénticos (mismo nombre normalizado) pueden competir por el mismo lugar.
                val identicos = padron.count { it.NOMBRE_NORMALIZADO == cliente.NOMBRE_NORMALIZADO }
                identicos <= 20 && resultado.none { it.CLIENTE_ID == cliente.CLIENTE_ID }
            }
            assertEquals(
                "clientes fuera con su nombre completo: ${fuera.size} de 150",
                0,
                fuera.size
            )
        }

    @Test
    fun `nombres completos que empiezan con MARIA GUADALUPE salen todos`() = runTest {
        val marias = padron.filter { it.NOMBRE_NORMALIZADO.startsWith("maria guadalupe ") }
        assertTrue("el corpus debe tener muchas MARIA GUADALUPE", marias.size > 100)
        val fuera = marias.take(60).filter { cliente ->
            repository.searchClientes(cliente.NOMBRE).none { it.CLIENTE_ID == cliente.CLIENTE_ID }
        }
        assertEquals(0, fuera.size)
    }

    @Test
    fun `el signo de porcentaje no es comodin de LIKE`() = runTest {
        assertEquals(emptyList<ClienteEntity>(), repository.searchClientes("%%"))
    }

    @Test
    fun `el guion bajo no es comodin de LIKE`() = runTest {
        assertEquals(emptyList<ClienteEntity>(), repository.searchClientes("__"))
    }
}
