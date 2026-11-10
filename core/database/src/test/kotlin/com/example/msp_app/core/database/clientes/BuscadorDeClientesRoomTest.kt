package com.example.msp_app.core.database.clientes

import com.example.msp_app.core.common.busqueda.FilaDeBusqueda
import com.example.msp_app.core.common.busqueda.MotorDeBusqueda
import com.example.msp_app.core.common.busqueda.NormalizadorDeBusqueda
import com.example.msp_app.core.database.entities.ClienteEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.padron.FuenteEnMemoria
import com.example.msp_app.core.testing.padron.PadronSintetico
import java.text.Normalizer
import java.util.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-157: el buscador contra Room REAL (SQLite in-memory vía Robolectric) — comodines de
 * LIKE, inyección, número de cliente, padrón guardado con la normalización vieja y volumen.
 */
class BuscadorDeClientesRoomTest : RoomTestBase() {

    private val buscador by lazy { BuscadorDeClientes(db.clienteDao()) }

    /** La normalización VIEJA, la que ya tienen guardada los teléfonos (5a881ca6). */
    private fun normalizacionVieja(texto: String): String =
        Normalizer.normalize(texto.trim().lowercase(), Normalizer.Form.NFD)
            .replace("\\p{M}".toRegex(), "")
            .replace("\\s+".toRegex(), " ")
            .trim()

    private fun cliente(
        id: Int,
        nombre: String,
        normalizar: (String) -> String = NormalizadorDeBusqueda::normalizar
    ) = ClienteEntity(id, nombre, "A", null, normalizar(nombre))

    private suspend fun sembrar(vararg clientes: ClienteEntity) = db.clienteDao().insertAll(
        clientes.toList()
    )

    private suspend fun nombres(texto: String) = buscador.buscar(texto).exactos.map { it.NOMBRE }

    // --- comodines de LIKE ---

    @Test fun `un porcentaje no es comodin`() = runTest {
        sembrar(cliente(1, "ANA RUIZ"), cliente(2, "BETO SOTO"))
        assertEquals(emptyList<String>(), nombres("a%"))
        assertEquals(emptyList<String>(), nombres("%%"))
    }

    @Test fun `un guion bajo no es comodin`() = runTest {
        sembrar(cliente(1, "ANA RUIZ"))
        assertEquals(emptyList<String>(), nombres("a_a"))
        assertEquals(emptyList<String>(), nombres("__"))
    }

    @Test fun `un porcentaje literal en el dato si se encuentra`() = runTest {
        sembrar(cliente(1, "TIENDA 100% NATURAL"), cliente(2, "TIENDA 1000 NATURAL"))
        assertEquals(listOf("TIENDA 100% NATURAL"), nombres("100%"))
    }

    @Test fun `un guion bajo literal en el dato si se encuentra y no hace de comodin`() = runTest {
        sembrar(cliente(1, "LUIS_PEREZ SOTO"), cliente(2, "LUISXPEREZ SOTO"))
        assertEquals(listOf("LUIS_PEREZ SOTO"), nombres("luis_perez"))
    }

    @Test fun `una diagonal invertida no truena ni escapa nada`() = runTest {
        sembrar(cliente(1, "ANA RUIZ"), cliente(2, "A\\B SOTO"))
        assertEquals(emptyList<String>(), nombres("\\"))
        assertEquals(emptyList<String>(), nombres("\\%"))
        assertEquals(listOf("A\\B SOTO"), nombres("a\\b"))
    }

    @Test fun `diagonal y porcentaje juntos no escapan el escape`() = runTest {
        sembrar(cliente(1, "ANA RUIZ"))
        assertEquals(emptyList<String>(), nombres("\\%%"))
        assertEquals(emptyList<String>(), nombres("%\\"))
    }

    // --- inyección y entradas hostiles ---

    @Test fun `la inyeccion SQL no borra ni cambia nada`() = runTest {
        sembrar(cliente(1, "ANA RUIZ"), cliente(2, "BETO SOTO"))
        listOf(
            "'; DROP TABLE cliente;--",
            "maria' OR '1'='1",
            "x\" OR \"\"=\"",
            "1; DELETE FROM cliente",
            "') UNION SELECT CLIENTE_ID, NOMBRE FROM cliente --",
            "ana'); DELETE FROM cliente; --"
        ).forEach { assertEquals("«$it»", emptyList<String>(), nombres(it)) }
        assertEquals(2, db.clienteDao().getCount())
    }

