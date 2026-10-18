package com.example.msp_app.core.diagnosticoenvio

import com.example.msp_app.core.database.entities.VisitEntity
import com.example.msp_app.core.testing.RoomTestBase
import com.example.msp_app.core.testing.time.FakeClock
import com.example.msp_app.core.visitas.envio.LimitadorDeEstadoDeVisitas
import com.example.msp_app.core.visitas.envio.MemoriaDeEstadoEnProceso
import com.example.msp_app.core.visitas.envio.TelemetriaDeVisitas
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plan visitas P1, Tarea 14 (H14): el evento `visitas_estado` con la base real
 * (Room en memoria): conteos de `Visit` y fallos vigentes de
 * `envio_diagnostico`, sin un solo dato de la visita.
 */
class EstadoDeEnvioDeVisitasTest : RoomTestBase() {

    private val ahora = Instant.parse("2026-10-16T12:00:00Z")

    @After
    fun limpiar() {
        TelemetriaDeVisitas.sinkParaPrueba = null
    }

    @Test
    fun `H14 el estado sale de la base y la fila atorada se ve`() = runTest {
        db.visitDao().insertVisit(
            visita("6f1c2a3b-0000-4000-8000-000000000001", "2026-10-16T09:00:00Z")
        )
        db.visitDao().insertVisit(
            visita("6f1c2a3b-0000-4000-8000-000000000002", "2026-10-16T11:00:00Z")
        )
        repeat(4) {
            db.envioDiagnosticoDao().registrarFallo(
                "visita",
                "6f1c2a3b-0000-4000-8000-000000000001",
                "visita_body_too_large_to_capture",
                "Envío muy grande",
                413,
                "2026-10-16T11:0$it:00Z"
            )
        }
        val capturado = mutableListOf<Pair<String, Map<String, Any>>>()
        TelemetriaDeVisitas.sinkParaPrueba = { e, m -> capturado += e to m }

        EstadoDeEnvioDeVisitas(
            db.visitDao(),
            db.envioDiagnosticoDao(),
            FakeClock(ahora),
            "2.21.0"
        ).emitir()

        val (evento, datos) = capturado.single()
        assertEquals("visitas_estado", evento)
        assertEquals(
            mapOf<String, Any>(
                "visitas.pendientes" to 2,
                "visitas.resguardadas" to 0,
                "visitas.confirmadas_24h" to 0,
                "app.version" to "2.21.0",
                "visitas.pendiente_mas_vieja_min" to 180L,
                "visitas.fallos.visita_body_too_large_to_capture" to 1,
                "visitas.fallos.intentos_max" to 4
            ),
            datos
        )
        val texto = datos.toString()
        assertTrue(listOf("6f1c2a3b", "30144", "Efraín", "19.04", "jueves").none { it in texto })
    }

    /** El worker corre cada 15 min; sin cambio de contenido el estado se manda una vez por hora. */
    @Test
    fun `H14 sin cambios no se vuelve a mandar en la siguiente corrida`() = runTest {
        db.visitDao().insertVisit(
            visita("6f1c2a3b-0000-4000-8000-000000000001", "2026-10-16T09:00:00Z")
        )
        val capturado = mutableListOf<String>()
        TelemetriaDeVisitas.sinkParaPrueba = { e, _ -> capturado += e }
        val clock = FakeClock(ahora)
        val estado = EstadoDeEnvioDeVisitas(
            db.visitDao(),
            db.envioDiagnosticoDao(),
            clock,
            "2.21.0",
            LimitadorDeEstadoDeVisitas(MemoriaDeEstadoEnProceso())
        )

        estado.emitir()
        clock.setNow(ahora.plusSeconds(15 * 60))
        estado.emitir()
        assertEquals(1, capturado.size)

        clock.setNow(ahora.plusSeconds(61 * 60))
        estado.emitir()
        assertEquals("latido de una hora", 2, capturado.size)
    }

    @Test
    fun `H14 si la base revienta emitir no lanza`() = runTest {
        db.close()
        TelemetriaDeVisitas.sinkParaPrueba = { _, _ -> error("no debe llegar") }
        EstadoDeEnvioDeVisitas(
            db.visitDao(),
            db.envioDiagnosticoDao(),
            FakeClock(ahora),
            "2.21.0"
        ).emitir()
    }

    private fun visita(id: String, fecha: String) = VisitEntity(
        ID = id,
        CLIENTE_ID = 30144,
        COBRADOR = "Domínguez Reyes, Efraín",
        COBRADOR_ID = 7,
        FECHA = fecha,
        FORMA_COBRO_ID = 157,
        LAT = 19.043415,
        LNG = -98.198234,
        NOTA = "No estaba, vuelvo el jueves",
        TIPO_VISITA = "SIN_PAGO",
        ZONA_CLIENTE_ID = 21
    )
}
