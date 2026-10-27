package com.example.msp_app.core.common.visitas.envio

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba de existencia COMPLETA (spec visitas §4.2-3, Review Focus 4 del plan
 * P1). El servidor (E-VIS-022) lista en `imagen_ids` solo las fotos con fila Y
 * archivo; un servidor viejo omite el campo y Gson lo deja nulo.
 */
class ExistenciaCompletaTest {
    private val id = "8f1c2b8e-1d3a-4e4b-9c55-0a1b2c3d4e5f"

    @Test fun `200 con el mismo id y sin fotos pendientes es completa`() = assertEquals(
        Existencia.COMPLETA,
        existenciaCompleta(id, VisitaEnServidor(id, emptyList()), emptySet(), 200)
    )

    @Test fun `404 no existe`() =
        assertEquals(Existencia.NO_EXISTE, existenciaCompleta(id, null, emptySet(), 404))

    @Test fun `500 o red es indeterminada`() {
        assertEquals(Existencia.INDETERMINADA, existenciaCompleta(id, null, emptySet(), 500))
        assertEquals(Existencia.INDETERMINADA, existenciaCompleta(id, null, emptySet(), null))
    }

    @Test fun `200 con otro id es indeterminada`() = assertEquals(
        Existencia.INDETERMINADA,
        existenciaCompleta(id, VisitaEnServidor("otro", emptyList()), emptySet(), 200)
    )

    @Test fun `200 sin cuerpo es indeterminada`() {
        assertEquals(Existencia.INDETERMINADA, existenciaCompleta(id, null, emptySet(), 200))
        assertEquals(
            Existencia.INDETERMINADA,
            existenciaCompleta(id, VisitaEnServidor(null, null), emptySet(), 200)
        )
    }

    @Test fun `otro 2xx distinto de 200 es indeterminada`() = assertEquals(
        Existencia.INDETERMINADA,
        existenciaCompleta(id, VisitaEnServidor(id, emptyList()), emptySet(), 204)
    )

    @Test fun `id enviado en blanco nunca es completa`() = assertEquals(
        Existencia.INDETERMINADA,
        existenciaCompleta("", VisitaEnServidor("", emptyList()), emptySet(), 200)
    )

    @Test fun existenciaSinLaFotoPendienteEsIncompleta() = assertEquals(
        Existencia.INCOMPLETA,
        existenciaCompleta(id, VisitaEnServidor(id, listOf("f1")), setOf("f1", "f2"), 200)
    )

    @Test fun imagenIdsAusenteConFotosPendientesEsIncompleta() = assertEquals(
        Existencia.INCOMPLETA,
        existenciaCompleta(id, VisitaEnServidor(id, null), setOf("f1"), 200)
    )

    @Test fun `imagen_ids vacio con fotos pendientes es incompleta`() = assertEquals(
        Existencia.INCOMPLETA,
        existenciaCompleta(id, VisitaEnServidor(id, emptyList()), setOf("f1"), 200)
    )

    @Test fun `imagen_ids ausente sin fotos pendientes es completa`() = assertEquals(
        Existencia.COMPLETA,
        existenciaCompleta(id, VisitaEnServidor(id, null), emptySet(), 200)
    )

    @Test fun `todas las fotos pendientes en imagen_ids es completa aunque haya de mas`() = assertEquals(
        Existencia.COMPLETA,
        existenciaCompleta(
            id,
            VisitaEnServidor(id, listOf("f0", "f1", "f2")),
            setOf("f1", "f2"),
            200
        )
    )

    /**
     * Propiedad (generador determinista con semilla; el repo no usa librería de
     * propiedades): COMPLETA si y solo si 200, mismo id no vacío, y toda foto
     * pendiente está en `imagen_ids`. El "solo si" impide soltar sin prueba; el
     * "si" impide que una visita probada se quede reintentando para siempre.
     */
    @Test fun `propiedad completa si y solo si 200 mismo id y todas las fotos`() {
        val r = Random(SEMILLA)
        val vistos = mutableMapOf<Existencia, Int>()
        repeat(VUELTAS) {
            val enviada = ids.random(r)
            val respuesta = if (r.nextInt(5) == 0) {
                null
            } else {
                VisitaEnServidor(
                    ids.random(r),
                    listaDeFotos(r)
                )
            }
            val pendientes = listaDeFotos(r).orEmpty().toSet()
            val codigo = codigos.random(r)

            val e = existenciaCompleta(enviada, respuesta, pendientes, codigo)
            vistos.merge(e, 1, Int::plus)

            val probada = codigo == 200 &&
                respuesta != null &&
                enviada.isNotBlank() &&
                respuesta.id == enviada &&
                (pendientes.isEmpty() || respuesta.imagenIds?.containsAll(pendientes) == true)
            assertEquals(
                "enviada=$enviada respuesta=$respuesta pendientes=$pendientes codigo=$codigo",
                probada,
                e == Existencia.COMPLETA
            )
            if (codigo == 404) assertEquals(Existencia.NO_EXISTE, e)
            if (e == Existencia.INCOMPLETA) assertTrue(pendientes.isNotEmpty())
        }
        // Control positivo: el generador alcanza las cuatro salidas.
        Existencia.entries.forEach {
            assertTrue("el generador nunca produjo $it: $vistos", (vistos[it] ?: 0) > 0)
        }
    }

    private fun listaDeFotos(r: Random): List<String>? =
        if (r.nextInt(4) == 0) null else fotos.filter { r.nextBoolean() }.shuffled(r)

    private companion object {
        const val SEMILLA = 20261017
        const val VUELTAS = 20_000
        val ids = listOf(
            "8f1c2b8e-1d3a-4e4b-9c55-0a1b2c3d4e5f",
            "8F1C2B8E-1D3A-4E4B-9C55-0A1B2C3D4E5F",
            "0b6d9a2c-7e41-4f0a-8d3e-5c2b1a0f9e8d",
            "",
            " "
        )
        val fotos = listOf("f1", "f2", "f3")
        val codigos =
            listOf(null, 200, 200, 200, 201, 204, 301, 401, 404, 409, 413, 500, 503, -1, 0)
    }
}