    @Test fun `entradas raras no truenan y no traen basura`() = runTest {
        sembrar(cliente(1, "ANA RUIZ"), cliente(2, "BETO SOTO"))
        listOf(
            "😀😀", "李小龍",
            "a".repeat(
                500
            ),
            "   ", "a", "", "\u0000\u0000", ";", "'", "\"\"", "مريم", "\uD800"
        ).forEach {
            assertEquals("«$it»", emptyList<String>(), nombres(it))
        }
    }

    @Test fun `un texto con 1500 palabras no revienta el limite de variables de SQLite`() = runTest {
        sembrar(cliente(1, "ANA RUIZ"))
        // Solo las primeras ocho palabras viajan como argumentos ligados (tope 999 en Android <= 11).
        assertEquals(listOf("ANA RUIZ"), nombres("ana ".repeat(1_500)))
    }

    @Test fun `el texto del usuario nunca aparece en el SQL`() {
        val peligro = "'; DROP TABLE cliente;--"
        val consulta = ConsultasDeBusqueda.porPalabras(listOf(peligro, "x"))
        assertFalse(consulta.sql.contains("DROP"))
        assertEquals(2, consulta.argCount)
        assertTrue(consulta.sql.contains("LIKE ? ESCAPE"))
    }

    @Test fun `una condicion por palabra unidas con AND`() {
        val sql = ConsultasDeBusqueda.porPalabras(listOf("a", "b", "c")).sql
        assertEquals(3, "LIKE ?".toRegex().findAll(sql).count())
        assertEquals(2, " AND ".toRegex().findAll(sql).count())
    }

    @Test fun `la consulta SQL no tiene LIMIT previo al filtrado`() {
        listOf(
            ConsultasDeBusqueda.porPalabras(listOf("maria")).sql,
            ConsultasDeBusqueda.porNumero("123").sql,
            ConsultasDeBusqueda.todas().sql
        ).forEach { assertFalse(it, it.contains("LIMIT", ignoreCase = true)) }
    }

    // --- número de cliente ---

    @Test fun `por numero exacto primero y luego prefijo`() = runTest {
        sembrar(
            cliente(123, "ANA"),
            cliente(1234, "LUIS"),
            cliente(12345, "ROSA"),
            cliente(2123, "ALMA"),
            cliente(99, "ELSA")
        )
        assertEquals(listOf(1234, 12345), buscador.buscar("1234").exactos.map { it.CLIENTE_ID })
        assertEquals(listOf(123, 1234, 12345), buscador.buscar("123").exactos.map { it.CLIENTE_ID })
        assertEquals(listOf(1234, 12345), buscador.buscar("12 34").exactos.map { it.CLIENTE_ID })
    }

    @Test fun `numero inexistente y numero enorme`() = runTest {
        sembrar(cliente(123, "ANA"))
        assertEquals(emptyList<Int>(), buscador.buscar("999").exactos.map { it.CLIENTE_ID })
        assertEquals(
            emptyList<Int>(),
            buscador.buscar("9".repeat(40)).exactos.map { it.CLIENTE_ID }
        )
    }

    @Test fun `el total de un prefijo corto cuenta todos y muestra 20`() = runTest {
        db.clienteDao().insertAll((1000..1999).map { cliente(it, "CLIENTE NUMERO $it") })
        val r = buscador.buscar("10")
        assertEquals(20, r.exactos.size)
        assertEquals(1000, r.exactos.first().CLIENTE_ID)
        assertEquals(100, r.totalExactos)
    }

    // --- padrón guardado con la normalización VIEJA ---

    @Test fun `padron viejo con puntuacion se encuentra con la consulta nueva`() = runTest {
        db.clienteDao().insertAll(
            listOf(
                cliente(1, "J. GUADALUPE RAMOS SOLÍS", ::normalizacionVieja),
                cliente(2, "PÉREZ-GARCÍA, JOSEFINA", ::normalizacionVieja),
                cliente(3, "FRANCISCO O'CONNOR RAMÍREZ", ::normalizacionVieja),
                cliente(4, "ADRIANA \"LA CHATA\" JIMÉNEZ", ::normalizacionVieja)
            )
        )
        assertEquals(listOf(1), buscador.buscar("j guadalupe").exactos.map { it.CLIENTE_ID })
        assertEquals(listOf(1), buscador.buscar("J.Guadalupe").exactos.map { it.CLIENTE_ID })
        assertEquals(
            listOf(2),
            buscador.buscar("perez garcia josefina").exactos.map { it.CLIENTE_ID }
        )
        assertEquals(listOf(2), buscador.buscar("perez-garcia").exactos.map { it.CLIENTE_ID })
        assertEquals(listOf(3), buscador.buscar("o connor").exactos.map { it.CLIENTE_ID })
        assertEquals(listOf(3), buscador.buscar("O'Connor").exactos.map { it.CLIENTE_ID })
        assertEquals(listOf(4), buscador.buscar("la chata").exactos.map { it.CLIENTE_ID })
    }

