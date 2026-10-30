package com.example.msp_app.core.ubicacion

import com.example.msp_app.core.common.location.captura.MotivoSinUbicacion
import com.example.msp_app.core.common.location.captura.OrigenDeUbicacion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** E-APP-143: eventos nuevos con lista blanca; sin datos personales. */
class TelemetriaDeUbicacionTest {

    private val emitidos = mutableListOf<Pair<String, Map<String, Any>>>()

    @Before
    fun instalar() {
        TelemetriaDeUbicacion.sinkParaPrueba = { e, d -> emitidos += e to d }
    }

    @After
    fun quitar() {
        TelemetriaDeUbicacion.sinkParaPrueba = null
    }

    @Test
    fun `E-APP-143 el primer fix manda tiempo precision y si vino de la ultima conocida`() {
        TelemetriaDeUbicacion.primerFix(
            msDesdeInicio = 4_200L,
            precisionM = 12,
            deUltimaConocida = true
        )
        assertEquals(
            listOf(
                "ubicacion_primer_fix" to mapOf<String, Any>(
                    "ubicacion.ms_hasta_primer_fix" to 4_200L,
                    "ubicacion.precision_m" to 12,
                    "ubicacion.de_ultima_conocida" to 1
                )
            ),
            emitidos
        )
    }

    @Test
    fun `E-APP-143 cada motivo de sin ubicacion tiene un codigo distinto y estable`() {
        val codigos = MotivoSinUbicacion.entries.map(TelemetriaDeUbicacion::codigoDeMotivo)
        assertEquals(listOf(0, 1, 2, 3), codigos)
        MotivoSinUbicacion.entries.forEach { TelemetriaDeUbicacion.sinUbicacion(it) }
        assertEquals(listOf(0, 1, 2, 3), emitidos.map { it.second["ubicacion.motivo"] })
        assertTrue(emitidos.all { it.first == "ubicacion_sin_ubicacion" })
    }

    @Test
    fun `E-APP-143 el guardado reporta origen antes despues o ninguno`() {
        TelemetriaDeUbicacion.guardado(OrigenDeUbicacion.ANTES, 20)
        TelemetriaDeUbicacion.guardado(OrigenDeUbicacion.DESPUES, 25)
        TelemetriaDeUbicacion.guardado(null, null)
        assertEquals(
            listOf(
                mapOf("ubicacion.origen" to 0, "ubicacion.precision_m" to 20),
                mapOf("ubicacion.origen" to 1, "ubicacion.precision_m" to 25),
                mapOf("ubicacion.origen" to 2)
            ),
            emitidos.map { it.second }
        )
    }

    @Test
    fun `E-APP-143 la lista blanca descarta claves ajenas y valores no enteros o negativos`() {
        TelemetriaDeUbicacion.emitir(
            "ubicacion_primer_fix",
            mapOf(
                "ubicacion.precision_m" to 10,
                "lat" to 20,
                "ubicacion.motivo" to "texto",
                "ubicacion.ms_hasta_primer_fix" to -1L,
                "ubicacion.origen" to 1.5
            )
        )
        assertEquals(listOf(mapOf("ubicacion.precision_m" to 10)), emitidos.map { it.second })
        emitidos.clear()
        TelemetriaDeUbicacion.emitir("otro_evento", mapOf("ubicacion.precision_m" to 10))
        TelemetriaDeUbicacion.emitir("ubicacion_primer_fix", mapOf("lat" to 20))
        assertTrue(emitidos.isEmpty())
    }

    @Test
    fun `E-APP-143 la lista de claves y eventos esta fijada a mano`() {
        assertEquals(
            setOf(
                "ubicacion.ms_hasta_primer_fix",
                "ubicacion.precision_m",
                "ubicacion.de_ultima_conocida",
                "ubicacion.motivo",
                "ubicacion.origen"
            ),
            TelemetriaDeUbicacion.CLAVES
        )
        assertEquals(
            setOf("ubicacion_primer_fix", "ubicacion_sin_ubicacion", "ubicacion_guardado"),
            TelemetriaDeUbicacion.EVENTOS
        )
    }

    @Test
    fun `E-APP-143 un sink que lanza no propaga`() {
        TelemetriaDeUbicacion.sinkParaPrueba = { _, _ -> error("caido") }
        TelemetriaDeUbicacion.primerFix(1L, 1, false)
    }
}
