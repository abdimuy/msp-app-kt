package com.example.msp_app.core.common.visitas.envio

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E-APP-098 (plan visitas P2, Tarea 15; spec visitas §4.2-3, §4.2-5, §5.2.5):
 * con contrato 3 la existencia COMPLETA exige además que cada bloque de
 * compromiso que el teléfono mandó (promesa, cita) esté en el servidor IGUAL.
 * Un bloque ausente en el servidor (servidor viejo, o fila sin relleno) o
 * distinto no prueba nada: INCOMPLETA, nunca se suelta por existencia.
 */
class ExistenciaConCompromisoTest {
    private val id = "8f1c2b8e-1d3a-4e4b-9c55-0a1b2c3d4e5f"

    private val promesa = CompromisoLocal(
        promesaVentaId = 13914738,
        promesaFecha = "2026-10-20",
        promesaMontoCentavos = 15000,
        citaFecha = null,
        citaHora = null
    )
    private val cita = CompromisoLocal(
        promesaVentaId = null,
        promesaFecha = null,
        promesaMontoCentavos = null,
        citaFecha = "2026-10-18",
        citaHora = "16:30"
    )
    private val ambos = promesa.copy(citaFecha = "2026-10-18", citaHora = "16:30")

    private fun servidor(compromiso: CompromisoLocal?) = VisitaEnServidor(
        id,
        emptyList(),
        compromiso
    )

    @Test fun `E-APP-098 existenciaSinCompromisoNoSuelta`() = assertEquals(
        Existencia.INCOMPLETA,
        existenciaCompleta(id, servidor(CompromisoLocal.NINGUNO), emptySet(), 200, promesa)
    )

    @Test fun `E-APP-098 servidor viejo que no dice el compromiso no prueba nada`() = assertEquals(
        Existencia.INCOMPLETA,
        existenciaCompleta(id, servidor(null), emptySet(), 200, promesa)
    )

    @Test fun `E-APP-098 compromiso igual es completa`() {
        assertEquals(
            Existencia.COMPLETA,
            existenciaCompleta(id, servidor(promesa), emptySet(), 200, promesa)
        )
        assertEquals(
            Existencia.COMPLETA,
            existenciaCompleta(id, servidor(cita), emptySet(), 200, cita)
        )
        assertEquals(
            Existencia.COMPLETA,
            existenciaCompleta(id, servidor(ambos), emptySet(), 200, ambos)
        )
    }

    @Test fun `E-APP-098 cada campo del bloque cuenta`() {
        val distintos = listOf(
            promesa.copy(promesaVentaId = 1),
            promesa.copy(promesaVentaId = null),
            promesa.copy(promesaFecha = "2026-10-21"),
            promesa.copy(promesaMontoCentavos = 15001),
            promesa.copy(promesaMontoCentavos = null)
        )
        distintos.forEach {
            assertEquals(
                "$it",
                Existencia.INCOMPLETA,
                existenciaCompleta(id, servidor(it), emptySet(), 200, promesa)
            )
        }
        listOf(
            cita.copy(citaHora = null),
            cita.copy(citaHora = "16:31"),
            cita.copy(citaFecha = "2026-10-19")
        )
            .forEach {
                assertEquals(
                    "$it",
                    Existencia.INCOMPLETA,
                    existenciaCompleta(id, servidor(it), emptySet(), 200, cita)
                )
            }
    }

    @Test fun `E-APP-098 con dos bloques falta uno y es incompleta`() {
        assertEquals(
            Existencia.INCOMPLETA,
            existenciaCompleta(id, servidor(promesa), emptySet(), 200, ambos)
        )
        assertEquals(
            Existencia.INCOMPLETA,
            existenciaCompleta(id, servidor(cita), emptySet(), 200, ambos)
        )
    }

