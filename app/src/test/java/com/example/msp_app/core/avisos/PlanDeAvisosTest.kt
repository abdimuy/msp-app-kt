package com.example.msp_app.core.avisos

import com.example.msp_app.core.common.cobranza.domain.TipoVisitaCatalogo
import com.example.msp_app.core.common.visitas.domain.CitaCompromiso
import com.example.msp_app.core.common.visitas.domain.CompromisoVigente
import com.example.msp_app.core.common.visitas.domain.CuentaCompromiso
import com.example.msp_app.core.common.visitas.domain.EstadoCompromiso
import com.example.msp_app.core.common.visitas.domain.EstadoTarea
import com.example.msp_app.core.common.visitas.domain.TipoCompromiso
import com.example.msp_app.core.common.visitas.domain.VisitaCompromiso
import com.example.msp_app.core.common.visitas.domain.derivarCompromisos
import com.example.msp_app.core.database.entities.TareaZonaEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** E-APP-124 · Qué alarmas deben existir (spec visitas §6.6; plan P3, Tarea 13). */
class PlanDeAvisosTest {

    private val cdmx = ZoneId.of("America/Mexico_City")
    private val tijuana = ZoneId.of("America/Tijuana")
    private val ahora = Instant.parse("2026-10-27T15:00:00Z")

    private fun compromiso(
        tipo: TipoCompromiso,
        estado: EstadoCompromiso = EstadoCompromiso.VIGENTE_FUTURA,
        dia: String = "2026-10-28",
        hora: LocalTime? = null,
        visita: String = "V-1"
    ) = CompromisoVigente(100, visita, tipo, estado, LocalDate.parse(dia), hora, null)

    private fun tarea(id: String = "T-1", fecha: String = "2026-10-28", hora: String? = null) =
        TareaZonaEntity(
            ID = id,
            VENTA_ID = 100,
            CLIENTE_ID = 10,
            ZONA_CLIENTE_ID = 21,
            FECHA = fecha,
            HORA = hora,
            NOTA = null,
            REEMPLAZA_A = null,
            CREATED_AT = "2026-10-27T14:00:00Z",
            UPDATED_AT = "2026-10-27T14:00:00Z",
            CANCELADA_AT = null
        )

    private fun planear(
        compromisos: List<CompromisoVigente> = emptyList(),
        tareas: List<Pair<TareaZonaEntity, EstadoTarea>> = emptyList(),
        zona: ZoneId = cdmx
    ) = PlanDeAvisos.planear(compromisos, tareas, ahora, zona)

    @Test
    fun `promesa vigente suena a las 8 del dia - E-APP-124`() {
        val p = planear(listOf(compromiso(TipoCompromiso.PROMESA))).single()
        assertEquals(Instant.parse("2026-10-28T14:00:00Z"), p.instante)
        assertEquals("promesa", p.tipo)
        assertEquals("V-1", p.origenId)
    }

    @Test
    fun `promesa vigente hoy a las 8 que ya paso no se planea`() {
        val r = planear(
            listOf(compromiso(TipoCompromiso.PROMESA, EstadoCompromiso.VIGENTE_HOY, "2026-10-27"))
        )
        assertTrue(r.isEmpty())
    }

    @Test
    fun `cita con hora suena 30 minutos antes - E-APP-124`() {
        val p = planear(
            listOf(compromiso(TipoCompromiso.CITA, hora = LocalTime.of(16, 0)))
        ).single()
        assertEquals(Instant.parse("2026-10-28T21:30:00Z"), p.instante)
        assertEquals("cita", p.tipo)
    }

    @Test
    fun `cita sin hora suena a las 8 del dia - E-APP-124`() {
        val p = planear(listOf(compromiso(TipoCompromiso.CITA))).single()
        assertEquals(Instant.parse("2026-10-28T14:00:00Z"), p.instante)
    }

