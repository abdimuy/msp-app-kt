package com.example.msp_app.core.visitas.envio

import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Revisión de la Tarea 14: `visitas_estado` no se manda cada 15 min. Se manda
 * cuando cambia el contenido o como latido de 1 h (alerta §12-5). E-VIS-029.
 */
class LimitadorDeEstadoDeVisitasTest {

    private val t0 = Instant.parse("2026-10-17T15:00:00Z")
    private fun datos(pendientes: Int, viejaMin: Long = 30L) = mapOf<String, Any>(
        "visitas.pendientes" to pendientes,
        "visitas.pendiente_mas_vieja_min" to viejaMin,
        "app.version" to "2.21.0"
    )

    /** Simula un día de corridas cada 15 min sin cambios y cuenta envíos. */
    @Test
    fun `E-VIS-029 un dia sin cambios manda a lo mas un latido por hora`() {
        val limitador = LimitadorDeEstadoDeVisitas(MemoriaDeEstadoEnProceso())
        var enviados = 0
        (0 until 96).forEach { i ->
            val ahora = t0.plusSeconds(i * 15L * 60L)
            val d = datos(pendientes = 2, viejaMin = 30L + i * 15L)
            if (limitador.debeEmitir(d, ahora)) {
                enviados++
                limitador.anotar(d, ahora)
            }
        }
        assertEquals(24, enviados)
    }

    @Test
    fun `E-VIS-029 un cambio de contenido se manda en la misma corrida`() {
        val limitador = LimitadorDeEstadoDeVisitas(MemoriaDeEstadoEnProceso())
        limitador.anotar(datos(2), t0)
        assertFalse(limitador.debeEmitir(datos(2, viejaMin = 45L), t0.plusSeconds(900)))
        assertTrue(limitador.debeEmitir(datos(3), t0.plusSeconds(900)))
    }

    @Test
    fun `E-VIS-029 la primera vez siempre se manda`() {
        assertTrue(LimitadorDeEstadoDeVisitas(MemoriaDeEstadoEnProceso()).debeEmitir(datos(0), t0))
    }

    @Test
    fun `E-VIS-029 un ultimo envio en el futuro no silencia el reporte`() {
        val limitador = LimitadorDeEstadoDeVisitas(MemoriaDeEstadoEnProceso())
        limitador.anotar(datos(2), t0.plusSeconds(86_400))
        assertTrue(limitador.debeEmitir(datos(2), t0))
    }

    /** La memoria solo vale en el aparato que la escribió: no entra al respaldo. */
    @Test
    fun `E-VIS-029 visitas_telemetria se excluye del respaldo y de la transferencia`() {
        val sinComentarios = { nombre: String ->
            File("src/main/res/xml/$nombre").readText().replace(Regex("""<!--[\s\S]*?-->"""), "")
        }
        val excluir = """<exclude domain="sharedpref" path="${PreferenciasDeEstadoEmitido.PREFS}.xml"/>"""
        val extraccion = sinComentarios("data_extraction_rules.xml")
        for (seccion in listOf("cloud-backup", "device-transfer")) {
            val cuerpo = Regex(
                """<$seccion>([\s\S]*?)</$seccion>"""
            ).find(extraccion)!!.groupValues[1]
            assertTrue(
                "<$seccion> no excluye ${PreferenciasDeEstadoEmitido.PREFS}",
                excluir in cuerpo
            )
        }
        assertTrue(excluir in sinComentarios("backup_rules.xml"))
    }

    /**
     * Un salto de reloj hacia adelante (o un teléfono dormido) mayor que el
     * latido no deja al teléfono callado: con el mismo contenido, se manda.
     */
    @Test
    fun `E-VIS-029 un salto de reloj mayor que el latido manda aunque no cambie nada`() {
        val limitador = LimitadorDeEstadoDeVisitas(MemoriaDeEstadoEnProceso())
        limitador.anotar(datos(2), t0)
        assertFalse(limitador.debeEmitir(datos(2), t0.plusSeconds(59 * 60)))
        assertTrue(limitador.debeEmitir(datos(2), t0.plusSeconds(3 * 86_400)))
    }
}