    /** Lo que el servidor tenga de más no es asunto del teléfono (I10: la ausencia nunca borra). */
    @Test fun `un bloque de mas en el servidor no estorba`() = assertEquals(
        Existencia.COMPLETA,
        existenciaCompleta(id, servidor(ambos), emptySet(), 200, promesa)
    )

    @Test fun `sin compromiso local se comporta como antes`() {
        assertEquals(
            Existencia.COMPLETA,
            existenciaCompleta(id, servidor(null), emptySet(), 200, null)
        )
        assertEquals(
            Existencia.COMPLETA,
            existenciaCompleta(id, servidor(null), emptySet(), 200, CompromisoLocal.NINGUNO)
        )
    }

    @Test fun `el compromiso no rescata una foto que falta ni un 404`() {
        assertEquals(
            Existencia.INCOMPLETA,
            existenciaCompleta(
                id,
                VisitaEnServidor(id, emptyList(), promesa),
                setOf("f1"),
                200,
                promesa
            )
        )
        assertEquals(Existencia.NO_EXISTE, existenciaCompleta(id, null, emptySet(), 404, promesa))
    }

    @Test fun `compromisoProbado exige cada bloque local igual`() {
        assertTrue(compromisoProbado(promesa, promesa))
        assertTrue(compromisoProbado(promesa, ambos))
        assertTrue(compromisoProbado(CompromisoLocal.NINGUNO, null))
        assertFalse(compromisoProbado(promesa, null))
        assertFalse(compromisoProbado(ambos, promesa))
        assertFalse(compromisoProbado(promesa, CompromisoLocal.NINGUNO))
    }

    /**
     * Propiedad: con compromiso local, COMPLETA ⇒ cada bloque local está igual
     * en el servidor. Generador con semilla (el repo no usa librería de
     * propiedades).
     */
    @Test fun `E-APP-098 propiedad completa implica bloques locales iguales`() {
        val r = Random(SEMILLA)
        var completas = 0
        var incompletas = 0
        repeat(VUELTAS) {
            val local = compromiso(r)
            val enServidor = if (r.nextInt(4) == 0) null else compromiso(r)
            val e = existenciaCompleta(id, servidor(enServidor), emptySet(), 200, local)
            val promesaOk = !local.tienePromesa ||
                (
                    enServidor != null && enServidor.promesaFecha == local.promesaFecha &&
                        enServidor.promesaVentaId == local.promesaVentaId &&
                        enServidor.promesaMontoCentavos == local.promesaMontoCentavos
                    )
            val citaOk = !local.tieneCita ||
                (enServidor != null && enServidor.citaFecha == local.citaFecha && enServidor.citaHora == local.citaHora)
            assertEquals(
                "local=$local servidor=$enServidor",
                promesaOk && citaOk,
                e == Existencia.COMPLETA
            )
            if (e == Existencia.COMPLETA) completas++ else incompletas++
        }
        // Control positivo: el generador alcanza las dos salidas.
        assertTrue(
            "completas=$completas incompletas=$incompletas",
            completas > 0 && incompletas > 0
        )
    }

    private fun compromiso(r: Random): CompromisoLocal {
        val conPromesa = r.nextBoolean()
        val conCita = r.nextBoolean()
        return CompromisoLocal(
            promesaVentaId = if (conPromesa && r.nextBoolean()) listOf(7, 8).random(r) else null,
            promesaFecha = if (conPromesa) listOf("2026-10-20", "2026-10-21").random(r) else null,
            promesaMontoCentavos = if (conPromesa && r.nextBoolean()) {
                listOf(
                    100L,
                    200L
                ).random(r)
            } else {
                null
            },
            citaFecha = if (conCita) listOf("2026-10-18", "2026-10-19").random(r) else null,
            citaHora = if (conCita && r.nextBoolean()) listOf("09:00", "16:30").random(r) else null
        )
    }

    private companion object {
        const val SEMILLA = 20261019
        const val VUELTAS = 20_000
    }
}
