package com.example.msp_app.core.common.visitas.envio

import org.junit.Assert.assertEquals
import org.junit.Test

/** E-APP-132: la brecha entre el compromiso del teléfono y el del servidor. */
class BrechaDeCompromisoTest {

    private val promesa = CompromisoLocal(100, "2026-10-20", 15000L, null, null)
    private val cita = CompromisoLocal(null, null, null, "2026-10-17", "10:00")
    private val ambos = CompromisoLocal(100, "2026-10-20", 15000L, "2026-10-17", "10:00")

    @Test
    fun `igual es completo`() {
        assertEquals(BrechaDeCompromiso.COMPLETO, brechaDeCompromiso(ambos, ambos))
    }

    @Test
    fun `servidor sin nada es por rellenar`() {
        assertEquals(
            BrechaDeCompromiso.POR_RELLENAR,
            brechaDeCompromiso(promesa, CompromisoLocal.NINGUNO)
        )
        assertEquals(
            BrechaDeCompromiso.POR_RELLENAR,
            brechaDeCompromiso(cita, CompromisoLocal.NINGUNO)
        )
    }

    @Test
    fun `falta un bloque y el otro esta igual es por rellenar`() {
        assertEquals(BrechaDeCompromiso.POR_RELLENAR, brechaDeCompromiso(ambos, promesa))
        assertEquals(BrechaDeCompromiso.POR_RELLENAR, brechaDeCompromiso(ambos, cita))
    }

    @Test
    fun `un bloque con otro valor es distinto`() {
        assertEquals(
            BrechaDeCompromiso.DISTINTO,
            brechaDeCompromiso(promesa, promesa.copy(promesaFecha = "2026-10-21"))
        )
        assertEquals(
            BrechaDeCompromiso.DISTINTO,
            brechaDeCompromiso(promesa, promesa.copy(promesaMontoCentavos = 20000L))
        )
        assertEquals(
            BrechaDeCompromiso.DISTINTO,
            brechaDeCompromiso(cita, cita.copy(citaHora = "11:00"))
        )
    }

    @Test
    fun `distinto gana sobre por rellenar`() {
        // La promesa difiere y la cita falta: reenviar traería el 422, no se reenvía.
        val servidor = promesa.copy(promesaFecha = "2026-10-21")
        assertEquals(BrechaDeCompromiso.DISTINTO, brechaDeCompromiso(ambos, servidor))
    }

    @Test
    fun `un bloque de mas en el servidor no estorba`() {
        assertEquals(BrechaDeCompromiso.COMPLETO, brechaDeCompromiso(promesa, ambos))
        assertEquals(
            BrechaDeCompromiso.COMPLETO,
            brechaDeCompromiso(CompromisoLocal.NINGUNO, ambos)
        )
    }

    @Test
    fun `sin compromiso local nunca hay brecha`() {
        assertEquals(
            BrechaDeCompromiso.COMPLETO,
            brechaDeCompromiso(CompromisoLocal.NINGUNO, CompromisoLocal.NINGUNO)
        )
    }

    /** Completo si y solo si el compromiso está probado, sobre una rejilla de combinaciones. */
    @Test
    fun `completo equivale a compromisoProbado - E-APP-132`() {
        val promesas = listOf(
            Triple<Int?, String?, Long?>(null, null, null),
            Triple(100, "2026-10-20", 15000L),
            Triple(100, "2026-10-21", 15000L),
            Triple(100, "2026-10-20", null)
        )
        val citas = listOf(
            null to null,
            "2026-10-17" to "10:00",
            "2026-10-17" to null,
            "2026-10-18" to "10:00"
        )
        val todos = promesas.flatMap { p ->
            citas.map { c -> CompromisoLocal(p.first, p.second, p.third, c.first, c.second) }
        }
        var distintos = 0
        for (local in todos) {
            for (servidor in todos) {
                val brecha = brechaDeCompromiso(local, servidor)
                assertEquals(
                    "local=$local servidor=$servidor",
                    compromisoProbado(local, servidor),
                    brecha == BrechaDeCompromiso.COMPLETO
                )
                if (brecha == BrechaDeCompromiso.DISTINTO) distintos++
            }
        }
        // Control positivo: la rejilla ejercita las tres salidas.
        assertEquals(true, distintos > 0)
    }
}