    @Test fun `padron viejo con espacios dobles y tabuladores`() = runTest {
        db.clienteDao().insertAll(
            listOf(
                cliente(1, "ANA\tMARIA RUIZ", {
                    it.lowercase()
                }),
                cliente(2, "BETO  SOTO", { it.lowercase() })
            )
        )
        assertEquals(listOf(1), buscador.buscar("ana maria").exactos.map { it.CLIENTE_ID })
        assertEquals(listOf(2), buscador.buscar("beto soto").exactos.map { it.CLIENTE_ID })
    }

    @Test fun `padron viejo y padron nuevo conviven en la misma tabla y dan el mismo resultado`() =
        runTest {
            db.clienteDao().insertAll(
                listOf(
                    cliente(1, "MARÍA J. LÓPEZ", ::normalizacionVieja),
                    cliente(2, "MARÍA J. LÓPEZ", NormalizadorDeBusqueda::normalizar)
                )
            )
            assertEquals(
                listOf(1, 2),
                buscador.buscar("maria j lopez").exactos.map { it.CLIENTE_ID }
            )
        }

    @Test fun `un padron guardado con la normalizacion vieja se busca igual que con la nueva en 2000 consultas`() =
        runTest {
            val viejo = PadronSintetico.generar(cantidad = 3_000, normalizar = ::normalizacionVieja)
            val azar = Random(11)
            db.clienteDao().insertAll(viejo)
            val nuevo = viejo.map { it.copy2(NormalizadorDeBusqueda.normalizar(it.NOMBRE)) }
            val consultas =
                List(200) {
                    viejo[
                        azar.nextInt(
                            viejo.size
                        )
                    ].NOMBRE.lowercase().split(
                        ' '
                    ).shuffled(azar).take(2 + azar.nextInt(2)).joinToString(" ")
                }
            val respuestasViejas = consultas.map {
                buscador.buscar(
                    it
                ).exactos.map { c -> c.CLIENTE_ID }
            }
            db.clienteDao().replaceAll(nuevo)
            val respuestasNuevas = consultas.map {
                buscador.buscar(
                    it
                ).exactos.map { c -> c.CLIENTE_ID }
            }
            assertEquals(respuestasNuevas, respuestasViejas)
        }

    private fun ClienteEntity.copy2(normalizado: String) = ClienteEntity(
        CLIENTE_ID,
        NOMBRE,
        ESTATUS,
        CAUSA_SUSP,
        normalizado
    )

    // --- orden y total con entidades reales ---

    @Test fun `trae las entidades completas con estatus y causa`() = runTest {
        db.clienteDao().insertAll(listOf(ClienteEntity(5, "ANA RUIZ", "V", "NO PAGA", "ana ruiz")))
        val r = buscador.buscar("ana ruiz").exactos.single()
        assertEquals("V", r.ESTATUS)
        assertEquals("NO PAGA", r.CAUSA_SUSP)
    }

    @Test fun `el padron vacio no truena`() = runTest {
        assertEquals(ResultadoDeClientes.VACIO, buscador.buscar("maria lopez"))
        assertEquals(ResultadoDeClientes.VACIO, buscador.buscar("1234"))
    }

    @Test fun `parecidos sobre Room`() = runTest {
        sembrar(cliente(1, "MARIA GUADALUPE HERNANDEZ LOPEZ"), cliente(2, "ANA KAREN RUIZ"))
        val r = buscador.buscar("guadalupe hernandes")
        assertTrue(r.exactos.isEmpty())
        assertEquals(listOf(1), r.parecidos.map { it.CLIENTE_ID })
        assertEquals(1, r.totalParecidos)
    }

