package com.example.msp_app.core.common.visitas.envio

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decisión de envío del worker de visitas v3 (spec §4.2-2, §4.2-5, §4.5 I1;
 * `ENTREGA_GARANTIZADA.md` "Pruebas que no pueden faltar", lado teléfono).
 * Cierra E-VIS-001: ningún código suelta la visita sin «la apliqué»
 * (existencia completa o eco del id) o «la tengo guardada» (`X-Intent-Captured`).
 */
class DecisionDeEnvioTest {
    private val id = "8f1c2b8e-1d3a-4e4b-9c55-0a1b2c3d4e5f"

    private val sinFotos = emptySet<String>()
    private val fotos = setOf("f1", "f2")

    @Test fun `2xx con el mismo id y sin fotos pendientes confirma con el eco`() =
        assertEquals(SalidaDeEnvio.CONFIRMADA, salidaTras2xx(id, id, sinFotos, get = null))

    /** Review Focus 1: JSON de otra visita (proxy, caché, túnel). */
    @Test fun v3_200_con_id_distinto_no_suelta() = assertEquals(
        SalidaDeEnvio.REINTENTA,
        salidaTras2xx(id, "otro", sinFotos, get = null)
    )

    /** Review Focus 1: cuerpo vacío o HTML 200 de un portal cautivo: no hay id que leer. */
    @Test fun v3_200_sin_cuerpo_no_suelta() = assertEquals(
        SalidaDeEnvio.REINTENTA,
        salidaTras2xx(id, null, sinFotos, get = null)
    )