    @Test
    fun `tarea pendiente con hora suena 30 minutos antes y sin hora a las 8 - E-APP-124`() {
        val r = planear(
            tareas = listOf(
                tarea("T-1", hora = "09:30") to EstadoTarea.PENDIENTE,
                tarea("T-2") to EstadoTarea.PENDIENTE
            )
        )
        assertEquals(
            listOf(
                Instant.parse("2026-10-28T14:00:00Z") to "T-2",
                Instant.parse("2026-10-28T15:00:00Z") to "T-1"
            ),
            r.map { it.instante to it.origenId }
        )
        assertTrue(r.all { it.tipo == "tarea" })
    }

    @Test
    fun `la zona inyectada manda - E-APP-124`() {
        val enMexico = planear(listOf(compromiso(TipoCompromiso.PROMESA))).single().instante
        val enTijuana = planear(listOf(compromiso(TipoCompromiso.PROMESA)), zona = tijuana)
            .single().instante
        assertNotEquals(enMexico, enTijuana)
        assertEquals(Instant.parse("2026-10-28T15:00:00Z"), enTijuana)
    }

    @Test
    fun `los estados que no son vigentes no planean nada - E-APP-124`() {
        val estados = listOf(
            EstadoCompromiso.INCUMPLIDA,
            EstadoCompromiso.CUMPLIDA,
            EstadoCompromiso.CITA_CUMPLIDA,
            EstadoCompromiso.CERRADA_SIN_CUMPLIR
        )
        val compromisos = estados.flatMap { e ->
            TipoCompromiso.entries.map { compromiso(it, e) }
        }
        val tareas = listOf(
            EstadoTarea.ATENDIDA,
            EstadoTarea.CANCELADA,
            EstadoTarea.NO_ATENDIDA
        ).map { tarea() to it }
        assertTrue(planear(compromisos, tareas).isEmpty())
    }

    @Test
    fun `instantePasadoNoSePlanea - E-APP-124`() {
        val r = planear(
            tareas = listOf(tarea(fecha = "2026-10-27", hora = "09:00") to EstadoTarea.PENDIENTE)
        )
        assertTrue(r.isEmpty())
    }

    @Test
    fun `idEsDeterminista - E-APP-124`() {
        val a = PlanDeAvisos.idDe("cita", "V-1", ahora)
        assertEquals(a, PlanDeAvisos.idDe("cita", "V-1", ahora))
        assertEquals(64, a.length)
        assertNotEquals(a, PlanDeAvisos.idDe("promesa", "V-1", ahora))
        assertNotEquals(a, PlanDeAvisos.idDe("cita", "V-2", ahora))
        assertNotEquals(a, PlanDeAvisos.idDe("cita", "V-1", ahora.plusSeconds(1)))
        assertEquals(
            planear(listOf(compromiso(TipoCompromiso.CITA))),
            planear(listOf(compromiso(TipoCompromiso.CITA)))
        )
    }

    @Test
    fun `una visita anterior a la hora de la cita no la reemplaza y su alarma sigue - E-APP-124`() {
        val cuenta = CuentaCompromiso(100, 10)
        val conCita = VisitaCompromiso(
            "00000000-0000-3000-8000-000000000001",
            10,
            100,
            TipoVisitaCatalogo.PIDE_TIEMPO,
            Instant.parse("2026-10-26T17:00:00Z"),
            null,
            CitaCompromiso(LocalDate.parse("2026-10-28"), LocalTime.of(16, 0))
        )
        val sinCompromiso = VisitaCompromiso(
            "00000000-0000-3000-8000-000000000002",
            10,
            100,
            TipoVisitaCatalogo.PIDE_TIEMPO,
            Instant.parse("2026-10-27T16:00:00Z"),
            null,
            null
        )
        val vigente = derivarCompromisos(
            listOf(cuenta),
            listOf(conCita, sinCompromiso),
            emptyList(),
            LocalDate.parse("2026-10-27"),
            cdmx
        ).values
        val plan = PlanDeAvisos.planear(vigente, emptyList(), ahora, cdmx)
        assertEquals(
            listOf("cita" to Instant.parse("2026-10-28T21:30:00Z")),
            plan.map { it.tipo to it.instante }
        )
        assertEquals(conCita.id, plan.single().origenId)
    }
}