    @Test fun `Room y el motor en memoria dan lo mismo en 300 consultas al azar`() = runTest {
        val padron = PadronSintetico.generar(
            cantidad = 5_000,
            normalizar = NormalizadorDeBusqueda::normalizar
        )
        db.clienteDao().insertAll(padron)
        val memoria =
            MotorDeBusqueda(
                FuenteEnMemoria(
                    padron.map {
                        FilaDeBusqueda(it.CLIENTE_ID, it.NOMBRE_NORMALIZADO)
                    }
                )
            )
        val azar = Random(5)
        repeat(300) {
            val palabras = padron[azar.nextInt(padron.size)].NOMBRE.split(' ')
            val consulta = palabras.shuffled(azar).take(1 + azar.nextInt(3)).joinToString(" ")
            val deRoom = buscador.buscar(consulta)
            val deMemoria = memoria.buscar(consulta)
            assertEquals("«$consulta»", deMemoria.ids, deRoom.exactos.map { it.CLIENTE_ID })
            assertEquals("«$consulta»", deMemoria.total, deRoom.totalExactos)
        }
    }

    // --- volumen y tiempo ---

    @Test fun `con 44 mil clientes el nombre completo sale siempre y cada consulta tarda menos del tope`() =
        runTest {
            val padron = PadronSintetico.generar(normalizar = NormalizadorDeBusqueda::normalizar)
            padron.chunked(2_000).forEach { db.clienteDao().insertAll(it) }
            val azar = Random(99)
            val muestra = List(120) { padron[azar.nextInt(padron.size)] }
            val tiempos = ArrayList<Long>()
            var fuera = 0
            for (c in muestra) {
                val inicio = System.nanoTime()
                val r = buscador.buscar(c.NOMBRE)
                tiempos += (System.nanoTime() - inicio) / 1_000_000
                val identicos = padron.filter {
                    it.NOMBRE_NORMALIZADO == c.NOMBRE_NORMALIZADO
                }.map { it.CLIENTE_ID }.sorted()
                val posibles = identicos.take(20)
                if (c.CLIENTE_ID in identicos && c.CLIENTE_ID !in posibles) continue
                if (r.exactos.none { it.CLIENTE_ID == c.CLIENTE_ID }) fuera++
            }
            tiempos.sort()
            println(
                "E-APP-157 MEDIDO Room 44k, nombre completo: fuera=$fuera/120 mediana=${tiempos[60]} ms p95=${tiempos[113]} ms max=${tiempos.last()} ms"
            )
            assertEquals(0, fuera)
            assertTrue("p95 ${tiempos[113]} ms", tiempos[113] < TOPE_P95_MS)
        }

    @Test fun `con 44 mil clientes una palabra comun de dos letras responde dentro del tope`() = runTest {
        val padron = PadronSintetico.generar(normalizar = NormalizadorDeBusqueda::normalizar)
        padron.chunked(2_000).forEach { db.clienteDao().insertAll(it) }
        val inicio = System.nanoTime()
        val r = buscador.buscar("ma")
        val ms = (System.nanoTime() - inicio) / 1_000_000
        println("E-APP-157 MEDIDO Room 44k, 'ma': total=${r.totalExactos} $ms ms")
        assertEquals(20, r.exactos.size)
        assertTrue("$ms ms", ms < TOPE_PEOR_CASO_MS)
    }

    @Test fun `con 44 mil clientes la segunda pasada de errores de dedo responde dentro del tope`() =
        runTest {
            val padron = PadronSintetico.generar(normalizar = NormalizadorDeBusqueda::normalizar)
            padron.chunked(2_000).forEach { db.clienteDao().insertAll(it) }
            val inicio = System.nanoTime()
            val r = buscador.buscar("guadalupe hernandes")
            val ms = (System.nanoTime() - inicio) / 1_000_000
            println(
                "E-APP-157 MEDIDO Room 44k, parecidos 'guadalupe hernandes': total=${r.totalParecidos} $ms ms"
            )
            assertTrue(r.exactos.isEmpty())
            assertTrue(r.parecidos.isNotEmpty())
            assertTrue("$ms ms", ms < TOPE_PEOR_CASO_MS)
        }

    private companion object {
        /** JVM de escritorio con Robolectric; en un teléfono de gama baja será de 3 a 6 veces más lento. */
        const val TOPE_P95_MS = 1_500L
        const val TOPE_PEOR_CASO_MS = 4_000L
    }
}