    @Test fun `2xx con id en blanco no suelta`() {
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTras2xx(id, "", sinFotos, get = null))
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTras2xx("", "", sinFotos, get = null))
    }

    // ── 2xx con fotos (desviación del plan aprobada por el dueño 2026-10-17, E-VIS-022) ──

    @Test fun `E-VIS-022 2xx con fotos sin GET no suelta`() =
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTras2xx(id, id, fotos, get = null))

    @Test fun `E-VIS-022 2xx con una foto ausente de imagen_ids no suelta`() = assertEquals(
        SalidaDeEnvio.REINTENTA,
        salidaTras2xx(id, id, fotos, ResultadoGet(200, VisitaEnServidor(id, listOf("f1"))))
    )

    @Test fun `E-VIS-022 2xx y el GET falla por red 5xx o 404 no suelta`() = listOf(
        ResultadoGet(null, null),
        ResultadoGet(500, null),
        ResultadoGet(503, null),
        ResultadoGet(404, null)
    ).forEach {
        assertEquals("$it", SalidaDeEnvio.REINTENTA, salidaTras2xx(id, id, fotos, it))
    }

    @Test fun `E-VIS-022 2xx con imagen_ids ausente de servidor viejo no suelta`() = assertEquals(
        SalidaDeEnvio.REINTENTA,
        salidaTras2xx(id, id, fotos, ResultadoGet(200, VisitaEnServidor(id, null)))
    )

    @Test fun `E-VIS-022 2xx con GET de otra visita no suelta`() = assertEquals(
        SalidaDeEnvio.REINTENTA,
        salidaTras2xx(
            id,
            id,
            fotos,
            ResultadoGet(200, VisitaEnServidor("otro", listOf("f1", "f2")))
        )
    )

    /** Control positivo: con el GET completo, el 2xx con fotos sí se suelta. */
    @Test fun `E-VIS-022 2xx y GET completo confirma`() = assertEquals(
        SalidaDeEnvio.CONFIRMADA,
        salidaTras2xx(
            id,
            id,
            fotos,
            ResultadoGet(200, VisitaEnServidor(id, listOf("f1", "f2")))
        )
    )

    @Test fun `E-VIS-022 GET completo no rescata un eco ajeno`() = assertEquals(
        SalidaDeEnvio.REINTENTA,
        salidaTras2xx(
            id,
            "otro",
            fotos,
            ResultadoGet(200, VisitaEnServidor(id, listOf("f1", "f2")))
        )
    )

    @Test fun `existencia completa confirma ante cualquier error`() =
        listOf(400, 409, 413, 422, 500, 503).forEach {
            assertEquals(SalidaDeEnvio.CONFIRMADA, salidaTrasError(Existencia.COMPLETA, it, false))
        }

    @Test fun `409 nunca suelta sin existencia completa`() = Existencia.entries.filter {
        it != Existencia.COMPLETA
    }.forEach { e ->
        listOf(
            true,
            false
        ).forEach { cap -> assertEquals(SalidaDeEnvio.REINTENTA, salidaTrasError(e, 409, cap)) }
    }

    @Test fun `existencia sin compromiso o sin foto no suelta por existencia`() =
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTrasError(Existencia.INCOMPLETA, 500, false))

    @Test fun `4xx con custodia confirmada se resguarda`() =
        assertEquals(SalidaDeEnvio.RESGUARDADA, salidaTrasError(Existencia.NO_EXISTE, 422, true))

    @Test fun `4xx sin custodia reintenta`() =
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTrasError(Existencia.NO_EXISTE, 422, false))

    @Test fun `401 siempre reintenta`() = listOf(true, false).forEach {
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTrasError(Existencia.NO_EXISTE, 401, it))
    }

    @Test fun `404 sin llegar al api reintenta`() =
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTrasError(Existencia.INDETERMINADA, 404, false))

    @Test fun `500 sin custodia reintenta`() =
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTrasError(Existencia.NO_EXISTE, 500, false))

    /** E-VIS-020: 413 `visita_body_too_large` llega CON la visita entera en la bandeja. */
    @Test fun `413 con custodia se resguarda E-VIS-020`() =
        assertEquals(SalidaDeEnvio.RESGUARDADA, salidaTrasError(Existencia.NO_EXISTE, 413, true))

    /** E-VIS-020: 413 `visita_body_too_large_to_capture` llega SIN cabecera: nadie la tiene. */
    @Test fun `413 sin custodia reintenta E-VIS-020`() =
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTrasError(Existencia.INDETERMINADA, 413, false))

    @Test fun `un 2xx que llegara al camino de error no suelta`() = listOf(true, false).forEach {
        assertEquals(SalidaDeEnvio.REINTENTA, salidaTrasError(Existencia.NO_EXISTE, 200, it))
    }

    /**
     * Propiedades (semilla fija): nunca CONFIRMADA sin existencia completa o sin
     * eco del mismo id; nunca RESGUARDADA sin `X-Intent-Captured`; y un 2xx
     * jamás resguarda (resguardar es solo para un rechazo custodiado).
     */
    @Test fun `propiedad nunca suelta sin prueba del servidor`() {
        val r = Random(SEMILLA)
        val vistos = mutableMapOf<SalidaDeEnvio, Int>()
        repeat(VUELTAS) {
            val enviada = ids.random(r)
            val eco = (ids + null).random(r)
            val pendientes = fotosAlAzar(r).orEmpty().toSet()
            val get = getAlAzar(r)
            val tras2xx = salidaTras2xx(enviada, eco, pendientes, get)
            vistos.merge(tras2xx, 1, Int::plus)
            assertNotEquals(SalidaDeEnvio.RESGUARDADA, tras2xx)
            val ctx2xx = "enviada=$enviada eco=$eco pendientes=$pendientes get=$get"
            val ecoValido = enviada.isNotBlank() && eco == enviada
            val fotosProbadas = pendientes.isEmpty() ||
                (get?.codigo == 200 && get.visita?.id == enviada && get.visita.imagenIds?.containsAll(pendientes) == true)
            assertEquals(ctx2xx, ecoValido && fotosProbadas, tras2xx == SalidaDeEnvio.CONFIRMADA)

            val existencia = Existencia.entries.random(r)
            val codigo = if (r.nextInt(3) == 0) r.nextInt(-10, 1000) else codigos.random(r)
            val capturada = r.nextBoolean()
            val trasError = salidaTrasError(existencia, codigo, capturada)
            vistos.merge(trasError, 1, Int::plus)
            val ctx = "existencia=$existencia codigo=$codigo capturada=$capturada"
            assertEquals(
                ctx,
                existencia == Existencia.COMPLETA,
                trasError == SalidaDeEnvio.CONFIRMADA
            )
            if (trasError == SalidaDeEnvio.RESGUARDADA) {
                assertTrue(ctx, capturada)
                assertTrue(ctx, codigo in 400..599 && codigo !in setOf(401, 408, 409, 425, 429))
            }
        }
        // Control positivo: el generador alcanza las tres salidas.
        SalidaDeEnvio.entries.forEach {
            assertTrue("el generador nunca produjo $it: $vistos", (vistos[it] ?: 0) > 0)
        }
    }

    /**
     * Propiedad de la cadena completa (GET + decisión): CONFIRMADA por el camino
     * de error exige que el GET haya dicho 200 con el mismo id y todas las fotos.
     */
    @Test fun `propiedad la cadena GET mas error no confirma sin la visita completa`() {
        val r = Random(SEMILLA + 1)
        var confirmadas = 0
        repeat(VUELTAS) {
            val enviada = ids.random(r)
            val respuesta = if (r.nextInt(5) == 0) {
                null
            } else {
                VisitaEnServidor(
                    (ids + null).random(r),
                    fotosAlAzar(r)
                )
            }
            val pendientes = fotosAlAzar(r).orEmpty().toSet()
            val codigoGet = listOf(null, 200, 200, 404, 500).random(r)
            val salida =
                salidaTrasError(
                    existenciaCompleta(enviada, respuesta, pendientes, codigoGet),
                    codigos.random(r),
                    r.nextBoolean()
                )
            if (salida == SalidaDeEnvio.CONFIRMADA) {
                confirmadas++
                assertEquals(200, codigoGet)
                assertEquals(enviada, respuesta!!.id)
                assertTrue(enviada.isNotBlank())
                assertTrue(pendientes.isEmpty() || respuesta.imagenIds!!.containsAll(pendientes))
            }
        }
        assertTrue("control positivo: la cadena nunca confirmó", confirmadas > 0)
    }

    /** Propiedad pedida por el dueño (2026-10-17, E-VIS-022): nunca se confirma con una foto local ausente. */
    @Test fun `propiedad E-VIS-022 un 2xx nunca confirma con una foto local ausente de imagen_ids`() {
        val r = Random(SEMILLA + 2)
        var confirmadasConFotos = 0
        repeat(VUELTAS) {
            val enviada = ids.random(r)
            val pendientes = fotosAlAzar(r).orEmpty().toSet()
            val get = getAlAzar(r)
            if (salidaTras2xx(enviada, enviada, pendientes, get) == SalidaDeEnvio.CONFIRMADA && pendientes.isNotEmpty()) {
                confirmadasConFotos++
                val enServidor = get?.visita?.imagenIds.orEmpty()
                pendientes.forEach {
                    assertTrue(
                        "foto $it ausente y aun asi confirmo: get=$get",
                        it in enServidor
                    )
                }
            }
        }
        assertTrue("control positivo: nunca confirmo una visita con fotos", confirmadasConFotos > 0)
    }

    private fun getAlAzar(r: Random): ResultadoGet? = when (r.nextInt(6)) {
        0 -> null
        1 -> ResultadoGet(listOf(null, 404, 500, 503, 401).random(r), null)
        else -> ResultadoGet(
            listOf(200, 200, 200, 204, 500).random(r),
            if (r.nextInt(6) == 0) {
                null
            } else {
                VisitaEnServidor(
                    (ids + null).random(r),
                    fotosAlAzar(r)
                )
            }
        )
    }

    private fun fotosAlAzar(r: Random): List<String>? =
        if (r.nextInt(4) == 0) null else listOf("f1", "f2", "f3").filter { r.nextBoolean() }

    private companion object {
        const val SEMILLA = 20261017
        const val VUELTAS = 20_000
        val ids = listOf(
            "8f1c2b8e-1d3a-4e4b-9c55-0a1b2c3d4e5f",
            "8F1C2B8E-1D3A-4E4B-9C55-0A1B2C3D4E5F",
            "0b6d9a2c-7e41-4f0a-8d3e-5c2b1a0f9e8d",
            ""
        )
        val codigos =
            listOf(200, 201, 204, 400, 401, 403, 404, 408, 409, 413, 422, 425, 429, 500, 502, 503, 504)
    }
}
