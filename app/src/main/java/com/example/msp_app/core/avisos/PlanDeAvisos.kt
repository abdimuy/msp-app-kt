package com.example.msp_app.core.avisos

import com.example.msp_app.core.common.time.AppTime
import com.example.msp_app.core.common.visitas.domain.CompromisoVigente
import com.example.msp_app.core.common.visitas.domain.EstadoCompromiso
import com.example.msp_app.core.common.visitas.domain.EstadoTarea
import com.example.msp_app.core.common.visitas.domain.TipoCompromiso
import com.example.msp_app.core.database.entities.AvisoProgramadoEntity
import com.example.msp_app.core.database.entities.TareaZonaEntity
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Una alarma que debe existir (spec visitas §6.6; plan P3, Tarea 13; E-APP-124). */
data class AvisoPlaneado(
    val id: String,
    val instante: Instant,
    val tipo: String,
    val origenId: String
) {
    fun aEntidad() = AvisoProgramadoEntity(
        ID = id,
        INSTANTE = AppTime.toWireFormat(instante),
        TIPO = tipo,
        ORIGEN_ID = origenId
    )
}

/**
 * Qué alarmas deben existir, como función pura de la agenda derivada (spec
 * visitas §6.6; plan P3, Tarea 13; E-APP-124). No lee reloj, zona ni base: todo
 * se inyecta.
 *
 * - Promesa vigente (futura u hoy): 8:00 de su día (DA-2).
 * - Cita vigente con hora: 30 min antes; sin hora: 8:00 (DA-15). Que una visita
 *   anterior a la hora de la cita no la reemplaza lo decide la derivación
 *   (`derivarCompromisos`), no este plan: aquí llega la cita vigente.
 * - Tarea pendiente con hora: 30 min antes; sin hora: 8:00.
 * - Solo instantes futuros. Cumplida, incumplida, cerrada, atendida, cancelada
 *   o no atendida: nada.
 */
object PlanDeAvisos {
    val HORA_MANANA: LocalTime = LocalTime.of(8, 0)
    const val MINUTOS_ANTES = 30L

    const val TIPO_PROMESA = "promesa"
    const val TIPO_CITA = "cita"
    const val TIPO_TAREA = "tarea"

    private val VIGENTES = setOf(EstadoCompromiso.VIGENTE_FUTURA, EstadoCompromiso.VIGENTE_HOY)

    fun planear(
        compromisos: Collection<CompromisoVigente>,
        tareas: Collection<Pair<TareaZonaEntity, EstadoTarea>>,
        ahora: Instant,
        zona: ZoneId
    ): List<AvisoPlaneado> {
        val candidatos = ArrayList<AvisoPlaneado>()
        for (c in compromisos) {
            if (c.estado !in VIGENTES) continue
            val instante = when (c.tipo) {
                TipoCompromiso.PROMESA -> instanteDe(c.dia, null, zona)
                TipoCompromiso.CITA -> instanteDe(c.dia, c.hora, zona)
            }
            candidatos += AvisoPlaneado(
                id = idDe(c.tipo.codigo, c.visitaId, instante),
                instante = instante,
                tipo = c.tipo.codigo,
                origenId = c.visitaId
            )
        }
        for ((t, estado) in tareas) {
            if (estado != EstadoTarea.PENDIENTE) continue
            val dia = AppTime.parseWireDateOrNull(t.FECHA) ?: continue
            val instante = instanteDe(dia, horaDe(t.HORA), zona)
            candidatos += AvisoPlaneado(idDe(TIPO_TAREA, t.ID, instante), instante, TIPO_TAREA, t.ID)
        }
        return candidatos
            .filter { it.instante.isAfter(ahora) }
            .distinctBy { it.id }
            .sortedWith(compareBy({ it.instante }, { it.id }))
    }

    /**
     * El plan que ya está en la tabla, sin recalcular: lo que sigue en el futuro.
     * Sirve cuando no se sabe la zona (tras un reinicio, antes de que la UI la
     * publique): la tabla es la fuente de verdad y las alarmas se reconstruyen.
     */
    fun desdeTabla(filas: Collection<AvisoProgramadoEntity>, ahora: Instant): List<AvisoPlaneado> =
        filas.mapNotNull { f ->
            AppTime.parseWireFormatOrNull(
                f.INSTANTE
            )?.let { AvisoPlaneado(f.ID, it, f.TIPO, f.ORIGEN_ID) }
        }.filter { it.instante.isAfter(ahora) }
            .sortedWith(compareBy({ it.instante }, { it.id }))

    /** sha-256 hex de `"tipo|origen|instante"`; el instante en formato de red. */
    fun idDe(tipo: String, origenId: String, instante: Instant): String {
        val bytes = "$tipo|$origenId|${AppTime.toWireFormat(instante)}".toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    private fun instanteDe(dia: LocalDate, hora: LocalTime?, zona: ZoneId): Instant =
        if (hora == null) {
            dia.atTime(HORA_MANANA).atZone(zona).toInstant()
        } else {
            dia.atTime(
                hora
            ).atZone(zona).toInstant().minusSeconds(MINUTOS_ANTES * SEGUNDOS_POR_MINUTO)
        }

    private fun horaDe(texto: String?): LocalTime? {
        if (texto.isNullOrBlank()) return null
        return try {
            LocalTime.parse(texto)
        } catch (_: DateTimeException) {
            null
        }
    }

    private const val SEGUNDOS_POR_MINUTO = 60L
}
